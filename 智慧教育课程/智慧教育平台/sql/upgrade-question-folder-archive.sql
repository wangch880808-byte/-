-- 题库文件夹归档支持
-- 为 edu_question_folder 增加 archived_at 字段，可重复执行

DROP PROCEDURE IF EXISTS upgrade_question_folder_archive;

DELIMITER $$
CREATE PROCEDURE upgrade_question_folder_archive()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'edu_question_folder'
          AND column_name = 'archived_at'
    ) THEN
        ALTER TABLE edu_question_folder
            ADD COLUMN archived_at DATETIME(3) NULL COMMENT '归档时间，NULL 表示未归档' AFTER visibility;
    END IF;
END$$
DELIMITER ;

CALL upgrade_question_folder_archive();
DROP PROCEDURE IF EXISTS upgrade_question_folder_archive;
