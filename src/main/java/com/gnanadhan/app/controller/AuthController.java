package com.gnanadhan.app.controller;

import com.gnanadhan.app.dto.AuthRequest;
import com.gnanadhan.app.dto.AuthResponse;
import com.gnanadhan.app.dto.ForgotPasswordRequest;
import com.gnanadhan.app.dto.RegisterRequest;
import com.gnanadhan.app.dto.ResendOtpRequest;
import com.gnanadhan.app.dto.ResetPasswordRequest;
import com.gnanadhan.app.dto.TokenRefreshRequest;
import com.gnanadhan.app.dto.VerifyOtpRequest;
import com.gnanadhan.app.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Handles all authentication-related endpoints.
 *
 * <p>All paths under {@code /api/auth/**} are publicly accessible
 * (configured in {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(
        name = "Authentication",
        description = "Endpoints for login, registration, token refresh, and the 3-step forgot-password OTP flow"
)
public class AuthController {

    private final AuthService authService;

    // -------------------------------------------------------------------------
    // Core auth
    // -------------------------------------------------------------------------

    @Operation(summary = "Login", description = "Authenticate with email and password to receive JWT access and refresh tokens.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content)
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest authRequest) {
        requireAnonymous();
        return ResponseEntity.ok(authService.login(authRequest));
    }

    @Operation(summary = "Register", description = "Create a new account and send verification OTP.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OTP sent to email",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
            @ApiResponse(responseCode = "400", description = "Email already in use or validation error", content = @Content)
    })
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest registerRequest, jakarta.servlet.http.HttpServletRequest servletRequest) {
        requireAnonymous();
        String clientIp = com.gnanadhan.app.util.ClientIpUtil.getClientIp(servletRequest);
        authService.register(registerRequest, clientIp);
        return ResponseEntity.ok(Map.of("message", "Registration initiated. Please check your email for the verification OTP."));
    }

    @Operation(summary = "Resend Verification OTP", description = "Resends a registration verification OTP to the given email.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verification OTP sent if account exists and is unverified",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
            @ApiResponse(responseCode = "400", description = "Account already verified or rate limit exceeded", content = @Content)
    })
    @PostMapping("/resend-verification-otp")
    public ResponseEntity<Map<String, String>> resendVerificationOtp(@Valid @RequestBody ResendOtpRequest request, jakarta.servlet.http.HttpServletRequest servletRequest) {
        requireAnonymous();
        String clientIp = com.gnanadhan.app.util.ClientIpUtil.getClientIp(servletRequest);
        authService.resendRegistrationOtp(request.getEmail(), clientIp);
        return ResponseEntity.ok(Map.of("message", "If an unverified account with that email exists, a verification OTP has been sent."));
    }

    @Operation(summary = "Verify Registration", description = "Verify OTP to complete registration. Returns JWT tokens on success.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Registration successful",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid OTP or already verified", content = @Content)
    })
    @PostMapping("/verify-registration")
    public ResponseEntity<AuthResponse> verifyRegistration(@Valid @RequestBody VerifyOtpRequest verifyRequest, jakarta.servlet.http.HttpServletRequest servletRequest) {
        requireAnonymous();
        String clientIp = com.gnanadhan.app.util.ClientIpUtil.getClientIp(servletRequest);
        return ResponseEntity.ok(authService.verifyRegistration(verifyRequest.getEmail(), verifyRequest.getOtp(), clientIp));
    }

    @Operation(summary = "Refresh token", description = "Exchange a valid refresh token for a new access token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token refreshed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token", content = @Content)
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request.getRefreshToken()));
    }

    @Operation(summary = "Logout", description = "Logout user session.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logout successful", content = @Content)
    })
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestBody(required = false) TokenRefreshRequest request) {
        if (request != null && request.getRefreshToken() != null) {
            authService.logout(request.getRefreshToken());
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully."));
    }

    // -------------------------------------------------------------------------
    // Forgot-password flow (3 steps)
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Step 1 — Request OTP",
            description = """
                    Initiates the forgot-password flow by sending a 6-digit OTP to the given email address.

                    **Security notes:**
                    - Always returns HTTP 200 regardless of whether the email exists (prevents enumeration).
                    - Rate-limited to **1 request per 60 seconds** per user (silently ignored when exceeded).
                    - OTP is valid for **10 minutes**.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Request accepted (OTP sent if email exists)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "If an account with that email exists, an OTP has been sent."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Validation error (malformed email)", content = @Content)
    })
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            jakarta.servlet.http.HttpServletRequest servletRequest) {

        String clientIp = com.gnanadhan.app.util.ClientIpUtil.getClientIp(servletRequest);
        authService.forgotPassword(request.getEmail(), clientIp);
        return ResponseEntity.ok(Map.of(
                "message", "If an account with that email exists, an OTP has been sent."
        ));
    }

    @Operation(
            summary = "Step 2 — Verify OTP",
            description = """
                    Validates the 6-digit OTP received by email.

                    **Security notes:**
                    - Enforces IP rate-limiting, dispatch cooldown, and a 3-minute lockout pause after 5 wrong attempts.
                    - OTP is valid for **10 minutes**.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "OTP verified successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "OTP verified successfully."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid OTP / Expired OTP / Too many failed attempts",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = {
                                    @ExampleObject(name = "Invalid OTP", value = """
                                            { "message": "Invalid OTP. 3 attempt(s) remaining.", "status": 400 }
                                            """),
                                    @ExampleObject(name = "Expired", value = """
                                            { "message": "Invalid or expired OTP", "status": 400 }
                                            """),
                                    @ExampleObject(name = "Locked", value = """
                                            { "message": "Too many failed attempts. Please wait 3 minutes.", "status": 400 }
                                            """)
                            }
                    )
            )
    })
    @PostMapping("/verify-otp")
    public ResponseEntity<Map<String, String>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request,
            jakarta.servlet.http.HttpServletRequest servletRequest) {

        String clientIp = com.gnanadhan.app.util.ClientIpUtil.getClientIp(servletRequest);
        authService.verifyOtp(request.getEmail(), request.getOtp(), clientIp);
        return ResponseEntity.ok(Map.of("message", "OTP verified successfully."));
    }

    @Operation(
            summary = "Step 3 — Reset password",
            description = """
                    Resets the user's password using the email, OTP, and new password.

                    **Security notes:**
                    - OTP is valid for **10 minutes**.
                    - Each OTP can only be used **once** — reuse returns HTTP 400.
                    - The new password must be at least **8 characters**.
                    - After a successful reset, the user can immediately log in with the new password.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Password reset successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "Password has been reset successfully."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid / expired OTP, or password too short",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = {
                                    @ExampleObject(name = "Invalid OTP", value = """
                                            { "message": "Invalid or expired OTP", "status": 400 }
                                            """),
                                    @ExampleObject(name = "Short password", value = """
                                            { "newPassword": "Password must be at least 8 characters" }
                                            """)
                            }
                    )
            )
    })
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            jakarta.servlet.http.HttpServletRequest servletRequest) {

        String clientIp = com.gnanadhan.app.util.ClientIpUtil.getClientIp(servletRequest);
        authService.resetPassword(request.getEmail(), request.getOtp(), request.getNewPassword(), clientIp);
        return ResponseEntity.ok(Map.of("message", "Password has been reset successfully."));
    }

    private void requireAnonymous() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is already authenticated");
        }
    }
}
