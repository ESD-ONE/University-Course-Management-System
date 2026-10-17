package com.ucms.academic.exception;

/** Thrown when a delete is blocked because other, still-live records depend on the
 * target (e.g. a degree program with existing courses, or an academic year with
 * existing assignments) — the caller must remove those first. */
public class HasDependentsException extends RuntimeException {
    public HasDependentsException(String message) { super(message); }
}
