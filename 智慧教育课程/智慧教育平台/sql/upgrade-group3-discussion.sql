-- 第三组讨论区：话题、楼中楼回复、讨论文件夹、点赞与讨论评分（已有数据库执行，可重复执行）
-- 全新环境请直接使用 install-fresh.sql，无需执行本文件。
-- 说明：讨论话题即课堂时间轴上的 DISCUSSION 环节；附件仅保存名称与地址，不提供上传接口。

CREATE TABLE IF NOT EXISTS edu_discussion_folder (
    id BIGINT NOT NULL AUTO_INCREMENT,
    binding_id BIGINT NOT NULL,
    course_space_id BIGINT NOT NULL,
    name VARCHAR(60) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_discussion_folder_id_binding (id, binding_id),
    KEY idx_edu_discussion_folder_binding_sort (binding_id, sort_order, id),
    CONSTRAINT fk_edu_discussion_folder_binding FOREIGN KEY (binding_id, course_space_id)
        REFERENCES edu_course_class_binding (id, course_space_id),
    CONSTRAINT chk_edu_discussion_folder_name CHECK (CHAR_LENGTH(TRIM(name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS edu_discussion_topic (
    id BIGINT NOT NULL AUTO_INCREMENT,
    binding_id BIGINT NOT NULL,
    teaching_class_id BIGINT NOT NULL,
    course_space_id BIGINT NOT NULL,
    lesson_id BIGINT NULL,
    folder_id BIGINT NULL,
    title VARCHAR(200) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    category VARCHAR(40) NULL,
    knowledge_point VARCHAR(200) NULL,
    publish_scope VARCHAR(16) NOT NULL DEFAULT 'CLASS',
    allow_anonymous TINYINT(1) NOT NULL DEFAULT 0,
    allow_attachment TINYINT(1) NOT NULL DEFAULT 0,
    allow_reply TINYINT(1) NOT NULL DEFAULT 1,
    allow_student_topic TINYINT(1) NOT NULL DEFAULT 0,
    score_enabled TINYINT(1) NOT NULL DEFAULT 0,
    score_max DECIMAL(10,2) NOT NULL DEFAULT 100.00,
    pinned TINYINT(1) NOT NULL DEFAULT 0,
    featured TINYINT(1) NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    opens_at DATETIME(3) NULL,
    closes_at DATETIME(3) NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    reply_count INT NOT NULL DEFAULT 0,
    like_count INT NOT NULL DEFAULT 0,
    view_count INT NOT NULL DEFAULT 0,
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_discussion_topic_id_binding (id, binding_id),
    KEY idx_edu_discussion_topic_binding_status (binding_id, status, pinned, updated_at),
    KEY idx_edu_discussion_topic_binding_folder (binding_id, folder_id),
    KEY idx_edu_discussion_topic_binding_lesson (binding_id, lesson_id),
    CONSTRAINT fk_edu_discussion_topic_binding FOREIGN KEY (binding_id, course_space_id)
        REFERENCES edu_course_class_binding (id, course_space_id),
    CONSTRAINT fk_edu_discussion_topic_binding_class FOREIGN KEY (binding_id, teaching_class_id)
        REFERENCES edu_course_class_binding (id, teaching_class_id),
    CONSTRAINT fk_edu_discussion_topic_lesson FOREIGN KEY (lesson_id, binding_id)
        REFERENCES edu_lesson (id, binding_id),
    CONSTRAINT fk_edu_discussion_topic_folder FOREIGN KEY (folder_id, binding_id)
        REFERENCES edu_discussion_folder (id, binding_id),
    CONSTRAINT chk_edu_discussion_topic_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT chk_edu_discussion_topic_times
        CHECK (closes_at IS NULL OR opens_at IS NULL OR closes_at > opens_at),
    CONSTRAINT chk_edu_discussion_topic_counts
        CHECK (pinned IN (0, 1) AND featured IN (0, 1)
               AND allow_anonymous IN (0, 1) AND allow_attachment IN (0, 1)
               AND allow_reply IN (0, 1) AND allow_student_topic IN (0, 1)
               AND score_enabled IN (0, 1)),
    CONSTRAINT chk_edu_discussion_topic_score CHECK (score_max >= 0),
    CONSTRAINT chk_edu_discussion_topic_counter_values
        CHECK (reply_count >= 0 AND like_count >= 0 AND view_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS edu_discussion_post (
    id BIGINT NOT NULL AUTO_INCREMENT,
    topic_id BIGINT NOT NULL,
    binding_id BIGINT NOT NULL,
    parent_post_id BIGINT NULL,
    user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    anonymous TINYINT(1) NOT NULL DEFAULT 0,
    attachment_name VARCHAR(255) NULL,
    attachment_url VARCHAR(1024) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    like_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_discussion_post_id_topic (id, topic_id),
    KEY idx_edu_discussion_post_topic_time (topic_id, created_at, id),
    KEY idx_edu_discussion_post_binding_user (binding_id, user_id),
    KEY idx_edu_discussion_post_parent (parent_post_id, topic_id),
    CONSTRAINT fk_edu_discussion_post_topic FOREIGN KEY (topic_id, binding_id)
        REFERENCES edu_discussion_topic (id, binding_id),
    CONSTRAINT fk_edu_discussion_post_parent FOREIGN KEY (parent_post_id, topic_id)
        REFERENCES edu_discussion_post (id, topic_id),
    CONSTRAINT chk_edu_discussion_post_status
        CHECK (status IN ('NORMAL', 'REMOVED')),
    CONSTRAINT chk_edu_discussion_post_flags CHECK (anonymous IN (0, 1)),
    CONSTRAINT chk_edu_discussion_post_like CHECK (like_count >= 0),
    CONSTRAINT chk_edu_discussion_post_attachment
        CHECK ((attachment_name IS NULL AND attachment_url IS NULL)
               OR (attachment_name IS NOT NULL AND attachment_url IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS edu_discussion_like (
    id BIGINT NOT NULL AUTO_INCREMENT,
    topic_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_discussion_like_target (topic_id, post_id, user_id),
    KEY idx_edu_discussion_like_user (user_id, topic_id),
    CONSTRAINT fk_edu_discussion_like_topic FOREIGN KEY (topic_id)
        REFERENCES edu_discussion_topic (id) ON DELETE CASCADE,
    CONSTRAINT chk_edu_discussion_like_post CHECK (post_id >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS edu_discussion_score (
    id BIGINT NOT NULL AUTO_INCREMENT,
    topic_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    score DECIMAL(10,2) NOT NULL,
    comment VARCHAR(500) NULL,
    graded_by_user_id BIGINT NOT NULL,
    graded_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_discussion_score_topic_user (topic_id, user_id),
    KEY idx_edu_discussion_score_user (user_id, topic_id),
    CONSTRAINT fk_edu_discussion_score_topic FOREIGN KEY (topic_id)
        REFERENCES edu_discussion_topic (id) ON DELETE CASCADE,
    CONSTRAINT chk_edu_discussion_score_value CHECK (score >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
