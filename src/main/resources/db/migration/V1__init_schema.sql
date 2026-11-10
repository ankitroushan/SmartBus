-- V1__init_schema.sql
-- Baseline relational schema, constraints, and indexes for SmartBus Real-Time Bus Tracking System

CREATE TABLE IF NOT EXISTS admin (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_id VARCHAR(32) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS bus (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bus_number VARCHAR(64) NOT NULL UNIQUE,
    registration_number VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS driver (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    driver_name VARCHAR(128) NOT NULL,
    driver_id VARCHAR(191) NOT NULL UNIQUE,
    phone_number VARCHAR(32) NOT NULL UNIQUE,
    license_number VARCHAR(64) NOT NULL UNIQUE,
    pin_hash VARCHAR(255),
    account_status VARCHAR(32),
    availability_status VARCHAR(32)
);

CREATE TABLE IF NOT EXISTS stop (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stop_name VARCHAR(191) NOT NULL,
    latitude DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_stop_name_coords UNIQUE (stop_name, latitude, longitude),
    INDEX idx_stop_name (stop_name)
);

CREATE TABLE IF NOT EXISTS route (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    route_code VARCHAR(64) NOT NULL UNIQUE,
    route_status VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS route_stop (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    route_id BIGINT NOT NULL,
    stop_id BIGINT NOT NULL,
    sequence_no BIGINT NOT NULL,
    CONSTRAINT uk_route_sequence UNIQUE (route_id, sequence_no),
    CONSTRAINT fk_route_stop_route FOREIGN KEY (route_id) REFERENCES route (id) ON DELETE CASCADE,
    CONSTRAINT fk_route_stop_stop FOREIGN KEY (stop_id) REFERENCES stop (id)
);

CREATE TABLE IF NOT EXISTS trip (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trip_id VARCHAR(64) NOT NULL UNIQUE,
    bus_id BIGINT,
    driver_id BIGINT,
    route_id BIGINT,
    status VARCHAR(32),
    scheduled_start_time TIMESTAMP(6) NULL,
    scheduled_end_time TIMESTAMP(6) NULL,
    actual_start_time TIMESTAMP(6) NULL,
    actual_end_time TIMESTAMP(6) NULL,
    current_latitude DOUBLE,
    current_longitude DOUBLE,
    location_updated_at TIMESTAMP(6) NULL,
    current_speed DOUBLE,
    current_bearing DOUBLE,
    last_segment_index INT,
    CONSTRAINT fk_trip_bus FOREIGN KEY (bus_id) REFERENCES bus (id),
    CONSTRAINT fk_trip_driver FOREIGN KEY (driver_id) REFERENCES driver (id),
    CONSTRAINT fk_trip_route FOREIGN KEY (route_id) REFERENCES route (id),
    INDEX idx_trip_status_route (status, route_id),
    INDEX idx_trip_driver (driver_id),
    INDEX idx_trip_bus (bus_id)
);
