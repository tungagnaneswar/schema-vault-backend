package com.schemavault.app.controller;

import com.schemavault.app.dto.AuthRequest;
import com.schemavault.app.dto.AuthResponse;
import com.schemavault.app.dto.ForgotPasswordRequest;
import com.schemavault.app.dto.RegisterRequest;
import com.schemavault.app.dto.ResendOtpRequest;
import com.schemavault.app.dto.ResetPasswordRequest;
import com.schemavault.app.dto.TokenRefreshRequest;
import com.schemavault.app.dto.VerifyOtpRequest;
import com.schemavault.app.service.AuthService;
import com.schemavault.app.util.ClientIpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user authentication and OTP operations")
public class AuthController {

    private final AuthService authService;

    @Value("${app.cookie.same-site:Lax}")
    private String cookieSameSite;

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @Operation(summary = "Login")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content)
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest authRequest,
            HttpServletResponse response) {
        requireAnonymous();
        AuthResponse authResponse = authService.login(authRequest);
        setRefreshTokenCookie(response, authResponse.getRefreshToken());
        return ResponseEntity.ok(authResponse);
    }

    @Operation(summary = "Register")
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest registerRequest,
            HttpServletRequest servletRequest) {
        requireAnonymous();
        String clientIp = ClientIpUtil.getClientIp(servletRequest);
        authService.register(registerRequest, clientIp);
        return ResponseEntity
                .ok(Map.of("message", "Registration initiated. Please check your email for the verification OTP."));
    }

    @Operation(summary = "Resend Verification OTP")
    @PostMapping("/resend-verification-otp")
    public ResponseEntity<Map<String, String>> resendVerificationOtp(@Valid @RequestBody ResendOtpRequest request,
            HttpServletRequest servletRequest) {
        requireAnonymous();
        String clientIp = ClientIpUtil.getClientIp(servletRequest);
        authService.resendRegistrationOtp(request.getEmail(), clientIp);
        return ResponseEntity.ok(Map.of("message",
                "If an unverified account with that email exists, a verification OTP has been sent."));
    }

    @Operation(summary = "Verify Registration")
    @PostMapping("/verify-registration")
    public ResponseEntity<AuthResponse> verifyRegistration(@Valid @RequestBody VerifyOtpRequest verifyRequest,
            HttpServletRequest servletRequest, HttpServletResponse response) {
        requireAnonymous();
        String clientIp = ClientIpUtil.getClientIp(servletRequest);
        AuthResponse authResponse = authService.verifyRegistration(verifyRequest.getEmail(), verifyRequest.getOtp(),
                clientIp);
        setRefreshTokenCookie(response, authResponse.getRefreshToken());
        return ResponseEntity.ok(authResponse);
    }

    @Operation(summary = "Refresh token")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@RequestBody(required = false) TokenRefreshRequest request,
            HttpServletRequest servletRequest, HttpServletResponse response) {
        String token = extractRefreshToken(request, servletRequest);
        if (token == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refresh token is missing");
        }
        AuthResponse authResponse = authService.refreshToken(token);
        setRefreshTokenCookie(response, authResponse.getRefreshToken());
        return ResponseEntity.ok(authResponse);
    }

    @Operation(summary = "Logout")
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestBody(required = false) TokenRefreshRequest request,
            HttpServletRequest servletRequest, HttpServletResponse response) {
        String token = extractRefreshToken(request, servletRequest);
        if (token != null) {
            authService.logout(token);
        }
        clearTokenCookies(response);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully."));
    }

    @Operation(summary = "Forgot Password — Step 1: Request OTP")
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest servletRequest) {
        String clientIp = ClientIpUtil.getClientIp(servletRequest);
        authService.forgotPassword(request.getEmail(), clientIp);
        return ResponseEntity.ok(Map.of("message", "If an account with that email exists, an OTP has been sent."));
    }

    @Operation(summary = "Forgot Password — Step 2: Verify OTP")
    @PostMapping("/verify-otp")
    public ResponseEntity<Map<String, String>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request,
            HttpServletRequest servletRequest) {
        String clientIp = ClientIpUtil.getClientIp(servletRequest);
        authService.verifyOtp(request.getEmail(), request.getOtp(), clientIp);
        return ResponseEntity.ok(Map.of("message", "OTP verified successfully."));
    }

    @Operation(summary = "Forgot Password — Step 3: Reset Password")
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest servletRequest) {
        String clientIp = ClientIpUtil.getClientIp(servletRequest);
        authService.resetPassword(request.getEmail(), request.getOtp(), request.getNewPassword(), clientIp);
        return ResponseEntity.ok(Map.of("message", "Password has been reset successfully."));
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        if (refreshToken != null) {
            ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
                    .httpOnly(true)
                    .path("/")
                    .maxAge(30 * 24 * 60 * 60)
                    .sameSite(cookieSameSite)
                    .secure(cookieSecure)
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        }
    }

    private void clearTokenCookies(HttpServletResponse response) {
        ResponseCookie accessCookie = ResponseCookie.from("accessToken", "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .sameSite(cookieSameSite)
                .secure(cookieSecure)
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .sameSite(cookieSameSite)
                .secure(cookieSecure)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    private String extractRefreshToken(TokenRefreshRequest request, HttpServletRequest servletRequest) {
        if (request != null && request.getRefreshToken() != null) {
            return request.getRefreshToken();
        }
        if (servletRequest != null && servletRequest.getCookies() != null) {
            for (Cookie cookie : servletRequest.getCookies()) {
                if ("refreshToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private void requireAnonymous() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is already authenticated");
        }
    }
}
