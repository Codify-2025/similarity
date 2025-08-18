package Codify.similarity.service.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AnalysisResult {
    public enum Status { READY, DONE, ERROR }
    private final Status status;
    private final Double cosine;     // DONE일 때만
    private final Integer ted;
    private final Double normalized;
}
