package Codify.similarity.exception.submissionexception;

import Codify.similarity.exception.ErrorCode;
import Codify.similarity.exception.baseException.BaseException;

public class SameSubmissionComparisonException extends BaseException {
    public SameSubmissionComparisonException() {
        super(ErrorCode.SAME_SUBMISSION_COMPARISON); }
}
