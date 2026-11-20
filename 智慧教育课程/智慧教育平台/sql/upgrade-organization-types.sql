-- Run on the existing project database. Adds metadata without rewriting organization records.
DROP PROCEDURE IF EXISTS smart_edu_upgrade_dept_type;
DELIMITER //
CREATE PROCEDURE smart_edu_upgrade_dept_type()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'sys_dept' AND column_name = 'dept_type'
    ) THEN
        ALTER TABLE sys_dept ADD COLUMN dept_type VARCHAR(16) NULL
            COMMENT '组织类型：SCHOOL/COLLEGE/MAJOR/CLASS/OTHER';
    END IF;
END//
CALL smart_edu_upgrade_dept_type()//
DROP PROCEDURE smart_edu_upgrade_dept_type//
DELIMITER ;

CREATE OR REPLACE SQL SECURITY INVOKER VIEW v_identity_dept AS
SELECT dept_id, parent_id, ancestors, dept_name, order_num, status, del_flag, dept_type
FROM sys_dept;
