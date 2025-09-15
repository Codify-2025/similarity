package Codify.similarity.web.controller;

import Codify.similarity.service.SimilarityBatchService;
import Codify.similarity.web.dto.SimilarityStartResponseDto;
import Codify.similarity.web.dto.SimilarityStatusResponseDto;
import Codify.similarity.web.dto.SubmissionIdsRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/similarity/assignments/{assignmentId}/submissions")
public class SimilarityController {

    private final SimilarityBatchService batchService;

    @PostMapping("/batch")
    public ResponseEntity<SimilarityStartResponseDto> run(
            @PathVariable final Integer assignmentId,
            @RequestBody @Valid final SubmissionIdsRequestDto submissionIdsRequestDto
    ) {
        return ResponseEntity.accepted().body(
                batchService.start(assignmentId, submissionIdsRequestDto.submissionIds())
        );
    }

    @Operation(
            operationId = "getSimilarityStatusFromOne",
            summary = "상태 조회, ids 없이 사용",
            description = """
            제출물 번호 하나만 입력하면 됩니다. 
            - 프론트가 ids를 구성하지 않아도 되는 간단 폴링용 엔드포인트입니다.
            """
    )
    @GetMapping("/{submissionFromId}/analyze")
    public ResponseEntity<SimilarityStatusResponseDto> statusFromOne(
            @PathVariable final Integer assignmentId,
            @PathVariable final Integer submissionFromId
    ) {
        return ResponseEntity.ok(batchService.status(assignmentId, java.util.List.of(submissionFromId)));
    }
}