-- 第 6 组学生公告/通知授权真实 MySQL 集成测试的精简 schema。
--
-- 来源（正式 DDL，本轮只抽取该链路实际读取的表/视图/约束）：
--   sql/mysql-v3-m0-core-schema.sql       edu_term / edu_official_course /
--                                        edu_course_space / edu_teaching_class /
--                                        edu_class_student / edu_course_class_binding
--   sql/mysql-v3-m0-teaching-schema.sql   edu_course_announcement / edu_user_notice
--   sql/upstream/ruoyi/ry_20260417.sql    sys_user（只保留 v_identity_user 读取的列）
--   sql/install-fresh.sql                 v_identity_user
--
-- 精简范围：不加载项目整套初始化脚本、演示数据、菜单、权限、其他业务表；
-- 保留本轮涉及的 DATETIME(3) 成员区间、CHECK、生成列和唯一约束，
-- 以及外键所必需的父表。不要对项目或生产数据库执行本文件。

CREATE TABLE sys_user (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    dept_id BIGINT NULL,
    user_name VARCHAR(30) NOT NULL,
    nick_name VARCHAR(30) NOT NULL,
    phonenumber VARCHAR(11) DEFAULT '',
    status CHAR(1) DEFAULT '0',
    del_flag CHAR(1) DEFAULT '0',
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 第 1 组公开只读身份契约；测试只需姓名/学号列。
CREATE OR REPLACE SQL SECURITY INVOKER VIEW v_identity_user AS
    SELECT user_id, user_name, nick_name, dept_id, phonenumber, status, del_flag
    FROM sys_user;

CREATE TABLE edu_term (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL,
    is_current TINYINT(1) NOT NULL DEFAULT 0,
    current_flag TINYINT GENERATED ALWAYS AS (IF(is_current = 1, 1, NULL)) STORED,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_term_id_org (id, organization_id),
    UNIQUE KEY uq_edu_term_org_code (organization_id, code),
    UNIQUE KEY uq_edu_term_single_current (current_flag),
    KEY idx_edu_term_org_dates (organization_id, starts_on, ends_on),
    CONSTRAINT chk_edu_term_dates CHECK (ends_on >= starts_on)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_official_course (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(160) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_official_course_id_org (id, organization_id),
    UNIQUE KEY uq_edu_official_course_org_code (organization_id, code),
    CONSTRAINT chk_edu_official_course_status
        CHECK (status IN ('ACTIVE', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_course_space (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    official_course_id BIGINT NULL,
    owner_user_id BIGINT NOT NULL,
    title VARCHAR(160) NOT NULL,
    summary TEXT NULL,
    learning_objectives TEXT NULL,
    cover_storage_key VARCHAR(512) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_course_space_id_org (id, organization_id),
    KEY idx_edu_course_space_owner_status (owner_user_id, status),
    KEY idx_edu_course_space_official_org (official_course_id, organization_id),
    CONSTRAINT fk_edu_course_space_official_org FOREIGN KEY (official_course_id,
        organization_id) REFERENCES edu_official_course (id, organization_id),
    CONSTRAINT chk_edu_course_space_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_teaching_class (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    term_id BIGINT NOT NULL,
    official_course_id BIGINT NULL,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(160) NOT NULL,
    class_kind VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_teaching_class_id_org (id, organization_id),
    UNIQUE KEY uq_edu_teaching_class_org_term_code (organization_id, term_id, code),
    KEY idx_edu_teaching_class_term_org (term_id, organization_id),
    KEY idx_edu_teaching_class_official_org (official_course_id, organization_id),
    CONSTRAINT fk_edu_teaching_class_term_org FOREIGN KEY (term_id, organization_id)
        REFERENCES edu_term (id, organization_id),
    CONSTRAINT fk_edu_teaching_class_official_org FOREIGN KEY (official_course_id,
        organization_id) REFERENCES edu_official_course (id, organization_id),
    CONSTRAINT chk_edu_teaching_class_kind CHECK (class_kind IN ('FORMAL', 'TRIAL')),
    CONSTRAINT chk_edu_teaching_class_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_class_student (
    id BIGINT NOT NULL AUTO_INCREMENT,
    teaching_class_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    joined_at DATETIME(3) NOT NULL,
    left_at DATETIME(3) NULL,
    active_user_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN left_at IS NULL THEN user_id ELSE NULL END) STORED,
    recorded_by_user_id BIGINT NOT NULL,
    left_by_user_id BIGINT NULL,
    leave_reason VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_class_student_id_class_user (id, teaching_class_id, user_id),
    UNIQUE KEY uq_edu_class_student_active (teaching_class_id, active_user_id),
    KEY idx_edu_class_student_user_dates (user_id, joined_at, left_at),
    CONSTRAINT fk_edu_class_student_class FOREIGN KEY (teaching_class_id)
        REFERENCES edu_teaching_class (id),
    CONSTRAINT chk_edu_class_student_dates CHECK (left_at IS NULL OR left_at > joined_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_course_class_binding (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    teaching_class_id BIGINT NOT NULL,
    course_space_id BIGINT NOT NULL,
    bound_by_user_id BIGINT NOT NULL,
    published_at DATETIME(3) NOT NULL,
    ended_at DATETIME(3) NULL,
    ended_by_user_id BIGINT NULL,
    end_reason VARCHAR(500) NULL,
    active_space_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN ended_at IS NULL THEN course_space_id ELSE NULL END) STORED,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_binding_id_class (id, teaching_class_id),
    UNIQUE KEY uq_edu_binding_id_space (id, course_space_id),
    UNIQUE KEY uq_edu_binding_active_pair (teaching_class_id, active_space_id),
    KEY idx_edu_binding_class_org (teaching_class_id, organization_id),
    KEY idx_edu_binding_space_org (course_space_id, organization_id),
    KEY idx_edu_binding_space_dates (course_space_id, published_at, ended_at),
    CONSTRAINT fk_edu_binding_class_org FOREIGN KEY (teaching_class_id, organization_id)
        REFERENCES edu_teaching_class (id, organization_id),
    CONSTRAINT fk_edu_binding_space_org FOREIGN KEY (course_space_id, organization_id)
        REFERENCES edu_course_space (id, organization_id),
    CONSTRAINT chk_edu_binding_dates CHECK (ended_at IS NULL OR ended_at > published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_course_announcement (
    id BIGINT NOT NULL AUTO_INCREMENT,
    binding_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    opens_at DATETIME(3) NULL,
    closes_at DATETIME(3) NULL,
    published_by_user_id BIGINT NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_edu_announcement_binding_window (binding_id, status, opens_at, closes_at),
    CONSTRAINT fk_edu_announcement_binding FOREIGN KEY (binding_id)
        REFERENCES edu_course_class_binding (id),
    CONSTRAINT chk_edu_announcement_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT chk_edu_announcement_window
        CHECK (opens_at IS NULL OR closes_at IS NULL OR closes_at > opens_at),
    CONSTRAINT chk_edu_announcement_publish
        CHECK ((status = 'DRAFT' AND published_by_user_id IS NULL)
            OR (status <> 'DRAFT' AND published_by_user_id IS NOT NULL
                AND opens_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_user_notice (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recipient_user_id BIGINT NOT NULL,
    binding_id BIGINT NULL,
    notice_kind VARCHAR(24) NOT NULL,
    source_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    visible_at DATETIME(3) NOT NULL,
    read_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_notice_recipient_source
        (recipient_user_id, notice_kind, source_id),
    KEY idx_edu_notice_recipient_read (recipient_user_id, read_at, visible_at),
    KEY idx_edu_notice_binding (binding_id),
    CONSTRAINT fk_edu_notice_binding FOREIGN KEY (binding_id)
        REFERENCES edu_course_class_binding (id),
    CONSTRAINT chk_edu_notice_kind CHECK (notice_kind IN
        ('ANNOUNCEMENT', 'PREVIEW', 'ACTIVITY', 'ASSIGNMENT', 'GRADE')),
    CONSTRAINT chk_edu_notice_read CHECK (read_at IS NULL OR read_at >= visible_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
