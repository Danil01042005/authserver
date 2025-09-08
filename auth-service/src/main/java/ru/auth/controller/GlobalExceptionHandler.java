package ru.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import ru.auth.dto.ErrorResponse;
import ru.auth.config.CorrelationIdFilter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final String SERVICE = "auth-service";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ErrorResponse body = ErrorResponse.builder()
                .code("VALIDATION_ERROR")
                .message(errors.toString())
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .service(SERVICE)
                .correlationId(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header("X-Error-Code", body.getCode())
                .header("X-Service", SERVICE)
                .body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.builder()
                .code("INTERNAL_ERROR")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .service(SERVICE)
                .correlationId(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("X-Error-Code", body.getCode())
                .header("X-Service", SERVICE)
                .body(body);
    }
}




