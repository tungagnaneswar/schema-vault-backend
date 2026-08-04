package com.gnanadhan.app.security;

import com.gnanadhan.app.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to protect against login brute-force attacks by tracking failed authentication attempts
 * and enforcing temporary account lockouts.
 */
@Service
@Slf4j
public class LoginRateLimiterService {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final long LOCKOUT_DURATION_SECONDS = 15 * 60; // 15 minutes

    private final Map<String, LockoutEntry> attemptsCache = new ConcurrentHashMap<>();

    private static class LockoutEntry {
        int attempts;
        long lastAttemptTimestamp;

        LockoutEntry(int attempts, long lastAttemptTimestamp) {
            this.attempts = attempts;
            this.lastAttemptTimestamp = lastAttemptTimestamp;
        }
    }

    /**
     * Checks if the given key (email) is currently locked out due to excessive failed attempts.
     * Throws an {@link UnauthorizedException} if locked.
     */
    public void checkLockout(String key) {
        if (key == null) return;
        String normalizedKey = key.toLowerCase().trim();
        LockoutEntry entry = attemptsCache.get(normalizedKey);

        if (entry != null && entry.attempts >= MAX_FAILED_ATTEMPTS) {
            long now = Instant.now().getEpochSecond();
            long elapsed = now - entry.lastAttemptTimestamp;

            if (elapsed < LOCKOUT_DURATION_SECONDS) {
                long remainingMinutes = (LOCKOUT_DURATION_SECONDS - elapsed + 59) / 60;
                log.warn("Brute-force lockout triggered for key: {}. Remaining lockout: {} minutes.", normalizedKey, remainingMinutes);
                throw new UnauthorizedException("Account temporarily locked due to repeated failed login attempts. Please try again in " + remainingMinutes + " minute(s).");
            } else {
                // Lockout period expired; reset entry
                attemptsCache.remove(normalizedKey);
            }
        }
    }

    /**
     * Records a failed login attempt for the given key (email).
     */
    public void recordFailedAttempt(String key) {
        if (key == null) return;
        String normalizedKey = key.toLowerCase().trim();
        long now = Instant.now().getEpochSecond();

        attemptsCache.compute(normalizedKey, (k, existing) -> {
            if (existing == null) {
                return new LockoutEntry(1, now);
            }
            long elapsed = now - existing.lastAttemptTimestamp;
            if (elapsed >= LOCKOUT_DURATION_SECONDS) {
                // Reset if previous window expired
                return new LockoutEntry(1, now);
            }
            existing.attempts += 1;
            existing.lastAttemptTimestamp = now;
            return existing;
        });
    }

    /**
     * Resets the failed attempt counter upon a successful login.
     */
    public void recordSuccess(String key) {
        if (key == null) return;
        String normalizedKey = key.toLowerCase().trim();
        attemptsCache.remove(normalizedKey);
    }
}
