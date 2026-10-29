package com.ucms.academic.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateEntityException.class)
    public ResponseEntity<Object> handleDuplicate(DuplicateEntityException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ArchivedYearException.class)
    public ResponseEntity<Object> handleArchivedYear(ArchivedYearException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(HasDependentsException.class)
    public ResponseEntity<Object> handleHasDependents(HasDependentsException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(com.ucms.academic.security.AssignmentsUnavailableException.class)
    public ResponseEntity<Object> handleAssignmentsUnavailable(RuntimeException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public ResponseEntity<Object> handleNotFound(jakarta.persistence.EntityNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Object> handleOptimisticLock(Exception ex) {
        return error(HttpStatus.CONFLICT, "This record was changed by someone else — refresh and try again.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException ex) {
        // The field's own message only: ex.getMessage() spells out internal class and
        // method signatures, which neither belong in front of users nor on the wire.
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getDefaultMessage() != null ? f.getDefaultMessage() : f.getField() + " is invalid")
                .findFirst().orElse("invalid request");
        return error(HttpStatus.BAD_REQUEST, "Validation failed: " + detail);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(Exception ex) {
        return error(HttpStatus.FORBIDDEN, "You don't have permission to do that.");
    }

    private ResponseEntity<Object> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "message", message
        ));
    }
}
