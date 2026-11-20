-- Execute in the existing project schema after upgrade-organization-types.sql.
-- Existing departments and student affiliations stay in place.
DROP PROCEDURE IF EXISTS smart_edu_upgrade_administrative_class;
DELIMITER //
CREATE PROCEDURE smart_edu_upgrade_administrative_class()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                   AND table_name = 'sys_dept' AND column_name = 'grade_year') THEN
        ALTER TABLE sys_dept ADD COLUMN grade_year SMALLINT UNSIGNED NULL COMMENT '行政班入学年份';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                   AND table_name = 'sys_dept' AND column_name = 'education_level') THEN
        ALTER TABLE sys_dept ADD COLUMN education_level VARCHAR(20) NULL COMMENT '行政班教育层次';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                   AND table_name = 'sys_dept' AND column_name = 'study_years') THEN
        ALTER TABLE sys_dept ADD COLUMN study_years TINYINT UNSIGNED NULL COMMENT '行政班学制年数';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                   AND table_name = 'sys_dept' AND column_name = 'class_remark') THEN
        ALTER TABLE sys_dept ADD COLUMN class_remark VARCHAR(500) NULL COMMENT '行政班备注';
    END IF;
END//
CALL smart_edu_upgrade_administrative_class()//
DROP PROCEDURE smart_edu_upgrade_administrative_class//
DELIMITER ;
