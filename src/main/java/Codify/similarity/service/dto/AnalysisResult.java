package Codify.similarity.service.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AnalysisResult {
    public enum Status { READY, DONE, ERROR }

    private final Status status;
    private final Integer total;     // DONE일 때만
    private final Integer done;
    private final Integer skipped;

    public AnalysisResult(Status status) {
        this(status, null, null, null);
    }
}
