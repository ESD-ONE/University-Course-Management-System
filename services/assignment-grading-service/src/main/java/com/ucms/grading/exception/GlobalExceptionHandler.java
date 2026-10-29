package com.ucms.grading.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Object> handleConflict(ConflictException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), ex.getCode());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleIntegrity(DataIntegrityViolationException ex) {
        return error(HttpStatus.CONFLICT, "This conflicts with an existing record — refresh and try again.", "CONFLICT");
    }

    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public ResponseEntity<Object> handleNotFound(jakarta.persistence.EntityNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Object> handleOptimisticLock(Exception ex) {
        return error(HttpStatus.CONFLICT, "This record was changed by someone else — refresh and try again.", "STALE");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .findFirst().orElse("invalid request");
        return error(HttpStatus.BAD_REQUEST, "Validation failed: " + detail, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(Exception ex) {
        return error(HttpStatus.FORBIDDEN, "You don't have permission to do that.", null);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<Object> handleTooLarge(Exception ex) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "That file is too large — the limit is 20MB.", "FILE_TOO_LARGE");
    }

    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    public ResponseEntity<Object> handleMissingPart(Exception ex) {
        return error(HttpStatus.BAD_REQUEST, "Choose a file to upload.", null);
    }

    @ExceptionHandler(UpstreamException.class)
    public ResponseEntity<Object> handleUpstream(UpstreamException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), "UPSTREAM_UNAVAILABLE");
    }

    private ResponseEntity<Object> error(HttpStatus status, String message, String code) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("message", message);
        if (code != null) body.put("code", code);
        return ResponseEntity.status(status).body(body);
    }
}
