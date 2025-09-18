package Codify.similarity.web.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SimilarityResponseDto {
    private double cosineSimilarity;
    private Integer treeEditDistance;
    private Double normalizedSimilarity;
}