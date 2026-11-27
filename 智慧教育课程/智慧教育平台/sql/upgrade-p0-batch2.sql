-- Run in the existing project database. Safe to run again (idempotent).
SET NAMES utf8mb4;

-- 教师团队细粒度权限（逗号分隔的权限键，如 outline,knowledgeGraph,material）
-- 课程空间：目录是否有未发布改动
DROP PROCEDURE IF EXISTS upgrade_p0_batch2;
DELIMITER $$
CREATE PROCEDURE upgrade_p0_batch2()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'edu_course_teacher' AND column_name = 'permissions'
    ) THEN
        ALTER TABLE edu_course_teacher
            ADD COLUMN permissions VARCHAR(255) NOT NULL DEFAULT ''
            COMMENT '逗号分隔的权限键' AFTER team_role;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'edu_course_space' AND column_name = 'outline_dirty'
    ) THEN
        ALTER TABLE edu_course_space
            ADD COLUMN outline_dirty TINYINT(1) NOT NULL DEFAULT 0
            COMMENT '目录有未发布改动' AFTER status;
    END IF;
END$$
DELIMITER ;
CALL upgrade_p0_batch2();
DROP PROCEDURE upgrade_p0_batch2;

-- 目录草稿表：教师编辑草稿，学生始终读已发布的目录
CREATE TABLE IF NOT EXISTS edu_course_unit_draft (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_space_id BIGINT NOT NULL,
    parent_unit_id BIGINT NULL,
    unit_kind VARCHAR(16) NOT NULL,
    title VARCHAR(160) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    content_text TEXT NULL,
    PRIMARY KEY (id),
    KEY idx_unit_draft_course (course_space_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS edu_knowledge_point_draft (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_space_id BIGINT NOT NULL,
    unit_id BIGINT NULL,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(160) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    content_text TEXT NULL,
    PRIMARY KEY (id),
    KEY idx_point_draft_course (course_space_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 学生学习进度（章节已学/未学、最近学习位置）
CREATE TABLE IF NOT EXISTS edu_outline_progress (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_space_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    unit_id BIGINT NOT NULL,
    learned_at DATETIME(3) NULL,
    last_viewed_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_progress_user_unit (course_space_id, user_id, unit_id),
    KEY idx_progress_course_user (course_space_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
