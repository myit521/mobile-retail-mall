package com.sky.handler;

import com.sky.constant.ErrorCode;
import com.sky.constant.MessageConstant;
import com.sky.exception.AccountLockedException;
import com.sky.exception.AccountNotFoundException;
import com.sky.exception.BaseException;
import com.sky.exception.LoginFailedException;
import com.sky.exception.PasswordErrorException;
import com.sky.exception.TooManyAttemptsException;
import com.sky.exception.UserNotLoginException;
import com.sky.observability.SensitiveValueSanitizer;
import com.sky.result.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolationException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            NumberFormatException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> validation(Exception exception) {
        String message = exception instanceof MethodArgumentNotValidException valid && valid.getBindingResult().hasErrors()
                ? valid.getBindingResult().getAllErrors().get(0).getDefaultMessage() : MessageConstant.INVALID_PARAM;
        log.warn("request rejected, errorType={}", exception.getClass().getSimpleName());
        return error(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, message);
    }

    @ExceptionHandler({UserNotLoginException.class, LoginFailedException.class, PasswordErrorException.class,
            AccountNotFoundException.class, AccountLockedException.class, TooManyAttemptsException.class})
    public ResponseEntity<ErrorResponse> authentication(BaseException exception) {
        log.warn("authentication failed, errorType={}", exception.getClass().getSimpleName());
        return error(HttpStatus.UNAUTHORIZED, ErrorCode.AUTHENTICATION_ERROR, safeBusinessMessage(exception));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> permission(SecurityException exception) {
        log.warn("permission denied, errorType={}", exception.getClass().getSimpleName());
        return error(HttpStatus.FORBIDDEN, ErrorCode.PERMISSION_DENIED, "无权限执行此操作");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> conflict(DataIntegrityViolationException exception) {
        log.warn("database conflict, errorType={}", exception.getClass().getSimpleName());
        return error(HttpStatus.CONFLICT, ErrorCode.CONFLICT, MessageConstant.ALREADY_EXISTS);
    }

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> business(BaseException exception) {
        log.warn("business failure, errorType={}, message={}", exception.getClass().getSimpleName(),
                SensitiveValueSanitizer.sanitize(safeBusinessMessage(exception)));
        return error(HttpStatus.BAD_REQUEST, ErrorCode.BUSINESS_ERROR, safeBusinessMessage(exception));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> system(Exception exception) {
        log.error("system failure, errorType={}", exception.getClass().getSimpleName());
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SYSTEM_ERROR, MessageConstant.UNKNOWN_ERROR);
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, MDC.get("traceId")));
    }

    private String safeBusinessMessage(BaseException exception) {
        return StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : MessageConstant.UNKNOWN_ERROR;
    }
}
