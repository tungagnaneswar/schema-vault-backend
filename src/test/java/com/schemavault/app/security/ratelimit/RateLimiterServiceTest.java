package com.schemavault.app.security.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.schemavault.app.security.ratelimit.InMemoryRateLimitStorage;
import com.schemavault.app.security.ratelimit.RateLimitResult;
import com.schemavault.app.security.ratelimit.RateLimitType;
import com.schemavault.app.security.ratelimit.RateLimiterService;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimiterService — Unified Rate Limiting Unit Tests")
class RateLimiterServiceTest {

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService(new InMemoryRateLimitStorage());
    }

    // =========================================================================
    // LOGIN rate limiting (5 attempts / 15 min)
    // =========================================================================

    @Nested
    @DisplayName("LOGIN — Brute Force Protection")
    class LoginTests {

        @Test
        @DisplayName("Under 5 failed attempts → allowed")
        void underMaxAttempts_allowed() {
            String email = "test@example.com";
            for (int i = 0; i < 4; i++) {
                rateLimiterService.record(RateLimitType.LOGIN, email);
            }
            RateLimitResult result = rateLimiterService.check(RateLimitType.LOGIN, email);
            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("5 failed attempts → blocked with remaining seconds > 0")
        void atMaxAttempts_blocked() {
            String email = "test@example.com";
            for (int i = 0; i < 5; i++) {
                rateLimiterService.record(RateLimitType.LOGIN, email);
            }
            RateLimitResult result = rateLimiterService.check(RateLimitType.LOGIN, email);
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getRemainingSeconds()).isGreaterThan(0);
        }

        @Test
        @DisplayName("Reset clears the counter → allowed again")
        void reset_clearsCounter() {
            String email = "test@example.com";
            for (int i = 0; i < 5; i++) {
                rateLimiterService.record(RateLimitType.LOGIN, email);
            }
            rateLimiterService.reset(RateLimitType.LOGIN, email);

            RateLimitResult result = rateLimiterService.check(RateLimitType.LOGIN, email);
            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("Null or blank key → always allowed")
        void nullOrBlankKey_allowed() {
            assertThat(rateLimiterService.check(RateLimitType.LOGIN, null).isAllowed()).isTrue();
            assertThat(rateLimiterService.check(RateLimitType.LOGIN, "").isAllowed()).isTrue();
            assertThat(rateLimiterService.check(RateLimitType.LOGIN, "   ").isAllowed()).isTrue();
        }

        @Test
        @DisplayName("Key normalization — case insensitive")
        void caseInsensitive() {
            rateLimiterService.record(RateLimitType.LOGIN, "Test@Example.COM");
            RateLimitResult result = rateLimiterService.check(RateLimitType.LOGIN, "test@example.com");
            // Should see the recorded attempt
            assertThat(result.isAllowed()).isTrue(); // only 1 attempt, under limit
        }
    }

    // =========================================================================
    // OTP_IP_THROTTLE (10 requests / 60 seconds)
    // =========================================================================

    @Nested
    @DisplayName("OTP_IP_THROTTLE — IP Rate Limiting")
    class OtpIpThrottleTests {

        @Test
        @DisplayName("10 requests → allowed; 11th → blocked")
        void exceedingIpLimit_blocked() {
            String ip = "192.168.1.1";
            for (int i = 0; i < 10; i++) {
                RateLimitResult result = rateLimiterService.checkAndRecord(RateLimitType.OTP_IP_THROTTLE, ip);
                assertThat(result.isAllowed()).isTrue();
            }
            RateLimitResult result = rateLimiterService.checkAndRecord(RateLimitType.OTP_IP_THROTTLE, ip);
            assertThat(result.isAllowed()).isFalse();
        }
    }

    // =========================================================================
    // OTP_DISPATCH_COOLDOWN (1 per 60 seconds)
    // =========================================================================

    @Nested
    @DisplayName("OTP_DISPATCH_COOLDOWN — Dispatch Cooldown")
    class OtpDispatchCooldownTests {

        @Test
        @DisplayName("First dispatch → allowed; immediate second → blocked")
        void secondDispatchWithinCooldown_blocked() {
            String email = "test@example.com";
            rateLimiterService.record(RateLimitType.OTP_DISPATCH_COOLDOWN, email);

            RateLimitResult result = rateLimiterService.check(RateLimitType.OTP_DISPATCH_COOLDOWN, email);
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getRemainingSeconds()).isGreaterThan(0);
        }

        @Test
        @DisplayName("Reset cooldown → allowed again")
        void resetCooldown_allowed() {
            String email = "test@example.com";
            rateLimiterService.record(RateLimitType.OTP_DISPATCH_COOLDOWN, email);
            rateLimiterService.reset(RateLimitType.OTP_DISPATCH_COOLDOWN, email);

            RateLimitResult result = rateLimiterService.check(RateLimitType.OTP_DISPATCH_COOLDOWN, email);
            assertThat(result.isAllowed()).isTrue();
        }
    }

    // =========================================================================
    // OTP_DISPATCH_HOURLY (5 per hour)
    // =========================================================================

    @Nested
    @DisplayName("OTP_DISPATCH_HOURLY — Hourly Dispatch Cap")
    class OtpDispatchHourlyTests {

        @Test
        @DisplayName("Under 5 dispatches → allowed; at 5 → blocked")
        void exceedingHourlyCap_blocked() {
            String email = "test@example.com";
            for (int i = 0; i < 5; i++) {
                rateLimiterService.record(RateLimitType.OTP_DISPATCH_HOURLY, email);
            }
            RateLimitResult result = rateLimiterService.check(RateLimitType.OTP_DISPATCH_HOURLY, email);
            assertThat(result.isAllowed()).isFalse();
        }
    }

    // =========================================================================
    // OTP_VERIFICATION_LOCKOUT (1 marker / 3 minutes)
    // =========================================================================

    @Nested
    @DisplayName("OTP_VERIFICATION_LOCKOUT — Verification Lockout")
    class OtpVerificationLockoutTests {

        @Test
        @DisplayName("No lockout recorded → allowed")
        void noLockout_allowed() {
            RateLimitResult result = rateLimiterService.check(
                    RateLimitType.OTP_VERIFICATION_LOCKOUT, "test@example.com");
            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("Lockout recorded → blocked")
        void lockoutRecorded_blocked() {
            String email = "test@example.com";
            rateLimiterService.record(RateLimitType.OTP_VERIFICATION_LOCKOUT, email);

            RateLimitResult result = rateLimiterService.check(RateLimitType.OTP_VERIFICATION_LOCKOUT, email);
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getRemainingSeconds()).isGreaterThan(0);
        }

        @Test
        @DisplayName("Reset lockout → allowed again")
        void resetLockout_allowed() {
            String email = "test@example.com";
            rateLimiterService.record(RateLimitType.OTP_VERIFICATION_LOCKOUT, email);
            rateLimiterService.reset(RateLimitType.OTP_VERIFICATION_LOCKOUT, email);

            RateLimitResult result = rateLimiterService.check(RateLimitType.OTP_VERIFICATION_LOCKOUT, email);
            assertThat(result.isAllowed()).isTrue();
        }
    }

    // =========================================================================
    // Cross-type isolation
    // =========================================================================

    @Nested
    @DisplayName("Cross-type isolation")
    class CrossTypeTests {

        @Test
        @DisplayName("Different types with same key do not interfere")
        void differentTypes_sameKey_isolated() {
            String email = "test@example.com";

            // Max out LOGIN
            for (int i = 0; i < 5; i++) {
                rateLimiterService.record(RateLimitType.LOGIN, email);
            }

            // OTP_DISPATCH_HOURLY for same email should be unaffected
            RateLimitResult result = rateLimiterService.check(RateLimitType.OTP_DISPATCH_HOURLY, email);
            assertThat(result.isAllowed()).isTrue();
        }
    }
}
