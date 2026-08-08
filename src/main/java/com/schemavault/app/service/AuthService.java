package com.schemavault.app.service;

import com.schemavault.app.dto.AuthRequest;
import com.schemavault.app.dto.AuthResponse;
import com.schemavault.app.dto.RegisterRequest;
import com.schemavault.app.entity.PasswordResetOtp;
import com.schemavault.app.entity.RegistrationOtp;
import com.schemavault.app.entity.Role;
import com.schemavault.app.entity.User;
import com.schemavault.app.exception.OtpException;
import com.schemavault.app.exception.UnauthorizedException;
import com.schemavault.app.repository.PasswordResetOtpRepository;
import com.schemavault.app.repository.RegistrationOtpRepository;
import com.schemavault.app.repository.RoleRepository;
import com.schemavault.app.repository.UserRepository;
import com.schemavault.app.security.JwtTokenProvider;
import com.schemavault.app.security.ratelimit.RateLimitResult;
import com.schemavault.app.security.ratelimit.RateLimitType;
import com.schemavault.app.security.ratelimit.RateLimiterService;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final int OTP_TTL_MINUTES = 10;
    private static final int MAX_OTP_ATTEMPTS = 5;

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetOtpRepository otpRepository;
    private final RegistrationOtpRepository registrationOtpRepository;
    private final EmailService emailService;
    private final RateLimiterService rateLimiterService;

    private final SecureRandom secureRandom = new SecureRandom();

    public AuthResponse login(AuthRequest authRequest) {
        checkLoginLockout(authRequest.getEmail());

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(authRequest.getEmail(), authRequest.getPassword()));
        } catch (Exception ex) {
            rateLimiterService.record(RateLimitType.LOGIN, authRequest.getEmail());
            throw ex;
        }

        rateLimiterService.reset(RateLimitType.LOGIN, authRequest.getEmail());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String accessToken = tokenProvider.generateToken(authentication);
        String refreshToken = tokenProvider.generateRefreshToken(authentication);

        User user = userRepository.findByEmail(authRequest.getEmail())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    @Transactional
    public void register(RegisterRequest request) {
        register(request, "UNKNOWN");
    }

    @Transactional(noRollbackFor = OtpException.class)
    public void register(RegisterRequest request, String clientIp) {
        checkOtpIpThrottle(clientIp);
        checkOtpVerificationLockout(request.getEmail(), clientIp);

        Optional<User> existingUserOpt = userRepository.findByEmail(request.getEmail());
        User user;

        if (existingUserOpt.isPresent()) {
            user = existingUserOpt.get();
            if (user.getIsActive()) {
                throw new IllegalArgumentException("Email already in use");
            }
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            userRepository.save(user);
        } else {
            Role userRole = roleRepository.findByName("USER")
                    .orElseThrow(() -> new RuntimeException("Default role not found"));

            user = User.builder()
                    .email(request.getEmail())
                    .password(passwordEncoder.encode(request.getPassword()))
                    .role(userRole)
                    .isActive(false)
                    .build();

            userRepository.save(user);
        }

        checkOtpDispatchRateLimit(request.getEmail(), clientIp);
        registrationOtpRepository.deleteByUser(user);

        String rawOtp = generateSixDigitOtp();

        RegistrationOtp otpRecord = RegistrationOtp.builder()
                .user(user)
                .otpHash(passwordEncoder.encode(rawOtp))
                .expiresAt(ZonedDateTime.now().plusMinutes(OTP_TTL_MINUTES))
                .build();

        registrationOtpRepository.save(otpRecord);
        recordOtpDispatch(request.getEmail());
        emailService.sendRegistrationOtpEmail(request.getEmail(), rawOtp);

        log.info("[IP: {}] Registration OTP generated and email dispatched for user id={}", clientIp, user.getId());
    }

    @Transactional
    public void resendRegistrationOtp(String email) {
        resendRegistrationOtp(email, "UNKNOWN");
    }

    @Transactional(noRollbackFor = OtpException.class)
    public void resendRegistrationOtp(String email, String clientIp) {
        checkOtpIpThrottle(clientIp);
        checkOtpVerificationLockout(email, clientIp);

        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            log.debug("[IP: {}] Resend registration OTP requested for unknown email (suppressed)", clientIp);
            return;
        }

        User user = userOpt.get();
        if (user.getIsActive()) {
            throw new IllegalArgumentException("Account is already verified. Please sign in.");
        }

        checkOtpDispatchRateLimit(email, clientIp);
        registrationOtpRepository.deleteByUser(user);

        String rawOtp = generateSixDigitOtp();

        RegistrationOtp otpRecord = RegistrationOtp.builder()
                .user(user)
                .otpHash(passwordEncoder.encode(rawOtp))
                .expiresAt(ZonedDateTime.now().plusMinutes(OTP_TTL_MINUTES))
                .build();

        registrationOtpRepository.save(otpRecord);
        recordOtpDispatch(email);
        emailService.sendRegistrationOtpEmail(email, rawOtp);

        log.info("[IP: {}] Resent registration OTP for user id={}", clientIp, user.getId());
    }

    @Transactional(noRollbackFor = OtpException.class)
    public AuthResponse verifyRegistration(String email, String rawOtp) {
        return verifyRegistration(email, rawOtp, "UNKNOWN");
    }

    @Transactional(noRollbackFor = OtpException.class)
    public AuthResponse verifyRegistration(String email, String rawOtp, String clientIp) {
        checkOtpIpThrottle(clientIp);
        checkOtpVerificationLockout(email, clientIp);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new OtpException("Invalid or expired OTP"));

        if (user.getIsActive()) {
            throw new IllegalArgumentException("User is already verified");
        }

        RegistrationOtp otpRecord = registrationOtpRepository
                .findActiveOtpForUpdateByUser(user, ZonedDateTime.now())
                .orElseThrow(() -> new OtpException("Invalid or expired OTP"));

        if (otpRecord.getAttemptCount() >= MAX_OTP_ATTEMPTS) {
            rateLimiterService.record(RateLimitType.OTP_VERIFICATION_LOCKOUT, email);
            log.warn("[IP: {}] OTP verification lockout triggered for email: {}", clientIp, email);
            throw new OtpException("Too many failed attempts. Please request a new OTP using Resend OTP.");
        }

        otpRecord.setAttemptCount(otpRecord.getAttemptCount() + 1);
        registrationOtpRepository.save(otpRecord);

        if (!passwordEncoder.matches(rawOtp, otpRecord.getOtpHash())) {
            int remaining = MAX_OTP_ATTEMPTS - otpRecord.getAttemptCount();
            if (remaining <= 0) {
                rateLimiterService.record(RateLimitType.OTP_VERIFICATION_LOCKOUT, email);
                log.warn("[IP: {}] OTP verification lockout triggered for email: {}", clientIp, email);
                throw new OtpException("Too many failed attempts. Please request a new OTP using Resend OTP.");
            }
            log.warn("[IP: {}] Invalid registration OTP entered for email {}. {} attempt(s) remaining.", clientIp,
                    email, remaining);
            throw new OtpException("Invalid OTP. " + remaining + " attempt(s) remaining.");
        }

        otpRecord.setIsUsed(true);
        registrationOtpRepository.save(otpRecord);

        user.setIsActive(true);
        userRepository.save(user);

        rateLimiterService.reset(RateLimitType.OTP_VERIFICATION_LOCKOUT, email);
        log.info("[IP: {}] Registration verified successfully for user id={}", clientIp, user.getId());

        String accessToken = tokenProvider.generateTokenFromUsername(user.getEmail());
        String refreshToken = tokenProvider.generateRefreshTokenFromUsername(user.getEmail());

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    public AuthResponse refreshToken(String refreshToken) {
        if (tokenProvider.validateToken(refreshToken)) {
            String username = tokenProvider.getUsernameFromToken(refreshToken);

            User user = userRepository.findByEmail(username)
                    .orElseThrow(() -> new UnauthorizedException("User not found"));

            String newAccessToken = tokenProvider.generateTokenFromUsername(username);

            return buildAuthResponse(user, newAccessToken, refreshToken);
        }
        throw new UnauthorizedException("Invalid refresh token");
    }

    public void logout(String refreshToken) {
        if (refreshToken != null && tokenProvider.validateToken(refreshToken)) {
            String username = tokenProvider.getUsernameFromToken(refreshToken);
            log.info("Logout processed for user={}", username);
        }
    }

    @Transactional
    public void forgotPassword(String email) {
        forgotPassword(email, "UNKNOWN");
    }

    @Transactional(noRollbackFor = OtpException.class)
    public void forgotPassword(String email, String clientIp) {
        checkOtpIpThrottle(clientIp);
        checkOtpVerificationLockout(email, clientIp);

        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            log.debug("[IP: {}] Forgot-password requested for unknown email (suppressed)", clientIp);
            return;
        }

        User user = userOpt.get();

        RateLimitResult cooldownResult = rateLimiterService.check(RateLimitType.OTP_DISPATCH_COOLDOWN, email);
        RateLimitResult hourlyResult = rateLimiterService.check(RateLimitType.OTP_DISPATCH_HOURLY, email);
        if (!cooldownResult.isAllowed() || !hourlyResult.isAllowed()) {
            log.info("[IP: {}] Forgot-password rate-limit triggered for email: {}", clientIp, email);
            return;
        }

        otpRepository.deleteByUser(user);

        String rawOtp = generateSixDigitOtp();

        PasswordResetOtp otpRecord = PasswordResetOtp.builder()
                .user(user)
                .otpHash(passwordEncoder.encode(rawOtp))
                .expiresAt(ZonedDateTime.now().plusMinutes(OTP_TTL_MINUTES))
                .build();

        otpRepository.save(otpRecord);
        recordOtpDispatch(email);
        emailService.sendOtpEmail(email, rawOtp);

        log.info("[IP: {}] OTP generated and email dispatched for user id={}", clientIp, user.getId());
    }

    @Transactional(noRollbackFor = OtpException.class)
    public void verifyOtp(String email, String rawOtp) {
        verifyOtp(email, rawOtp, "UNKNOWN");
    }

    @Transactional(noRollbackFor = OtpException.class)
    public void verifyOtp(String email, String rawOtp, String clientIp) {
        checkOtpIpThrottle(clientIp);
        checkOtpVerificationLockout(email, clientIp);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new OtpException("Invalid or expired OTP"));

        PasswordResetOtp otpRecord = otpRepository
                .findActiveOtpForUpdateByUser(user, ZonedDateTime.now())
                .orElseThrow(() -> new OtpException("Invalid or expired OTP"));

        if (otpRecord.getAttemptCount() >= MAX_OTP_ATTEMPTS) {
            rateLimiterService.record(RateLimitType.OTP_VERIFICATION_LOCKOUT, email);
            log.warn("[IP: {}] OTP verification lockout triggered for email: {}", clientIp, email);
            throw new OtpException("Too many failed attempts. Please wait 3 minutes before trying again.");
        }

        otpRecord.setAttemptCount(otpRecord.getAttemptCount() + 1);
        otpRepository.save(otpRecord);

        if (!passwordEncoder.matches(rawOtp, otpRecord.getOtpHash())) {
            int remaining = MAX_OTP_ATTEMPTS - otpRecord.getAttemptCount();
            if (remaining <= 0) {
                rateLimiterService.record(RateLimitType.OTP_VERIFICATION_LOCKOUT, email);
                log.warn("[IP: {}] OTP verification lockout triggered for email: {}", clientIp, email);
                throw new OtpException("Too many failed attempts. Please wait 3 minutes before trying again.");
            }
            log.warn("[IP: {}] Invalid OTP entered for email {}. {} attempt(s) remaining.", clientIp, email, remaining);
            throw new OtpException("Invalid OTP. " + remaining + " attempt(s) remaining.");
        }

        rateLimiterService.reset(RateLimitType.OTP_VERIFICATION_LOCKOUT, email);
        log.info("[IP: {}] OTP verified successfully for user id={}", clientIp, user.getId());
    }

    @Transactional(noRollbackFor = OtpException.class)
    public void resetPassword(String email, String rawOtp, String newPassword) {
        resetPassword(email, rawOtp, newPassword, "UNKNOWN");
    }

    @Transactional(noRollbackFor = OtpException.class)
    public void resetPassword(String email, String rawOtp, String newPassword, String clientIp) {
        checkOtpIpThrottle(clientIp);
        checkOtpVerificationLockout(email, clientIp);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new OtpException("Invalid or expired OTP"));

        PasswordResetOtp otpRecord = otpRepository
                .findActiveOtpForUpdateByUser(user, ZonedDateTime.now())
                .orElseThrow(() -> new OtpException("Invalid or expired OTP"));

        if (otpRecord.getAttemptCount() >= MAX_OTP_ATTEMPTS) {
            rateLimiterService.record(RateLimitType.OTP_VERIFICATION_LOCKOUT, email);
            log.warn("[IP: {}] OTP verification lockout triggered for email: {}", clientIp, email);
            throw new OtpException("Too many failed attempts. Please wait 3 minutes before trying again.");
        }

        otpRecord.setAttemptCount(otpRecord.getAttemptCount() + 1);
        otpRepository.save(otpRecord);

        if (!passwordEncoder.matches(rawOtp, otpRecord.getOtpHash())) {
            int remaining = MAX_OTP_ATTEMPTS - otpRecord.getAttemptCount();
            if (remaining <= 0) {
                rateLimiterService.record(RateLimitType.OTP_VERIFICATION_LOCKOUT, email);
                log.warn("[IP: {}] OTP verification lockout triggered for email: {}", clientIp, email);
                throw new OtpException("Too many failed attempts. Please wait 3 minutes before trying again.");
            }
            log.warn("[IP: {}] Invalid OTP entered during password reset for email {}. {} attempt(s) remaining.",
                    clientIp, email, remaining);
            throw new OtpException("Invalid OTP. " + remaining + " attempt(s) remaining.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        otpRecord.setIsUsed(true);
        otpRepository.save(otpRecord);

        rateLimiterService.reset(RateLimitType.OTP_VERIFICATION_LOCKOUT, email);
        log.info("[IP: {}] Password reset successfully for user id={}", clientIp, user.getId());
    }

    // -------------------------------------------------------------------------
    // Rate limit helpers — translate RateLimitResult into business exceptions
    // -------------------------------------------------------------------------

    private void checkLoginLockout(String email) {
        RateLimitResult result = rateLimiterService.check(RateLimitType.LOGIN, email);
        if (!result.isAllowed()) {
            long remainingMinutes = (result.getRemainingSeconds() + 59) / 60;
            throw new UnauthorizedException(
                    "Account temporarily locked due to repeated failed login attempts. Please try again in "
                            + remainingMinutes + " minute(s).");
        }
    }

    private void checkOtpIpThrottle(String clientIp) {
        if ("UNKNOWN".equalsIgnoreCase(clientIp)) {
            return;
        }
        RateLimitResult result = rateLimiterService.checkAndRecord(RateLimitType.OTP_IP_THROTTLE, clientIp);
        if (!result.isAllowed()) {
            log.warn("[IP: {}] Rate limit exceeded on OTP endpoint.", clientIp);
            throw new OtpException("Too many requests from this IP. Please wait a minute before trying again.");
        }
    }

    private void checkOtpVerificationLockout(String email, String clientIp) {
        RateLimitResult result = rateLimiterService.check(RateLimitType.OTP_VERIFICATION_LOCKOUT, email);
        if (!result.isAllowed()) {
            long remainingMinutes = (result.getRemainingSeconds() + 59) / 60;
            log.warn("[IP: {}] Verification attempted for locked email: {}. Remaining lock: {}s",
                    clientIp, email, result.getRemainingSeconds());
            throw new OtpException(
                    "Account temporarily paused due to repeated failed attempts. Please wait "
                            + remainingMinutes + " minute(s).");
        }
    }

    private void checkOtpDispatchRateLimit(String email, String clientIp) {
        RateLimitResult cooldownResult = rateLimiterService.check(RateLimitType.OTP_DISPATCH_COOLDOWN, email);
        if (!cooldownResult.isAllowed()) {
            log.warn("[IP: {}] Dispatch cooldown active for email: {}. Remaining: {}s",
                    clientIp, email, cooldownResult.getRemainingSeconds());
            throw new OtpException(
                    "Please wait " + cooldownResult.getRemainingSeconds()
                            + " seconds before requesting another OTP.");
        }

        RateLimitResult hourlyResult = rateLimiterService.check(RateLimitType.OTP_DISPATCH_HOURLY, email);
        if (!hourlyResult.isAllowed()) {
            log.warn("[IP: {}] Hourly email cap reached for email: {}", clientIp, email);
            throw new OtpException("Maximum OTP request limit reached for this hour. Please try again later.");
        }
    }

    private void recordOtpDispatch(String email) {
        rateLimiterService.record(RateLimitType.OTP_DISPATCH_COOLDOWN, email);
        rateLimiterService.record(RateLimitType.OTP_DISPATCH_HOURLY, email);
    }

    // -------------------------------------------------------------------------
    // Other helpers
    // -------------------------------------------------------------------------

    private AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .email(user.getEmail())
                .role(user.getRole().getName())
                .build();
    }

    private String generateSixDigitOtp() {
        int otp = 100_000 + secureRandom.nextInt(900_000);
        return String.valueOf(otp);
    }
}
