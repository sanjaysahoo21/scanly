package com.scanly.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Global Exception Handler.
 *
 * Catches exceptions thrown across all controllers and converts them to
 * proper JSON responses, preventing Spring from forwarding to /error
 * with an empty body (which breaks the frontend's JSON parsing).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles @Valid validation failures (e.g. short password, missing fields).
     * Returns a 400 Bad Request with a map of field → error message.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("error", "VALIDATION_FAILED");
        body.put("message", "Request validation failed");
        body.put("fields", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Catch-all for unexpected runtime exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericError(Exception ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "INTERNAL_ERROR");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
