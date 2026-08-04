package com.gnanadhan.app.security;

import com.gnanadhan.app.exception.OtpException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class OtpRateLimiterService {

    public static final int IP_RATE_LIMIT_PER_MINUTE = 10;
    public static final int DISPATCH_COOLDOWN_SECONDS = 60;
    public static final int HOURLY_DISPATCH_CAP = 5;
    public static final long SHORT_LOCKOUT_DURATION_SECONDS = 3 * 60;

    private final Map<String, IpTracker> ipCache = new ConcurrentHashMap<>();
    private final Map<String, EmailDispatchTracker> dispatchCache = new ConcurrentHashMap<>();
    private final Map<String, LockoutEntry> lockoutCache = new ConcurrentHashMap<>();

    private static class IpTracker {
        int count;
        long windowStartTimestamp;

        IpTracker(int count, long windowStartTimestamp) {
            this.count = count;
            this.windowStartTimestamp = windowStartTimestamp;
        }
    }

    private static class EmailDispatchTracker {
        long lastDispatchTimestamp;
        int hourlyCount;
        long hourWindowStartTimestamp;

        EmailDispatchTracker(long timestamp) {
            this.lastDispatchTimestamp = timestamp;
            this.hourlyCount = 1;
            this.hourWindowStartTimestamp = timestamp;
        }
    }

    private static class LockoutEntry {
        long lockoutEndTimestamp;

        LockoutEntry(long lockoutEndTimestamp) {
            this.lockoutEndTimestamp = lockoutEndTimestamp;
        }
    }

    public void checkIpRateLimit(String clientIp) {
        if (clientIp == null || clientIp.isBlank() || "UNKNOWN".equalsIgnoreCase(clientIp)) {
            return;
        }
        long now = Instant.now().getEpochSecond();
        IpTracker tracker = ipCache.compute(clientIp, (ip, existing) -> {
            if (existing == null || (now - existing.windowStartTimestamp) >= 60) {
                return new IpTracker(1, now);
            }
            existing.count++;
            return existing;
        });

        if (tracker.count > IP_RATE_LIMIT_PER_MINUTE) {
            log.warn("[IP: {}] Rate limit exceeded on OTP endpoint. Total requests in window: {}", clientIp, tracker.count);
            throw new OtpException("Too many requests from this IP. Please wait a minute before trying again.");
        }
    }

    public void checkEmailLockout(String email, String clientIp) {
        if (email == null || email.isBlank()) return;
        String normalizedEmail = normalize(email);
        LockoutEntry entry = lockoutCache.get(normalizedEmail);

        if (entry != null) {
            long now = Instant.now().getEpochSecond();
            if (now < entry.lockoutEndTimestamp) {
                long remainingSeconds = entry.lockoutEndTimestamp - now;
                long remainingMinutes = (remainingSeconds + 59) / 60;
                log.warn("[IP: {}] Verification attempted for locked email: {}. Remaining lock: {}s", clientIp, normalizedEmail, remainingSeconds);
                throw new OtpException("Account temporarily paused due to repeated failed attempts. Please wait " + remainingMinutes + " minute(s).");
            } else {
                lockoutCache.remove(normalizedEmail);
            }
        }
    }

    public void checkEmailDispatchRateLimit(String email, String clientIp) {
        if (email == null || email.isBlank()) return;
        String normalizedEmail = normalize(email);
        long now = Instant.now().getEpochSecond();

        EmailDispatchTracker tracker = dispatchCache.get(normalizedEmail);
        if (tracker != null) {
            long elapsedSinceLast = now - tracker.lastDispatchTimestamp;
            if (elapsedSinceLast < DISPATCH_COOLDOWN_SECONDS) {
                long remaining = DISPATCH_COOLDOWN_SECONDS - elapsedSinceLast;
                log.warn("[IP: {}] Dispatch cooldown active for email: {}. Remaining: {}s", clientIp, normalizedEmail, remaining);
                throw new OtpException("Please wait " + remaining + " seconds before requesting another OTP.");
            }

            long elapsedHour = now - tracker.hourWindowStartTimestamp;
            if (elapsedHour < 3600 && tracker.hourlyCount >= HOURLY_DISPATCH_CAP) {
                log.warn("[IP: {}] Hourly email cap reached for email: {}. Count: {}", clientIp, normalizedEmail, tracker.hourlyCount);
                throw new OtpException("Maximum OTP request limit reached for this hour. Please try again later.");
            }
        }
    }

    public void recordEmailDispatch(String email, String clientIp) {
        if (email == null || email.isBlank()) return;
        String normalizedEmail = normalize(email);
        long now = Instant.now().getEpochSecond();

        dispatchCache.compute(normalizedEmail, (k, existing) -> {
            if (existing == null || (now - existing.hourWindowStartTimestamp) >= 3600) {
                return new EmailDispatchTracker(now);
            }
            existing.lastDispatchTimestamp = now;
            existing.hourlyCount++;
            return existing;
        });
        log.info("[IP: {}] OTP email dispatched to: {}", clientIp, normalizedEmail);
    }

    public void triggerShortLockout(String email, String clientIp) {
        if (email == null || email.isBlank()) return;
        String normalizedEmail = normalize(email);
        long lockoutEnd = Instant.now().getEpochSecond() + SHORT_LOCKOUT_DURATION_SECONDS;
        lockoutCache.put(normalizedEmail, new LockoutEntry(lockoutEnd));
        log.warn("[IP: {}] 3-minute OTP pause triggered for email: {}", clientIp, normalizedEmail);
    }

    public void resetLockout(String email) {
        if (email == null || email.isBlank()) return;
        lockoutCache.remove(normalize(email));
    }

    private String normalize(String email) {
        return email.toLowerCase().trim();
    }
}
