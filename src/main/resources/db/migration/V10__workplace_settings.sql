CREATE TABLE organization_workplace_settings (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    latitude DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    radius_meters INTEGER NOT NULL CHECK (radius_meters BETWEEN 10 AND 5000),
    gps_tolerance_meters INTEGER NOT NULL CHECK (gps_tolerance_meters BETWEEN 20 AND 50),
    version BIGINT NOT NULL CHECK (version >= 1),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID NOT NULL
);
