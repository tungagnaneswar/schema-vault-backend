package com.gnanadhan.app.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

/**
 * Represents a single OTP record for the forgot-password flow.
 *
 * <p>Security invariants:
 * <ul>
 *   <li>The plain OTP value is <b>never</b> stored here; only its BCrypt hash.</li>
 *   <li>The plain reset token is <b>never</b> logged; only stored as a UUID string.</li>
 *   <li>A record is considered active when {@code isUsed = false} and {@code expiresAt} is in the future.</li>
 *   <li>After 5 failed verification attempts ({@code attemptCount >= 5}), the record is treated as locked.</li>
 * </ul>
 */
@Entity
@Table(name = "password_reset_otps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The user this OTP belongs to. Cascade delete keeps the table clean. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** BCrypt hash of the 6-digit numeric OTP sent to the user's email. */
    @Column(name = "otp_hash", nullable = false)
    private String otpHash;

    /** True once the password reset has been completed — prevents token reuse. */
    @Column(name = "is_used", nullable = false)
    @Builder.Default
    private Boolean isUsed = false;

    /**
     * Counts failed OTP verification attempts.
     * When this reaches {@code MAX_OTP_ATTEMPTS}, the OTP is locked.
     */
    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    /** Timestamp after which the OTP is no longer valid (set at creation time). */
    @Column(name = "expires_at", nullable = false)
    private ZonedDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}
