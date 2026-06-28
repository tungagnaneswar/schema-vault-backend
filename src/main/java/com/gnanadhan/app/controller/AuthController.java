package com.gnanadhan.app.controller;

import com.gnanadhan.app.dto.AuthRequest;
import com.gnanadhan.app.dto.AuthResponse;
import com.gnanadhan.app.dto.ForgotPasswordRequest;
import com.gnanadhan.app.dto.RegisterRequest;
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

    @Operation(summary = "Register", description = "Create a new account. Returns JWT tokens on success.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Registration successful",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Email already in use or validation error", content = @Content)
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        requireAnonymous();
        return ResponseEntity.ok(authService.register(registerRequest));
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
            @Valid @RequestBody ForgotPasswordRequest request) {

        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(Map.of(
                "message", "If an account with that email exists, an OTP has been sent."
        ));
    }

    @Operation(
            summary = "Step 2 — Verify OTP",
            description = """
                    Validates the 6-digit OTP received by email and returns a short-lived **reset token**.

                    **Security notes:**
                    - OTP is locked after **5 failed attempts** — request a new one via `/forgot-password`.
                    - Reset token is valid for **10 minutes** from the time of successful verification.
                    - The reset token is a UUID and is stored hashed in the database.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "OTP verified — reset token returned",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "resetToken": "f3a2b1c0-d4e5-4f6a-b7c8-d9e0f1a2b3c4"
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
                                            { "message": "Too many failed attempts. Please request a new OTP.", "status": 400 }
                                            """)
                            }
                    )
            )
    })
    @PostMapping("/verify-otp")
    public ResponseEntity<Map<String, String>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request) {

        String resetToken = authService.verifyOtp(request.getEmail(), request.getOtp());
        return ResponseEntity.ok(Map.of("resetToken", resetToken));
    }

    @Operation(
            summary = "Step 3 — Reset password",
            description = """
                    Resets the user's password using the **reset token** obtained from Step 2.

                    **Security notes:**
                    - The reset token expires after **10 minutes**.
                    - Each reset token can only be used **once** — reuse returns HTTP 400.
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
                    description = "Invalid / expired / already-used reset token, or password too short",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = {
                                    @ExampleObject(name = "Invalid token", value = """
                                            { "message": "Invalid or expired reset token", "status": 400 }
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
            @Valid @RequestBody ResetPasswordRequest request) {

        authService.resetPassword(request.getResetToken(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password has been reset successfully."));
    }

    private void requireAnonymous() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is already authenticated");
        }
    }
}
