-- Run in the existing project database. Safe to run again.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS edu_course_teacher (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_space_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    can_manage_students TINYINT(1) NOT NULL DEFAULT 0,
    can_export TINYINT(1) NOT NULL DEFAULT 0,
    joined_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ended_at DATETIME(3) NULL,
    active_user_id BIGINT GENERATED ALWAYS AS (IF(ended_at IS NULL, user_id, NULL)) STORED,
    added_by_user_id BIGINT NOT NULL,
    ended_by_user_id BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_course_teacher_active (course_space_id, active_user_id),
    KEY idx_course_teacher_user (user_id, ended_at, course_space_id),
    CONSTRAINT fk_course_teacher_course FOREIGN KEY (course_space_id) REFERENCES edu_course_space(id),
    CONSTRAINT chk_course_teacher_flags CHECK (can_manage_students IN (0, 1) AND can_export IN (0, 1)),
    CONSTRAINT chk_course_teacher_dates CHECK (ended_at IS NULL OR ended_at > joined_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
