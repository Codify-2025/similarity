package Codify.similarity.exception.baseException;

import Codify.similarity.exception.ErrorCode;

//BaseException을 extends 받아서 다른 custom exception 생성하여 사용
public class BaseException extends RuntimeException {

    private final ErrorCode errorCode;

    //1. ErrorCode enum의 message 그대로 사용
    public BaseException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    //2. custom message
    public BaseException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    //ApiExceptionHandler에서 errorCode를 가져오기 위해 사용하는 method
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}