-- 第三组：发布来源区分（课程建设 / 课前准备）
-- 适用：已有业务库执行一次；可重复执行。
-- 全新环境请直接使用 install-fresh.sql（已包含本字段）。

-- 执行前请先选中目标数据库；本脚本不绑定具体库名。

-- 1. 增加 release_source 字段：STUDIO=课程建设，PREPARE=课前准备，BOTH=两处均已发布
SET @has_release_source := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'edu_resource_class_release'
       AND COLUMN_NAME = 'release_source'
);
SET @ddl := IF(@has_release_source = 0,
    'ALTER TABLE edu_resource_class_release ADD COLUMN release_source VARCHAR(16) NOT NULL DEFAULT ''STUDIO'' AFTER released_by_user_id',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 历史数据统一按课程建设处理
UPDATE edu_resource_class_release
   SET release_source = 'STUDIO'
 WHERE release_source IS NULL OR release_source = '';

-- 3. 来源过滤索引
SET @has_source_index := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'edu_resource_class_release'
       AND INDEX_NAME = 'idx_edu_release_binding_source'
);
SET @ddl := IF(@has_source_index = 0,
    'ALTER TABLE edu_resource_class_release ADD KEY idx_edu_release_binding_source (binding_id, release_source, opens_at)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
