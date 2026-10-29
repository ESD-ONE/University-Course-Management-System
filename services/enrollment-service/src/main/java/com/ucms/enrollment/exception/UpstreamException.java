package com.ucms.enrollment.exception;

/** A service this one depends on synchronously failed or was unreachable → 503. */
public class UpstreamException extends RuntimeException {
    public UpstreamException(String message) {
        super(message);
    }
}
