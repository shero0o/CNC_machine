# CNC Machining Center - Data Pipeline & Monitoring System
### Final Assignment - Documentation 



----

## 1. Introduction

This project implements a full industrial-style data pipeline for monitoring a CNC machining center using:

- **OPC-UA** for machine telemetry
- **MQTT** as lightweight transport
- **Kafka / Redpanda** for scalable event streaming
- **Redis** for contextual metadata enrichment
- **TimescaleDB** for time-series storage and analytics
- **Grafana** for real-time and historical visualization

### Use Case Overview

The pipeline is designed for the following use case:

> **High-frequency monitoring of CNC machine behavior to detect spindle anomalies, thermal drift, and tool wear issues in near-real time.**

A CNC machine publishes telemetry every **2 seconds**, allowing operators to:

- identify abnormal spindle speed deviations,
- detect coolant temperature spikes,
- track remaining tool life,
- visualize production progress,
- correlate sensor values with contextual data (plant, operator, shift).

This use case focuses on **continuous condition monitoring** rather than discrete events.

---




## 2. OPC-UA Server Overview

The OPC-UA Server simulates a realistic CNC machining center.  
All OPC-UA variables are defined inside **`CncNodeManager.java`**, structured under the root node:

---

## 3. System Architecture

### 3.1 Components
The complete system consists of seven logical components:

| Component                       | Purpose                                                                     |
|---------------------------------|-----------------------------------------------------------------------------|
| **OPC-UA Server**               | Simulates CNC telemetry fields and machine behavior.                        |
| **MqttOpcUaAgent**              | Reads OPC-UA values and publishes MQTT JSON messages..                      |
| **Mosquitto Broker**            | Lightweight publish/subscribe system.                                       |
| **Redis**                       | Stores contextual metadata (plant/line/operator/shift).                     |
| **Redis Init**                  | One-shot container that writes initial context into Redis.                  |
| **HydrationAgent**              | Adds contextual metadata from Redis and sends to Kafka.                     |
| **Redpanda Broker (Kafka API)** | Event streaming platform, stores enriched telemetry in topics.              |
| **TimescaleAgent**              | Consumes Kafka messages and writes into TimescaleDB.                        |
| **TimescaleDB**                 | Time-series database optimized for analytics.                               |
| **Timescale Refresh**           | One-shot initial refresh job to refresh CAGGs once after first data exists. |
| **pgAdmin**                     | Admin UI for SQL inspection                                                 |
| **Grafana**                     | Visualization interface.                                                    |



### 3.2 Ports Used

| Service         | Port                           | Purpose                  |
|-----------------|--------------------------------|--------------------------|
| OPC-UA Server   | 52599 (host) 52520 (container) | Binary OPC-UA endpoint   |
| Mosquitto MQTT  | 1883                           | MQTT                     |
| Redpanda Kafka  | 19092 / 9092                   | Kafka API                |
| Redpanda Console | 8087                           | UI for Kafka topics      |
| Redis           | 6379                           | Context store            |
| TimescaleDB     | 5432                           | PostgreSQL-compatible DB |
| Grafana         | 3000                           | Dashboard UI             |
| pgAdmin         | 5050                           | SQL administration UI    |


### 3.3 User Interfaces (URLs)
####  Grafana: 
- URL: http://localhost:3000
- Username: `admin`
- Password: `admin123`


#### Redpanda Console: 
- URL: http://localhost:8087


#### pgAdmin: 
- URL: http://localhost:5050
- Email: `admin@local.com`
- Password: `admin123


#### TimescaleDb (PostgreSQL)
- Host: `timescaledb`
- Port: `5432`
- Database: `mydb`
- Username: `admin`
- Password: `admin123`
---


## 4. Redis Context Enrichment

### 4.1 Purpose
Redis provides contextual metadata that is not part of raw telemetry but is important for correlation (plant, line, operator, shift).
The HydrationAgent retrieves metadata from Redis using the key:

`machine:1:context`

Example stored value:

```json
{
  "plant": "Dornbirn Factory",
  "line": "Line A",
  "operator": "Müller",
  "shift": "Early"
}
```

This metadata is merged into each Kafka-bound message:
```json 
{
    "machine": "CNC-01",
    "timestamp": 1700000000,
    "data": { ... },
    "context": {
        "plant": "Vienna",
        "line": "MachiningLine-A",
        "operator": "John D.",
        "shift": "Early"
    }
}
```

### 4.2 Redis Initialization (redis_init)
A dedicated one-shot container (redis_init) inserts initial context so the system works immediately after startup (no manual Redis setup required).


---
## 5. TimescaleDB Schema

Schema creation is done via `timescale.sql`

### 5.1 Base Table cnc_machine
```sql
CREATE TABLE cnc_machine (
time TIMESTAMPTZ NOT NULL,
machine TEXT NOT NULL,
status TEXT CHECK (status IN ('Running', 'Stopped', 'Error', 'Maintenance')),
spindle_speed DOUBLE PRECISION (spindle_speed >= 0),
coolant_temp DOUBLE PRECISION (coolant_temp >= 0),
tool_life DOUBLE PRECISION (tool_life >= 0 AND tool_life <= 100),
progress DOUBLE PRECISION (progress >= 0 AND progress <= 100),
plant TEXT,
line TEXT,
operator TEXT,
shift TEXT,
PRIMARY KEY (time, machine)
);
```

### 5.2 Hypertable
```sql
SELECT create_hypertable('cnc_machine', 'time', if_not_exists => TRUE);

