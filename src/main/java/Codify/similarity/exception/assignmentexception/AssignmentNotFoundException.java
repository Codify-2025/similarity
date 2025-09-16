package Codify.similarity.exception.assignmentexception;

import Codify.similarity.exception.ErrorCode;
import Codify.similarity.exception.baseException.BaseException;

public class AssignmentNotFoundException extends BaseException {
    public AssignmentNotFoundException() {
        super(ErrorCode.ASSIGNMENT_NOT_FOUND);
    }
}
