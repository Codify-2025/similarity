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
import Codify.similarity.mongo.ResultDocRepository;
import Codify.similarity.repository.ResultRepository;
import Codify.similarity.service.dto.AnalysisResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class SimilarityService {

    private final ResultDocRepository resultDocRepository; // Mongo
    private final ResultRepository resultRepository; // JPA
    private final ObjectMapper objectMapper;

    private static final double COSINE_THRESHOLD = 0.6;

    @Transactional
    public AnalysisResult analyzeAndSave(
            Long fromStudentId, Long fromSubmissionId, Long toStudentId, Long toSubmissionId
    ) {
        try {
            // 1. 동일한 제출물 감지
            if (fromSubmissionId == null || toSubmissionId == null
                    || fromStudentId == null || toStudentId == null) {
                throw new BaseException(ErrorCode.INVALID_INPUT_VALUE);
            }
            if (fromSubmissionId.equals(toSubmissionId)) {
                throw new SameSubmissionComparisonException();
            }
            if (fromStudentId.equals(toStudentId)) {
                throw new SameStudentComparisonException();
            }

            // 2. exception: submissionId 존재 여부 감지
            var fromSubmissionDoc = resultDocRepository.findBySubmissionId(fromSubmissionId)
                    .orElseThrow(SubmissionNotFoundException::new);
            var toSubmissionDoc = resultDocRepository.findBySubmissionId(toSubmissionId)
                    .orElseThrow(SubmissionNotFoundException::new);

            // 3. 제출물-학번 매칭 감지
            if (!fromStudentId.equals(fromSubmissionDoc.getStudentId())
                    || !toStudentId.equals(toSubmissionDoc.getStudentId())) {
                throw new StudentSubmissionMismatchException();
            }

            // AST 비어 있는 경우 -> READY 반환
            if (fromSubmissionDoc.getAst() == null || toSubmissionDoc.getAst() == null) {
                return new AnalysisResult(AnalysisResult.Status.READY, null, null, null);
            }

            JsonNode json1 = toJsonNode(fromSubmissionDoc.getAst());
            JsonNode json2 = toJsonNode(toSubmissionDoc.getAst());

            // 1차 필터링: 타입 벡터 + 코사인
            Map<String, Integer> vec1 = ASTVectorizer.buildTypeVector(json1);
            Map<String, Integer> vec2 = ASTVectorizer.buildTypeVector(json2);
            double cosine = CosineSimilarity.calculate(vec1, vec2);

            Integer ted = null;
            Double normalized = null;

            // 2차 분석: 임계치 이상일 때 TED
            if (cosine >= COSINE_THRESHOLD) {
                TreeNode tree1 = TreeNodeBuilder.fromJson(json1);
                TreeNode tree2 = TreeNodeBuilder.fromJson(json2);
                ted = TreeEditDistance.compute(tree1, tree2);
                int maxSize = Math.max(countNodes(tree1), countNodes(tree2));
                normalized = 1.0 - ((double) ted / maxSize);
            }

            // Result 저장 — accumulateResult = normalized
            Result result = new Result();
            result.setSubmissionFromId(fromStudentId); // 첫 번째 학생의 학번
            result.setSubmissionToId(toStudentId); // 두 번째 학생의 학번
            result.setSubmissionId(fromSubmissionId);
            if (normalized != null) {
                result.setAccumulateResult(normalized);
            } else {
                // 1차 필터링에서 유사도 수치가 낮게 나온 경우
                result.setAccumulateResult(0.0); // 0.0으로 넣을지 다른 수치 넣을지 고민 중
            }
            resultRepository.save(result);

            return new AnalysisResult(AnalysisResult.Status.DONE, cosine, ted, normalized);
        } catch (BaseException be) {
            // 비즈니스 예외는 컨트롤러에서 200
            throw be;
        } catch (Exception e) {
            // 알 수 없는 예외는 내부 서버 에러
            throw new BaseException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
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