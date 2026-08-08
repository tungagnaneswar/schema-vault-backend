package com.schemavault.app.security.ratelimit;

/**
 * Represents a rate-limit attempt record within a time window.
 *
 * <p>
 * Tracks the number of attempts and when the current window started.
 * Used by {@link RateLimitStorage} implementations to manage state.
 */
public class AttemptRecord {

    private int count;
    private long windowStartTimestamp;

    public AttemptRecord(int count, long windowStartTimestamp) {
        this.count = count;
        this.windowStartTimestamp = windowStartTimestamp;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public long getWindowStartTimestamp() {
        return windowStartTimestamp;
    }

    public void setWindowStartTimestamp(long windowStartTimestamp) {
        this.windowStartTimestamp = windowStartTimestamp;
    }
}
