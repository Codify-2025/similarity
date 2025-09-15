package Codify.similarity.service;

import Codify.similarity.exception.ErrorCode;
import Codify.similarity.exception.baseException.BaseException;
import Codify.similarity.exception.submissionexception.SubmissionNotFoundException;
import Codify.similarity.mongo.ResultDoc;
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

    // 중복되는 부분 공통 메서드로 추출
    private List<Integer> determineStartSubmissions(final Integer assignmentId, final List<Integer> ids) {
        if (ids.size() == 1) {
            Integer from = ids.get(0);
            var docs = resultDocRepository
                    .findAllByAssignmentIdAndSubmissionIdGreaterThanEqualAndAstIsNotNullOrderBySubmissionIdAsc(
                            assignmentId, from);
            var expanded = docs.stream().map(ResultDoc::getSubmissionId).toList();
            return (expanded.size() <= 1) ? List.of() : expanded.subList(0, expanded.size() - 1);
        }
        return ids;
    }

    // 비동기 실행
    @Transactional
    public SimilarityStartResponseDto start(final Integer assignmentId, final List<Integer> submissionIds) {
        if (submissionIds == null || submissionIds.isEmpty())
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE);

        final var ids = submissionIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (ids.isEmpty()) throw new BaseException(ErrorCode.INVALID_INPUT_VALUE);

        final List<Integer> starts = determineStartSubmissions(assignmentId, ids);

        for (Integer sid : starts) taskRunner.runOne(assignmentId, sid);

        return new SimilarityStartResponseDto(true, submissionIds.size(), starts.size(), starts);
    }

    // status 집계
    @Transactional(readOnly = true)
    public SimilarityStatusResponseDto status(final Integer assignmentId, final List<Integer> submissionIds) {
        if (submissionIds == null || submissionIds.isEmpty())
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE);

        final var ids = submissionIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (ids.isEmpty()) throw new BaseException(ErrorCode.INVALID_INPUT_VALUE);

        final List<Integer> starts = determineStartSubmissions(assignmentId, ids);

        int total = 0, done = 0, skipped = 0;
        var per = new ArrayList<SimilarityStatusResponseDto.PerSubmissionStatus>();
        var overall = AnalysisResult.Status.DONE;

        for (Integer sid : starts) {
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
