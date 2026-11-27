-- 教师公告授权与学生通知组合 MySQL 集成测试的补充 schema。
--
-- 在 notice-auth-minimal-schema.sql 之后加载；只补教师任课、课程团队和
-- 教师角色视图所必需的表/列，不加载项目整套初始化脚本或演示数据。
-- 不要对项目库或生产库执行本文件。

-- edu_course_teacher 的团队查询会过滤已逻辑删除的课程空间。
ALTER TABLE edu_course_space ADD COLUMN deleted_at DATETIME(3) NULL;

-- 来源：sql/install-fresh.sql 的 edu_class_teacher（保留教师授权段与发布位）。
CREATE TABLE edu_class_teacher (
    id BIGINT NOT NULL AUTO_INCREMENT,
    teaching_class_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    teacher_role VARCHAR(20) NOT NULL,
    can_publish TINYINT(1) NOT NULL DEFAULT 0,
    can_grade TINYINT(1) NOT NULL DEFAULT 0,
    can_export TINYINT(1) NOT NULL DEFAULT 0,
    valid_from DATETIME(3) NOT NULL,
    valid_to DATETIME(3) NULL,
    active_user_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN valid_to IS NULL THEN user_id ELSE NULL END) STORED,
    granted_by_user_id BIGINT NOT NULL,
    ended_by_user_id BIGINT NULL,
    end_reason VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_class_teacher_active (teaching_class_id, active_user_id),
    KEY idx_edu_class_teacher_user_dates (user_id, valid_from, valid_to),
    CONSTRAINT fk_edu_class_teacher_class FOREIGN KEY (teaching_class_id)
        REFERENCES edu_teaching_class (id),
    CONSTRAINT chk_edu_class_teacher_role CHECK (teacher_role IN ('LEAD', 'CO_TEACHER')),
    CONSTRAINT chk_edu_class_teacher_flags CHECK
        (can_publish IN (0, 1) AND can_grade IN (0, 1) AND can_export IN (0, 1)),
    CONSTRAINT chk_edu_class_teacher_dates CHECK (valid_to IS NULL OR valid_to > valid_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 来源：sql/install-fresh.sql 的 edu_course_teacher（团队 fallback 所需）。
CREATE TABLE edu_course_teacher (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_space_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    team_role VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
    permissions VARCHAR(255) NOT NULL DEFAULT '',
    can_manage_students TINYINT(1) NOT NULL DEFAULT 0,
    can_export TINYINT(1) NOT NULL DEFAULT 0,
    joined_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ended_at DATETIME(3) NULL,
    active_user_id BIGINT GENERATED ALWAYS AS
        (IF(ended_at IS NULL, user_id, NULL)) STORED,
    added_by_user_id BIGINT NOT NULL,
    ended_by_user_id BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_course_teacher_active (course_space_id, active_user_id),
    KEY idx_course_teacher_user (user_id, ended_at, course_space_id),
    CONSTRAINT fk_course_teacher_course FOREIGN KEY (course_space_id)
        REFERENCES edu_course_space (id),
    CONSTRAINT chk_course_teacher_flags CHECK
        (can_manage_students IN (0, 1) AND can_export IN (0, 1)),
    CONSTRAINT chk_course_teacher_dates CHECK (ended_at IS NULL OR ended_at > joined_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 精简角色表：只保留 v_identity_user_role / v_identity_role 读取的列。
CREATE TABLE sys_role (
    role_id BIGINT NOT NULL AUTO_INCREMENT,
    role_name VARCHAR(30) NOT NULL,
    role_key VARCHAR(100) NOT NULL,
    role_sort INT NOT NULL DEFAULT 0,
    status CHAR(1) NOT NULL DEFAULT '0',
    del_flag CHAR(1) NOT NULL DEFAULT '0',
    PRIMARY KEY (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE OR REPLACE SQL SECURITY INVOKER VIEW v_identity_user_role AS
    SELECT user_id, role_id FROM sys_user_role;

CREATE OR REPLACE SQL SECURITY INVOKER VIEW v_identity_role AS
    SELECT role_id, role_key, status, del_flag FROM sys_role;
