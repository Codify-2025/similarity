package Codify.similarity.web.dto;


import Codify.similarity.core.RangeUtil;
import Codify.similarity.core.TreeMatcher;
import Codify.similarity.domain.Codeline;

import java.util.List;

public record CodelineData(
        Long resultId,
        Long fromStudentId,
        Long toStudentId,
        Long fromSubmissionId,
        Long toSubmissionId,
        List<TreeMatcher.Seg> segments,
        List<RangeUtil.Interval> leftMergedRanges,
        List<RangeUtil.Interval> rightMergedRanges) {

}
