package Codify.similarity.service;

import Codify.similarity.domain.Result;
import Codify.similarity.model.TreeNode;
import Codify.similarity.core.ASTVectorizer;
import Codify.similarity.core.CosineSimilarity;
import Codify.similarity.core.TreeEditDistance;
import Codify.similarity.model.TreeNodeBuilder;
import Codify.similarity.mongo.ResultDocRepository;
import Codify.similarity.repository.ResultRepository;
import Codify.similarity.service.dto.AnalysisResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class SimilarityService {

    private final ResultDocRepository resultDocRepository; // Mongo
    private final ResultRepository resultRepository; // JPA
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private static final double COSINE_THRESHOLD = 0.6;

    @Transactional
    public AnalysisResult analyzeAndSaveByStudent(Long fromStudentId, Long toStudentId) {
        try {
            var fromSubmissionOpt = resultDocRepository.findTopByStudentIdOrderBySubmissionIdDesc(fromStudentId);
            var toSubmissionOpt = resultDocRepository.findTopByStudentIdOrderBySubmissionIdDesc(toStudentId);

            if (fromSubmissionOpt.isEmpty() || toSubmissionOpt.isEmpty()) {
                // 아직 준비되지 않음
                return new AnalysisResult(AnalysisResult.Status.READY, null, null, null);
            }

            var json1 = toJsonNode(fromSubmissionOpt.get().getAst());
            var json2 = toJsonNode(toSubmissionOpt.get().getAst());

            // 1차 필터링: 타입 벡터 + 코사인
            var vec1 = ASTVectorizer.buildTypeVector(json1);
            var vec2 = ASTVectorizer.buildTypeVector(json2);
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

            Long fromSubmissionId = fromSubmissionOpt.get().getSubmissionId(); // MongoDB에 저장된 과제 제출 ID (첫 번째 학생)
            Long submissionIdForResult = fromSubmissionId; // 첫 번째 학생의 과제 제출 Id를 Result(MySQL) 테이블의 과제 제출 Id로 저장

            // Result 저장 — accumulateResult = normalized
            Result result = new Result();
            result.setSubmissionFromId(fromStudentId); // 첫 번째 학생의 학번
            result.setSubmissionToId(toStudentId); // 두 번째 학생의 학번
            result.setSubmissionId(submissionIdForResult);
            if (normalized != null) {
                result.setAccumulateResult(normalized);
            } else {
                // 1차 필터링에서 유사도 수치가 낮게 나온 경우
                result.setAccumulateResult(0.0); // 0.0으로 넣을지 다른 수치 넣을지 고민 중
            }
            resultRepository.save(result);

            return new AnalysisResult(AnalysisResult.Status.DONE, cosine, ted, normalized);
        } catch (Exception e) {
            return new AnalysisResult(AnalysisResult.Status.ERROR, null, null, null);
        }
    }

    private com.fasterxml.jackson.databind.JsonNode toJsonNode(Object ast) {
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

    /*public Result analyzeAndSave(JsonNode json1, JsonNode json2, Long fromId, Long toId) throws Exception {
        //JsonNode ast1 = objectMapper.readTree(json1);
        //JsonNode ast2 = objectMapper.readTree(json2);

        // 1차 필터링: 타입 벡터 + 코사인 유사도
        var vec1 = ASTVectorizer.buildTypeVector(json1);
        var vec2 = ASTVectorizer.buildTypeVector(json2);

        double cosine = CosineSimilarity.calculate(vec1, vec2);

        Integer ted = null;
        Double normalized = null;

        // 임계치 이상인 경우 2차 분석
        if (cosine >= 0.6) {
            TreeNode tree1 = TreeNodeBuilder.fromJson(json1);
            TreeNode tree2 = TreeNodeBuilder.fromJson(json2);
            ted = TreeEditDistance.compute(tree1, tree2);
            int maxSize = Math.max(countNodes(tree1), countNodes(tree2));
            normalized = 1.0 - ((double) ted / maxSize);
        }

        // 4) 결과 저장 — 네이티브 INSERT
        jdbc.update("""
            INSERT INTO Result (submissionId, submission_from_id, submission_to_id, accumulateResult)
             VALUES (?, ?, ?, ?)
        """, ps -> {
            ps.setLong(1, submissionId);     // BIGINT
            ps.setLong(2, fromId);           // BIGINT
            ps.setBytes(3, uuidToBytes(toId)); // BINARY(16) 변환
            ps.setDouble(4, cosine);         // FLOAT
        });

        Result result = new Result();
        result.setSubmissionFromId(fromId);
        result.setSubmissionToId(toId);
        result.setAccumulateResult(cosine);
        return result;
    }

    private int countNodes(TreeNode node) {
        if (node == null) return 0;
        int count = 1;
        for (TreeNode child: node.children) {
            count += countNodes(child);
        }
        return count;
    }*/
}