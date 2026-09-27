-- Phases 3-5: pickup ETA at accept time, actual distance driven, richer AI insights.

ALTER TABLE trips
    ADD COLUMN pickup_eta_s      INT,   -- driver's estimated time to reach the pickup, when they accepted
    ADD COLUMN actual_distance_m INT;   -- measured from GPS points during the ride

ALTER TABLE trip_insights
    ADD COLUMN flags   JSONB NOT NULL DEFAULT '[]',   -- e.g. ["LONG_WAIT", "ROUTE_DEVIATION"]
    ADD COLUMN metrics JSONB NOT NULL DEFAULT '{}';   -- the numbers the insight was based on

CREATE INDEX trip_events_type_idx ON trip_events (trip_id, type);
