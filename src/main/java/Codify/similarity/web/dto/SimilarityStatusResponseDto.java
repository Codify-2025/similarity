package Codify.similarity.web.dto;

import java.util.List;

public record SimilarityStatusResponseDto(
        String status,    // READY, DONE, ERROR
        Integer total,
        Integer done,
        Integer skipped,
        List<PerSubmissionStatus> perSubmissions
) {
    public record PerSubmissionStatus(
            Integer submissionId,
            String status,
            Integer total,
            Integer done,
            Integer skipped
    ) {}
}