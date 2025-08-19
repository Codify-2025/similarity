package Codify.similarity.service;

import Codify.similarity.domain.Result;
import Codify.similarity.exception.ErrorCode;
import Codify.similarity.exception.baseException.BaseException;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SimilarityService {

    private final ResultDocRepository resultDocRepository; // Mongo
    private final ResultRepository resultRepository; // JPA
    private final ObjectMapper objectMapper;

    private static final double COSINE_THRESHOLD = 0.6;

    @Transactional
    public void analyzeAndSave(Integer assignmentId, Integer fromStudentId, Integer fromSubmissionId) {
        if (assignmentId == null || fromStudentId == null || fromSubmissionId == null) {
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // 1. 동일한 제출물 감지
        var fromSubmissionDoc = resultDocRepository.findBySubmissionId(fromSubmissionId)
                .orElseThrow(SubmissionNotFoundException::new);
        if (!Objects.equals(fromSubmissionDoc.getStudentId(), fromStudentId)) {
            throw new StudentSubmissionMismatchException(); // 학번 != 제출물
        }
        if (!Objects.equals(fromSubmissionDoc.getAssignmentId(), assignmentId)){
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE); // 과제 불일치
        }

        // 2. 같은 과제 & submissionId > Y
        var candidatesSubmission = resultDocRepository
                .findAllByAssignmentIdAndSubmissionIdGreaterThanOrderBySubmissionIdAsc(
                        assignmentId, fromSubmissionId
                );

        JsonNode fromJson = toJsonNode(fromSubmissionDoc.getAst());
        var fromVec = ASTVectorizer.buildTypeVector(fromJson);
        TreeNode fromTree = null;

        // for 루프
        for(ResultDoc candidates : candidatesSubmission) {
            if (candidates.getAst() == null) continue;
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
            Result result = new Result();
            result.setStudentFromId(fromStudentId.longValue());
            result.setSubmissionFromId(fromSubmissionId.longValue());
            result.setStudentToId(candidates.getStudentId().longValue());
            result.setSubmissionToId(candidates.getSubmissionId().longValue());
            result.setAccumulateResult(normalized != null ? normalized : 0.0);

            resultRepository.save(result);
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