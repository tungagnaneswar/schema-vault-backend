package com.gnanadhan.app.repository;

import com.gnanadhan.app.entity.PasswordResetOtp;
import com.gnanadhan.app.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import jakarta.persistence.QueryHint;
import java.time.ZonedDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {

    /**
     * Retrieves the most-recently created OTP for a user, regardless of status.
     * Used to enforce the rate-limit check (deny if created < 1 min ago).
     */
    Optional<PasswordResetOtp> findTopByUserOrderByCreatedAtDesc(User user);

    /**
     * Retrieves and LOCKS the active (unused, non-expired) OTP for a user during verification.
     *
     * <p>Uses a pessimistic write lock ({@code SELECT ... FOR UPDATE}) so that concurrent
     * requests for the same OTP are serialized at the database level. The second request
     * will block until the first commits, then re-read and find the record already consumed
     * (attempt_count updated or is_used set), preventing double-verification.
     *
     * <p>{@code jakarta.persistence.lock.timeout = 3000} sets a 3-second timeout so the
     * request fails fast rather than blocking indefinitely if another transaction holds the lock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("""
            SELECT o FROM PasswordResetOtp o
            WHERE o.user = :user
              AND o.isUsed = false
              AND o.expiresAt > :now
            ORDER BY o.createdAt DESC
            LIMIT 1
            """)
    Optional<PasswordResetOtp> findActiveOtpForUpdateByUser(User user, ZonedDateTime now);

    /**
     * Deletes all OTP records for a user before creating a new one.
     * Keeps the table clean and prevents accumulation of stale rows.
     */
    @Modifying
    void deleteByUser(User user);

    /**
     * Bulk-deletes expired, unused OTP records.
     * Called by the hourly scheduled cleanup job.
     * The partial index {@code idx_otp_expires_at} keeps this efficient on large datasets.
     */
    @Modifying
    @Query("DELETE FROM PasswordResetOtp o WHERE o.expiresAt < :cutoff AND o.isUsed = false")
    void deleteExpiredUnusedOtps(ZonedDateTime cutoff);

    /**
     * Bulk-deletes already-used OTP records older than the given retention cutoff.
     * Called by the hourly cleanup job to prevent stale used-records from accumulating.
     */
    @Modifying
    @Query("DELETE FROM PasswordResetOtp o WHERE o.isUsed = true AND o.createdAt < :retentionCutoff")
    void deleteStaleUsedOtps(ZonedDateTime retentionCutoff);
}
