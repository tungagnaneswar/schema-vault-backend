package com.gnanadhan.app.security.ratelimit;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link RateLimitStorage} backed by
 * {@link ConcurrentHashMap}.
 *
 * <p>Thread-safe via {@code compute()} for atomic read-modify-write.
 * Can be replaced by a Redis-backed implementation by providing
 * another {@code @Component} and using Spring profiles.
 */
@Component
public class InMemoryRateLimitStorage implements RateLimitStorage {

    private final Map<String, AttemptRecord> store = new ConcurrentHashMap<>();

    @Override
    public AttemptRecord increment(String key, long windowSeconds) {
        long now = Instant.now().getEpochSecond();

        return store.compute(key, (k, existing) -> {
            if (existing == null || (now - existing.getWindowStartTimestamp()) >= windowSeconds) {
                return new AttemptRecord(1, now);
            }
            existing.setCount(existing.getCount() + 1);
            return existing;
        });
    }

    @Override
    public Optional<AttemptRecord> get(String key) {
        return Optional.ofNullable(store.get(key));
    }

    @Override
    public void remove(String key) {
        store.remove(key);
    }
}
