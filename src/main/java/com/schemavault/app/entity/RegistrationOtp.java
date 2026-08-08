package com.schemavault.app.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

/**
 * Represents a single OTP record for the registration flow.
 */
@Entity
@Table(name = "registration_otps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistrationOtp {

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

    /** True once the registration has been completed — prevents OTP reuse. */
    @Column(name = "is_used", nullable = false)
    @Builder.Default
    private Boolean isUsed = false;

    /**
     * Counts failed OTP verification attempts.
     * When this reaches the limit, the OTP is locked.
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
