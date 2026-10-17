package com.ucms.academic.exception;

/** Thrown when a write targets an ARCHIVED academic year — read-only regardless of role. */
public class ArchivedYearException extends RuntimeException {
    public ArchivedYearException(String message) { super(message); }
}
