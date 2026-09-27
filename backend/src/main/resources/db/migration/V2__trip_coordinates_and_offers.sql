-- Phase 1 + 2: store trip coordinates as plain lat/lng (easy to map in JPA) and let
-- Postgres derive the PostGIS geography columns from them automatically.

DROP INDEX IF EXISTS trips_pickup_gix;
ALTER TABLE trips DROP COLUMN pickup, DROP COLUMN dropoff;

ALTER TABLE trips
    ADD COLUMN pickup_lat  DOUBLE PRECISION NOT NULL,
    ADD COLUMN pickup_lng  DOUBLE PRECISION NOT NULL,
    ADD COLUMN dropoff_lat DOUBLE PRECISION NOT NULL,
    ADD COLUMN dropoff_lng DOUBLE PRECISION NOT NULL;

-- Generated columns: always in sync with the lat/lng above, ready for spatial queries.
ALTER TABLE trips
    ADD COLUMN pickup GEOGRAPHY(POINT, 4326)
        GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(pickup_lng, pickup_lat), 4326)::geography) STORED,
    ADD COLUMN dropoff GEOGRAPHY(POINT, 4326)
        GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(dropoff_lng, dropoff_lat), 4326)::geography) STORED;

CREATE INDEX trips_pickup_gix ON trips USING GIST (pickup);

-- Matching: which driver currently holds the offer, and until when.
ALTER TABLE trips
    ADD COLUMN offered_driver_id BIGINT REFERENCES users (id),
    ADD COLUMN offer_expires_at  TIMESTAMPTZ,
    ADD COLUMN arrived_at        TIMESTAMPTZ,
    ADD COLUMN cancelled_at      TIMESTAMPTZ,
    ADD COLUMN cancel_reason     TEXT;

CREATE INDEX trips_status_idx ON trips (status);
CREATE INDEX trips_offered_driver_idx ON trips (offered_driver_id) WHERE status = 'REQUESTED';
