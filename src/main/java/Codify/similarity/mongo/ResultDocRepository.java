package Codify.similarity.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ResultDocRepository extends MongoRepository<ResultDoc, String> {
    Optional<ResultDoc> findBySubmissionId(Integer submissionId);

    List<ResultDoc> findAllByAssignmentIdAndSubmissionIdGreaterThanOrderBySubmissionIdAsc(
            Integer assignmentId, Integer submissionId
    );
}
