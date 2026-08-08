package com.schemavault.app.repository;

import com.schemavault.app.entity.RegistrationOtp;
import com.schemavault.app.entity.User;
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
public interface RegistrationOtpRepository extends JpaRepository<RegistrationOtp, Long> {

  /**
   * Retrieves the most-recently created OTP for a user, regardless of status.
   * Used to enforce rate-limit checks.
   */
  Optional<RegistrationOtp> findTopByUserOrderByCreatedAtDesc(User user);

  /**
   * Retrieves and LOCKS the active (unused, non-expired) OTP for a user during
   * verification.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
  @Query("""
      SELECT o FROM RegistrationOtp o
      WHERE o.user = :user
        AND o.isUsed = false
        AND o.expiresAt > :now
      ORDER BY o.createdAt DESC
      LIMIT 1
      """)
  Optional<RegistrationOtp> findActiveOtpForUpdateByUser(User user, ZonedDateTime now);

  /**
   * Deletes all OTP records for a user before creating a new one.
   */
  @Modifying
  void deleteByUser(User user);

  /**
   * Bulk-deletes expired, unused OTP records.
   */
  @Modifying
  @Query("DELETE FROM RegistrationOtp o WHERE o.expiresAt < :cutoff AND o.isUsed = false")
  void deleteExpiredUnusedOtps(ZonedDateTime cutoff);

  /**
   * Bulk-deletes already-used OTP records older than the given retention cutoff.
   */
  @Modifying
  @Query("DELETE FROM RegistrationOtp o WHERE o.isUsed = true AND o.createdAt < :retentionCutoff")
  void deleteStaleUsedOtps(ZonedDateTime retentionCutoff);
}
