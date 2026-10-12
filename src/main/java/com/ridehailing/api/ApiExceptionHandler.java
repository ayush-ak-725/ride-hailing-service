package com.ridehailing.api;

import com.ridehailing.api.dto.ErrorResponse;
import com.ridehailing.error.DomainException;
import com.ridehailing.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Translates every failure into the same JSON error shape. Business errors map to status codes
 * here, keeping HTTP out of the domain. Unexpected errors are logged and returned without
 * internals.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomain(DomainException ex) {
        return ResponseEntity.status(statusFor(ex.code()))
                .body(ErrorResponse.of(ex.code().name(), ex.getMessage(), List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred", List.of()));
    }

    static HttpStatus statusFor(ErrorCode code) {
        return switch (code) {
            case USER_NOT_FOUND, DRIVER_NOT_FOUND, RIDE_NOT_FOUND, COUPON_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DUPLICATE_USER, DUPLICATE_CAB, DUPLICATE_COUPON,
                 NO_DRIVER_AVAILABLE, USER_HAS_ONGOING_RIDE, RIDE_NOT_ONGOING -> HttpStatus.CONFLICT;
            case INVALID_COUPON -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .toList();
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("VALIDATION_FAILED", "Request validation failed", details));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Report which field was wrong, but never echo parser internals (class names, offsets).
        List<String> details = ex.getCause() instanceof JacksonException jackson && !jackson.getPath().isEmpty()
                ? List.of(jackson.getPath().stream()
                        .map(ref -> ref.getPropertyName() != null ? ref.getPropertyName() : "[" + ref.getIndex() + "]")
                        .collect(Collectors.joining(".")) + ": invalid value")
                : List.of();
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("MALFORMED_REQUEST", "Request body is missing or not valid JSON", details));
    }

    /** Everything else Spring MVC raises (404 route, 405, 415, ...) gets the same shape. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String code = status != null ? status.name() : "HTTP_" + statusCode.value();
        String message = ex instanceof org.springframework.web.ErrorResponse springError
                && springError.getBody().getDetail() != null
                ? springError.getBody().getDetail()
                : "Request could not be processed";
        return ResponseEntity.status(statusCode).headers(headers).body(ErrorResponse.of(code, message, List.of()));
    }
}
