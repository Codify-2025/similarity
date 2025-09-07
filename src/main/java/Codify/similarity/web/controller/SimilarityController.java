package Codify.similarity.web.controller;

import Codify.similarity.service.SimilarityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/similarity/assignments/{assignmentId}/submissions/{submissionId}")
public class SimilarityController {

    private final SimilarityService similarityService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> run(
            @RequestParam Integer assignmentId,
            @RequestParam Integer studentId,
            @RequestParam Integer submissionId
    ) {
        similarityService.startAnalysisAsync(assignmentId, studentId, submissionId);

        // HTTP 표준 사용: 202 Accepted 권장(또는 200 OK)
        return ResponseEntity.accepted().body(
                Map.of("status", 202, "success", true, "message", Map.of("accepted", true))
                // 혹은 Map.of("status", 202, "success", true, "message", Map.of("status", "READY"))
        );
    }
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(
            @RequestParam Integer assignmentId,
            @RequestParam Integer studentId,
            @RequestParam Integer submissionId
    ) {
        var ar = similarityService.status(assignmentId, studentId, submissionId);
        return ResponseEntity.ok(
                Map.of("status", 200, "success", true, "message", Map.of("status", ar.getStatus().name()))
        );
    }
}