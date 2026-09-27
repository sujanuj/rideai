-- RideAI initial schema.
-- Live driver positions are NOT stored here: they change every few seconds and live in Redis.

CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    full_name     TEXT NOT NULL,
    role          TEXT NOT NULL CHECK (role IN ('RIDER', 'DRIVER')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE drivers (
    user_id BIGINT PRIMARY KEY REFERENCES users (id),
    vehicle TEXT NOT NULL,
    plate   TEXT NOT NULL,
    rating  NUMERIC(2, 1) DEFAULT 5.0,
    status  TEXT NOT NULL DEFAULT 'OFFLINE'
        CHECK (status IN ('OFFLINE', 'AVAILABLE', 'ON_TRIP'))
);

CREATE TABLE trips (
    id             BIGSERIAL PRIMARY KEY,
    rider_id       BIGINT NOT NULL REFERENCES users (id),
    driver_id      BIGINT REFERENCES users (id),
    status         TEXT NOT NULL
        CHECK (status IN ('REQUESTED', 'ACCEPTED', 'ARRIVED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    pickup         GEOGRAPHY(POINT, 4326) NOT NULL,
    dropoff        GEOGRAPHY(POINT, 4326) NOT NULL,
    route          GEOGRAPHY(LINESTRING, 4326),   -- actual path, filled on completion
    est_distance_m INT,
    est_duration_s INT,
    fare_cents     INT,
    requested_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    accepted_at    TIMESTAMPTZ,
    started_at     TIMESTAMPTZ,
    completed_at   TIMESTAMPTZ
);

CREATE INDEX trips_pickup_gix ON trips USING GIST (pickup);
CREATE INDEX trips_rider_idx ON trips (rider_id, requested_at DESC);
CREATE INDEX trips_driver_idx ON trips (driver_id, requested_at DESC);

-- The timeline the AI Trip Assistant reads.
CREATE TABLE trip_events (
    id         BIGSERIAL PRIMARY KEY,
    trip_id    BIGINT NOT NULL REFERENCES trips (id),
    type       TEXT NOT NULL,          -- e.g. DRIVER_ASSIGNED, DELAY_DETECTED, ROUTE_DEVIATION
    payload    JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX trip_events_trip_idx ON trip_events (trip_id, created_at);

CREATE TABLE trip_insights (
    trip_id     BIGINT PRIMARY KEY REFERENCES trips (id),
    summary     TEXT NOT NULL,
    suggestions JSONB NOT NULL,        -- list of {title, detail}
    model       TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
