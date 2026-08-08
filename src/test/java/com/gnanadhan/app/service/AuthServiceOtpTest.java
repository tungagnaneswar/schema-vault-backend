package com.schemavault.app.service;

import com.schemavault.app.dto.RegisterRequest;
import com.schemavault.app.entity.PasswordResetOtp;
import com.schemavault.app.entity.RegistrationOtp;
import com.schemavault.app.entity.Role;
import com.schemavault.app.entity.User;
import com.schemavault.app.exception.OtpException;
import com.schemavault.app.repository.PasswordResetOtpRepository;
import com.schemavault.app.repository.RegistrationOtpRepository;
import com.schemavault.app.repository.RoleRepository;
import com.schemavault.app.repository.UserRepository;
import com.schemavault.app.security.JwtTokenProvider;
import com.schemavault.app.security.ratelimit.RateLimitResult;
import com.schemavault.app.security.ratelimit.RateLimitType;
import com.schemavault.app.security.ratelimit.RateLimiterService;
import com.schemavault.app.service.AuthService;
import com.schemavault.app.service.EmailService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the OTP-based forgot-password and registration flow in
 * {@link AuthService}.
 *
 * <p>
 * All dependencies are mocked with Mockito — no Spring context is loaded.
 */
@DisplayName("AuthService — OTP Password Reset and Registration Flow")
class AuthServiceOtpTest {

        // -------------------------------------------------------------------------
        // Mocks
        // -------------------------------------------------------------------------

        @Mock
        private AuthenticationManager authenticationManager;
        @Mock
        private JwtTokenProvider tokenProvider;
        @Mock
        private UserRepository userRepository;
        @Mock
        private RoleRepository roleRepository;
        @Mock
        private PasswordEncoder passwordEncoder;
        @Mock
        private PasswordResetOtpRepository otpRepository;
        @Mock
        private RegistrationOtpRepository registrationOtpRepository;
        @Mock
        private EmailService emailService;
        @Mock
        private RateLimiterService rateLimiterService;

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

                // Default: all rate limit checks return allowed
                when(rateLimiterService.check(any(RateLimitType.class), anyString()))
                                .thenReturn(RateLimitResult.allowed());
                when(rateLimiterService.checkAndRecord(any(RateLimitType.class), anyString()))
                                .thenReturn(RateLimitResult.allowed());

