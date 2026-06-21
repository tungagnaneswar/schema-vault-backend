package com.gnanadhan.app.service;

import com.gnanadhan.app.dto.AuthRequest;
import com.gnanadhan.app.dto.AuthResponse;
import com.gnanadhan.app.dto.RegisterRequest;
import com.gnanadhan.app.entity.PasswordResetOtp;
import com.gnanadhan.app.entity.Role;
import com.gnanadhan.app.entity.User;
import com.gnanadhan.app.exception.OtpException;
import com.gnanadhan.app.exception.UnauthorizedException;
import com.gnanadhan.app.repository.PasswordResetOtpRepository;
import com.gnanadhan.app.repository.RoleRepository;
import com.gnanadhan.app.repository.UserRepository;
import com.gnanadhan.app.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /** Number of minutes before an OTP expires after creation. */
    private static final int OTP_TTL_MINUTES = 10;

    /** Number of minutes before a reset token expires after OTP verification. */
    private static final int RESET_TOKEN_TTL_MINUTES = 10;

    /** Maximum consecutive failed OTP attempts before the record is locked. */
    private static final int MAX_OTP_ATTEMPTS = 5;

    /**
     * Minimum gap (in seconds) between OTP generation requests for the same user.
     * Prevents email flooding without leaking whether the email exists.
     */
    private static final int RATE_LIMIT_SECONDS = 60;

    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetOtpRepository otpRepository;
    private final EmailService emailService;

    private final SecureRandom secureRandom = new SecureRandom();

    // =========================================================================
    // Auth — Login / Register / Refresh
    // =========================================================================

    public AuthResponse login(AuthRequest authRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(authRequest.getEmail(), authRequest.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String accessToken = tokenProvider.generateToken(authentication);
        String refreshToken = tokenProvider.generateRefreshToken(authentication);

        User user = userRepository.findByEmail(authRequest.getEmail())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .email(user.getEmail())
                .role(user.getRole().getName())
                .build();
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }

        Role userRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new RuntimeException("Default role not found"));

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(userRole)
                .isActive(true)
                .build();

        userRepository.save(user);

        AuthRequest loginRequest = new AuthRequest();
        loginRequest.setEmail(request.getEmail());
        loginRequest.setPassword(request.getPassword());
        return login(loginRequest);
    }

    public AuthResponse refreshToken(String refreshToken) {
        if (tokenProvider.validateToken(refreshToken)) {
            String username = tokenProvider.getUsernameFromToken(refreshToken);

            User user = userRepository.findByEmail(username)
                    .orElseThrow(() -> new UnauthorizedException("User not found"));

            String newAccessToken = tokenProvider.generateTokenFromUsername(username);

            return AuthResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(refreshToken)
                    .email(user.getEmail())
                    .role(user.getRole().getName())
                    .build();
        }
        throw new UnauthorizedException("Invalid refresh token");
    }

    // =========================================================================
    // Forgot Password — Step 1: Request OTP
    // =========================================================================

    /**
     * Initiates the forgot-password flow by generating and emailing a 6-digit OTP.
     *
     * <p>Security guarantees:
     * <ul>
     *   <li>Always returns successfully — callers cannot distinguish between
     *       an existing email and a non-existent one (no enumeration).</li>
     *   <li>Rate-limited: if an OTP was already created within the last
     *       {@value RATE_LIMIT_SECONDS} seconds, this call is a silent no-op.</li>
     *   <li>The plain OTP value is never written to any log.</li>
     *   <li>All previous OTP records for the user are purged before creating a new one.</li>
     * </ul>
     *
     * @param email the address to send the OTP to
     */
    @Transactional
    public void forgotPassword(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            // Silent no-op — do not leak whether the email exists.
            log.debug("Forgot-password requested for unknown email (suppressed)");
            return;
        }

        User user = userOpt.get();

        // Rate-limit: reject if the most recent OTP was created less than RATE_LIMIT_SECONDS ago.
        if (isRateLimited(user)) {
            log.info("Forgot-password rate-limit triggered for user id={}", user.getId());
            return; // Silent no-op to avoid information leakage
        }

        // Clean up all previous OTP records for this user.
        otpRepository.deleteByUser(user);

        // Generate a cryptographically secure 6-digit OTP.
        String rawOtp = generateSixDigitOtp();

        PasswordResetOtp otpRecord = PasswordResetOtp.builder()
                .user(user)
                .otpHash(passwordEncoder.encode(rawOtp)) // store only the hash
                .expiresAt(ZonedDateTime.now().plusMinutes(OTP_TTL_MINUTES))
                .build();

        otpRepository.save(otpRecord);

        // Send asynchronously — failure is handled inside EmailService without propagating.
        emailService.sendOtpEmail(email, rawOtp);

        log.info("OTP generated and email dispatched for user id={}", user.getId());
        // rawOtp is intentionally NOT logged here.
    }

    // =========================================================================
    // Forgot Password — Step 2: Verify OTP
    // =========================================================================

    /**
     * Verifies the provided OTP for the given email and issues a short-lived reset token.
     *
     * <p>Attempt count is incremented <em>before</em> the hash comparison so that
     * the increment is always persisted, even if the comparison fails.
     *
     * @param email  the user's email address
     * @param rawOtp the plain OTP entered by the user
     * @return a UUID reset token valid for {@value RESET_TOKEN_TTL_MINUTES} minutes
     * @throws OtpException if the OTP is invalid, expired, or the account is locked
     */
    @Transactional
    public String verifyOtp(String email, String rawOtp) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new OtpException("Invalid or expired OTP"));

        PasswordResetOtp otpRecord = otpRepository
                .findActiveOtpForUpdateByUser(user, ZonedDateTime.now())
                .orElseThrow(() -> new OtpException("Invalid or expired OTP"));

        // Check brute-force lock before attempting password comparison.
        if (otpRecord.getAttemptCount() >= MAX_OTP_ATTEMPTS) {
            throw new OtpException("Too many failed attempts. Please request a new OTP.");
        }

        // Increment attempt count first — ensures it's saved even on failure.
        otpRecord.setAttemptCount(otpRecord.getAttemptCount() + 1);
        otpRepository.save(otpRecord);

        if (!passwordEncoder.matches(rawOtp, otpRecord.getOtpHash())) {
            int remaining = MAX_OTP_ATTEMPTS - otpRecord.getAttemptCount();
            if (remaining <= 0) {
                throw new OtpException("Too many failed attempts. Please request a new OTP.");
            }
            throw new OtpException("Invalid OTP. " + remaining + " attempt(s) remaining.");
        }

        // OTP is valid — generate a reset token with its own independent TTL.
        String resetToken = UUID.randomUUID().toString();

        otpRecord.setResetToken(resetToken);
        otpRecord.setResetTokenExpiresAt(ZonedDateTime.now().plusMinutes(RESET_TOKEN_TTL_MINUTES));
        otpRepository.save(otpRecord);

        log.info("OTP verified successfully for user id={}", user.getId());
        // resetToken is intentionally NOT logged here.

        return resetToken;
    }

    // =========================================================================
    // Forgot Password — Step 3: Reset Password
    // =========================================================================

    /**
     * Resets the user's password using a previously issued reset token.
     *
     * <p>The reset token's expiry ({@code reset_token_expires_at}) is validated
     * independently from the OTP expiry — intentionally separate TTLs.
     * Once used, the OTP record is marked as {@code isUsed = true} to prevent reuse.
     *
     * @param resetToken  the UUID token from the verify-OTP step
     * @param newPassword the new plain-text password (will be BCrypt-encoded)
     * @throws OtpException if the token is invalid, expired, or already used
     */
    @Transactional
    public void resetPassword(String resetToken, String newPassword) {
        PasswordResetOtp otpRecord = otpRepository
                .findActiveResetTokenForUpdate(resetToken, ZonedDateTime.now())
                .orElseThrow(() -> new OtpException("Invalid or expired reset token"));

        User user = otpRecord.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Mark as used to prevent any further use of this token.
        otpRecord.setIsUsed(true);
        otpRepository.save(otpRecord);

        log.info("Password reset successfully for user id={}", user.getId());
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Returns {@code true} if an OTP was created for this user within the last
     * {@value RATE_LIMIT_SECONDS} seconds.
     */
    private boolean isRateLimited(User user) {
        return otpRepository.findTopByUserOrderByCreatedAtDesc(user)
                .map(last -> last.getCreatedAt() != null
                        && last.getCreatedAt().isAfter(
                        ZonedDateTime.now().minusSeconds(RATE_LIMIT_SECONDS)))
                .orElse(false);
    }

    /**
     * Generates a cryptographically secure 6-digit numeric OTP.
     * Uses {@link SecureRandom} to ensure unpredictability.
     */
    private String generateSixDigitOtp() {
        int otp = 100_000 + secureRandom.nextInt(900_000);
        return String.valueOf(otp);
    }
}
