package Codify.similarity.exception;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiErrorResponse {

    private String code;
    private String message;

    //1. error code enum의 message를 그대로 사용
    private ApiErrorResponse(final ErrorCode errorCode) {
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }

    //2. error code 그대로 사용하고 custom message 사용
    private ApiErrorResponse(final ErrorCode errorCode, final String message) {
        this.code = errorCode.getCode();
        this.message= message;
    }

    //of() factory method를 통해서만 ApiErrorResponse 객체를 만들도록 함
    public static ApiErrorResponse of(final ErrorCode errorCode) {
        return new ApiErrorResponse(errorCode);
    }

    public static ApiErrorResponse of(final ErrorCode errorCode, final String message) {
        return new ApiErrorResponse(errorCode, message);
    }


}

