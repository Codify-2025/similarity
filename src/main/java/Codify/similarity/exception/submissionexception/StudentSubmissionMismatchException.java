package Codify.similarity.exception.submissionexception;

import Codify.similarity.exception.ErrorCode;
import Codify.similarity.exception.baseException.BaseException;

public class StudentSubmissionMismatchException extends BaseException {
    public StudentSubmissionMismatchException() {
        super(ErrorCode.STUDENT_SUBMISSION_MISMATCH); }
}