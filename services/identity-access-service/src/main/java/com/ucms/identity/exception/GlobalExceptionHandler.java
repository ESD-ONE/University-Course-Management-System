package com.ucms.identity.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Account changes are mirrored to Keycloak in the same transaction; if it can't be
     *  reached the change is rolled back and the caller can retry. */
    @ExceptionHandler(com.ucms.identity.keycloak.KeycloakUnavailableException.class)
    public ResponseEntity<Object> handleKeycloakUnavailable(RuntimeException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE,
                "The sign-in service is unavailable, so this change was not saved — try again shortly.");
    }

    @ExceptionHandler(com.ucms.identity.keycloak.KeycloakRejectedException.class)
    public ResponseEntity<Object> handleKeycloakRejected(RuntimeException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(EmailAlreadyInUseException.class)
    public ResponseEntity<Object> handleEmailInUse(EmailAlreadyInUseException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(DuplicateRoleNameException.class)
    public ResponseEntity<Object> handleDuplicateRoleName(DuplicateRoleNameException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(StaleRoleVersionException.class)
    public ResponseEntity<Object> handleStaleRoleVersion(StaleRoleVersionException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(WrongPasswordException.class)
    public ResponseEntity<Object> handleWrongPassword(WrongPasswordException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
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

    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public ResponseEntity<Object> handleNotFound(jakarta.persistence.EntityNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    private ResponseEntity<Object> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "message", message
        ));
    }
}
