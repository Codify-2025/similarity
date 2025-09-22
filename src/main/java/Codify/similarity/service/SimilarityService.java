package Codify.similarity.service;

import Codify.similarity.core.*;
import Codify.similarity.domain.Codeline;
import Codify.similarity.domain.Result;
import Codify.similarity.exception.ErrorCode;
import Codify.similarity.exception.baseException.BaseException;
import Codify.similarity.exception.submissionexception.SameStudentComparisonException;
import Codify.similarity.exception.submissionexception.SameSubmissionComparisonException;
import Codify.similarity.exception.submissionexception.StudentSubmissionMismatchException;
import Codify.similarity.exception.submissionexception.SubmissionNotFoundException;
import Codify.similarity.model.TreeNode;
import Codify.similarity.model.TreeNodeBuilder;
import Codify.similarity.mongo.ResultDoc;
import Codify.similarity.mongo.ResultDocRepository;
import Codify.similarity.repository.CodelineRepository;
import Codify.similarity.repository.ResultRepository;
import Codify.similarity.service.dto.AnalysisResult;
import Codify.similarity.web.dto.CodelineData;
import Codify.similarity.web.dto.MessageDto;
import Codify.similarity.web.dto.ProcessResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimilarityService {

    private final ResultDocRepository resultDocRepository; // Mongo
    private final ResultRepository resultRepository; // JPA
    private final ObjectMapper objectMapper;
    private final AnalysisRuntimeRegistry runtime;
    private final CodelineRepository codelineRepository;
    private final RabbitTemplate rabbitTemplate;
    private final SseEventPublisher sseEventPublisher;


    private static final double COSINE_THRESHOLD = 0.8;
    private static final long TIMEOUT_SEC = 300;     // 기본 5분, 추후 변경 가능성 O

    private final CodelineService codelineService;

    //유사도 분석 및 결과 저장
    @Transactional
    public void analyzeAndSave(Integer assignmentId, Integer fromStudentId, Integer fromSubmissionId) {
        if (assignmentId == null || fromStudentId == null || fromSubmissionId == null) {
            // 에러 로그
            runtime.markError(assignmentId, fromStudentId, fromSubmissionId);
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // 1. 동일한 제출물 감지
        var fromSubmissionDoc = resultDocRepository.findBySubmissionId(fromSubmissionId)
                .orElseThrow(() -> {
                    runtime.markError(assignmentId, fromStudentId, fromSubmissionId);
                    return new SubmissionNotFoundException();
                });
        if (!Objects.equals(fromSubmissionDoc.getStudentId(), fromStudentId)) {
            runtime.markError(assignmentId, fromStudentId, fromSubmissionId);
            throw new StudentSubmissionMismatchException(); // 학번 != 제출물
        }
        if (!Objects.equals(fromSubmissionDoc.getAssignmentId(), assignmentId)){
            runtime.markError(assignmentId, fromStudentId, fromSubmissionId);
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE); // 과제 불일치
        }
        if (fromSubmissionDoc.getAst() == null) {
            runtime.markError(assignmentId, fromStudentId, fromSubmissionId);
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // 2. 같은 과제 & submissionId > Y
        var candidatesSubmission = resultDocRepository
                .findAllByAssignmentIdAndSubmissionIdGreaterThanAndAstIsNotNullOrderBySubmissionIdAsc( // AST 없으면 분석 X
                        assignmentId, fromSubmissionId
                );

        JsonNode fromJson = toJsonNode(fromSubmissionDoc.getAst());
        var fromVec = ASTVectorizer.buildTypeVector(fromJson);
        TreeNode fromTree = null;

        // for 루프
        for(ResultDoc candidates : candidatesSubmission) {
            // 1. 같은 제출물 감지
            if (Objects.equals(candidates.getSubmissionId(), fromSubmissionId)) {
                runtime.markError(assignmentId, fromStudentId, fromSubmissionId);
                throw new SameSubmissionComparisonException();
            }

            // 2. 같은 학생의 제출물 감지
            if (Objects.equals(candidates.getStudentId(), fromStudentId)) {
                runtime.markError(assignmentId, fromStudentId, fromSubmissionId);
                throw new SameStudentComparisonException();
            }

            JsonNode candidatesJson = toJsonNode(candidates.getAst());
            double cosine = CosineSimilarity.calculate(fromVec, ASTVectorizer.buildTypeVector(candidatesJson));

                Double normalized = null;
                List<TreeMatcher.Seg> segs = java.util.Collections.emptyList();

            if (cosine >= COSINE_THRESHOLD) {
                if (fromTree == null) fromTree = TreeNodeBuilder.fromJson(fromJson);
                TreeNode candidatesTree = TreeNodeBuilder.fromJson(candidatesJson);
                int ted = TreeEditDistance.compute(fromTree, candidatesTree);
                int maxSize = Math.max(countNodes(fromTree), countNodes(candidatesTree));
                normalized = 1.0 - ((double) ted / maxSize);

                // 개선된 매칭 사용 (구조적 유사성 고려)
                var matches = TreeMatcher.match(fromTree, candidatesTree);
                segs = TreeMatcher.toSegments(matches, /*minLines=*/2);
                
                // 디버깅 로그 추가
                log.info("Matching {} vs {}: cosine={}, normalized={}, matches={}, segs={}", 
                    fromSubmissionId, candidates.getSubmissionId(), 
                    cosine, normalized, matches.size(), segs.size());
                for (var seg : segs) {
                    log.info("  Segment: from[{}-{}] to[{}-{}]", 
                        seg.fs(), seg.fe(), seg.ts(), seg.te());
                }
            }

            try {
                // Result 저장 — accumulateResult = normalized
                Result result = Result.builder()
                        .studentFromId(fromStudentId.longValue())
                        .submissionFromId(fromSubmissionId.longValue())
                        .studentToId(candidates.getStudentId().longValue())
                        .submissionToId(candidates.getSubmissionId().longValue())
                        .accumulateResult(normalized != null ? normalized : 0.0)
                        .assignmentId(assignmentId.longValue())
                        .build();

                Result saved = resultRepository
                        .findByAssignmentIdAndSubmissionFromIdAndSubmissionToId(
                                assignmentId.longValue(),
                                fromSubmissionId.longValue(),
                                candidates.getSubmissionId().longValue()
                        )
                        .orElseGet(() -> resultRepository.save(result));

                if (!segs.isEmpty()) {
                    try {
                        codelineService.saveMergedRanges(
                                saved.getId(),
                                fromStudentId.longValue(),
                                candidates.getStudentId().longValue(),
                                segs
                        );
                    } catch (Exception e) {
                        log.warn("Codeline 저장 실패 - Result는 유지됨. resultId={}", saved.getId(), e);
                    }
                }
                runtime.markProgress(assignmentId, fromStudentId, fromSubmissionId);
            }
            catch (org.springframework.dao.DataAccessException e) {
                // 3. Result 저장(또는 조회) 자체가 실패한 케이스 → Codeline 시도하지 않음
                runtime.markError(assignmentId, fromStudentId, fromSubmissionId);
                log.error("Result 저장/조회 실패 → 이 페어는 스킵. assignmentId={}, fromSub={}, toSub={}",
                        assignmentId, fromSubmissionId, candidates.getSubmissionId(), e);
                // continue; // 다음 후보로 넘어감(배치 계속)
            }
        }
    }

    // status 폴링
    // total = AST 존재 & submissionId > Y인 수
    // done = 유사도 분석 수행 완료 후 DB에 저장된 결과 수
    // skipped = AST 없어서 skip한 갯수
    @Transactional(readOnly = true)
    public AnalysisResult status(Integer assignmentId, Integer fromStudentId, Integer fromSubmissionId) {
        int total   = resultDocRepository
                .countByAssignmentIdAndSubmissionIdGreaterThanAndAstIsNotNull(assignmentId, fromSubmissionId);
        int done    = resultRepository
                .countByAssignmentIdAndSubmissionFromId(assignmentId.longValue(), fromSubmissionId.longValue());
        int skipped = resultDocRepository
                .countByAssignmentIdAndSubmissionIdGreaterThanAndAstIsNull(assignmentId, fromSubmissionId);

        // DONE
        if (total == 0 || done >= total) {
            runtime.clear(assignmentId, fromStudentId, fromSubmissionId);
            return new AnalysisResult(AnalysisResult.Status.DONE, total, done, skipped);
        }

        // 비동기 중 에러가 기록됐으면 ERROR
        if (runtime.getLastErrorAt(assignmentId, fromStudentId, fromSubmissionId).isPresent()) {
            return new AnalysisResult(AnalysisResult.Status.ERROR, total, done, skipped);
        }

        // 타임아웃 체크: lastProgressAt 없으면 startedAt 기준
        var lastActivity = runtime.getLastProgressAt(assignmentId, fromStudentId, fromSubmissionId)
                .or(() -> runtime.getStartedAt(assignmentId, fromStudentId, fromSubmissionId))
                .orElseGet(Instant::now);

        boolean timeout = lastActivity.plusSeconds(TIMEOUT_SEC).isBefore(Instant.now());
        return new AnalysisResult(timeout ? AnalysisResult.Status.ERROR : AnalysisResult.Status.READY, total, done, skipped);
    }

    //리팩토링 로직
    //유사도 분석 및 결과 저장 - 리팩토링
    @Async("similarityExecutor")
    @Transactional
    public void analyzeAndSaveRefactor(MessageDto message) {
        Long assignmentId = message.getAssignmentId();
        List<Integer> submissionIds =
                message.getSubmissionIds().stream()
                        .map(Math::toIntExact)
                        .toList();
        // 1.mongoDB에서 모든 document 리스트 가져오기
        List<ResultDoc> results = resultDocRepository.findAllByAssignmentIdAndSubmissionIdInAndAstIsNotNull(assignmentId, submissionIds);

        //2. document의 모든 ast를 벡터화하여 Map에 저장 -> 단일 스레드

        Map<Integer, Map<String, Integer>> vectorCache = new ConcurrentHashMap<>();
        Map<Integer, TreeNode> treeCache = new ConcurrentHashMap<>();

        for (ResultDoc doc : results) {
            JsonNode json = toJsonNode(doc.getAst());
            vectorCache.put(doc.getSubmissionId(), ASTVectorizer.buildTypeVector(json));
        }

        //3.코사인 유사도 도출 -> 2차 분석까지 병렬처리
        List<CompletableFuture<ProcessResult>> futures = new
                ArrayList<>();

        // 각 submission별로 병렬 처리
        for (int i = 0; i < results.size(); i++) {
            final int fromIndex = i;
            ResultDoc fromDoc = results.get(fromIndex);

            CompletableFuture<ProcessResult> future =
                    CompletableFuture.supplyAsync(() -> {
                        return processSubmissionPairs(fromDoc, results,
                                fromIndex, vectorCache, treeCache, assignmentId);
                    });

            futures.add(future);
        }

        // 모든 병렬 작업 완료 대기 및 결과 수집
        List<Result> allResults = new ArrayList<>();
        List<CodelineData> allCodelines = new ArrayList<>();

        for (CompletableFuture<ProcessResult> future : futures)
        {
            try {
                ProcessResult processResult = future.get();
                allResults.addAll(processResult.results());
                allCodelines.addAll(processResult.codelineDataList());
            } catch (Exception e) {
                log.error("병렬 처리 중 오류 발생", e);
            }
        }

        // 4. 한꺼번에 데이터베이스 저장 -> 결과 + 코드라인 둘 다 저장
        if (!allResults.isEmpty()) {
            resultRepository.saveAll(allResults);
            log.info("총 {}개 결과 저장 완료",
                    allResults.size());
        }
        // 5. Codeline 일괄 저장
        if (!allCodelines.isEmpty()) {
            log.info("codeline 저장 시작");
            saveCodelinesBatch(allCodelines, allResults);
            log.info("codeline 저장 완료");
        }

        // SSE 완료 이벤트 발행
        try {
            sseEventPublisher.publishCompleted(message.getGroupId());
            log.info("SSE 완료 이벤트 발행 성공: groupId={}", message.getGroupId());
        } catch (Exception e) {
            log.error("SSE 완료 이벤트 발행 실패: groupId={}", message.getGroupId(), e);
        }

        //유사도 완료 후 message를 RabbitMQ에 push
        MessageDto completedMessage = new MessageDto(
                "SIMILARITY_COMPLETED",
                message.getGroupId(),
                message.getAssignmentId(),
                message.getSubmissionIds(),
                message.getTotalFiles(),
                LocalDateTime.now()
        );
        log.info("Sending similarity complete message to RabbitMQ: {}", completedMessage);
        rabbitTemplate.convertAndSend("codifyExchange", "similarity.complete", completedMessage);
        log.info("Message sent successfully");

    }


    //ast를 jsonNode로 변환
    private JsonNode toJsonNode(Object ast) {
        if (ast instanceof Map || ast instanceof java.util.List) {
            return objectMapper.valueToTree(ast);
        }
        // ast가 문자열(JSON String)로 들어온 경우도 대비
        if (ast instanceof String s) {
            try { return objectMapper.readTree(s); }
            catch (Exception ignore) { return objectMapper.valueToTree(s); }
        }
        // 그 외 타입(Bson Document 등) 대비
        return objectMapper.valueToTree(ast);
    }

    //트리노드 전체 수 계산
    private int countNodes(TreeNode node) {
        if (node == null) return 0;
        int count = 1;
        for (var child : node.children) {
            count += countNodes(child);
        }
        return count;
    }
    private ProcessResult processSubmissionPairs(
            ResultDoc fromDoc,
            List<ResultDoc> allResults,
            int fromIndex,
            Map<Integer, Map<String, Integer>> vectorCache,
            Map<Integer, TreeNode> treeCache,
            Long assignmentId) {

        List<Result> results = new ArrayList<>();
        List<CodelineData> codelineDataList = new ArrayList<>();
        var fromVec =
                vectorCache.get(fromDoc.getSubmissionId());
        TreeNode fromTree = null;

        // fromIndex + 1부터 비교 (중복 제거)
        for (int j = fromIndex + 1; j < allResults.size(); j++)
        {
            ResultDoc toDoc = allResults.get(j);
            var toVec =
                    vectorCache.get(toDoc.getSubmissionId());

            // 1차 분석: 코사인 유사도
            double cosine = CosineSimilarity.calculate(fromVec,
                    toVec);

            Double normalizedSimilarity = null;
            List<TreeMatcher.Seg> segments =
                    Collections.emptyList();

            // 2차 분석: 임계값 넘은 경우만 TED 계산
            if (cosine >= COSINE_THRESHOLD) {
                // Tree 캐싱 활용
                if (fromTree == null) {
                    fromTree =
                            treeCache.computeIfAbsent(fromDoc.getSubmissionId(),
                                    id ->
                                            TreeNodeBuilder.fromJson(toJsonNode(fromDoc.getAst())));
                }

                TreeNode toTree =
                        treeCache.computeIfAbsent(toDoc.getSubmissionId(),
                                id ->
                                        TreeNodeBuilder.fromJson(toJsonNode(toDoc.getAst())));

                // Tree Edit Distance 계산
                int ted = TreeEditDistance.compute(fromTree,
                        toTree);
                int maxSize = Math.max(countNodes(fromTree),
                        countNodes(toTree));
                normalizedSimilarity = 1.0 - ((double) ted /
                        maxSize);

                // 매칭 세그먼트 추출
                var matches = TreeMatcher.match(fromTree,
                        toTree);
                segments = TreeMatcher.toSegments(matches, 2);
            }

            // Result 객체 생성 (저장은 나중에 일괄 처리)
            Result result = Result.builder()

                    .studentFromId(fromDoc.getStudentId().longValue())

                    .submissionFromId(fromDoc.getSubmissionId().longValue())

                    .studentToId(toDoc.getStudentId().longValue())

                    .submissionToId(toDoc.getSubmissionId().longValue())
                    .accumulateResult(normalizedSimilarity !=
                            null ? normalizedSimilarity : 0.0)
                    .assignmentId(assignmentId)
                    .build();

            results.add(result);

            // Codeline 데이터는 별도로 처리 (필요시)
            if (!segments.isEmpty()) {
                var leftIntervals = segments.stream()
                        .map(seg -> new RangeUtil.Interval(seg.fs(), seg.fe()))
                        .collect(Collectors.toList());

                var rightIntervals = segments.stream()
                        .map(seg -> new RangeUtil.Interval(seg.ts(), seg.te()))
                        .collect(Collectors.toList());

                // 병합
                var leftMerged = RangeUtil.mergeRanges(leftIntervals);
                var rightMerged = RangeUtil.mergeRanges(rightIntervals);

                CodelineData codelineData = new CodelineData(
                        null, // resultId는 나중에 설정
                        fromDoc.getStudentId().longValue(),
                        toDoc.getStudentId().longValue(),
                        fromDoc.getSubmissionId().longValue(),
                        toDoc.getSubmissionId().longValue(),
                        segments,
                        leftMerged,   // 병합된 left 범위
                        rightMerged   // 병합된 right 범위
                );
                codelineDataList.add(codelineData);            }
        }

        return new ProcessResult(results, codelineDataList);
    }

    private void saveCodelinesBatch(List<CodelineData>
                                            allCodelines, List<Result> savedResults) {
        log.info("=== saveCodelinesBatch 시작 ===");
        log.info("allCodelines 크기: {}", allCodelines.size());
        log.info("savedResults 크기: {}", savedResults.size());
        // Result ID 매핑
        Map<String, Long> resultIdMap = savedResults.stream()
                .collect(Collectors.toMap(
                        result -> result.getSubmissionFromId() + "-" +
                                result.getSubmissionToId(),
                        Result::getId
                ));
        log.info("resultIdMap 크기: {}", resultIdMap.size());
        log.info("resultIdMap 내용: {}", resultIdMap);

        List<Codeline> allCodelineEntities = new ArrayList<>();
        Set<Long> resultIdsToDelete = new HashSet<>();

        for (CodelineData data : allCodelines) {
            String key = data.fromSubmissionId() + "-" +
                    data.toSubmissionId();
            Long resultId = resultIdMap.get(key);
            log.info("CodelineData 처리: key={}, resultId={}",
                    key, resultId);

            if (resultId != null) {
                resultIdsToDelete.add(resultId);
                log.info("Left ranges 크기: {}",
                        data.leftMergedRanges().size());
                log.info("Right ranges 크기: {}",
                        data.rightMergedRanges().size());

                // Left 학생 데이터
                for (var interval : data.leftMergedRanges()) {
                    allCodelineEntities.add(Codeline.builder()
                            .resultId(resultId)
                            .studentId(data.fromStudentId())
                            .startLine(interval.start())
                            .endLine(interval.end())
                            .build());
                    log.info("Left Codeline 생성: {}", allCodelineEntities);
                }

                // Right 학생 데이터
                for (var interval : data.rightMergedRanges()) {
                    allCodelineEntities.add(Codeline.builder()
                            .resultId(resultId)
                            .studentId(data.toStudentId())
                            .startLine(interval.start())
                            .endLine(interval.end())
                            .build());
                    log.info("Right Codeline 생성: {}", allCodelineEntities);
                }
                log.warn("resultId가 null입니다. key: {}", key);
            }
        }

        log.info("최종 Codeline 엔티티 크기: {}",
                allCodelineEntities.size());
        log.info("삭제할 resultIds: {}", resultIdsToDelete);

        // 일괄 삭제 및 저장
        if (!resultIdsToDelete.isEmpty()) {

            codelineRepository.deleteByResultIdIn(resultIdsToDelete);
            log.info("기존 Codeline 삭제 완료");
        }

        if (!allCodelineEntities.isEmpty()) {
            codelineRepository.saveAll(allCodelineEntities);
            log.info("총 {}개 Codeline 저장 완료",
                    allCodelineEntities.size());
        }
        log.info("=== saveCodelinesBatch 완료 ===");

    }


}