package com.schemavault.app.security.ratelimit;

/**
 * Centralized rate-limit configuration.
 *
 * <p>
 * Each constant defines the maximum number of attempts allowed
 * and the time window (in seconds) within which those attempts are counted.
 * Adding a new rate-limited feature is as simple as adding a new enum constant.
 *
 * <p>
 * This enum is intentionally free of business logic, exception types,
 * or framework-specific concerns — it holds configuration only.
 */
public enum RateLimitType {

    /** Failed login attempts: 5 attempts per 15-minute window. */
    LOGIN(5, 15 * 60),

    /** OTP endpoint IP throttle: 10 requests per 60-second window. */
    OTP_IP_THROTTLE(10, 60),

    /** OTP email dispatch cooldown: 1 dispatch per 60-second window. */
    OTP_DISPATCH_COOLDOWN(1, 60),

    /** OTP email dispatch hourly cap: 5 dispatches per 1-hour window. */
    OTP_DISPATCH_HOURLY(5, 3600),

    /** OTP verification lockout: 1-attempt marker with 3-minute window. */
    OTP_VERIFICATION_LOCKOUT(1, 3 * 60);

    private final int maxAttempts;
    private final long windowSeconds;

    RateLimitType(int maxAttempts, long windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.windowSeconds = windowSeconds;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public long getWindowSeconds() {
        return windowSeconds;
    }
}
