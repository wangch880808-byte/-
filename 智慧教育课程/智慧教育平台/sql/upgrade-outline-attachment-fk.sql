-- Run in the existing project database. Safe to run again (idempotent).
SET NAMES utf8mb4;

-- 附件表原带指向正式章节/知识点表的组合外键，导致草稿节点（未发布）上传附件时 500。
-- 草稿表与正式表沿用同一 ID，附件按 ID 归属、课程空间校验，无需此外键；
-- 删除节点的附件清理由目录发布流程显式完成。
DROP PROCEDURE IF EXISTS upgrade_outline_attachment_fk;
DELIMITER $$
CREATE PROCEDURE upgrade_outline_attachment_fk()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'edu_outline_attachment'
          AND constraint_name = 'fk_outline_attachment_unit' AND constraint_type = 'FOREIGN KEY'
    ) THEN
        ALTER TABLE edu_outline_attachment DROP FOREIGN KEY fk_outline_attachment_unit;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'edu_outline_attachment'
          AND constraint_name = 'fk_outline_attachment_point' AND constraint_type = 'FOREIGN KEY'
    ) THEN
        ALTER TABLE edu_outline_attachment DROP FOREIGN KEY fk_outline_attachment_point;
    END IF;
END$$
DELIMITER ;
CALL upgrade_outline_attachment_fk();
DROP PROCEDURE upgrade_outline_attachment_fk;