                testRole = new Role();
                testRole.setName("USER");

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
                @DisplayName("within 60s of last request → rate-limited, silent no-op")
                void forgotPassword_rateLimited_silentNoOp() {
                        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
                        when(rateLimiterService.check(RateLimitType.OTP_DISPATCH_COOLDOWN, "user@example.com"))
                                        .thenReturn(RateLimitResult.blocked(45));

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
                @DisplayName("valid OTP → verifies successfully, saves updated attempt count")
                void verifyOtp_validOtp_verifiesSuccessfully() {
                        PasswordResetOtp otpRecord = buildActiveOtp(0);

                        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
                        when(otpRepository.findActiveOtpForUpdateByUser(
                                        eq(testUser), any())).thenReturn(Optional.of(otpRecord));
                        when(passwordEncoder.matches("123456", "$2a$10$hashedotp")).thenReturn(true);
                        when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                        authService.verifyOtp("user@example.com", "123456");

                        verify(otpRepository).save(otpRecord);
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
                @DisplayName("valid email and OTP → updates password, marks OTP as used")
                void resetPassword_validOtp_updatesPassword() {
                        PasswordResetOtp otpRecord = buildActiveOtp(0);

                        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
                        when(otpRepository.findActiveOtpForUpdateByUser(
                                        eq(testUser), any())).thenReturn(Optional.of(otpRecord));
                        when(passwordEncoder.matches("123456", "$2a$10$hashedotp")).thenReturn(true);
                        when(passwordEncoder.encode("newPassword1")).thenReturn("$2a$10$newhashedpassword");
                        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
                        when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                        authService.resetPassword("user@example.com", "123456", "newPassword1");

                        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
                        verify(userRepository).save(userCaptor.capture());
                        assertThat(userCaptor.getValue().getPassword()).isEqualTo("$2a$10$newhashedpassword");

                        assertThat(otpRecord.getIsUsed()).isTrue();
                }

                @Test
                @DisplayName("expired or missing OTP → throws OtpException")
                void resetPassword_expiredOtp_throwsException() {
                        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
                        when(otpRepository.findActiveOtpForUpdateByUser(
                                        eq(testUser), any())).thenReturn(Optional.empty());

                        assertThatThrownBy(
                                        () -> authService.resetPassword("user@example.com", "123456", "newPassword1"))
                                        .isInstanceOf(OtpException.class)
                                        .hasMessageContaining("Invalid or expired OTP");

                        verifyNoInteractions(passwordEncoder);
                }
        }

        // =========================================================================
        // Registration & Resend Registration OTP Tests
        // =========================================================================

        @Nested
        @DisplayName("resendRegistrationOtp() & register() unverified flow")
        class RegistrationAndResendOtpTests {

                @Test
                @DisplayName("re-register unverified user → updates password, deletes old OTP, sends new OTP")
                void register_unverifiedUser_updatesPasswordAndSendsOtp() {
                        User inactiveUser = User.builder()
                                        .id(2L)
                                        .email("unverified@example.com")
                                        .password("$2a$10$oldpass")
                                        .role(testRole)
                                        .isActive(false)
                                        .build();

                        RegisterRequest request = new RegisterRequest();
                        request.setEmail("unverified@example.com");
                        request.setPassword("newSecurePassword123");

                        when(userRepository.findByEmail("unverified@example.com"))
                                        .thenReturn(Optional.of(inactiveUser));
                        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashedotp");
                        when(passwordEncoder.encode("newSecurePassword123")).thenReturn("$2a$10$newpass");

                        authService.register(request);

                        verify(userRepository).save(inactiveUser);
                        assertThat(inactiveUser.getPassword()).isEqualTo("$2a$10$newpass");
                        verify(registrationOtpRepository).deleteByUser(inactiveUser);
                        verify(registrationOtpRepository).save(any(RegistrationOtp.class));
                        verify(emailService).sendRegistrationOtpEmail(eq("unverified@example.com"), anyString());
                }

                @Test
                @DisplayName("resendRegistrationOtp valid unverified user → deletes old OTPs, saves new OTP, sends email")
                void resendRegistrationOtp_unverifiedUser_sendsOtp() {
                        User inactiveUser = User.builder()
                                        .id(2L)
                                        .email("unverified@example.com")
                                        .password("$2a$10$pass")
                                        .role(testRole)
                                        .isActive(false)
                                        .build();

                        when(userRepository.findByEmail("unverified@example.com"))
                                        .thenReturn(Optional.of(inactiveUser));
                        when(registrationOtpRepository.findTopByUserOrderByCreatedAtDesc(inactiveUser))
                                        .thenReturn(Optional.empty());
                        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashedotp");

                        authService.resendRegistrationOtp("unverified@example.com");

                        verify(registrationOtpRepository).deleteByUser(inactiveUser);
                        verify(registrationOtpRepository).save(any(RegistrationOtp.class));
                        verify(emailService).sendRegistrationOtpEmail(eq("unverified@example.com"), anyString());
                }

                @Test
                @DisplayName("resendRegistrationOtp rate limited (<60s) → throws OtpException")
                void resendRegistrationOtp_rateLimited_throwsException() {
                        User inactiveUser = User.builder()
                                        .id(2L)
                                        .email("unverified@example.com")
                                        .password("$2a$10$pass")
                                        .role(testRole)
                                        .isActive(false)
                                        .build();

                        when(userRepository.findByEmail("unverified@example.com"))
                                        .thenReturn(Optional.of(inactiveUser));
                        when(rateLimiterService.check(RateLimitType.OTP_DISPATCH_COOLDOWN, "unverified@example.com"))
                                        .thenReturn(RateLimitResult.blocked(45));

                        assertThatThrownBy(() -> authService.resendRegistrationOtp("unverified@example.com"))
                                        .isInstanceOf(OtpException.class)
                                        .hasMessageContaining("Please wait");
                }

                @Test
                @DisplayName("resendRegistrationOtp already verified user → throws IllegalArgumentException")
                void resendRegistrationOtp_alreadyVerified_throwsException() {
                        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser)); // isActive
                                                                                                                // =
                                                                                                                // true

                        assertThatThrownBy(() -> authService.resendRegistrationOtp("user@example.com"))
                                        .isInstanceOf(IllegalArgumentException.class)
                                        .hasMessageContaining("Account is already verified");
                }

                @Test
                @DisplayName("resendRegistrationOtp unknown email → silent no-op (anti-enumeration)")
                void resendRegistrationOtp_unknownEmail_silentNoOp() {
                        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

                        authService.resendRegistrationOtp("nobody@example.com");

                        verifyNoInteractions(registrationOtpRepository, emailService);
                }

                @Test
                @DisplayName("login calls check and reset on successful login")
                void login_successful_recordsSuccess() {
                        com.schemavault.app.dto.AuthRequest authRequest = new com.schemavault.app.dto.AuthRequest();
                        authRequest.setEmail("user@example.com");
                        authRequest.setPassword("password123");

                        org.springframework.security.core.userdetails.User springUser = new org.springframework.security.core.userdetails.User(
                                        "user@example.com", "password123", java.util.Collections.emptyList());
                        Authentication auth = new UsernamePasswordAuthenticationToken(springUser, "password123");

                        when(authenticationManager.authenticate(any())).thenReturn(auth);
                        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
                        when(tokenProvider.generateToken(auth)).thenReturn("accessToken");
                        when(tokenProvider.generateRefreshToken(auth)).thenReturn("refreshToken");

                        com.schemavault.app.dto.AuthResponse response = authService.login(authRequest);

                        assertThat(response).isNotNull();
                        verify(rateLimiterService).check(RateLimitType.LOGIN, "user@example.com");
                        verify(rateLimiterService).reset(RateLimitType.LOGIN, "user@example.com");
                }

                @Test
                @DisplayName("login failed authentication calls record")
                void login_failed_recordsFailedAttempt() {
                        com.schemavault.app.dto.AuthRequest authRequest = new com.schemavault.app.dto.AuthRequest();
                        authRequest.setEmail("user@example.com");
                        authRequest.setPassword("wrongpassword");

                        when(authenticationManager.authenticate(any())).thenThrow(
                                        new com.schemavault.app.exception.UnauthorizedException("Bad credentials"));

                        assertThatThrownBy(() -> authService.login(authRequest))
                                        .isInstanceOf(com.schemavault.app.exception.UnauthorizedException.class);

                        verify(rateLimiterService).check(RateLimitType.LOGIN, "user@example.com");
                        verify(rateLimiterService).record(RateLimitType.LOGIN, "user@example.com");
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
}
