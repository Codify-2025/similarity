package Codify.similarity.web.controller;

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
            @RequestParam Long toStudentId
    ) {
        AnalysisResult ar = similarityService.analyzeAndSaveByStudent(fromStudentId, toStudentId);

        String status = switch (ar.getStatus()) {
            case READY -> "READY";
            case DONE -> "DONE";
            case ERROR -> "ERROR";
        };

        // 요구 포맷: 항상 200, message.status만 다르게
        return ResponseEntity.ok(
                Map.of(
                        "status", 200,
                        "success", true,
                        "message", Map.of("status", status)
                )
        );
    }

    /*@PostMapping
    public ResponseEntity<SimilarityResponseDto> calculate(
            @RequestParam Long fromId,
            @RequestParam Long toId,
            @Valid @RequestBody CompareRequest request
    ) throws Exception {

        // JSON(AST) + submissionId로 분석 및 저장
        Result result = similarityService.analyzeAndSave(
                request.getJson1(), request.getJson2(), fromId, toId
        );

         double cosine = result.getAccumulateResult();
         return ResponseEntity.ok(new SimilarityResponseDto(cosine, null, null));
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CompareRequest {
        private JsonNode json1;
        private JsonNode json2;
    }*/
}