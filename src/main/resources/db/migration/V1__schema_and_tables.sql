CREATE SCHEMA IF NOT EXISTS auth;

CREATE TABLE IF NOT EXISTS auth.auth_users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    global_role VARCHAR NOT NULL DEFAULT 'USER',
    full_name VARCHAR(255),
    email_verified_at TIMESTAMPTZ,
    status VARCHAR NOT NULL DEFAULT 'ACTIVE',
    failed_login_count INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS auth.auth_user_sso_accounts (
    provider VARCHAR NOT NULL,
    provider_user_id VARCHAR NOT NULL,
    user_id UUID NOT NULL REFERENCES auth.auth_users(id) ON DELETE CASCADE,
    email_at_provider VARCHAR(255),
    email_verified BOOLEAN,
    linked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login_at TIMESTAMPTZ,
    PRIMARY KEY (provider, provider_user_id)
);

CREATE TABLE IF NOT EXISTS auth.auth_email_verifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.auth_users(id) ON DELETE CASCADE,
    token_hash TEXT UNIQUE NOT NULL,
    email_at_issue VARCHAR(255),
    expires_at TIMESTAMPTZ,
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS auth.auth_password_resets (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.auth_users(id) ON DELETE CASCADE,
    token_hash TEXT UNIQUE NOT NULL,
    expires_at TIMESTAMPTZ,
    used_at TIMESTAMPTZ,
    request_ip VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS auth.auth_clients (
    client_id VARCHAR PRIMARY KEY,
    client_secret_hash TEXT,
    name VARCHAR(255),
    scopes TEXT[],
    roles TEXT[],
    status VARCHAR NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ
);
