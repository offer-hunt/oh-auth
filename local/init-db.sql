CREATE ROLE auth_user WITH LOGIN PASSWORD 'auth_pass' NOSUPERUSER;
CREATE DATABASE authdb OWNER auth_user;

\connect authdb
CREATE SCHEMA IF NOT EXISTS auth AUTHORIZATION auth_user;
GRANT USAGE ON SCHEMA auth TO auth_user;
ALTER ROLE auth_user SET search_path TO auth;
