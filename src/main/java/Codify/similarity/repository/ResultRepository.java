package Codify.similarity.repository;

import Codify.similarity.domain.Result;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResultRepository extends JpaRepository<Result, Long> {
    int countByAssignmentIdAndSubmissionFromId(Long assignmentId, Long submissionFromId);

    Optional<Result> findByAssignmentIdAndSubmissionFromIdAndSubmissionToId(
            Long assignmentId, Long submissionFromId, Long submissionToId);
}