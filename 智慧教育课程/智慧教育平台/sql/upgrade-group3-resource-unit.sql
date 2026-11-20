-- 第三组：资料章节归属落库（已有业务库执行一次；可重复执行）
-- 为 edu_resource 增加 unit_id，使上传时选择的课程章节能够持久化；
-- 全新环境请直接使用 install-fresh.sql，其中已包含本字段与外键。

DROP PROCEDURE IF EXISTS upgrade_group3_resource_unit;

DELIMITER $$
CREATE PROCEDURE upgrade_group3_resource_unit()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'edu_resource' AND column_name = 'unit_id'
    ) THEN
        ALTER TABLE edu_resource
            ADD COLUMN unit_id BIGINT NULL COMMENT '所属课程章节（edu_course_unit.id）' AFTER folder_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'edu_resource'
          AND index_name = 'idx_edu_resource_unit'
    ) THEN
        ALTER TABLE edu_resource ADD KEY idx_edu_resource_unit (unit_id, course_space_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'edu_resource'
          AND constraint_name = 'fk_edu_resource_unit' AND constraint_type = 'FOREIGN KEY'
    ) THEN
        ALTER TABLE edu_resource ADD CONSTRAINT fk_edu_resource_unit
            FOREIGN KEY (unit_id) REFERENCES edu_course_unit (id) ON DELETE SET NULL;
    END IF;
END$$
DELIMITER ;

CALL upgrade_group3_resource_unit();
DROP PROCEDURE IF EXISTS upgrade_group3_resource_unit;