```

### 5.3 Retention and Indexing
- Descending time index for common “latest data” queries:
```sql
CREATE INDEX IF NOT EXISTS idx_cnc_time_desc
    ON cnc_machine (time DESC);
```

- Retention policy (keep 30 days raw data):
```sql
SELECT add_retention_policy(
               'cnc_machine',
               INTERVAL '30 days',
               if_not_exists => TRUE
       );

```
---

## 6. Continuous Aggregate

### 6.1 Hourly KPIs
- Interval: 1 hour
- Metrics: AVG/MIN/MAX for spindle speed and coolant temp, AVG tool life and progress, plus samples
```sql
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


```

### 6.2 Daily KPIs
- Interval: 1 day
- Metrics: daily averages and extrema for long-term reporting
```sql
CREATE MATERIALIZED VIEW public.daily_cnc_kpis
    WITH (timescaledb.continuous) AS
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


```

### 6.3 Refresh Policies

#### Automatic refresh policies
- Hourly KPIs policy refreshes every 1 minute, covering the last 2 days, excluding the last 1 minute (to avoid incomplete near-now windows).
- Daily KPIs policy refreshes every 10 minutes, covering the last 14 days, excluding the last 10 minutes.
- One-shot refresh job (timescale_refresh)

A dedicated docker-compose service performs an initial refresh once the first telemetry rows exist. This ensures that dashboards show KPI data immediately after startup

1. Hourly aggregate refresh policy:
```sql
SELECT add_continuous_aggregate_policy('hourly_cnc_kpis',
                                       start_offset => INTERVAL '2 days',
                                       end_offset => INTERVAL '1 minute',
                                       schedule_interval => INTERVAL '1 minute');

```
2. Daily aggregate refresh policy:
```sql
SELECT add_continuous_aggregate_policy('daily_cnc_kpis',
                                       start_offset => INTERVAL '14 days',
                                       end_offset => INTERVAL '10 minutes',
                                       schedule_interval => INTERVAL '10 minutes');

```
---

## 7. Data Pipeline Agents


### 7.1 HydrationAgent (MQTT > Kafka with Redis context)

- Subscribes to MQTT telemetry
- Loads Redis context
- Publishes enriched event to Kafka topic


### 7.2 TimescaleAgent (Kafka > TimescaleDB)
The TimescaleAgent agent consumes Redpanda messages and inserts them:

```sql
INSERT INTO cnc_machine
(time, machine, status, spindle_speed, coolant_temp, tool_life, progress, plant, line, operator, shift)
VALUES (...)

```
---

## 8 Grafana Dashboard

- The dashboard is exported as JSON `Cnc_dashboard.json`

#### The dashboard fulfills the requirement for:
- Short-term visualization (last 5min - 24h)
- Long-term visualization (last 2 days or longer)

### 8.1 Short-Term Panels (last 24 hours)
#### Panel 1: Spindle Speed (RPM)
```sql
SELECT time AS time, spindle_speed
FROM cnc_machine
WHERE machine = 'CNC-01'
AND $__timeFilter(time)
ORDER BY time;

```

Interpretation:
Shows short-term spindle behavior. Oscillations may indicate load changes or instability.

#### Panel 2: Coolant Temperature (°C)
```sql
SELECT time AS time, coolant_temp
FROM cnc_machine
WHERE machine = 'CNC-01'
AND $__timeFilter(time)
ORDER BY time;
```
Interpretation:
Highlights short-term thermal stability. Rising trends or spikes may indicate cooling inefficiency or abnormal conditions.


### 8.2 Long-Term Panels (Continuous Aggregate)
#### Panel 3: Hourly Spindle Speed KPIs
```sql
SELECT bucket AS time, avg_spindle_speed, min_spindle_speed, max_spindle_speed
FROM hourly_cnc_kpis
WHERE machine = 'CNC-01'
AND $__timeFilter(bucket)
ORDER BY bucket;
```
Interpretation:
- avg_spindle_speed shows the main operating regime
- min/max show variability per hour
- If min=avg=max that is because of very few samples.

#### Panel 4: Daily KPIs
For a visually clearer daily panel (different units):
```sql
SELECT bucket AS time, max_spindle_speed, avg_coolant_temp, avg_progress
FROM daily_cnc_kpis
WHERE machine = 'CNC-01'
AND $__timeFilter(bucket)
ORDER BY bucket;
```
Interpretation:
- max_spindle_speed for highest operating load per day
- avg_coolant_temp indicates daily thermal baseline
- avg_progress provides daily production trend

##### Visualization note (dual axis):
- To keep the panel intuitive, RPM is mapped to the left Y-axis and percentage/temperature to the right Y-axis in Grafana (field overrides per series).



---

## 9. End-to-End Data Flow Description

1. OPC-UA Server simulates CNC telemetry continuously.
2. MqttOpcUaAgent reads OPC-UA data and publishes JSON to MQTT broker.
3. Mosquitto delivers MQTT messages
4. HydrationAgent enriches messages with Redis context data and publishes to Kafka.
5. Redpanda stores data in topic `machine_data`.
6. TimescaleAgent consumes from Kafka and inserts into TimescaleDB
7. TimescaleDB continuous aggregates compute hourly/daily analytics automatically.
8. Grafana visualizes both short-term and long-term trends.