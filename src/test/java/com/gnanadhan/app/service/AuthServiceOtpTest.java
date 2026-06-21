package com.gnanadhan.app.service;

import com.gnanadhan.app.entity.PasswordResetOtp;
import com.gnanadhan.app.entity.Role;
import com.gnanadhan.app.entity.User;
import com.gnanadhan.app.exception.OtpException;
import com.gnanadhan.app.repository.PasswordResetOtpRepository;
import com.gnanadhan.app.repository.RoleRepository;
import com.gnanadhan.app.repository.UserRepository;
import com.gnanadhan.app.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the OTP-based forgot-password flow in {@link AuthService}.
 *
 * <p>All dependencies are mocked with Mockito — no Spring context is loaded.
 */
@DisplayName("AuthService — OTP Password Reset Flow")
class AuthServiceOtpTest {

    // -------------------------------------------------------------------------
    // Mocks
    // -------------------------------------------------------------------------

    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PasswordResetOtpRepository otpRepository;
    @Mock private EmailService emailService;

    @InjectMocks
    private AuthService authService;

    // -------------------------------------------------------------------------
    // Test fixtures
    // -------------------------------------------------------------------------

    private User testUser;
    private Role testRole;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        testRole = new Role();
        testRole.setName("ADMIN");

        testUser = User.builder()
                .id(1L)
                .email("user@example.com")
                .password("$2a$10$hashedpassword")
                .role(testRole)
                .isActive(true)
                .build();
    }

    // =========================================================================
    // Step 1 — forgotPassword
    // =========================================================================

    @Nested
    @DisplayName("forgotPassword()")
    class ForgotPasswordTests {

        @Test
        @DisplayName("valid email → deletes old OTPs, saves new OTP, sends email")
        void forgotPassword_validEmail_sendsOtp() {
            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
            when(otpRepository.findTopByUserOrderByCreatedAtDesc(testUser)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashedotp");
            when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            authService.forgotPassword("user@example.com");

            verify(otpRepository).deleteByUser(testUser);
            verify(otpRepository).save(any(PasswordResetOtp.class));
            verify(emailService).sendOtpEmail(eq("user@example.com"), anyString());
        }

        @Test
        @DisplayName("unknown email → silent no-op, nothing saved or sent")
        void forgotPassword_unknownEmail_noException() {
            when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

            authService.forgotPassword("nobody@example.com");

            verifyNoInteractions(otpRepository, emailService);
        }

        @Test
        @DisplayName("rate-limited (OTP < 1 min ago) → silent no-op, nothing saved or sent")
        void forgotPassword_rateLimited_silentNoOp() {
            PasswordResetOtp recentOtp = PasswordResetOtp.builder()
                    .user(testUser)
                    .otpHash("$2a$10$hash")
                    .expiresAt(ZonedDateTime.now().plusMinutes(9))
                    .createdAt(ZonedDateTime.now().minusSeconds(30)) // 30s ago — within 60s limit
                    .build();

            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
            when(otpRepository.findTopByUserOrderByCreatedAtDesc(testUser)).thenReturn(Optional.of(recentOtp));

            authService.forgotPassword("user@example.com");

            verify(otpRepository, never()).deleteByUser(any());
            verify(otpRepository, never()).save(any());
            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("OTP older than 1 min → not rate-limited, new OTP sent")
        void forgotPassword_notRateLimited_sendsNewOtp() {
            PasswordResetOtp oldOtp = PasswordResetOtp.builder()
                    .user(testUser)
                    .otpHash("$2a$10$oldhash")
                    .expiresAt(ZonedDateTime.now().minusMinutes(5)) // already expired
                    .createdAt(ZonedDateTime.now().minusMinutes(12)) // more than 60s ago
                    .build();

            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
            when(otpRepository.findTopByUserOrderByCreatedAtDesc(testUser)).thenReturn(Optional.of(oldOtp));
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$newhash");
            when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            authService.forgotPassword("user@example.com");

            verify(otpRepository).deleteByUser(testUser);
            verify(otpRepository).save(any(PasswordResetOtp.class));
            verify(emailService).sendOtpEmail(eq("user@example.com"), anyString());
        }
    }

    // =========================================================================
    // Step 2 — verifyOtp
    // =========================================================================

    @Nested
    @DisplayName("verifyOtp()")
    class VerifyOtpTests {

        @Test
        @DisplayName("valid OTP → returns reset token, saves updated record")
        void verifyOtp_validOtp_returnsResetToken() {
            PasswordResetOtp otpRecord = buildActiveOtp(0);

            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
            when(otpRepository.findActiveOtpForUpdateByUser(
                    eq(testUser), any())).thenReturn(Optional.of(otpRecord));
            when(passwordEncoder.matches("123456", "$2a$10$hashedotp")).thenReturn(true);
            when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            String resetToken = authService.verifyOtp("user@example.com", "123456");

            assertThat(resetToken).isNotBlank();
            assertThat(otpRecord.getResetToken()).isEqualTo(resetToken);
            assertThat(otpRecord.getResetTokenExpiresAt()).isAfter(ZonedDateTime.now());
            verify(otpRepository, times(2)).save(otpRecord); // once for attempt, once for token
        }

        @Test
        @DisplayName("wrong OTP → increments attempt count, throws OtpException")
        void verifyOtp_wrongOtp_throwsException() {
            PasswordResetOtp otpRecord = buildActiveOtp(0);

            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
            when(otpRepository.findActiveOtpForUpdateByUser(
                    eq(testUser), any())).thenReturn(Optional.of(otpRecord));
            when(passwordEncoder.matches("000000", "$2a$10$hashedotp")).thenReturn(false);
            when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatThrownBy(() -> authService.verifyOtp("user@example.com", "000000"))
                    .isInstanceOf(OtpException.class)
                    .hasMessageContaining("Invalid OTP");

            assertThat(otpRecord.getAttemptCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("expired OTP → throws OtpException (no record found)")
        void verifyOtp_expiredOtp_throwsException() {
            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
            when(otpRepository.findActiveOtpForUpdateByUser(
                    eq(testUser), any())).thenReturn(Optional.empty()); // expired = not found

            assertThatThrownBy(() -> authService.verifyOtp("user@example.com", "123456"))
                    .isInstanceOf(OtpException.class)
                    .hasMessageContaining("Invalid or expired OTP");
        }

        @Test
        @DisplayName("attempt count at 5 → throws locked exception before checking OTP")
        void verifyOtp_maxAttemptsExceeded_throwsException() {
            PasswordResetOtp lockedOtp = buildActiveOtp(5); // already at max

            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
            when(otpRepository.findActiveOtpForUpdateByUser(
                    eq(testUser), any())).thenReturn(Optional.of(lockedOtp));

            assertThatThrownBy(() -> authService.verifyOtp("user@example.com", "123456"))
                    .isInstanceOf(OtpException.class)
                    .hasMessageContaining("Too many failed attempts");

            // passwordEncoder must NOT be called when already locked
            verifyNoInteractions(passwordEncoder);
        }

        @Test
        @DisplayName("4th attempt wrong OTP → attempt count becomes 5, subsequent call is locked")
        void verifyOtp_fourthFailureLocks() {
            PasswordResetOtp otpRecord = buildActiveOtp(4); // one more attempt will lock

            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
            when(otpRepository.findActiveOtpForUpdateByUser(
                    eq(testUser), any())).thenReturn(Optional.of(otpRecord));
            when(passwordEncoder.matches("000000", "$2a$10$hashedotp")).thenReturn(false);
            when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatThrownBy(() -> authService.verifyOtp("user@example.com", "000000"))
                    .isInstanceOf(OtpException.class)
                    .hasMessageContaining("Too many failed attempts");

            assertThat(otpRecord.getAttemptCount()).isEqualTo(5);
        }
    }

    // =========================================================================
    // Step 3 — resetPassword
    // =========================================================================

    @Nested
    @DisplayName("resetPassword()")
    class ResetPasswordTests {

        @Test
        @DisplayName("valid reset token → updates password, marks OTP as used")
        void resetPassword_validToken_updatesPassword() {
            PasswordResetOtp otpRecord = buildVerifiedOtp("valid-reset-token");

            when(otpRepository.findActiveResetTokenForUpdate(
                    eq("valid-reset-token"), any())).thenReturn(Optional.of(otpRecord));
            when(passwordEncoder.encode("newPassword1")).thenReturn("$2a$10$newhashedpassword");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            authService.resetPassword("valid-reset-token", "newPassword1");

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getPassword()).isEqualTo("$2a$10$newhashedpassword");

            assertThat(otpRecord.getIsUsed()).isTrue();
        }

        @Test
        @DisplayName("used reset token → throws OtpException (repository returns empty)")
        void resetPassword_usedToken_throwsException() {
            when(otpRepository.findActiveResetTokenForUpdate(
                    eq("used-token"), any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.resetPassword("used-token", "newPassword1"))
                    .isInstanceOf(OtpException.class)
                    .hasMessageContaining("Invalid or expired reset token");

            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("expired reset token → throws OtpException (repository returns empty)")
        void resetPassword_expiredToken_throwsException() {
            when(otpRepository.findActiveResetTokenForUpdate(
                    eq("expired-token"), any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.resetPassword("expired-token", "newPassword1"))
                    .isInstanceOf(OtpException.class)
                    .hasMessageContaining("Invalid or expired reset token");
        }

        @Test
        @DisplayName("unknown reset token → throws OtpException")
        void resetPassword_unknownToken_throwsException() {
            when(otpRepository.findActiveResetTokenForUpdate(
                    eq("unknown-token"), any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.resetPassword("unknown-token", "newPassword1"))
                    .isInstanceOf(OtpException.class)
                    .hasMessageContaining("Invalid or expired reset token");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private PasswordResetOtp buildActiveOtp(int attemptCount) {
        return PasswordResetOtp.builder()
                .id(1L)
                .user(testUser)
                .otpHash("$2a$10$hashedotp")
                .isUsed(false)
                .attemptCount(attemptCount)
                .expiresAt(ZonedDateTime.now().plusMinutes(9))
                .build();
    }

    private PasswordResetOtp buildVerifiedOtp(String resetToken) {
        return PasswordResetOtp.builder()
                .id(1L)
                .user(testUser)
                .otpHash("$2a$10$hashedotp")
                .resetToken(resetToken)
                .isUsed(false)
                .attemptCount(1)
                .expiresAt(ZonedDateTime.now().plusMinutes(9))
                .resetTokenExpiresAt(ZonedDateTime.now().plusMinutes(9))
                .build();
    }
}
