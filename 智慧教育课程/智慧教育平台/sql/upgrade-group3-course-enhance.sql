-- 第三组：课程建设增强（已有业务库执行一次；可重复执行）
-- 1) 题库版本增加难度 difficulty 与标签 tags，支持筛选与组卷；
-- 2) 资料增加下载开关、预览水印、视频任务点配置；
-- 3) 新增 edu_resource_progress 学生观看进度表。
-- 全新环境请直接使用 install-fresh.sql，其中已包含本脚本的全部结构。

DROP PROCEDURE IF EXISTS upgrade_group3_course_enhance;

DELIMITER $$
CREATE PROCEDURE upgrade_group3_course_enhance()
BEGIN
    -- 题库难度
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'edu_question_version' AND column_name = 'difficulty'
    ) THEN
        ALTER TABLE edu_question_version
            ADD COLUMN difficulty VARCHAR(16) NOT NULL DEFAULT 'MEDIUM'
                COMMENT '难度：EASY / MEDIUM / HARD' AFTER rubric_json;
    END IF;

    -- 题库标签
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'edu_question_version' AND column_name = 'tags'
    ) THEN
        ALTER TABLE edu_question_version
            ADD COLUMN tags VARCHAR(500) NULL COMMENT '标签，逗号分隔' AFTER difficulty;
    END IF;

    -- 题库筛选索引
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'edu_question_version'
          AND index_name = 'idx_edu_question_version_filter'
    ) THEN
        ALTER TABLE edu_question_version ADD KEY idx_edu_question_version_filter (question_id, difficulty);
    END IF;

    -- 题库难度 CHECK
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'edu_question_version'
          AND constraint_name = 'chk_edu_question_difficulty' AND constraint_type = 'CHECK'
    ) THEN
        ALTER TABLE edu_question_version ADD CONSTRAINT chk_edu_question_difficulty
            CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD'));
    END IF;

    -- 资料下载开关
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'edu_resource' AND column_name = 'download_enabled'
    ) THEN
        ALTER TABLE edu_resource
            ADD COLUMN download_enabled TINYINT(1) NOT NULL DEFAULT 1
                COMMENT '是否允许学生下载原文件：1 允许 / 0 禁止' AFTER status;
    END IF;

    -- 资料预览水印
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'edu_resource' AND column_name = 'watermark_text'
    ) THEN
        ALTER TABLE edu_resource
            ADD COLUMN watermark_text VARCHAR(200) NULL
                COMMENT '预览水印文字，为空表示不叠加水印' AFTER download_enabled;
    END IF;

    -- 视频任务点开关
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'edu_resource' AND column_name = 'task_enabled'
    ) THEN
        ALTER TABLE edu_resource
            ADD COLUMN task_enabled TINYINT(1) NOT NULL DEFAULT 0
                COMMENT '是否为视频任务点：1 是 / 0 否' AFTER watermark_text;
    END IF;

    -- 视频任务点要求观看秒数
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'edu_resource' AND column_name = 'task_required_seconds'
    ) THEN
        ALTER TABLE edu_resource
            ADD COLUMN task_required_seconds INT UNSIGNED NOT NULL DEFAULT 0
                COMMENT '任务点要求观看秒数，0 表示按视频总时长比例' AFTER task_enabled;
    END IF;

    -- 资料下载开关 CHECK
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'edu_resource'
          AND constraint_name = 'chk_edu_resource_download' AND constraint_type = 'CHECK'
    ) THEN
        ALTER TABLE edu_resource ADD CONSTRAINT chk_edu_resource_download CHECK (download_enabled IN (0, 1));
    END IF;

    -- 任务点开关 CHECK
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'edu_resource'
          AND constraint_name = 'chk_edu_resource_task_enabled' AND constraint_type = 'CHECK'
    ) THEN
        ALTER TABLE edu_resource ADD CONSTRAINT chk_edu_resource_task_enabled CHECK (task_enabled IN (0, 1));
    END IF;

    -- 学生观看进度表
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'edu_resource_progress'
    ) THEN
        CREATE TABLE edu_resource_progress (
            id BIGINT NOT NULL AUTO_INCREMENT,
            resource_id BIGINT NOT NULL,
            course_space_id BIGINT NOT NULL,
            binding_id BIGINT NOT NULL,
            user_id BIGINT NOT NULL,
            watched_seconds INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '累计观看秒数（服务端取较大值，防回退）',
            completed TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否达到任务点要求',
            completed_at DATETIME(3) NULL,
            created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
            PRIMARY KEY (id),
            UNIQUE KEY uq_edu_resource_progress (resource_id, binding_id, user_id),
            KEY idx_edu_resource_progress_space (course_space_id, user_id),
            CONSTRAINT fk_edu_resource_progress_resource FOREIGN KEY (resource_id)
                REFERENCES edu_resource (id),
            CONSTRAINT chk_edu_resource_progress_watched CHECK (watched_seconds >= 0),
            CONSTRAINT chk_edu_resource_progress_completed CHECK (completed IN (0, 1))
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
    END IF;
END$$
DELIMITER ;

CALL upgrade_group3_course_enhance();
DROP PROCEDURE IF EXISTS upgrade_group3_course_enhance;
