package Codify.similarity.web.dto;

import java.util.List;

public record SimilarityStartResponseDto(
        boolean accepted,
        int requestedCount,
        int startedCount,
        List<Integer> startedSubmissionIds
) {}