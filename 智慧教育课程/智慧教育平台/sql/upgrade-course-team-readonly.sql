-- Run in the existing project database. Safe to run again (idempotent).
SET NAMES utf8mb4;

-- 教师团队身份：MEMBER 协作教师 / READONLY 只读协作。
-- 负责人 OWNER 由 edu_course_space.owner_user_id 推导，不在此列存储。
DROP PROCEDURE IF EXISTS upgrade_course_team_readonly;
DELIMITER $$
CREATE PROCEDURE upgrade_course_team_readonly()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'edu_course_teacher' AND column_name = 'team_role'
    ) THEN
        ALTER TABLE edu_course_teacher
            ADD COLUMN team_role VARCHAR(16) NOT NULL DEFAULT 'MEMBER'
            COMMENT '团队身份：MEMBER 协作教师 / READONLY 只读协作' AFTER user_id;
    END IF;
END$$
DELIMITER ;
CALL upgrade_course_team_readonly();
DROP PROCEDURE upgrade_course_team_readonly;
