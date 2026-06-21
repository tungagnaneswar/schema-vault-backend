-- V7: Harden password_reset_otps indexing and uniqueness
--
-- Missing from V6:
--   1. Composite index on (user_id, expires_at) for efficient active-OTP lookup
--   2. Composite index on (user_id, reset_token_expires_at) for reset-token lookups
--   3. Index on expires_at alone for the cleanup scheduler's bulk delete
--   4. UNIQUE constraint on reset_token — prevents two records from sharing a token
--      (NULL values are exempt from uniqueness in PostgreSQL, so nullable is fine)

-- Supports: WHERE user_id = ? AND is_used = false AND expires_at > ?
CREATE INDEX idx_otp_user_active
    ON password_reset_otps (user_id, expires_at)
    WHERE is_used = FALSE;

-- Supports: WHERE reset_token_expires_at > ? (cleanup + reset-token lookup)
CREATE INDEX idx_otp_reset_token_expires
    ON password_reset_otps (reset_token_expires_at)
    WHERE reset_token IS NOT NULL;

-- Supports: cleanup scheduler's DELETE WHERE expires_at < cutoff
CREATE INDEX idx_otp_expires_at
    ON password_reset_otps (expires_at);

-- Prevents two active records sharing the same reset_token UUID.
-- NULL is excluded from uniqueness in PostgreSQL, so rows without a token are unaffected.
ALTER TABLE password_reset_otps
    ADD CONSTRAINT uq_otp_reset_token UNIQUE (reset_token);
