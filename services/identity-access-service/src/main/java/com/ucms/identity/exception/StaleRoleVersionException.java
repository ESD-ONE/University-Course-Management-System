package com.ucms.identity.exception;

/**
 * Thrown when a role-permission update request's {@code version} doesn't match the
 * role's current {@code @Version}. See G-10 in docs/gap-analysis-report.md: the DTO's
 * {@code version} field used to be accepted but never compared against anything, which
 * misled API consumers into thinking they were sending a concurrency token that mattered.
 * Maps to 409 CONFLICT the same way {@code ObjectOptimisticLockingFailureException} does.
 */
public class StaleRoleVersionException extends RuntimeException {
    public StaleRoleVersionException(String message) {
        super(message);
    }
}
