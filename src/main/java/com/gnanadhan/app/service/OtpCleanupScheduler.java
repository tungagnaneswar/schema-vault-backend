package com.gnanadhan.app.service;

import com.gnanadhan.app.repository.PasswordResetOtpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

/**
 * Periodically removes stale OTP records from the {@code password_reset_otps} table.
 *
 * <p>Two categories are purged:
 * <ol>
 *   <li><b>Expired unused records</b> — OTPs that were never verified before their TTL elapsed.
 *       Removed immediately once the clock passes {@code expires_at}.</li>
 *   <li><b>Used records past retention</b> — OTPs that completed a password reset are kept
 *       for {@value USED_RECORD_RETENTION_HOURS} hours for audit purposes, then deleted.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OtpCleanupScheduler {

    /**
     * How long (in hours) to retain used OTP records after a successful password reset.
     * Keeping them briefly allows audit-log correlation; they must not accumulate indefinitely.
     */
    private static final int USED_RECORD_RETENTION_HOURS = 24;

    private final PasswordResetOtpRepository otpRepository;

    /**
     * Runs every hour at the top of the hour.
     *
     * <p>Both deletes are issued in a single transaction so the table never ends up
     * in a partially cleaned state if the JVM is interrupted mid-run.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void purgeStaleOtps() {
        ZonedDateTime now = ZonedDateTime.now();

        // 1 — Remove expired, never-used OTPs (their TTL has passed).
        otpRepository.deleteExpiredUnusedOtps(now);

        // 2 — Remove used records older than the retention window.
        //     These completed resets; retaining them beyond 24 h has no audit value.
        ZonedDateTime retentionCutoff = now.minusHours(USED_RECORD_RETENTION_HOURS);
        otpRepository.deleteStaleUsedOtps(retentionCutoff);

        log.info("OTP cleanup completed at {} — expired-unused and used-past-retention records removed", now);
    }
}
