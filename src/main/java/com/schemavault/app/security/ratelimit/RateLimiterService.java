package com.schemavault.app.security.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Unified rate-limiting service.
 *
 * <p>
 * Contains all core rate-limiting logic, delegating storage to
 * {@link RateLimitStorage}. This service is framework-agnostic — it does
 * not throw business-specific exceptions. Callers inspect the returned
 * {@link RateLimitResult} and decide what exception to throw based on
 * their own business context.
 *
 * <p>
 * Usage example:
 * 
 * <pre>{@code
 * RateLimitResult result = rateLimiterService.check(RateLimitType.LOGIN, email);
 * if (!result.isAllowed()) {
 *     long minutes = (result.getRemainingSeconds() + 59) / 60;
 *     throw new UnauthorizedException("Locked for " + minutes + " minute(s).");
 * }
 * }</pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {

    private final RateLimitStorage storage;

    /**
     * Checks whether the rate limit has been exceeded for the given type and key.
     *
     * <p>
     * Does <strong>not</strong> increment the counter — use {@link #record}
     * or {@link #checkAndRecord} for that.
     *
     * @param type the rate limit configuration to check against
     * @param key  the identifier (e.g. email, IP address)
     * @return a {@link RateLimitResult} indicating whether the request is allowed
     */
    public RateLimitResult check(RateLimitType type, String key) {
        if (key == null || key.isBlank()) {
            return RateLimitResult.allowed();
        }

        String storageKey = buildKey(type, normalize(key));
        Optional<AttemptRecord> recordOpt = storage.get(storageKey);

        if (recordOpt.isEmpty()) {
            return RateLimitResult.allowed();
        }

        AttemptRecord record = recordOpt.get();
        long now = Instant.now().getEpochSecond();
        long elapsed = now - record.getWindowStartTimestamp();

        if (elapsed >= type.getWindowSeconds()) {
            // Window has expired — clean up and allow
            storage.remove(storageKey);
            return RateLimitResult.allowed();
        }

        if (record.getCount() >= type.getMaxAttempts()) {
            long remaining = type.getWindowSeconds() - elapsed;
            log.warn("Rate limit exceeded for type={}, key={}. Remaining window: {}s",
                    type, normalize(key), remaining);
            return RateLimitResult.blocked(remaining);
        }

        return RateLimitResult.allowed();
    }

    /**
     * Records an attempt for the given type and key.
     *
     * <p>
     * Atomically increments the counter. If the window has expired,
     * a new window is started automatically by the storage layer.
     *
     * @param type the rate limit configuration
     * @param key  the identifier (e.g. email, IP address)
     */
    public void record(RateLimitType type, String key) {
        if (key == null || key.isBlank()) {
            return;
        }

        String storageKey = buildKey(type, normalize(key));
        storage.increment(storageKey, type.getWindowSeconds());
    }

    /**
     * Atomically checks the rate limit and records an attempt.
     *
     * <p>
     * Useful for fire-and-check patterns (e.g. IP throttling)
     * where each request should both count as an attempt and be
     * checked against the limit.
     *
     * @param type the rate limit configuration
     * @param key  the identifier (e.g. email, IP address)
     * @return a {@link RateLimitResult} indicating whether the request is allowed
     */
    public RateLimitResult checkAndRecord(RateLimitType type, String key) {
        if (key == null || key.isBlank()) {
            return RateLimitResult.allowed();
        }

        String storageKey = buildKey(type, normalize(key));
        AttemptRecord record = storage.increment(storageKey, type.getWindowSeconds());

        if (record.getCount() > type.getMaxAttempts()) {
            long now = Instant.now().getEpochSecond();
            long remaining = type.getWindowSeconds() - (now - record.getWindowStartTimestamp());
            log.warn("Rate limit exceeded for type={}, key={}. Remaining window: {}s",
                    type, normalize(key), remaining);
            return RateLimitResult.blocked(Math.max(0, remaining));
        }

        return RateLimitResult.allowed();
    }

    /**
     * Resets the counter for the given type and key.
     *
     * <p>
     * Typically called on success (e.g. successful login clears
     * the failed-attempt counter).
     *
     * @param type the rate limit configuration
     * @param key  the identifier (e.g. email, IP address)
     */
    public void reset(RateLimitType type, String key) {
        if (key == null || key.isBlank()) {
            return;
        }

        String storageKey = buildKey(type, normalize(key));
        storage.remove(storageKey);
    }

    /**
     * Builds a namespaced storage key to prevent collisions between
     * different rate limit types for the same identifier.
     */
    private String buildKey(RateLimitType type, String normalizedKey) {
        return type.name() + ":" + normalizedKey;
    }

    private String normalize(String key) {
        return key.toLowerCase().trim();
    }
}
