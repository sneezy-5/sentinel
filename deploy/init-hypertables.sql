-- Converts the four time-series tables into real TimescaleDB hypertables (architecture
-- doc, section 4.2). Run this ONCE, after central-server has booted at least once and
-- created the plain tables via ddl-auto=update - it can't run any earlier since the
-- tables don't exist yet, and TimescaleDB isn't involved in Hibernate's DDL at all.
--
-- Usage (from the repo root, with the deploy stack already up):
--   docker compose -f deploy/docker-compose.yml exec -T timescaledb \
--     psql -U sentinel -d sentinel -f - < deploy/init-hypertables.sql
--
-- create_hypertable() requires the partitioning column (timestamp) to be part of any
-- unique/primary key constraint on the table. Hibernate creates a single-column PRIMARY
-- KEY on `id` (auto-increment), which conflicts with that - hence dropping it below.
-- These id columns aren't referenced by any foreign key elsewhere, so this is safe; it
-- just means `id` is no longer guaranteed unique at the DB level (Hibernate's
-- GenerationType.IDENTITY still produces unique values in practice).
--
-- If a DROP CONSTRAINT below fails with "constraint does not exist", inspect the real
-- name with `\d system_metrics` (etc.) - Postgres' default naming (<table>_pkey) is
-- assumed here but wasn't verified against a live database in this environment.

CREATE EXTENSION IF NOT EXISTS timescaledb;

ALTER TABLE system_metrics DROP CONSTRAINT IF EXISTS system_metrics_pkey;
SELECT create_hypertable('system_metrics', by_range('timestamp'), if_not_exists => TRUE, migrate_data => TRUE);

ALTER TABLE service_metrics DROP CONSTRAINT IF EXISTS service_metrics_pkey;
SELECT create_hypertable('service_metrics', by_range('timestamp'), if_not_exists => TRUE, migrate_data => TRUE);

ALTER TABLE logs_raw DROP CONSTRAINT IF EXISTS logs_raw_pkey;
SELECT create_hypertable('logs_raw', by_range('timestamp'), if_not_exists => TRUE, migrate_data => TRUE);

ALTER TABLE log_events DROP CONSTRAINT IF EXISTS log_events_pkey;
SELECT create_hypertable('log_events', by_range('timestamp'), if_not_exists => TRUE, migrate_data => TRUE);

-- Retention policies (architecture doc, section 4.2: short for raw logs, long for the rest).
SELECT add_retention_policy('logs_raw', INTERVAL '7 days', if_not_exists => TRUE);
