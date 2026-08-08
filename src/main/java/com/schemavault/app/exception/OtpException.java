package com.schemavault.app.exception;

/**
 * Thrown for all OTP and reset-token validation failures.
 *
 * <p>
 * Mapped by {@link GlobalExceptionHandler} to HTTP 400 Bad Request.
 * Messages are intentionally generic where needed to avoid leaking state.
 */
public class OtpException extends RuntimeException {

    public OtpException(String message) {
        super(message);
    }
}
