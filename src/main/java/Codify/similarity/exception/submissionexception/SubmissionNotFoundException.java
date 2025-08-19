package Codify.similarity.exception.submissionexception;

import Codify.similarity.exception.ErrorCode;
import Codify.similarity.exception.baseException.BaseException;

public class SubmissionNotFoundException extends BaseException {
    public SubmissionNotFoundException() {
        super(ErrorCode.SUBMISSION_NOT_FOUND); }
}