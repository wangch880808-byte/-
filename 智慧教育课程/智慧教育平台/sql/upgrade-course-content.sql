-- 在当前项目使用的既有数据库中执行。可重复执行。
SET @edu_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='edu_course_space' AND column_name='deleted_at'), 'SELECT 1', 'ALTER TABLE edu_course_space ADD COLUMN deleted_at DATETIME(3) NULL');
PREPARE edu_stmt FROM @edu_ddl;
EXECUTE edu_stmt;
DEALLOCATE PREPARE edu_stmt;

SET @edu_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='edu_course_unit' AND column_name='content_text'), 'SELECT 1', 'ALTER TABLE edu_course_unit ADD COLUMN content_text TEXT NULL');
PREPARE edu_stmt FROM @edu_ddl;
EXECUTE edu_stmt;
DEALLOCATE PREPARE edu_stmt;

SET @edu_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='edu_knowledge_point' AND column_name='content_text'), 'SELECT 1', 'ALTER TABLE edu_knowledge_point ADD COLUMN content_text TEXT NULL');
PREPARE edu_stmt FROM @edu_ddl;
EXECUTE edu_stmt;
DEALLOCATE PREPARE edu_stmt;

CREATE TABLE IF NOT EXISTS edu_outline_attachment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_space_id BIGINT NOT NULL,
    unit_id BIGINT NULL,
    point_id BIGINT NULL,
    file_name VARCHAR(200) NOT NULL,
    file_size INT UNSIGNED NOT NULL,
    file_data MEDIUMBLOB NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_outline_attachment_unit (unit_id, course_space_id),
    KEY idx_outline_attachment_point (point_id, course_space_id),
    CONSTRAINT fk_outline_attachment_unit FOREIGN KEY (unit_id, course_space_id)
        REFERENCES edu_course_unit(id, course_space_id) ON DELETE CASCADE,
    CONSTRAINT fk_outline_attachment_point FOREIGN KEY (point_id, course_space_id)
        REFERENCES edu_knowledge_point(id, course_space_id) ON DELETE CASCADE,
    CONSTRAINT chk_outline_attachment_target CHECK ((unit_id IS NULL) <> (point_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
