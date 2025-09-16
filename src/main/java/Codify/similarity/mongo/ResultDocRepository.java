package Codify.similarity.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ResultDocRepository extends MongoRepository<ResultDoc, String> {
    Optional<ResultDoc> findBySubmissionId(Integer submissionId);

    boolean existsByAssignmentId(Integer assignmentId);

    List<ResultDoc> findAllByAssignmentIdAndSubmissionIdGreaterThanAndAstIsNotNullOrderBySubmissionIdAsc(
            Integer assignmentId, Integer submissionId
    );

    List<ResultDoc> findAllByAssignmentIdAndSubmissionIdGreaterThanEqualAndAstIsNotNullOrderBySubmissionIdAsc(
            Integer assignmentId, Integer submissionId
    );

    int countByAssignmentIdAndSubmissionIdGreaterThanAndAstIsNotNull(
            Integer assignmentId, Integer submissionId
    );

    int countByAssignmentIdAndSubmissionIdGreaterThanAndAstIsNull(
            Integer assignmentId, Integer submissionId
    );

}
