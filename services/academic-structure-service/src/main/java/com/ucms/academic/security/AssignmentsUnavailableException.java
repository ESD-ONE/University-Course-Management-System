package com.ucms.academic.security;

/** The caller's scope assignments couldn't be loaded (Identity & Access unreachable), so
 *  a permission check can't be decided — answered with 503, never with a guessed "no". */
public class AssignmentsUnavailableException extends RuntimeException {
    public AssignmentsUnavailableException(Throwable cause) {
        super("Could not load your permissions from Identity & Access — try again shortly.", cause);
    }
}
