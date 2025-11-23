ALTER TABLE auth.auth_users
    ALTER COLUMN password_hash DROP NOT NULL;
