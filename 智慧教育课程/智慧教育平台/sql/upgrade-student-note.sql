-- 学生个人笔记表（课程进度板）
-- 已有数据库执行一次；可重复执行（存储过程 + IF NOT EXISTS 防护）。
-- 全新环境请直接使用 install-fresh.sql（已包含本表）。

DROP PROCEDURE IF EXISTS upgrade_student_note;

DELIMITER $$
CREATE PROCEDURE upgrade_student_note()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'edu_student_note'
    ) THEN
        CREATE TABLE edu_student_note (
            id BIGINT NOT NULL AUTO_INCREMENT,
            binding_id BIGINT NOT NULL COMMENT '教学班绑定',
            user_id BIGINT NOT NULL COMMENT '学生用户',
            content TEXT NOT NULL COMMENT '笔记内容',
            updated_at DATETIME(3) NOT NULL COMMENT '更新时间(UTC)',
            PRIMARY KEY (id),
            UNIQUE KEY uk_student_note_binding_user (binding_id, user_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生个人笔记';
    END IF;
END$$
DELIMITER ;

CALL upgrade_student_note();
DROP PROCEDURE IF EXISTS upgrade_student_note;
