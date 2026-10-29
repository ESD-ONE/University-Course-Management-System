package com.ucms.identity.exception;

/**
 * Thrown when a non-deleted role already exists with the requested name. See G-03 in
 * docs/gap-analysis-report.md: an unconstrained {@code createRole()} let a custom
 * "Department Head" role collide in {@code kind} with the seeded "Lecturer" role, which
 * broke user creation with a {@code NonUniqueResultException}. Rejecting duplicate names
 * up front closes the root cause, not just the lookup's symptom.
 */
public class DuplicateRoleNameException extends RuntimeException {
    public DuplicateRoleNameException(String message) {
        super(message);
    }
}
