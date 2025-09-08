package Codify.similarity.service;

import Codify.similarity.domain.Result;
import Codify.similarity.exception.ErrorCode;
import Codify.similarity.exception.baseException.BaseException;
import Codify.similarity.exception.submissionexception.SameStudentComparisonException;
import Codify.similarity.exception.submissionexception.SameSubmissionComparisonException;
import Codify.similarity.exception.submissionexception.StudentSubmissionMismatchException;
import Codify.similarity.exception.submissionexception.SubmissionNotFoundException;
import Codify.similarity.model.TreeNode;
import Codify.similarity.core.ASTVectorizer;
import Codify.similarity.core.CosineSimilarity;
import Codify.similarity.core.TreeEditDistance;
import Codify.similarity.model.TreeNodeBuilder;
import Codify.similarity.mongo.ResultDoc;
import Codify.similarity.mongo.ResultDocRepository;
import Codify.similarity.repository.ResultRepository;
import Codify.similarity.service.dto.AnalysisResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimilarityService {

    private final ResultDocRepository resultDocRepository; // Mongo
    private final ResultRepository resultRepository; // JPA
    private final ObjectMapper objectMapper;
    private final AnalysisRuntimeRegistry runtime;

    private static final double COSINE_THRESHOLD = 0.8;
    private static final long TIMEOUT_SEC = 300;     // 기본 5분, 추후 변경 가능성 O

    // 비동기 처리
    /*@Async("analysisExecutor")
    @Transactional
    public void startAnalysisAsync(Integer assignmentId, Integer submissionId) {
        var doc = resultDocRepository.findBySubmissionId(submissionId)
                .orElseThrow(SubmissionNotFoundException::new);

        var studentId = doc.getStudentId();

        runtime.markStarted(assignmentId, studentId, submissionId);
        try {
            // 실제 분석 실행
            analyzeAndSave(assignmentId, studentId, submissionId);
        } catch (Exception e) {
            runtime.markError(assignmentId, studentId, submissionId);
            log.error("Async analysis failed: aId={}, subFrom={}", assignmentId, submissionId, e);
            throw e;
        }
    }*/

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

            // 2) 같은 학생의 제출물 감지
            if (Objects.equals(candidates.getStudentId(), fromStudentId)) {
                runtime.markError(assignmentId, fromStudentId, fromSubmissionId);
                throw new SameStudentComparisonException();
            }

            JsonNode candidatesJson = toJsonNode(candidates.getAst());
            double cosine = CosineSimilarity.calculate(fromVec, ASTVectorizer.buildTypeVector(candidatesJson));

            Double normalized = null;
            if (cosine >= COSINE_THRESHOLD) {
                if (fromTree == null) fromTree = TreeNodeBuilder.fromJson(fromJson);
                TreeNode candidatesTree = TreeNodeBuilder.fromJson(candidatesJson);
                int ted = TreeEditDistance.compute(fromTree, candidatesTree);
                int maxSize = Math.max(countNodes(fromTree), countNodes(candidatesTree));
                normalized = 1.0 - ((double) ted / maxSize);
            }

            // Result 저장 — accumulateResult = normalized
            Result result = Result.builder()
                    .studentFromId(fromStudentId.longValue())
                    .submissionFromId(fromSubmissionId.longValue())
                    .studentToId(candidates.getStudentId().longValue())
                    .submissionToId(candidates.getSubmissionId().longValue())
                    .accumulateResult(normalized != null ? normalized : 0.0)
                    .assignmentId(assignmentId.longValue())
                    .build();

            resultRepository.save(result);

            runtime.markProgress(assignmentId, fromStudentId, fromSubmissionId);
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

    private int countNodes(TreeNode node) {
        if (node == null) return 0;
        int count = 1;
        for (var child : node.children) {
            count += countNodes(child);
        }
        return count;
    }
}