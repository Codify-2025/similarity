package Codify.similarity.web.controller;

import Codify.similarity.exception.ErrorCode;
import Codify.similarity.exception.baseException.BaseException;
import Codify.similarity.service.dto.AnalysisResult;
import Codify.similarity.service.SimilarityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/similarity")
public class SimilarityController {

    private final SimilarityService similarityService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> run(
            @RequestParam Long fromStudentId,
            @RequestParam Long fromSubmissionId,
            @RequestParam Long toStudentId,
            @RequestParam Long toSubmissionId
    ) {
        try{
            AnalysisResult analysisResult = similarityService.analyzeAndSave(
                    fromStudentId, fromSubmissionId, toStudentId, toSubmissionId
            );

            String status = switch (analysisResult.getStatus()) {
                case READY -> "READY";
                case DONE -> "DONE";
                case ERROR -> "ERROR";
            };

            // 항상 200, message.status만 다르게
            return ResponseEntity.ok(
                    Map.of(
                            "status", 200,
                            "success", true,
                            "message", Map.of("status", status)
                    )
            );
        } catch (BaseException baseException) {
            var errorCode = baseException.getErrorCode();
            return ResponseEntity.ok(
                    Map.of(
                            "status", 200,
                            "success", true,
                            "message", Map.of(
                                    "status", "ERROR",
                                    "code", errorCode.getCode(),
                                    "message", errorCode.getMessage()
                                    )
                    )
            );
        } catch (Exception e) {
            ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
            return ResponseEntity.ok(
                    Map.of(
                            "status", 200,
                            "success", true,
                            "message", Map.of(
                                    "status", "ERROR",
                                    "code", errorCode.getCode(),
                                    "message", errorCode.getMessage()
                            )
                    )
            );
        }
    }
}