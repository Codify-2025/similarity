package Codify.similarity.web.dto;

import Codify.similarity.domain.Result;

import java.util.List;

public record ProcessResult(
        List<Result> results,
        List<CodelineData> codelineDataList
) {
}
