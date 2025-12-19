ALTER TABLE auth.auth_users
    ADD COLUMN IF NOT EXISTS bio TEXT;

ALTER TABLE auth.auth_users
    ADD COLUMN IF NOT EXISTS avatar_key TEXT;

ALTER TABLE auth.auth_users
    ADD COLUMN IF NOT EXISTS avatar_content_type VARCHAR(100);

ALTER TABLE auth.auth_users
    ADD COLUMN IF NOT EXISTS avatar_updated_at TIMESTAMPTZ;