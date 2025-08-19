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
            @RequestParam Integer assignmentId,
            @RequestParam Integer studentId,
            @RequestParam Integer submissionId
    ) {
        try {
            similarityService.analyzeAndSave(assignmentId, studentId, submissionId);
            return ResponseEntity.ok(
                    Map.of(
                            "status", 200,
                            "success", true,
                            "message", Map.of("status", "DONE")
                    )
            );
        } catch (BaseException be) {
            var ec = be.getErrorCode();
            return ResponseEntity.ok(
                    Map.of(
                            "status", 200,
                            "success", true,
                            "message", Map.of(
                                    "status", "ERROR",
                                    "code", ec.getCode(),
                                    "message", ec.getMessage()
                            )
                    )
            );
        } catch (Exception e) {
            var ec = ErrorCode.INTERNAL_SERVER_ERROR;
            return ResponseEntity.ok(
                    Map.of(
                            "status", 200,
                            "success", true,
                            "message", Map.of(
                                    "status", "ERROR",
                                    "code", ec.getCode(),
                                    "message", ec.getMessage()
                            )
                    )
            );
        }
    }
}