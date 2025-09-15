package Codify.similarity.web.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record SubmissionIdsRequestDto (
    @NotEmpty(message = "submissionIds must not be empty")
    List<@NotNull @Positive Integer> submissionIds
) {}
