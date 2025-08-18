package Codify.similarity.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "E1", "올바르지 않은 입력값입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "E2", "잘못된 HTTP 메서드를 호출했습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "E3", "서버 에러가 발생했습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "E4", "존재하지 않는 엔티티입니다."),

    SUBMISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "S1", "존재하지 않는 제출물입니다."),
    STUDENT_SUBMISSION_MISMATCH(HttpStatus.BAD_REQUEST, "S2", "학번과 제출물이 일치하지 않습니다."),
    SAME_SUBMISSION_COMPARISON(HttpStatus.BAD_REQUEST, "S3", "동일한 제출물끼리는 비교할 수 없습니다."),
    SAME_STUDENT_COMPARISON(HttpStatus.BAD_REQUEST,"S4", "동일한 학생 간에는 비교할 수 없습니다."),

    ARTICLE_NOT_FOUND(HttpStatus.NOT_FOUND, "A1", "존재하지 않는 아티클입니다.");

    private final HttpStatus status; //http 상태 코드
    private final String code; //에러 구분 코드
    private final String message; //사용자에게 보여줄 에러 메시지

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
