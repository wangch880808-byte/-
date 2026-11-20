-- 课程资料库 / 题库：文件夹持久化（已有数据库执行，可重复执行）
-- 全新环境请直接使用 install-fresh.sql，无需执行本文件。

DROP PROCEDURE IF EXISTS upgrade_group3_library_folders;

DELIMITER $$
CREATE PROCEDURE upgrade_group3_library_folders()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'edu_resource_folder'
    ) THEN
        CREATE TABLE edu_resource_folder (
            id BIGINT NOT NULL AUTO_INCREMENT,
            course_space_id BIGINT NOT NULL,
            name VARCHAR(60) NOT NULL,
            visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
            created_by_user_id BIGINT NOT NULL,
            created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            PRIMARY KEY (id),
            KEY idx_edu_resource_folder_space (course_space_id, id),
            CONSTRAINT fk_edu_resource_folder_space FOREIGN KEY (course_space_id)
                REFERENCES edu_course_space (id),
            CONSTRAINT chk_edu_resource_folder_visibility
                CHECK (visibility IN ('PUBLIC', 'PRIVATE', 'PARTIAL'))
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'edu_question_folder'
    ) THEN
        CREATE TABLE edu_question_folder (
            id BIGINT NOT NULL AUTO_INCREMENT,
            course_space_id BIGINT NOT NULL,
            name VARCHAR(60) NOT NULL,
            visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
            created_by_user_id BIGINT NOT NULL,
            created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            PRIMARY KEY (id),
            KEY idx_edu_question_folder_space (course_space_id, id),
            CONSTRAINT fk_edu_question_folder_space FOREIGN KEY (course_space_id)
                REFERENCES edu_course_space (id),
            CONSTRAINT chk_edu_question_folder_visibility
                CHECK (visibility IN ('PUBLIC', 'PRIVATE', 'PARTIAL'))
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'edu_resource' AND column_name = 'folder_id'
    ) THEN
        ALTER TABLE edu_resource ADD COLUMN folder_id BIGINT NULL AFTER course_space_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'edu_resource' AND index_name = 'idx_edu_resource_folder'
    ) THEN
        ALTER TABLE edu_resource ADD KEY idx_edu_resource_folder (course_space_id, folder_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'edu_resource'
          AND constraint_name = 'fk_edu_resource_folder' AND constraint_type = 'FOREIGN KEY'
    ) THEN
        ALTER TABLE edu_resource ADD CONSTRAINT fk_edu_resource_folder FOREIGN KEY (folder_id)
            REFERENCES edu_resource_folder (id) ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'edu_question' AND column_name = 'folder_id'
    ) THEN
        ALTER TABLE edu_question ADD COLUMN folder_id BIGINT NULL AFTER course_space_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'edu_question' AND index_name = 'idx_edu_question_folder'
    ) THEN
        ALTER TABLE edu_question ADD KEY idx_edu_question_folder (course_space_id, folder_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'edu_question'
          AND constraint_name = 'fk_edu_question_folder' AND constraint_type = 'FOREIGN KEY'
    ) THEN
        ALTER TABLE edu_question ADD CONSTRAINT fk_edu_question_folder FOREIGN KEY (folder_id)
            REFERENCES edu_question_folder (id) ON DELETE SET NULL;
    END IF;
END$$
DELIMITER ;

CALL upgrade_group3_library_folders();
DROP PROCEDURE IF EXISTS upgrade_group3_library_folders;
