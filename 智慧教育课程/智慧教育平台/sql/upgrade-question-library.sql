-- 题目资源库（P1）：共享题目快照 + 引入来源标记
-- 可重复执行

DROP PROCEDURE IF EXISTS upgrade_question_library;

DELIMITER $$
CREATE PROCEDURE upgrade_question_library()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'edu_question_share'
    ) THEN
        CREATE TABLE edu_question_share (
            id BIGINT NOT NULL AUTO_INCREMENT,
            question_version_id BIGINT NOT NULL COMMENT '共享的题目版本快照',
            source_course_space_id BIGINT NOT NULL COMMENT '来源课程空间',
            shared_by_user_id BIGINT NOT NULL,
            scope VARCHAR(16) NOT NULL DEFAULT 'SCHOOL',
            status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
            import_count INT NOT NULL DEFAULT 0,
            shared_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            withdrawn_at DATETIME(3) NULL,
            PRIMARY KEY (id),
            UNIQUE KEY uk_question_share_version (question_version_id),
            KEY idx_question_share_status (status, shared_at),
            CONSTRAINT fk_question_share_version FOREIGN KEY (question_version_id)
                REFERENCES edu_question_version (id) ON DELETE CASCADE,
            CONSTRAINT fk_question_share_space FOREIGN KEY (source_course_space_id)
                REFERENCES edu_course_space (id),
            CONSTRAINT chk_question_share_status CHECK (status IN ('PUBLISHED', 'WITHDRAWN'))
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'edu_question'
          AND column_name = 'origin_share_id'
    ) THEN
        ALTER TABLE edu_question
            ADD COLUMN origin_share_id BIGINT NULL COMMENT '来自题目资源库的共享记录' AFTER folder_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'edu_question'
          AND index_name = 'idx_edu_question_origin_share'
    ) THEN
        ALTER TABLE edu_question ADD KEY idx_edu_question_origin_share (course_space_id, origin_share_id);
    END IF;
END$$
DELIMITER ;

CALL upgrade_question_library();
DROP PROCEDURE IF EXISTS upgrade_question_library;
