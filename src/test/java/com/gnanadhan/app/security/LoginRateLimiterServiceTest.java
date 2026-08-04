package com.gnanadhan.app.security;

import com.gnanadhan.app.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LoginRateLimiterService — Brute Force Protection Unit Tests")
class LoginRateLimiterServiceTest {

    private LoginRateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService = new LoginRateLimiterService();
    }

    @Test
    @DisplayName("Under 5 failed attempts → allowed without exception")
    void underFiveFailedAttempts_allowed() {
        String email = "test@example.com";
        for (int i = 0; i < 4; i++) {
            rateLimiterService.recordFailedAttempt(email);
        }
        assertThatCode(() -> rateLimiterService.checkLockout(email))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("5 failed attempts → triggers account lockout exception")
    void fiveFailedAttempts_triggersLockout() {
        String email = "test@example.com";
        for (int i = 0; i < 5; i++) {
            rateLimiterService.recordFailedAttempt(email);
        }

        assertThatThrownBy(() -> rateLimiterService.checkLockout(email))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Account temporarily locked");
    }

    @Test
    @DisplayName("Successful login → clears failed attempt counter")
    void successfulLogin_clearsCounter() {
        String email = "test@example.com";
        for (int i = 0; i < 4; i++) {
            rateLimiterService.recordFailedAttempt(email);
        }

        rateLimiterService.recordSuccess(email);

        assertThatCode(() -> rateLimiterService.checkLockout(email))
                .doesNotThrowAnyException();
    }
}
