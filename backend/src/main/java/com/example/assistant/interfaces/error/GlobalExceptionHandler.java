package com.example.assistant.interfaces.error;

import com.example.assistant.application.chat.BusinessException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleBusinessException(
            BusinessException exception,
            ServerWebExchange exchange
    ) {
        LOGGER.warn(
                "business exception, code={}, path={}",
                exception.errorCode().code(),
                exchange.getRequest().getPath().value()
        );
        ErrorResponse response = new ErrorResponse(
                exception.errorCode().code(),
                exception.errorCode().message(),
                exchange.getRequest().getPath().value(),
                Instant.now()
        );
        return Mono.just(ResponseEntity.status(exception.errorCode().httpStatus()).body(response));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleValidationException(
            WebExchangeBindException exception,
            ServerWebExchange exchange
    ) {
        String message = exception.getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        ErrorResponse response = new ErrorResponse(
                "PARAMETER_INVALID",
                message,
                exchange.getRequest().getPath().value(),
                Instant.now()
        );
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleConstraintViolationException(
            ConstraintViolationException exception,
            ServerWebExchange exchange
    ) {
        String message = exception.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));
        ErrorResponse response = new ErrorResponse(
                "PARAMETER_INVALID",
                message,
                exchange.getRequest().getPath().value(),
                Instant.now()
        );
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleResponseStatusException(
            ResponseStatusException exception,
            ServerWebExchange exchange
    ) {
        ErrorResponse response = new ErrorResponse(
                "PARAMETER_INVALID",
                exception.getReason() == null ? "请求参数不合法" : exception.getReason(),
                exchange.getRequest().getPath().value(),
                Instant.now()
        );
        return Mono.just(ResponseEntity.status(exception.getStatusCode()).body(response));
    }

    @ExceptionHandler(Throwable.class)
    public Mono<ResponseEntity<ErrorResponse>> handleUnexpectedException(
            Throwable throwable,
            ServerWebExchange exchange
    ) {
        LOGGER.error("unexpected exception, path={}", exchange.getRequest().getPath().value(), throwable);
        ErrorResponse response = new ErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "服务暂时不可用",
                exchange.getRequest().getPath().value(),
                Instant.now()
        );
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response));
    }

    private String formatFieldError(FieldError fieldError) {
        String defaultMessage = StringUtils.hasText(fieldError.getDefaultMessage())
                ? fieldError.getDefaultMessage()
                : "参数不合法";
        return fieldError.getField() + ": " + defaultMessage;
    }
}
