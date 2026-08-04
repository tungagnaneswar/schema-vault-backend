package com.gnanadhan.app.security;

import com.gnanadhan.app.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class LoginRateLimiterService {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final long LOCKOUT_DURATION_SECONDS = 15 * 60;

    private final Map<String, LockoutEntry> attemptsCache = new ConcurrentHashMap<>();

    private static class LockoutEntry {
        int attempts;
        long lastAttemptTimestamp;

        LockoutEntry(int attempts, long lastAttemptTimestamp) {
            this.attempts = attempts;
            this.lastAttemptTimestamp = lastAttemptTimestamp;
        }
    }

    public void checkLockout(String key) {
        if (key == null || key.isBlank()) return;
        String normalizedKey = normalize(key);
        LockoutEntry entry = attemptsCache.get(normalizedKey);

        if (entry != null && entry.attempts >= MAX_FAILED_ATTEMPTS) {
            long now = Instant.now().getEpochSecond();
            long elapsed = now - entry.lastAttemptTimestamp;

            if (elapsed < LOCKOUT_DURATION_SECONDS) {
                long remainingMinutes = (LOCKOUT_DURATION_SECONDS - elapsed + 59) / 60;
                log.warn("Brute-force lockout triggered for key: {}. Remaining lockout: {} minutes.", normalizedKey, remainingMinutes);
                throw new UnauthorizedException("Account temporarily locked due to repeated failed login attempts. Please try again in " + remainingMinutes + " minute(s).");
            } else {
                attemptsCache.remove(normalizedKey);
            }
        }
    }

    public void recordFailedAttempt(String key) {
        if (key == null || key.isBlank()) return;
        String normalizedKey = normalize(key);
        long now = Instant.now().getEpochSecond();

        attemptsCache.compute(normalizedKey, (k, existing) -> {
            if (existing == null || (now - existing.lastAttemptTimestamp) >= LOCKOUT_DURATION_SECONDS) {
                return new LockoutEntry(1, now);
            }
            existing.attempts++;
            existing.lastAttemptTimestamp = now;
            return existing;
        });
    }

    public void recordSuccess(String key) {
        if (key == null || key.isBlank()) return;
        attemptsCache.remove(normalize(key));
    }

    private String normalize(String key) {
        return key.toLowerCase().trim();
    }
}
