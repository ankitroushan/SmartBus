-- V2__ensure_audit_columns.sql
-- Safely upgrades pre-existing busTrackingSystem databases by adding any missing audit columns and indexes

SET @db_name = DATABASE();

-- 1. admin.must_change_password
SET @sql = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'admin' AND COLUMN_NAME = 'must_change_password'),
    'SELECT 1',
    'ALTER TABLE admin ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. bus.active
SET @sql = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'bus' AND COLUMN_NAME = 'active'),
    'SELECT 1',
    'ALTER TABLE bus ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. stop.active
SET @sql = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'stop' AND COLUMN_NAME = 'active'),
    'SELECT 1',
    'ALTER TABLE stop ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. route.active
SET @sql = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'route' AND COLUMN_NAME = 'active'),
    'SELECT 1',
    'ALTER TABLE route ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5. trip.current_bearing
SET @sql = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'trip' AND COLUMN_NAME = 'current_bearing'),
    'SELECT 1',
    'ALTER TABLE trip ADD COLUMN current_bearing DOUBLE NULL'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 6. trip.last_segment_index
SET @sql = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'trip' AND COLUMN_NAME = 'last_segment_index'),
    'SELECT 1',
    'ALTER TABLE trip ADD COLUMN last_segment_index INT DEFAULT 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
