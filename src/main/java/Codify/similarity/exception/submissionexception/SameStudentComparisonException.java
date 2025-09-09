package Codify.similarity.exception.submissionexception;

import Codify.similarity.exception.ErrorCode;
import Codify.similarity.exception.baseException.BaseException;

public class SameStudentComparisonException extends BaseException {
    public SameStudentComparisonException() {
        super(ErrorCode.SAME_STUDENT_COMPARISON);
    }
}
