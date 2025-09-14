package Codify.similarity.service;

import Codify.similarity.exception.submissionexception.SubmissionNotFoundException;
import Codify.similarity.mongo.ResultDocRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimilarityTaskRunner {
    private final ResultDocRepository resultDocRepository;
    private final SimilarityService similarityService;
    private final AnalysisRuntimeRegistry runtime;

    @Async("analysisExecutor")
    @Transactional
    public void runOne(final Integer assignmentId, final Integer submissionId) {
        final var doc = resultDocRepository.findBySubmissionId(submissionId)
                .orElseThrow(SubmissionNotFoundException::new);

        final var studentId = doc.getStudentId();
        runtime.markStarted(assignmentId, studentId, submissionId);
        try {
            similarityService.analyzeAndSave(assignmentId, studentId, submissionId);
        } catch (Exception e) {
            runtime.markError(assignmentId, studentId, submissionId);
            log.error("Async analysis failed: aId={}, subFrom={}", assignmentId, submissionId, e);
            throw e;
        }
    }
}
