-- Password Reset OTP Table
-- Stores hashed OTPs for forgot-password flow.
-- Security design:
--   - otp_hash         : BCrypt hash of the 6-digit OTP (plain value is never stored)
--   - reset_token      : UUID issued only after OTP is successfully verified
--   - attempt_count    : incremented on every failed verification; OTP is locked after 5 attempts
--   - expires_at       : OTP validity window (10 minutes from creation)
--   - reset_token_expires_at : separate reset-token validity window (10 minutes from OTP verification)
--   - is_used          : set to TRUE after a successful password reset to prevent token reuse

CREATE TABLE password_reset_otps
(
    id                     BIGSERIAL PRIMARY KEY,
    user_id                BIGINT       NOT NULL,
    otp_hash               VARCHAR(255) NOT NULL,
    reset_token            VARCHAR(255),
    is_used                BOOLEAN      NOT NULL DEFAULT FALSE,
    attempt_count          INT          NOT NULL DEFAULT 0,
    expires_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    reset_token_expires_at TIMESTAMP WITH TIME ZONE,
    created_at             TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_otp_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Index for fast lookup during OTP verification and reset token validation
CREATE INDEX idx_otp_user_id      ON password_reset_otps (user_id);
CREATE INDEX idx_otp_reset_token  ON password_reset_otps (reset_token);
