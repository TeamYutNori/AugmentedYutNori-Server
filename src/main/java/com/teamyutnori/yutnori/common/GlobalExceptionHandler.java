package com.teamyutnori.yutnori.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFoundHandler(NotFoundException e) {
        ErrorResponse error = new ErrorResponse(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    } // 없는 방 코드, 없는 플레이어 (HTTP 상태: 404)

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> conflictHandler(ConflictException e) {
        ErrorResponse error = new ErrorResponse(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    } // 방이 꽉 참, 이미 시작한 게임 (HTTP 상태: 409)

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> forbiddenHandler(ForbiddenException e) {
        ErrorResponse error = new ErrorResponse(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    } // 방장이 아닌데 시작 요청, 내 턴이 아님 (HTTP 상태: 403)

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> unauthorizedHandler(UnauthorizedException e) {
        ErrorResponse error = new ErrorResponse(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    } // 로그인 실패(아이디·비밀번호 불일치), 토큰 없음·만료·위조 (HTTP 상태: 401)

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> invalidRequestHandler(InvalidRequestException e) {
        ErrorResponse error = new ErrorResponse(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    } // 값이 잘못됨(인원 수 범위 밖 등) (HTTP 상태: 400)

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> methodArgumentNotValidHandler(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("요청 값이 올바르지 않습니다.");
        ErrorResponse error = new ErrorResponse("INVALID_REQUEST", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    } // @Valid 검증 실패 (HTTP 상태: 400)

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> messageNotReadableHandler() {
        ErrorResponse error = new ErrorResponse("INVALID_REQUEST", "요청 본문을 읽을 수 없습니다.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    } // 요청 본문이 JSON 형식이 아니거나 필드 타입이 맞지 않음 (HTTP 상태: 400)

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> noResourceFoundHandler() {
        ErrorResponse error = new ErrorResponse("NOT_FOUND", "요청한 주소를 찾을 수 없습니다.");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    } // 존재하지 않는 URL 요청 (HTTP 상태: 404)

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> notSupportedHandler() {
        ErrorResponse error = new ErrorResponse("METHOD_NOT_ALLOWED", "지원하지 않는 요청 방식입니다.");
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(error);
    } // 해당 URL이 지원하지 않는 HTTP 메서드로 요청 (예: GET 전용 주소에 POST) (HTTP 상태: 405)

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> mediaTypeNotSupportedHandler() {
        ErrorResponse error = new ErrorResponse(
                "UNSUPPORTED_MEDIA_TYPE", "Content-Type은 application/json이어야 합니다.");
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(error);
    } // Content-Type이 application/json이 아닌 요청 (HTTP 상태: 415)

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> argumentTypeMismatchHandler(MethodArgumentTypeMismatchException e) {
        ErrorResponse error = new ErrorResponse(
                "INVALID_REQUEST", "'" + e.getName() + "' 값의 형식이 올바르지 않습니다.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    } // 경로 변수나 쿼리 파라미터의 타입이 맞지 않음 (예: 숫자 자리에 문자) (HTTP 상태: 400)

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> exceptionHandler(Exception e) {
        log.error("예상치 못한 오류", e);
        ErrorResponse error = new ErrorResponse("INTERNAL_ERROR", "서버 오류가 발생했습니다.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    } // 위에서 처리하지 못한 모든 예외. 원인은 로그에만 남기고 클라엔 내부 정보를 숨김 (HTTP 상태: 500)
}
