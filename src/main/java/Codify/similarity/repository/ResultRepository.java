package Codify.similarity.repository;

import Codify.similarity.domain.Result;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResultRepository extends JpaRepository<Result, Long> {
    int countByAssignmentIdAndSubmissionFromId(Long assignmentId, Long submissionFromId);
}
