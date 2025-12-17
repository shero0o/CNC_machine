CREATE EXTENSION IF NOT EXISTS timescaledb;
CREATE TABLE IF NOT EXISTS cnc_machine (
                                           time TIMESTAMPTZ NOT NULL,
                                           machine TEXT NOT NULL,
                                           status TEXT CHECK (status IN ('Running', 'Stopped', 'Error', 'Maintenance')),
                                           spindle_speed DOUBLE PRECISION CHECK (spindle_speed >= 0),
                                           coolant_temp DOUBLE PRECISION CHECK (coolant_temp >= 0),
                                           tool_life DOUBLE PRECISION CHECK (tool_life >= 0 AND tool_life <= 100),
                                           progress DOUBLE PRECISION CHECK (progress >= 0 AND progress <= 100),
                                           plant TEXT,
                                           line TEXT,
                                           operator TEXT,
                                           shift TEXT,
                                           PRIMARY KEY (time, machine)
);


SELECT create_hypertable('cnc_machine', 'time', if_not_exists => TRUE);

CREATE INDEX IF NOT EXISTS idx_cnc_time_desc
    ON cnc_machine (time DESC);

SELECT add_retention_policy(
               'cnc_machine',
               INTERVAL '30 days',
               if_not_exists => TRUE
       );




DROP MATERIALIZED VIEW IF EXISTS public.hourly_cnc_kpis CASCADE;
DROP MATERIALIZED VIEW IF EXISTS public.daily_cnc_kpis CASCADE;
DROP VIEW IF EXISTS public.hourly_cnc_kpis CASCADE;
DROP VIEW IF EXISTS public.daily_cnc_kpis CASCADE;


CREATE MATERIALIZED VIEW public.hourly_cnc_kpis
WITH (timescaledb.continuous)
AS
SELECT
    time_bucket('1 hour', time) AS bucket,
    machine,
    AVG(spindle_speed) AS avg_spindle_speed,
    MIN(spindle_speed) AS min_spindle_speed,
    MAX(spindle_speed) AS max_spindle_speed,
    AVG(coolant_temp) AS avg_coolant_temp,
    MIN(coolant_temp) AS min_coolant_temp,
    MAX(coolant_temp) AS max_coolant_temp,
    AVG(tool_life) AS avg_tool_life,
    AVG(progress) AS avg_progress,
    COUNT(*) AS samples
FROM cnc_machine
GROUP BY bucket, machine
WITH NO DATA;

CALL refresh_continuous_aggregate('hourly_cnc_kpis', NULL, NULL);


CREATE MATERIALIZED VIEW public.daily_cnc_kpis
WITH (timescaledb.continuous)
AS
SELECT
    time_bucket('1 day', time) AS bucket,
    machine,
    AVG(spindle_speed) AS avg_spindle_speed,
    MAX(spindle_speed) AS max_spindle_speed,
    AVG(coolant_temp) AS avg_coolant_temp,
    MIN(tool_life) AS min_tool_life,
    AVG(progress) AS avg_progress
FROM cnc_machine
GROUP BY bucket, machine
WITH NO DATA;

CALL refresh_continuous_aggregate('daily_cnc_kpis', NULL, NULL);


SELECT add_continuous_aggregate_policy('hourly_cnc_kpis',
                                       start_offset => INTERVAL '2 days',
                                       end_offset => INTERVAL '1 minute',
                                       schedule_interval => INTERVAL '1 minute');

SELECT add_continuous_aggregate_policy('daily_cnc_kpis',
                                       start_offset => INTERVAL '14 days',
                                       end_offset => INTERVAL '10 minutes',
                                       schedule_interval => INTERVAL '10 minutes');

