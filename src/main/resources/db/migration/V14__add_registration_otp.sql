-- Registration OTP Table
-- Stores hashed OTPs for registration email verification flow.

CREATE TABLE IF NOT EXISTS registration_otps
(
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT                   NOT NULL,
    otp_hash      VARCHAR(255)             NOT NULL,
    is_used       BOOLEAN                  NOT NULL DEFAULT FALSE,
    attempt_count INT                      NOT NULL DEFAULT 0,
    expires_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_registration_otp_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_registration_otp_user_id ON registration_otps (user_id);
