package com.ucms.grading.exception;

/** 409 with a machine-readable code alongside the message (e.g. COURSE_FULL, which the
 *  Browse Courses screen turns into a "join the waitlist?" prompt). */
public class ConflictException extends RuntimeException {
    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
