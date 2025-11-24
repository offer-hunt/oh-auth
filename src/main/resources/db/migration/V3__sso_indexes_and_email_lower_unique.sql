ALTER TABLE auth.auth_users
DROP CONSTRAINT IF EXISTS auth_users_email_key;

CREATE UNIQUE INDEX IF NOT EXISTS ux_auth_users_email_lower
    ON auth.auth_users (LOWER(email));

CREATE INDEX IF NOT EXISTS idx_auth_user_sso_accounts_user_id
    ON auth.auth_user_sso_accounts (user_id);

CREATE INDEX IF NOT EXISTS idx_auth_email_verifications_user_id
    ON auth.auth_email_verifications (user_id);

CREATE INDEX IF NOT EXISTS idx_auth_email_verifications_expires_at
    ON auth.auth_email_verifications (expires_at);

CREATE INDEX IF NOT EXISTS idx_auth_password_resets_user_id
    ON auth.auth_password_resets (user_id);

CREATE INDEX IF NOT EXISTS idx_auth_password_resets_expires_at
    ON auth.auth_password_resets (expires_at);
