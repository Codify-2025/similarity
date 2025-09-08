package Codify.similarity.service;

import Codify.similarity.exception.ErrorCode;
import Codify.similarity.exception.baseException.BaseException;
import Codify.similarity.exception.submissionexception.SubmissionNotFoundException;
import Codify.similarity.mongo.ResultDocRepository;
import Codify.similarity.service.dto.AnalysisResult;
import Codify.similarity.web.dto.SimilarityStartResponseDto;
import Codify.similarity.web.dto.SimilarityStatusResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SimilarityBatchService {
    private final SimilarityTaskRunner taskRunner;
    private final SimilarityService similarityService;
    private final ResultDocRepository resultDocRepository;

    // 비동기 실행
    @Transactional
    public SimilarityStartResponseDto start(Integer assignmentId, List<Integer> submissionIds) {
        if (submissionIds == null || submissionIds.isEmpty())
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE);

        var ids = submissionIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (ids.isEmpty()) throw new BaseException(ErrorCode.INVALID_INPUT_VALUE);

        for (Integer sid : ids) taskRunner.runOne(assignmentId, sid);

        return new SimilarityStartResponseDto(true, submissionIds.size(), ids.size(), ids);
    }

    // status 집계
    @Transactional(readOnly = true)
    public SimilarityStatusResponseDto status(Integer assignmentId, List<Integer> submissionIds) {
        if (submissionIds == null || submissionIds.isEmpty())
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE);

        var ids = submissionIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (ids.isEmpty()) throw new BaseException(ErrorCode.INVALID_INPUT_VALUE);

        int total = 0, done = 0, skipped = 0;
        var per = new ArrayList<SimilarityStatusResponseDto.PerSubmissionStatus>();
        var overall = AnalysisResult.Status.DONE;

        for (Integer sid : ids) {
            var doc = resultDocRepository.findBySubmissionId(sid)
                    .orElseThrow(SubmissionNotFoundException::new);

            var ar = similarityService.status(assignmentId, doc.getStudentId(), sid);

            total   += ar.getTotal()   == null ? 0 : ar.getTotal();
            done    += ar.getDone()    == null ? 0 : ar.getDone();
            skipped += ar.getSkipped() == null ? 0 : ar.getSkipped();

            per.add(new SimilarityStatusResponseDto.PerSubmissionStatus(
                    sid, ar.getStatus().name(), ar.getTotal(), ar.getDone(), ar.getSkipped()
            ));

            if (ar.getStatus() == AnalysisResult.Status.ERROR) overall = AnalysisResult.Status.ERROR;
            else if (overall != AnalysisResult.Status.ERROR && ar.getStatus() != AnalysisResult.Status.DONE)
                overall = AnalysisResult.Status.READY;
        }

        return new SimilarityStatusResponseDto(overall.name(), total, done, skipped, per);
    }
}
