package com.bryant.notifications.api;

import com.bryant.notifications.service.NotificationExceptions.IdempotencyConflict;
import com.bryant.notifications.service.NotificationExceptions.NotFound;
import com.bryant.notifications.service.NotificationExceptions.QueueUnavailable;
import com.bryant.notifications.service.NotificationExceptions.RateLimitExceeded;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(NotFound.class)
    ResponseEntity<ApiError> notFound(NotFound e, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IdempotencyConflict.class)
    ResponseEntity<ApiError> conflict(IdempotencyConflict e) {
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(RateLimitExceeded.class)
    ResponseEntity<ApiError> rateLimit(RateLimitExceeded e) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", Long.toString(e.getRetryAfterSeconds()));
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).headers(headers)
                .body(new ApiError(Instant.now(), 429, "Too Many Requests", e.getMessage()));
    }

    @ExceptionHandler(QueueUnavailable.class)
    ResponseEntity<ApiError> queueUnavailable(QueueUnavailable e) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class, IllegalArgumentException.class})
    ResponseEntity<ApiError> badRequest(Exception e) {
        String message = e.getMessage();
        if (e instanceof MethodArgumentNotValidException validationException) {
            message = validationException.getBindingResult().getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        }
        return error(HttpStatus.BAD_REQUEST, message == null ? "Invalid request" : message);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message));
    }
}
