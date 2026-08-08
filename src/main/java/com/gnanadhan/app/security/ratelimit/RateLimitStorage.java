package com.gnanadhan.app.security.ratelimit;

import java.util.Optional;

/**
 * Abstraction over rate-limit state storage.
 *
 * <p>Implementations must be thread-safe. The current default is
 * {@link InMemoryRateLimitStorage} (backed by {@code ConcurrentHashMap}).
 * A Redis-backed implementation can be swapped in later without
 * changing the business logic in {@link RateLimiterService}.
 */
public interface RateLimitStorage {

    /**
     * Atomically increments the attempt counter for the given key.
     * If the key does not exist or its window has expired, a new window is started.
     *
     * @param key           the rate-limit key (e.g. "LOGIN:user@example.com")
     * @param windowSeconds the duration of the sliding window in seconds
     * @return the updated {@link AttemptRecord} after incrementing
     */
    AttemptRecord increment(String key, long windowSeconds);

    /**
     * Returns the current attempt record for the given key without modifying it.
     *
     * @param key the rate-limit key
     * @return the record if present and not expired, otherwise empty
     */
    Optional<AttemptRecord> get(String key);

    /**
     * Removes the record for the given key (e.g. on successful login).
     *
     * @param key the rate-limit key
     */
    void remove(String key);
}
