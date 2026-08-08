package com.gnanadhan.app.security.ratelimit;

/**
 * Result of a rate-limit check.
 *
 * <p>Returned by {@link RateLimiterService#check} so that the calling
 * service can decide what exception to throw (if any) based on its
 * own business context. This keeps the rate limiter framework-agnostic.
 */
public class RateLimitResult {

    private final boolean allowed;
    private final long remainingSeconds;

    private RateLimitResult(boolean allowed, long remainingSeconds) {
        this.allowed = allowed;
        this.remainingSeconds = remainingSeconds;
    }

    /** Creates an allowed result. */
    public static RateLimitResult allowed() {
        return new RateLimitResult(true, 0);
    }

    /** Creates a blocked result with the remaining lockout time. */
    public static RateLimitResult blocked(long remainingSeconds) {
        return new RateLimitResult(false, Math.max(0, remainingSeconds));
    }

    /** Returns {@code true} if the request is within the rate limit. */
    public boolean isAllowed() {
        return allowed;
    }

    /** Returns the remaining seconds until the window resets. Zero if allowed. */
    public long getRemainingSeconds() {
        return remainingSeconds;
    }
}
