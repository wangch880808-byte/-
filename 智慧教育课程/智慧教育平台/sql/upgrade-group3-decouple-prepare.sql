-- 第三组：课程建设与课前准备资料解耦
-- 1) edu_resource 增加 origin，标记资料归属（课程建设 STUDIO / 课前准备 PREPARE）。
-- 2) 发布记录按「教学班 + 资源 + 发布来源」唯一，替代原来「教学班 + 资源」唯一，
--    使同一资料在两个入口的发布、撤回、删除互不影响。
-- 3) 历史上合并为 BOTH 的有效发布拆分为 STUDIO 与 PREPARE 两条独立记录。

SET @group3_ddl = IF(EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'edu_resource' AND column_name = 'origin'
), 'SELECT 1',
    'ALTER TABLE edu_resource ADD COLUMN origin VARCHAR(16) NOT NULL DEFAULT ''STUDIO'' COMMENT ''STUDIO 课程建设 / PREPARE 课前准备'' AFTER resource_kind');
PREPARE group3_stmt FROM @group3_ddl;
EXECUTE group3_stmt;
DEALLOCATE PREPARE group3_stmt;

SET @group3_ddl = IF(EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'edu_resource_class_release' AND column_name = 'active_resource_id'
), 'ALTER TABLE edu_resource_class_release
    DROP INDEX uq_edu_release_active_resource, DROP COLUMN active_resource_id,
    ADD COLUMN active_release_key VARCHAR(80) GENERATED ALWAYS AS
        (CASE WHEN ended_at IS NULL THEN CONCAT(resource_id, '':'', release_source) ELSE NULL END) STORED,
    ADD UNIQUE KEY uq_edu_release_active_source (binding_id, active_release_key)',
    'SELECT 1');
PREPARE group3_stmt FROM @group3_ddl;
EXECUTE group3_stmt;
DEALLOCATE PREPARE group3_stmt;

START TRANSACTION;
INSERT INTO edu_resource_class_release
    (binding_id, course_space_id, resource_id, resource_version_id, released_by_user_id,
     release_source, released_at, opens_at, closes_at, created_at)
SELECT legacy.binding_id, legacy.course_space_id, legacy.resource_id, legacy.resource_version_id,
       legacy.released_by_user_id, 'PREPARE', legacy.released_at, legacy.opens_at, legacy.closes_at, legacy.created_at
FROM edu_resource_class_release legacy
LEFT JOIN edu_resource_class_release existing
  ON existing.binding_id = legacy.binding_id AND existing.resource_id = legacy.resource_id
 AND existing.release_source = 'PREPARE' AND existing.ended_at IS NULL
WHERE legacy.release_source = 'BOTH' AND legacy.ended_at IS NULL AND existing.id IS NULL;

UPDATE edu_resource_class_release legacy
LEFT JOIN edu_resource_class_release existing
  ON existing.binding_id = legacy.binding_id AND existing.resource_id = legacy.resource_id
 AND existing.release_source = 'STUDIO' AND existing.ended_at IS NULL
SET legacy.release_source = 'STUDIO',
    legacy.ended_at = IF(existing.id IS NULL, NULL, GREATEST(UTC_TIMESTAMP(3), legacy.released_at)),
    legacy.end_kind = IF(existing.id IS NULL, NULL, 'REPLACED')
WHERE legacy.release_source = 'BOTH' AND legacy.ended_at IS NULL;
COMMIT;
