package Codify.similarity.web.controller;

import Codify.similarity.service.SimilarityBatchService;
import Codify.similarity.web.dto.SimilarityStartResponseDto;
import Codify.similarity.web.dto.SimilarityStatusResponseDto;
import Codify.similarity.web.dto.SubmissionIdsRequestDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/similarity/assignments/{assignmentId}/submissions")
public class SimilarityController {

    // private final SimilarityService similarityService;
    private final SimilarityBatchService batchService;

    @PostMapping("/batch")
    public ResponseEntity<SimilarityStartResponseDto> run(
            @PathVariable Integer assignmentId,
            @RequestBody @Valid SubmissionIdsRequestDto req
    ) {
        return ResponseEntity.accepted().body(
                batchService.start(assignmentId, req.submissionIds())
        );
    }
    @GetMapping("/analyze")
    public ResponseEntity<SimilarityStatusResponseDto> status(
            @PathVariable Integer assignmentId,
            @RequestParam(name = "ids") java.util.List<Integer> submissionIds
    ) {
        return ResponseEntity.ok(
                batchService.status(assignmentId, submissionIds)
        );
    }
}