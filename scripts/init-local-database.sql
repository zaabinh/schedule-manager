-- Bootstrap a standalone/local PostgreSQL instance for Schedule Manager.
-- Run this file with psql while connected as a PostgreSQL administrator.
-- Required psql variables: app_db, app_user, app_password.
--
-- Example:
-- psql -U postgres -d postgres \
--   -v app_db=schedule_manager \
--   -v app_user=schedule_app \
--   -v app_password=schedule_local_password \
--   -f scripts/init-local-database.sql

\set ON_ERROR_STOP on

SELECT format(
    'CREATE ROLE %I LOGIN PASSWORD %L',
    :'app_user',
    :'app_password'
)
WHERE NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = :'app_user'
) \gexec

-- Keep reruns deterministic for local development.
ALTER ROLE :"app_user" WITH LOGIN PASSWORD :'app_password';

SELECT format(
    'CREATE DATABASE %I OWNER %I ENCODING %L TEMPLATE template0',
    :'app_db',
    :'app_user',
    'UTF8'
)
WHERE NOT EXISTS (
    SELECT 1 FROM pg_database WHERE datname = :'app_db'
) \gexec

ALTER DATABASE :"app_db" OWNER TO :"app_user";

\connect :app_db

ALTER SCHEMA public OWNER TO :"app_user";
GRANT ALL ON SCHEMA public TO :"app_user";
GRANT CONNECT, TEMPORARY ON DATABASE :"app_db" TO :"app_user";

\echo 'Database and application role are ready.'
\echo 'Start the Spring Boot backend to let Flyway create/update the schema.'
