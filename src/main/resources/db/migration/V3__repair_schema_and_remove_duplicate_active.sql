-- V3__repair_schema_and_remove_duplicate_active.sql
-- Safely upgrades existing databases:
-- 1. Ensures trip.current_speed exists if upgraded from an older schema.
-- 2. Migrates any legacy active = FALSE flags into Bus.status and Route.route_status.
-- 3. Drops duplicate active boolean columns from bus and route so BusStatus and RouteStatus are the single source of truth.

SET @db_name = DATABASE();

-- 1. Ensure trip.current_speed exists
SET @sql = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'trip' AND COLUMN_NAME = 'current_speed'),
    'SELECT 1',
    'ALTER TABLE trip ADD COLUMN current_speed DOUBLE NULL'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. Migrate and drop duplicate bus.active column
SET @sql = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'bus' AND COLUMN_NAME = 'active'),
    'UPDATE bus SET status = ''INACTIVE'' WHERE active = FALSE',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'bus' AND COLUMN_NAME = 'active'),
    'ALTER TABLE bus DROP COLUMN active',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. Migrate and drop duplicate route.active column
SET @sql = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'route' AND COLUMN_NAME = 'active'),
    'UPDATE route SET route_status = ''UNAVAILABLE'' WHERE active = FALSE',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'route' AND COLUMN_NAME = 'active'),
    'ALTER TABLE route DROP COLUMN active',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
