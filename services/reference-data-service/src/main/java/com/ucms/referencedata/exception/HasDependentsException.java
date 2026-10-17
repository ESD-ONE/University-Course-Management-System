package com.ucms.referencedata.exception;

/** Thrown when a delete is blocked because other, still-live records depend on the
 * target (e.g. a faculty with existing departments) — the caller must remove those first. */
public class HasDependentsException extends RuntimeException {
    public HasDependentsException(String message) { super(message); }
}
