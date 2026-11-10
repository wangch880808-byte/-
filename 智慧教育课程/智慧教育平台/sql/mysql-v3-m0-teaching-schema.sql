-- V3.0 M0 education baseline, part 2 of 2.
-- Run after mysql-v3-m0-core-schema.sql in the same empty MySQL 8.0.16+ database.
-- IDs pointing to RuoYi users and organizations are logical references until integration.
-- All DATETIME(3) values are UTC. Content versions and published history are append-only in services.

-- course: one planned lesson belongs to a single class/space binding.
CREATE TABLE edu_lesson (
    id BIGINT NOT NULL AUTO_INCREMENT,
    binding_id BIGINT NOT NULL,
    course_space_id BIGINT NOT NULL,
    unit_id BIGINT NULL,
    title VARCHAR(160) NOT NULL,
    scheduled_at DATETIME(3) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PLANNED',
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_lesson_id_binding (id, binding_id),
    KEY idx_edu_lesson_binding_schedule (binding_id, scheduled_at),
    KEY idx_edu_lesson_binding_space (binding_id, course_space_id),
    KEY idx_edu_lesson_unit_space (unit_id, course_space_id),
    CONSTRAINT fk_edu_lesson_binding FOREIGN KEY (binding_id, course_space_id)
        REFERENCES edu_course_class_binding (id, course_space_id),
    CONSTRAINT fk_edu_lesson_unit FOREIGN KEY (unit_id, course_space_id)
        REFERENCES edu_course_unit (id, course_space_id),
    CONSTRAINT chk_edu_lesson_status CHECK (status IN ('PLANNED', 'ACTIVE', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- resource: page conversion status is independent of upload success.
CREATE TABLE edu_resource_page (
    id BIGINT NOT NULL AUTO_INCREMENT,
    resource_version_id BIGINT NOT NULL,
    resource_kind VARCHAR(16) NOT NULL DEFAULT 'FILE',
    page_no INT UNSIGNED NOT NULL,
    preview_storage_key VARCHAR(512) NOT NULL,
    width_px INT UNSIGNED NULL,
    height_px INT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_resource_page_version_no (resource_version_id, page_no),
    KEY idx_edu_resource_page_version_kind (resource_version_id, resource_kind),
    CONSTRAINT fk_edu_resource_page_version FOREIGN KEY (resource_version_id, resource_kind)
        REFERENCES edu_resource_version (id, resource_kind),
    CONSTRAINT chk_edu_resource_page_kind CHECK (resource_kind = 'FILE'),
    CONSTRAINT chk_edu_resource_page_no CHECK (page_no > 0),
    CONSTRAINT chk_edu_resource_page_key CHECK (CHAR_LENGTH(TRIM(preview_storage_key)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_question (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_space_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_question_id_space (id, course_space_id),
    KEY idx_edu_question_space_status (course_space_id, status),
    CONSTRAINT fk_edu_question_space FOREIGN KEY (course_space_id)
        REFERENCES edu_course_space (id),
    CONSTRAINT chk_edu_question_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_question_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    question_kind VARCHAR(20) NOT NULL,
    stem TEXT NOT NULL,
    options_json JSON NULL,
    answer_json JSON NULL,
    explanation TEXT NULL,
    rubric_json JSON NULL,
    max_score DECIMAL(10,2) NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_question_version_id_question (id, question_id),
    UNIQUE KEY uq_edu_question_version_no (question_id, version_no),
    CONSTRAINT fk_edu_question_version_question FOREIGN KEY (question_id)
        REFERENCES edu_question (id),
    CONSTRAINT chk_edu_question_version_no CHECK (version_no > 0),
    CONSTRAINT chk_edu_question_kind CHECK
        (question_kind IN ('SINGLE', 'MULTIPLE', 'TRUE_FALSE', 'SHORT_TEXT')),
    CONSTRAINT chk_edu_question_score CHECK (max_score >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_question_knowledge_point (
    question_id BIGINT NOT NULL,
    course_space_id BIGINT NOT NULL,
    knowledge_point_id BIGINT NOT NULL,
    PRIMARY KEY (question_id, knowledge_point_id),
    KEY idx_edu_qkp_point_space (knowledge_point_id, course_space_id),
    CONSTRAINT fk_edu_qkp_question FOREIGN KEY (question_id, course_space_id)
        REFERENCES edu_question (id, course_space_id),
    CONSTRAINT fk_edu_qkp_point FOREIGN KEY (knowledge_point_id, course_space_id)
        REFERENCES edu_knowledge_point (id, course_space_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- prepare: private plan and template never confer student access by themselves.
CREATE TABLE edu_lesson_plan (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lesson_id BIGINT NOT NULL,
    binding_id BIGINT NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    title VARCHAR(160) NOT NULL,
    private_notes TEXT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_plan_id_binding (id, binding_id),
    UNIQUE KEY uq_edu_plan_id_lesson_binding (id, lesson_id, binding_id),
    UNIQUE KEY uq_edu_plan_lesson_version (lesson_id, version_no),
    KEY idx_edu_plan_lesson_binding (lesson_id, binding_id),
    CONSTRAINT fk_edu_plan_lesson FOREIGN KEY (lesson_id, binding_id)
        REFERENCES edu_lesson (id, binding_id),
    CONSTRAINT chk_edu_plan_version CHECK (version_no > 0),
    CONSTRAINT chk_edu_plan_status CHECK (status IN ('DRAFT', 'READY', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_plan_material (
    id BIGINT NOT NULL AUTO_INCREMENT,
    plan_id BIGINT NOT NULL,
    binding_id BIGINT NOT NULL,
    course_space_id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    resource_version_id BIGINT NOT NULL,
    sort_order INT UNSIGNED NOT NULL,
    private_notes TEXT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_plan_material_order (plan_id, sort_order),
    KEY idx_edu_plan_material_plan_binding (plan_id, binding_id),
    KEY idx_edu_plan_material_binding_space (binding_id, course_space_id),
    KEY idx_edu_plan_material_resource_space (resource_id, course_space_id),
    KEY idx_edu_plan_material_version_resource (resource_version_id, resource_id),
    CONSTRAINT fk_edu_plan_material_plan FOREIGN KEY (plan_id, binding_id)
        REFERENCES edu_lesson_plan (id, binding_id),
    CONSTRAINT fk_edu_plan_material_binding FOREIGN KEY (binding_id, course_space_id)
        REFERENCES edu_course_class_binding (id, course_space_id),
    CONSTRAINT fk_edu_plan_material_resource FOREIGN KEY (resource_id, course_space_id)
        REFERENCES edu_resource (id, course_space_id),
    CONSTRAINT fk_edu_plan_material_version FOREIGN KEY (resource_version_id, resource_id)
        REFERENCES edu_resource_version (id, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_activity_template (
    id BIGINT NOT NULL AUTO_INCREMENT,
    plan_id BIGINT NULL,
    binding_id BIGINT NOT NULL,
    course_space_id BIGINT NOT NULL,
    template_kind VARCHAR(16) NOT NULL,
    title VARCHAR(160) NOT NULL,
    question_id BIGINT NULL,
    question_version_id BIGINT NULL,
    duration_seconds INT UNSIGNED NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_edu_template_plan_binding (plan_id, binding_id),
    KEY idx_edu_template_binding_space (binding_id, course_space_id),
    KEY idx_edu_template_question_space (question_id, course_space_id),
    KEY idx_edu_template_question_version (question_version_id, question_id),
    UNIQUE KEY uq_edu_template_id_binding (id, binding_id),
    UNIQUE KEY uq_edu_template_id_binding_kind (id, binding_id, template_kind),
    CONSTRAINT fk_edu_template_plan FOREIGN KEY (plan_id, binding_id)
        REFERENCES edu_lesson_plan (id, binding_id),
    CONSTRAINT fk_edu_template_binding FOREIGN KEY (binding_id, course_space_id)
        REFERENCES edu_course_class_binding (id, course_space_id),
    CONSTRAINT fk_edu_template_question_space FOREIGN KEY (question_id, course_space_id)
        REFERENCES edu_question (id, course_space_id),
    CONSTRAINT fk_edu_template_question FOREIGN KEY (question_version_id, question_id)
        REFERENCES edu_question_version (id, question_id),
    CONSTRAINT chk_edu_template_kind CHECK (template_kind IN ('ATTENDANCE', 'QUIZ', 'CLASSWORK')),
    CONSTRAINT chk_edu_template_question CHECK
        ((template_kind = 'QUIZ' AND question_id IS NOT NULL AND question_version_id IS NOT NULL) OR
         (template_kind <> 'QUIZ' AND question_id IS NULL AND question_version_id IS NULL)),
    CONSTRAINT chk_edu_template_status CHECK (status IN ('DRAFT', 'READY', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- classroom: snapshot sequence and ordered events support reconnection.
CREATE TABLE edu_class_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    binding_id BIGINT NOT NULL,
    lesson_id BIGINT NULL,
    plan_id BIGINT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PLANNED',
    active_binding_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status IN ('RUNNING', 'PAUSED') THEN binding_id ELSE NULL END) STORED,
    started_by_user_id BIGINT NULL,
    started_at DATETIME(3) NULL,
    ended_by_user_id BIGINT NULL,
    ended_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_session_id_binding (id, binding_id),
    UNIQUE KEY uq_edu_session_active_binding (active_binding_id),
    KEY idx_edu_session_binding_status (binding_id, status, id),
    KEY idx_edu_session_lesson_binding (lesson_id, binding_id),
    KEY idx_edu_session_plan_lesson_binding (plan_id, lesson_id, binding_id),
    CONSTRAINT fk_edu_session_binding FOREIGN KEY (binding_id)
        REFERENCES edu_course_class_binding (id),
    CONSTRAINT fk_edu_session_lesson FOREIGN KEY (lesson_id, binding_id)
        REFERENCES edu_lesson (id, binding_id),
    CONSTRAINT fk_edu_session_plan FOREIGN KEY (plan_id, lesson_id, binding_id)
        REFERENCES edu_lesson_plan (id, lesson_id, binding_id),
    CONSTRAINT chk_edu_session_status CHECK (status IN ('PLANNED', 'RUNNING', 'PAUSED', 'ENDED')),
    CONSTRAINT chk_edu_session_times CHECK
        ((status = 'PLANNED' AND started_at IS NULL AND ended_at IS NULL) OR
         (status IN ('RUNNING', 'PAUSED') AND started_at IS NOT NULL AND ended_at IS NULL) OR
         (status = 'ENDED' AND started_at IS NOT NULL AND ended_at >= started_at)),
    CONSTRAINT chk_edu_session_plan_lesson CHECK (plan_id IS NULL OR lesson_id IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_session_projection (
    session_id BIGINT NOT NULL,
    binding_id BIGINT NOT NULL,
    state_seq BIGINT UNSIGNED NOT NULL DEFAULT 0,
    release_id BIGINT NULL,
    resource_version_id BIGINT NULL,
    page_no INT UNSIGNED NULL,
    student_can_browse TINYINT(1) NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (session_id),
    KEY idx_edu_projection_session_binding (session_id, binding_id),
    KEY idx_edu_projection_release_binding (release_id, binding_id),
    KEY idx_edu_projection_release_version (release_id, resource_version_id),
    KEY idx_edu_projection_version_page (resource_version_id, page_no),
    CONSTRAINT fk_edu_projection_session FOREIGN KEY (session_id, binding_id)
        REFERENCES edu_class_session (id, binding_id),
    CONSTRAINT fk_edu_projection_release_binding FOREIGN KEY (release_id, binding_id)
        REFERENCES edu_resource_class_release (id, binding_id),
    CONSTRAINT fk_edu_projection_release_version FOREIGN KEY (release_id, resource_version_id)
        REFERENCES edu_resource_class_release (id, resource_version_id),
    CONSTRAINT fk_edu_projection_page FOREIGN KEY (resource_version_id, page_no)
        REFERENCES edu_resource_page (resource_version_id, page_no),
    CONSTRAINT chk_edu_projection_fields CHECK
        ((release_id IS NULL AND resource_version_id IS NULL AND page_no IS NULL) OR
         (release_id IS NOT NULL AND resource_version_id IS NOT NULL AND page_no IS NOT NULL)),
    CONSTRAINT chk_edu_projection_browse CHECK (student_can_browse IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_session_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    seq_no BIGINT UNSIGNED NOT NULL,
    event_kind VARCHAR(24) NOT NULL,
    visibility VARCHAR(16) NOT NULL,
    public_payload_json JSON NULL,
    actor_user_id BIGINT NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_session_event_seq (session_id, seq_no),
    CONSTRAINT fk_edu_session_event_session FOREIGN KEY (session_id)
        REFERENCES edu_class_session (id),
    CONSTRAINT chk_edu_session_event_visibility CHECK (visibility IN ('PUBLIC', 'TEACHER')),
    CONSTRAINT chk_edu_session_event_payload CHECK
        (visibility = 'PUBLIC' OR public_payload_json IS NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- activity: standalone or classroom attendance/quiz, with immutable target snapshot.
CREATE TABLE edu_activity_instance (
    id BIGINT NOT NULL AUTO_INCREMENT,
    binding_id BIGINT NOT NULL,
    teaching_class_id BIGINT NOT NULL,
    course_space_id BIGINT NOT NULL,
    session_id BIGINT NULL,
    template_id BIGINT NULL,
    activity_kind VARCHAR(16) NOT NULL,
    title VARCHAR(160) NOT NULL,
    question_id BIGINT NULL,
    question_version_id BIGINT NULL,
    opens_at DATETIME(3) NOT NULL,
    closes_at DATETIME(3) NOT NULL,
    answer_opens_at DATETIME(3) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    active_kind VARCHAR(16) GENERATED ALWAYS AS
        (CASE WHEN status = 'OPEN' THEN activity_kind ELSE NULL END) STORED,
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_activity_id_binding (id, binding_id),
    UNIQUE KEY uq_edu_activity_id_class (id, teaching_class_id),
    UNIQUE KEY uq_edu_activity_active_kind (binding_id, active_kind),
    KEY idx_edu_activity_session_binding (session_id, binding_id),
    KEY idx_edu_activity_binding_class (binding_id, teaching_class_id),
    KEY idx_edu_activity_binding_space (binding_id, course_space_id),
    KEY idx_edu_activity_template_binding_kind (template_id, binding_id, activity_kind),
    KEY idx_edu_activity_question_space (question_id, course_space_id),
    KEY idx_edu_activity_question_version (question_version_id, question_id),
    KEY idx_edu_activity_binding_status (binding_id, status, opens_at),
    CONSTRAINT fk_edu_activity_binding_class FOREIGN KEY (binding_id, teaching_class_id)
        REFERENCES edu_course_class_binding (id, teaching_class_id),
    CONSTRAINT fk_edu_activity_binding_space FOREIGN KEY (binding_id, course_space_id)
        REFERENCES edu_course_class_binding (id, course_space_id),
    CONSTRAINT fk_edu_activity_session FOREIGN KEY (session_id, binding_id)
        REFERENCES edu_class_session (id, binding_id),
    CONSTRAINT fk_edu_activity_template FOREIGN KEY (template_id, binding_id, activity_kind)
        REFERENCES edu_activity_template (id, binding_id, template_kind),
    CONSTRAINT fk_edu_activity_question_space FOREIGN KEY (question_id, course_space_id)
        REFERENCES edu_question (id, course_space_id),
    CONSTRAINT fk_edu_activity_question FOREIGN KEY (question_version_id, question_id)
        REFERENCES edu_question_version (id, question_id),
    CONSTRAINT chk_edu_activity_kind CHECK (activity_kind IN ('ATTENDANCE', 'QUIZ')),
    CONSTRAINT chk_edu_activity_question CHECK
        ((activity_kind = 'QUIZ' AND question_id IS NOT NULL AND question_version_id IS NOT NULL) OR
         (activity_kind = 'ATTENDANCE' AND question_id IS NULL AND question_version_id IS NULL)),
    CONSTRAINT chk_edu_activity_status CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED', 'ARCHIVED')),
    CONSTRAINT chk_edu_activity_times CHECK
        (closes_at > opens_at AND (answer_opens_at IS NULL OR answer_opens_at >= closes_at))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_activity_target (
    id BIGINT NOT NULL AUTO_INCREMENT,
    activity_id BIGINT NOT NULL,
    teaching_class_id BIGINT NOT NULL,
    student_user_id BIGINT NOT NULL,
    enrollment_id BIGINT NOT NULL,
    target_status VARCHAR(16) NOT NULL DEFAULT 'REQUIRED',
    adjusted_by_user_id BIGINT NULL,
    adjustment_reason VARCHAR(500) NULL,
    captured_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_activity_target_student (activity_id, student_user_id),
    UNIQUE KEY uq_edu_activity_target_id_activity (id, activity_id),
    KEY idx_edu_activity_target_activity_class (activity_id, teaching_class_id),
    KEY idx_edu_activity_target_enrollment (enrollment_id, teaching_class_id, student_user_id),
    CONSTRAINT fk_edu_activity_target_activity FOREIGN KEY (activity_id, teaching_class_id)
        REFERENCES edu_activity_instance (id, teaching_class_id),
    CONSTRAINT fk_edu_activity_target_enrollment FOREIGN KEY
        (enrollment_id, teaching_class_id, student_user_id)
        REFERENCES edu_class_student (id, teaching_class_id, user_id),
    CONSTRAINT chk_edu_activity_target_status CHECK
        (target_status IN ('REQUIRED', 'EXEMPT', 'REMOVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_activity_response (
    id BIGINT NOT NULL AUTO_INCREMENT,
    activity_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    accepted_at DATETIME(3) NOT NULL,
    response_state VARCHAR(16) NOT NULL,
    current_revision_no INT UNSIGNED NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_activity_response_target (target_id),
    UNIQUE KEY uq_edu_activity_response_id_activity (id, activity_id),
    KEY idx_edu_activity_response_target_activity (target_id, activity_id),
    CONSTRAINT fk_edu_activity_response_target FOREIGN KEY (target_id, activity_id)
        REFERENCES edu_activity_target (id, activity_id),
    CONSTRAINT chk_edu_activity_response_state CHECK
        (response_state IN ('ON_TIME', 'LATE', 'CORRECTED')),
    CONSTRAINT chk_edu_activity_response_revision CHECK (current_revision_no > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_activity_response_revision (
    id BIGINT NOT NULL AUTO_INCREMENT,
    response_id BIGINT NOT NULL,
    revision_no INT UNSIGNED NOT NULL,
    answer_json JSON NULL,
    is_correct TINYINT(1) NULL,
    recorded_by_user_id BIGINT NOT NULL,
    recorded_at DATETIME(3) NOT NULL,
    reason VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_activity_revision_no (response_id, revision_no),
    CONSTRAINT fk_edu_activity_revision_response FOREIGN KEY (response_id)
        REFERENCES edu_activity_response (id),
    CONSTRAINT chk_edu_activity_revision_no CHECK (revision_no > 0),
    CONSTRAINT chk_edu_activity_revision_correct CHECK (is_correct IS NULL OR is_correct IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- assignment: preview, classroom and homework share one submission/grade path.
CREATE TABLE edu_assignment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    binding_id BIGINT NOT NULL,
    teaching_class_id BIGINT NOT NULL,
    course_space_id BIGINT NOT NULL,
    lesson_id BIGINT NULL,
    session_id BIGINT NULL,
    assignment_kind VARCHAR(16) NOT NULL,
    title VARCHAR(200) NOT NULL,
    instructions TEXT NULL,
    opens_at DATETIME(3) NOT NULL,
    closes_at DATETIME(3) NOT NULL,
    max_submissions INT UNSIGNED NOT NULL DEFAULT 1,
    late_allowed TINYINT(1) NOT NULL DEFAULT 0,
    answer_opens_at DATETIME(3) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    published_at DATETIME(3) NULL,
    published_by_user_id BIGINT NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_assignment_id_binding (id, binding_id),
    UNIQUE KEY uq_edu_assignment_id_class (id, teaching_class_id),
    UNIQUE KEY uq_edu_assignment_id_space (id, course_space_id),
    KEY idx_edu_assignment_binding_space (binding_id, course_space_id),
    KEY idx_edu_assignment_binding_class (binding_id, teaching_class_id),
    KEY idx_edu_assignment_lesson_binding (lesson_id, binding_id),
    KEY idx_edu_assignment_session_binding (session_id, binding_id),
    KEY idx_edu_assignment_binding_status (binding_id, status, closes_at),
    CONSTRAINT fk_edu_assignment_binding FOREIGN KEY (binding_id, course_space_id)
        REFERENCES edu_course_class_binding (id, course_space_id),
    CONSTRAINT fk_edu_assignment_binding_class FOREIGN KEY (binding_id, teaching_class_id)
        REFERENCES edu_course_class_binding (id, teaching_class_id),
    CONSTRAINT fk_edu_assignment_lesson FOREIGN KEY (lesson_id, binding_id)
        REFERENCES edu_lesson (id, binding_id),
    CONSTRAINT fk_edu_assignment_session FOREIGN KEY (session_id, binding_id)
        REFERENCES edu_class_session (id, binding_id),
    CONSTRAINT chk_edu_assignment_kind CHECK
        (assignment_kind IN ('PREVIEW', 'CLASSWORK', 'HOMEWORK')),
    CONSTRAINT chk_edu_assignment_status CHECK
        (status IN ('DRAFT', 'PUBLISHED', 'CLOSED', 'ARCHIVED')),
    CONSTRAINT chk_edu_assignment_times CHECK
        (closes_at > opens_at AND (answer_opens_at IS NULL OR answer_opens_at >= closes_at)),
    CONSTRAINT chk_edu_assignment_rules CHECK (max_submissions > 0 AND late_allowed IN (0, 1)),
    CONSTRAINT chk_edu_assignment_publish CHECK
        ((status = 'DRAFT' AND published_at IS NULL AND published_by_user_id IS NULL) OR
         (status <> 'DRAFT' AND published_at IS NOT NULL AND published_by_user_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_assignment_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    course_space_id BIGINT NOT NULL,
    sort_order INT UNSIGNED NOT NULL,
    item_kind VARCHAR(16) NOT NULL,
    prompt TEXT NOT NULL,
    question_id BIGINT NULL,
    question_version_id BIGINT NULL,
    max_score DECIMAL(10,2) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_assignment_item_id_assignment (id, assignment_id),
    UNIQUE KEY uq_edu_assignment_item_id_question (id, question_id, question_version_id),
    UNIQUE KEY uq_edu_assignment_item_order (assignment_id, sort_order),
    KEY idx_edu_assignment_item_assignment_space (assignment_id, course_space_id),
    KEY idx_edu_assignment_item_question_space (question_id, course_space_id),
    KEY idx_edu_assignment_item_question_version (question_version_id, question_id),
    CONSTRAINT fk_edu_assignment_item_assignment FOREIGN KEY (assignment_id, course_space_id)
        REFERENCES edu_assignment (id, course_space_id),
    CONSTRAINT fk_edu_assignment_item_question_space FOREIGN KEY (question_id, course_space_id)
        REFERENCES edu_question (id, course_space_id),
    CONSTRAINT fk_edu_assignment_item_question_version FOREIGN KEY (question_version_id, question_id)
        REFERENCES edu_question_version (id, question_id),
    CONSTRAINT chk_edu_assignment_item_kind CHECK (item_kind IN ('QUESTION', 'TEXT', 'FILE')),
    CONSTRAINT chk_edu_assignment_item_question CHECK
        ((item_kind = 'QUESTION' AND question_id IS NOT NULL AND question_version_id IS NOT NULL) OR
         (item_kind <> 'QUESTION' AND question_id IS NULL AND question_version_id IS NULL)),
    CONSTRAINT chk_edu_assignment_item_score CHECK (max_score >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_assignment_target (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    teaching_class_id BIGINT NOT NULL,
    student_user_id BIGINT NOT NULL,
    enrollment_id BIGINT NOT NULL,
    target_status VARCHAR(16) NOT NULL DEFAULT 'REQUIRED',
    adjusted_by_user_id BIGINT NULL,
    adjustment_reason VARCHAR(500) NULL,
    captured_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_assignment_target_student (assignment_id, student_user_id),
    UNIQUE KEY uq_edu_assignment_target_id_assignment (id, assignment_id),
    UNIQUE KEY uq_edu_assignment_target_id_student (id, student_user_id),
    KEY idx_edu_assignment_target_assignment_class (assignment_id, teaching_class_id),
    KEY idx_edu_assignment_target_enrollment (enrollment_id, teaching_class_id, student_user_id),
    CONSTRAINT fk_edu_assignment_target_assignment FOREIGN KEY (assignment_id, teaching_class_id)
        REFERENCES edu_assignment (id, teaching_class_id),
    CONSTRAINT fk_edu_assignment_target_enrollment FOREIGN KEY
        (enrollment_id, teaching_class_id, student_user_id)
        REFERENCES edu_class_student (id, teaching_class_id, user_id),
    CONSTRAINT chk_edu_assignment_target_status CHECK
        (target_status IN ('REQUIRED', 'EXEMPT', 'REMOVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_assignment_submission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_submission_target (target_id),
    UNIQUE KEY uq_edu_submission_id_assignment (id, assignment_id),
    UNIQUE KEY uq_edu_submission_id_target (id, target_id),
    KEY idx_edu_submission_target_assignment (target_id, assignment_id),
    CONSTRAINT fk_edu_submission_target FOREIGN KEY (target_id, assignment_id)
        REFERENCES edu_assignment_target (id, assignment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_submission_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    submission_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    submitted_at DATETIME(3) NOT NULL,
    is_late TINYINT(1) NOT NULL DEFAULT 0,
    superseded_at DATETIME(3) NULL,
    active_submission_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN superseded_at IS NULL THEN submission_id ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_submission_version_id_submission (id, submission_id),
    UNIQUE KEY uq_edu_submission_version_id_assignment (id, assignment_id),
    UNIQUE KEY uq_edu_submission_version_no (submission_id, version_no),
    UNIQUE KEY uq_edu_submission_version_active (active_submission_id),
    KEY idx_edu_submission_version_submission_assignment (submission_id, assignment_id),
    CONSTRAINT fk_edu_submission_version_submission FOREIGN KEY (submission_id, assignment_id)
        REFERENCES edu_assignment_submission (id, assignment_id),
    CONSTRAINT chk_edu_submission_version_no CHECK (version_no > 0),
    CONSTRAINT chk_edu_submission_version_late CHECK (is_late IN (0, 1)),
    CONSTRAINT chk_edu_submission_version_dates CHECK
        (superseded_at IS NULL OR superseded_at >= submitted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_submission_answer (
    id BIGINT NOT NULL AUTO_INCREMENT,
    submission_version_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    assignment_item_id BIGINT NOT NULL,
    answer_json JSON NULL,
    attachment_storage_key VARCHAR(512) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_submission_answer_item (submission_version_id, assignment_item_id),
    KEY idx_edu_submission_answer_version_assignment (submission_version_id, assignment_id),
    KEY idx_edu_submission_answer_item_assignment (assignment_item_id, assignment_id),
    CONSTRAINT fk_edu_submission_answer_version FOREIGN KEY (submission_version_id, assignment_id)
        REFERENCES edu_submission_version (id, assignment_id),
    CONSTRAINT fk_edu_submission_answer_item FOREIGN KEY (assignment_item_id, assignment_id)
        REFERENCES edu_assignment_item (id, assignment_id),
    CONSTRAINT chk_edu_submission_answer_content CHECK
        (answer_json IS NOT NULL OR attachment_storage_key IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_grade_revision (
    id BIGINT NOT NULL AUTO_INCREMENT,
    submission_version_id BIGINT NOT NULL,
    revision_no INT UNSIGNED NOT NULL,
    score DECIMAL(10,2) NOT NULL,
    max_score DECIMAL(10,2) NOT NULL,
    feedback TEXT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    graded_by_user_id BIGINT NOT NULL,
    confirmed_by_user_id BIGINT NULL,
    confirmed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_grade_id_version_status (id, submission_version_id, status),
    UNIQUE KEY uq_edu_grade_version_revision (submission_version_id, revision_no),
    CONSTRAINT fk_edu_grade_submission_version FOREIGN KEY (submission_version_id)
        REFERENCES edu_submission_version (id),
    CONSTRAINT chk_edu_grade_revision_no CHECK (revision_no > 0),
    CONSTRAINT chk_edu_grade_score CHECK (score >= 0 AND max_score >= 0 AND score <= max_score),
    CONSTRAINT chk_edu_grade_status CHECK (status IN ('DRAFT', 'CONFIRMED')),
    CONSTRAINT chk_edu_grade_confirmation CHECK
        ((status = 'DRAFT' AND confirmed_by_user_id IS NULL AND confirmed_at IS NULL) OR
         (status = 'CONFIRMED' AND confirmed_by_user_id IS NOT NULL AND confirmed_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_grade_publication (
    id BIGINT NOT NULL AUTO_INCREMENT,
    submission_id BIGINT NOT NULL,
    submission_version_id BIGINT NOT NULL,
    grade_revision_id BIGINT NOT NULL,
    required_grade_status VARCHAR(16) NOT NULL DEFAULT 'CONFIRMED',
    publication_no INT UNSIGNED NOT NULL,
    published_by_user_id BIGINT NOT NULL,
    published_at DATETIME(3) NOT NULL,
    withdrawn_at DATETIME(3) NULL,
    withdrawn_by_user_id BIGINT NULL,
    withdrawal_reason VARCHAR(500) NULL,
    active_submission_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN withdrawn_at IS NULL THEN submission_id ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_publication_no (submission_id, publication_no),
    UNIQUE KEY uq_edu_publication_active (active_submission_id),
    KEY idx_edu_publication_version_submission (submission_version_id, submission_id),
    KEY idx_edu_publication_grade_status
        (grade_revision_id, submission_version_id, required_grade_status),
    CONSTRAINT fk_edu_publication_version FOREIGN KEY (submission_version_id, submission_id)
        REFERENCES edu_submission_version (id, submission_id),
    CONSTRAINT fk_edu_publication_grade FOREIGN KEY
        (grade_revision_id, submission_version_id, required_grade_status)
        REFERENCES edu_grade_revision (id, submission_version_id, status),
    CONSTRAINT chk_edu_publication_grade_status CHECK (required_grade_status = 'CONFIRMED'),
    CONSTRAINT chk_edu_publication_no CHECK (publication_no > 0),
    CONSTRAINT chk_edu_publication_withdrawal CHECK
        ((withdrawn_at IS NULL AND withdrawn_by_user_id IS NULL AND withdrawal_reason IS NULL) OR
         (withdrawn_at IS NOT NULL AND withdrawn_at >= published_at
          AND withdrawn_by_user_id IS NOT NULL AND withdrawal_reason IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_idempotency_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT NOT NULL,
    operation_kind VARCHAR(40) NOT NULL,
    scope_id BIGINT NOT NULL,
    key_hash BINARY(32) NOT NULL,
    request_hash BINARY(32) NOT NULL,
    result_id BIGINT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_idempotency_scope (actor_user_id, operation_kind, scope_id, key_hash),
    KEY idx_edu_idempotency_expiry (expires_at),
    CONSTRAINT chk_edu_idempotency_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_edu_idempotency_expiry CHECK (expires_at > created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- learning: raw evidence remains distinct from materialized progress.
CREATE TABLE edu_learning_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    binding_id BIGINT NOT NULL,
    student_user_id BIGINT NOT NULL,
    event_kind VARCHAR(24) NOT NULL,
    source_kind VARCHAR(24) NOT NULL,
    source_id BIGINT NOT NULL,
    source_version_id BIGINT NULL,
    client_event_key VARCHAR(100) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    metadata_json JSON NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_learning_event_dedupe (student_user_id, client_event_key),
    KEY idx_edu_learning_event_binding_student_time (binding_id, student_user_id, occurred_at),
    CONSTRAINT fk_edu_learning_event_binding FOREIGN KEY (binding_id)
        REFERENCES edu_course_class_binding (id),
    CONSTRAINT chk_edu_learning_event_kind CHECK
        (event_kind IN ('RESOURCE_OPEN', 'VIDEO_WATCH', 'PREVIEW_COMPLETE',
                        'ACTIVITY_COMPLETE', 'ASSIGNMENT_SUBMIT', 'REMEDIATION_COMPLETE')),
    CONSTRAINT chk_edu_learning_source_kind CHECK
        (source_kind IN ('RESOURCE_RELEASE', 'ACTIVITY', 'ASSIGNMENT', 'QUESTION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_video_watch_interval (
    id BIGINT NOT NULL AUTO_INCREMENT,
    binding_id BIGINT NOT NULL,
    student_user_id BIGINT NOT NULL,
    release_id BIGINT NOT NULL,
    resource_version_id BIGINT NOT NULL,
    start_ms BIGINT UNSIGNED NOT NULL,
    end_ms BIGINT UNSIGNED NOT NULL,
    recorded_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_edu_video_interval_student_version (student_user_id, resource_version_id, start_ms),
    KEY idx_edu_video_interval_release_binding (release_id, binding_id),
    KEY idx_edu_video_interval_release_version (release_id, resource_version_id),
    CONSTRAINT fk_edu_video_interval_release_binding FOREIGN KEY (release_id, binding_id)
        REFERENCES edu_resource_class_release (id, binding_id),
    CONSTRAINT fk_edu_video_interval_release_version FOREIGN KEY (release_id, resource_version_id)
        REFERENCES edu_resource_class_release (id, resource_version_id),
    CONSTRAINT chk_edu_video_interval_range CHECK (end_ms > start_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_wrong_question (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_user_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    submission_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    submission_version_id BIGINT NOT NULL,
    assignment_item_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    question_version_id BIGINT NOT NULL,
    recorded_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_wrong_question_evidence
        (student_user_id, submission_version_id, assignment_item_id),
    KEY idx_edu_wrong_question_version_assignment (submission_version_id, assignment_id),
    KEY idx_edu_wrong_question_version_submission (submission_version_id, submission_id),
    KEY idx_edu_wrong_question_submission_target (submission_id, target_id),
    KEY idx_edu_wrong_question_target_student (target_id, student_user_id),
    KEY idx_edu_wrong_question_item_assignment (assignment_item_id, assignment_id),
    KEY idx_edu_wrong_question_item_question
        (assignment_item_id, question_id, question_version_id),
    CONSTRAINT fk_edu_wrong_question_submission FOREIGN KEY (submission_version_id, assignment_id)
        REFERENCES edu_submission_version (id, assignment_id),
    CONSTRAINT fk_edu_wrong_question_version_submission FOREIGN KEY
        (submission_version_id, submission_id)
        REFERENCES edu_submission_version (id, submission_id),
    CONSTRAINT fk_edu_wrong_question_submission_target FOREIGN KEY (submission_id, target_id)
        REFERENCES edu_assignment_submission (id, target_id),
    CONSTRAINT fk_edu_wrong_question_target_student FOREIGN KEY (target_id, student_user_id)
        REFERENCES edu_assignment_target (id, student_user_id),
    CONSTRAINT fk_edu_wrong_question_item FOREIGN KEY (assignment_item_id, assignment_id)
        REFERENCES edu_assignment_item (id, assignment_id),
    CONSTRAINT fk_edu_wrong_question_item_question FOREIGN KEY
        (assignment_item_id, question_id, question_version_id)
        REFERENCES edu_assignment_item (id, question_id, question_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- analysis: nullable value distinguishes missing evidence from a measured zero.
CREATE TABLE edu_metric_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    binding_id BIGINT NOT NULL,
    metric_code VARCHAR(60) NOT NULL,
    definition_version INT UNSIGNED NOT NULL,
    subject_kind VARCHAR(20) NOT NULL,
    subject_id BIGINT NOT NULL,
    window_starts_at DATETIME(3) NOT NULL,
    window_ends_at DATETIME(3) NOT NULL,
    evidence_cutoff_at DATETIME(3) NOT NULL,
    numerator DECIMAL(18,4) NULL,
    denominator DECIMAL(18,4) NULL,
    sample_count INT UNSIGNED NOT NULL DEFAULT 0,
    metric_value DECIMAL(18,4) NULL,
    source_watermark VARCHAR(120) NULL,
    calculated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_metric_snapshot_identity
        (binding_id, metric_code, definition_version, subject_kind, subject_id,
         window_starts_at, window_ends_at, evidence_cutoff_at),
    KEY idx_edu_metric_snapshot_subject (binding_id, subject_kind, subject_id, calculated_at),
    CONSTRAINT fk_edu_metric_snapshot_binding FOREIGN KEY (binding_id)
        REFERENCES edu_course_class_binding (id),
    CONSTRAINT chk_edu_metric_snapshot_subject CHECK
        (subject_kind IN ('CLASS', 'STUDENT', 'KNOWLEDGE_POINT')),
    CONSTRAINT chk_edu_metric_snapshot_window CHECK (window_ends_at > window_starts_at),
    CONSTRAINT chk_edu_metric_snapshot_version CHECK (definition_version > 0),
    CONSTRAINT chk_edu_metric_snapshot_numbers CHECK
        ((numerator IS NULL OR numerator >= 0) AND
         (denominator IS NULL OR denominator >= 0) AND
         (metric_value IS NULL OR metric_value >= 0) AND
         (denominator IS NULL OR denominator > 0 OR metric_value IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ai: M0 Q&A audit and citations; the service checks live authorization twice.
CREATE TABLE edu_ai_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    requester_user_id BIGINT NOT NULL,
    requester_role VARCHAR(16) NOT NULL,
    binding_id BIGINT NULL,
    conversation_key BINARY(16) NOT NULL,
    request_key BINARY(16) NOT NULL,
    question_hash BINARY(32) NOT NULL,
    question_storage_key VARCHAR(512) NULL,
    model_code VARCHAR(100) NOT NULL,
    prompt_template_version VARCHAR(60) NOT NULL,
    retrieval_index_version VARCHAR(60) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    answer_storage_key VARCHAR(512) NULL,
    content_retention_until DATETIME(3) NULL,
    input_tokens INT UNSIGNED NULL,
    output_tokens INT UNSIGNED NULL,
    error_code VARCHAR(60) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    finished_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_ai_task_request (requester_user_id, request_key),
    UNIQUE KEY uq_edu_ai_task_id_binding (id, binding_id),
    KEY idx_edu_ai_task_conversation (requester_user_id, conversation_key, created_at),
    KEY idx_edu_ai_task_binding_status (binding_id, status),
    CONSTRAINT fk_edu_ai_task_binding FOREIGN KEY (binding_id)
        REFERENCES edu_course_class_binding (id),
    CONSTRAINT chk_edu_ai_task_role CHECK (requester_role IN ('TEACHER', 'STUDENT')),
    CONSTRAINT chk_edu_ai_task_status CHECK
        (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'TIMED_OUT')),
    CONSTRAINT chk_edu_ai_task_finished CHECK
        ((status IN ('PENDING', 'RUNNING') AND finished_at IS NULL) OR
         (status IN ('SUCCEEDED', 'FAILED', 'TIMED_OUT')
          AND finished_at IS NOT NULL AND finished_at >= created_at)),
    CONSTRAINT chk_edu_ai_task_content_retention CHECK
        ((question_storage_key IS NULL AND answer_storage_key IS NULL) OR
         (content_retention_until IS NOT NULL AND content_retention_until > created_at))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_ai_citation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ai_task_id BIGINT NOT NULL,
    binding_id BIGINT NOT NULL,
    release_id BIGINT NOT NULL,
    resource_version_id BIGINT NOT NULL,
    page_no INT UNSIGNED NULL,
    quote_hash BINARY(32) NULL,
    sort_order INT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_ai_citation_order (ai_task_id, sort_order),
    KEY idx_edu_ai_citation_task_binding (ai_task_id, binding_id),
    KEY idx_edu_ai_citation_release_binding (release_id, binding_id),
    KEY idx_edu_ai_citation_release_version (release_id, resource_version_id),
    KEY idx_edu_ai_citation_version_page (resource_version_id, page_no),
    CONSTRAINT fk_edu_ai_citation_task FOREIGN KEY (ai_task_id, binding_id)
        REFERENCES edu_ai_task (id, binding_id),
    CONSTRAINT fk_edu_ai_citation_release_binding FOREIGN KEY (release_id, binding_id)
        REFERENCES edu_resource_class_release (id, binding_id),
    CONSTRAINT fk_edu_ai_citation_release_version FOREIGN KEY (release_id, resource_version_id)
        REFERENCES edu_resource_class_release (id, resource_version_id),
    CONSTRAINT fk_edu_ai_citation_page FOREIGN KEY (resource_version_id, page_no)
        REFERENCES edu_resource_page (resource_version_id, page_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- course communication: announcements are scoped to one published class binding.
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
    CONSTRAINT chk_edu_announcement_status CHECK
        (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT chk_edu_announcement_window CHECK
        (opens_at IS NULL OR closes_at IS NULL OR closes_at > opens_at),
    CONSTRAINT chk_edu_announcement_publish CHECK
        ((status = 'DRAFT' AND published_by_user_id IS NULL) OR
         (status <> 'DRAFT' AND published_by_user_id IS NOT NULL AND opens_at IS NOT NULL))
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
    CONSTRAINT chk_edu_notice_kind CHECK
        (notice_kind IN ('ANNOUNCEMENT', 'PREVIEW', 'ACTIVITY', 'ASSIGNMENT', 'GRADE')),
    CONSTRAINT chk_edu_notice_read CHECK (read_at IS NULL OR read_at >= visible_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
