-- Synthetic V3.0 M0 teaching demo data. Never use in a production database.
-- Run once, after both schema files, in the requested zhijiao_m0 database.
-- Back up existing data first if this database is no longer empty.
-- All user/organization IDs below are fictional logical IDs; no RuoYi accounts are created.
-- File and AI object keys are placeholders; no files are uploaded and no model is called.
USE zhijiao_m0;
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS edu_demo_seed_20261022;
DELIMITER //
CREATE PROCEDURE edu_demo_seed_20261022()
BEGIN
    DECLARE v_base BIGINT DEFAULT 910000000001;
    DECLARE v_org BIGINT DEFAULT 920000000001;
    DECLARE v_teacher BIGINT DEFAULT 930000000001;
    DECLARE v_student_a BIGINT DEFAULT 930000000101;
    DECLARE v_student_b BIGINT DEFAULT 930000000102;
    DECLARE v_student_c BIGINT DEFAULT 930000000103;
    DECLARE v_student_d BIGINT DEFAULT 930000000104;
    DECLARE v_now DATETIME(3) DEFAULT UTC_TIMESTAMP(3);
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    IF EXISTS (SELECT 1 FROM edu_term WHERE id = v_base) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Demo data already exists; do not rerun seed';
    END IF;

    START TRANSACTION;
    -- course: one fictional organization, two teaching classes, four students.
    INSERT INTO edu_term
        (id, organization_id, code, name, starts_on, ends_on)
    VALUES (v_base, v_org, 'DEMO-TERM', '演示学期',
            DATE(v_now - INTERVAL 30 DAY), DATE(v_now + INTERVAL 180 DAY));
    INSERT INTO edu_official_course
        (id, organization_id, code, name)
    VALUES (v_base, v_org, 'DEMO-SMART-EDU', '智慧教育导论（演示）');
    INSERT INTO edu_course_space
        (id, organization_id, official_course_id, owner_user_id, title,
         summary, learning_objectives, status)
    VALUES (v_base, v_org, v_base, v_teacher, '智慧教育导论·教师课程空间',
            '仅用于演示数据库关系的虚构课程。',
            '认识课程资源、课堂互动与学习反馈。', 'ACTIVE');
    INSERT INTO edu_course_unit
        (id, course_space_id, parent_unit_id, unit_kind, title, sort_order)
    VALUES (v_base, v_base, NULL, 'CHAPTER', '第一章：智慧学习', 1),
           (v_base + 1, v_base, v_base, 'SECTION', '第一节：形成性评价', 1);
    INSERT INTO edu_knowledge_point
        (id, course_space_id, unit_id, code, name)
    VALUES (v_base, v_base, v_base + 1, 'KP-DEMO-1', '形成性评价的作用');
    INSERT INTO edu_teaching_class
        (id, organization_id, term_id, official_course_id, code, name,
         class_kind, status)
    VALUES (v_base, v_org, v_base, v_base, 'DEMO-A', '演示教学班 A',
            'FORMAL', 'ACTIVE'),
           (v_base + 1, v_org, v_base, v_base, 'DEMO-B', '演示教学班 B',
            'FORMAL', 'ACTIVE');
    INSERT INTO edu_class_teacher
        (id, teaching_class_id, user_id, teacher_role, can_publish,
         can_grade, can_export, valid_from, granted_by_user_id)
    VALUES (v_base, v_base, v_teacher, 'LEAD', 1, 1, 1,
            v_now - INTERVAL 30 DAY, v_teacher),
           (v_base + 1, v_base + 1, v_teacher, 'LEAD', 1, 1, 1,
            v_now - INTERVAL 30 DAY, v_teacher);
    INSERT INTO edu_class_student
        (id, teaching_class_id, user_id, joined_at, recorded_by_user_id)
    VALUES (v_base, v_base, v_student_a, v_now - INTERVAL 20 DAY, v_teacher),
           (v_base + 1, v_base, v_student_b, v_now - INTERVAL 20 DAY, v_teacher),
           (v_base + 2, v_base, v_student_c, v_now - INTERVAL 20 DAY, v_teacher),
           (v_base + 3, v_base + 1, v_student_d, v_now - INTERVAL 20 DAY, v_teacher);
    INSERT INTO edu_course_class_binding
        (id, organization_id, teaching_class_id, course_space_id,
         bound_by_user_id, published_at)
    VALUES (v_base, v_org, v_base, v_base, v_teacher, v_now - INTERVAL 14 DAY),
           (v_base + 1, v_org, v_base + 1, v_base, v_teacher,
            v_now - INTERVAL 14 DAY);

    -- resource: one slide file and one video file reused by the same course.
    INSERT INTO edu_resource
        (id, course_space_id, title, resource_kind, status, created_by_user_id)
    VALUES (v_base, v_base, '第一讲课件（演示，无实际文件）', 'FILE', 'ACTIVE', v_teacher),
           (v_base + 1, v_base, '导学视频（演示，无实际文件）', 'FILE', 'ACTIVE', v_teacher);
    INSERT INTO edu_resource_version
        (id, resource_id, resource_kind, version_no, storage_key,
         mime_type, byte_size, processing_status, created_by_user_id)
    VALUES (v_base, v_base, 'FILE', 1, 'demo-only/slides-v1.pptx',
            'application/vnd.openxmlformats-officedocument.presentationml.presentation',
            102400, 'READY', v_teacher),
           (v_base + 1, v_base + 1, 'FILE', 1, 'demo-only/intro-v1.mp4',
            'video/mp4', 204800, 'READY', v_teacher);
    INSERT INTO edu_resource_class_release
        (id, binding_id, course_space_id, resource_id, resource_version_id,
         released_by_user_id, released_at, opens_at, closes_at)
    VALUES (v_base, v_base, v_base, v_base, v_base, v_teacher,
            v_now - INTERVAL 7 DAY, v_now - INTERVAL 7 DAY, v_now + INTERVAL 30 DAY),
           (v_base + 1, v_base + 1, v_base, v_base, v_base, v_teacher,
            v_now - INTERVAL 7 DAY, v_now - INTERVAL 7 DAY, v_now + INTERVAL 30 DAY),
           (v_base + 2, v_base, v_base, v_base + 1, v_base + 1, v_teacher,
            v_now - INTERVAL 7 DAY, v_now - INTERVAL 7 DAY, v_now + INTERVAL 30 DAY);
    INSERT INTO edu_resource_page
        (id, resource_version_id, page_no, preview_storage_key, width_px, height_px)
    VALUES (v_base, v_base, 1, 'demo-only/slides-v1/page-1.png', 1280, 720),
           (v_base + 1, v_base, 2, 'demo-only/slides-v1/page-2.png', 1280, 720);
    INSERT INTO edu_question
        (id, course_space_id, title, status, created_by_user_id)
    VALUES (v_base, v_base, '形成性评价基础题', 'ACTIVE', v_teacher);
    INSERT INTO edu_question_version
        (id, question_id, version_no, question_kind, stem,
         options_json, answer_json, explanation, max_score, created_by_user_id)
    VALUES (v_base, v_base, 1, 'SINGLE',
            '以下哪一项最能体现形成性评价？',
            JSON_ARRAY('只在期末给分', '在学习过程中反馈并调整教学', '只统计登录次数'),
            JSON_OBJECT('correct_index', 1),
            '形成性评价强调学习过程中的证据与反馈。', 10, v_teacher);
    INSERT INTO edu_question_knowledge_point
        (question_id, course_space_id, knowledge_point_id)
    VALUES (v_base, v_base, v_base);

    -- prepare and classroom: one active class with a public slide snapshot.
    INSERT INTO edu_lesson
        (id, binding_id, course_space_id, unit_id, title, scheduled_at,
         status, created_by_user_id)
    VALUES (v_base, v_base, v_base, v_base + 1, '第 1 次课：形成性评价',
            v_now, 'ACTIVE', v_teacher);
    INSERT INTO edu_lesson_plan
        (id, lesson_id, binding_id, version_no, title, private_notes,
         status, created_by_user_id)
    VALUES (v_base, v_base, v_base, 1, '第一讲备课方案',
            '演示用教师私有备注，不应投放到学生端。', 'READY', v_teacher);
    INSERT INTO edu_plan_material
        (id, plan_id, binding_id, course_space_id, resource_id,
         resource_version_id, sort_order, private_notes)
    VALUES (v_base, v_base, v_base, v_base, v_base, v_base, 1,
            '先讲概念，再打开随堂题。');
    INSERT INTO edu_activity_template
        (id, plan_id, binding_id, course_space_id, template_kind, title,
         question_id, question_version_id, duration_seconds, status,
         created_by_user_id)
    VALUES (v_base, v_base, v_base, v_base, 'QUIZ', '课堂单选题模板',
            v_base, v_base, 900, 'READY', v_teacher);
    INSERT INTO edu_class_session
        (id, binding_id, lesson_id, plan_id, status,
         started_by_user_id, started_at)
    VALUES (v_base, v_base, v_base, v_base, 'RUNNING',
            v_teacher, v_now - INTERVAL 30 MINUTE);
    INSERT INTO edu_session_projection
        (session_id, binding_id, state_seq, release_id, resource_version_id,
         page_no, student_can_browse, updated_at)
    VALUES (v_base, v_base, 2, v_base, v_base, 2, 0, v_now);
    INSERT INTO edu_session_event
        (id, session_id, seq_no, event_kind, visibility,
         public_payload_json, actor_user_id, occurred_at)
    VALUES (v_base, v_base, 1, 'PAGE_CHANGED', 'PUBLIC',
            JSON_OBJECT('page', 1), v_teacher, v_now - INTERVAL 25 MINUTE),
           (v_base + 1, v_base, 2, 'PAGE_CHANGED', 'PUBLIC',
            JSON_OBJECT('page', 2), v_teacher, v_now - INTERVAL 20 MINUTE);

    -- activity: a quiz and attendance can be open together in the same class.
    INSERT INTO edu_activity_instance
        (id, binding_id, teaching_class_id, course_space_id, session_id,
         template_id, activity_kind, title, question_id, question_version_id,
         opens_at, closes_at, answer_opens_at, status, created_by_user_id)
    VALUES (v_base, v_base, v_base, v_base, v_base, v_base,
            'QUIZ', '课堂随堂题', v_base, v_base,
            v_now - INTERVAL 15 MINUTE, v_now + INTERVAL 15 MINUTE,
            v_now + INTERVAL 15 MINUTE, 'OPEN', v_teacher),
           (v_base + 1, v_base, v_base, v_base, v_base, NULL,
            'ATTENDANCE', '本次课堂签到', NULL, NULL,
            v_now - INTERVAL 20 MINUTE, v_now + INTERVAL 10 MINUTE,
            NULL, 'OPEN', v_teacher);
    INSERT INTO edu_activity_target
        (id, activity_id, teaching_class_id, student_user_id,
         enrollment_id, captured_at)
    VALUES (v_base, v_base, v_base, v_student_a, v_base, v_now - INTERVAL 15 MINUTE),
           (v_base + 1, v_base, v_base, v_student_b, v_base + 1, v_now - INTERVAL 15 MINUTE),
           (v_base + 2, v_base, v_base, v_student_c, v_base + 2, v_now - INTERVAL 15 MINUTE),
           (v_base + 3, v_base + 1, v_base, v_student_a, v_base, v_now - INTERVAL 20 MINUTE),
           (v_base + 4, v_base + 1, v_base, v_student_b, v_base + 1, v_now - INTERVAL 20 MINUTE),
           (v_base + 5, v_base + 1, v_base, v_student_c, v_base + 2, v_now - INTERVAL 20 MINUTE);
    INSERT INTO edu_activity_response
        (id, activity_id, target_id, accepted_at, response_state)
    VALUES (v_base, v_base, v_base, v_now - INTERVAL 8 MINUTE, 'ON_TIME'),
           (v_base + 1, v_base, v_base + 1, v_now - INTERVAL 7 MINUTE, 'ON_TIME'),
           (v_base + 2, v_base + 1, v_base + 3, v_now - INTERVAL 6 MINUTE, 'ON_TIME');
    INSERT INTO edu_activity_response_revision
        (id, response_id, revision_no, answer_json, is_correct,
         recorded_by_user_id, recorded_at)
    VALUES (v_base, v_base, 1, JSON_OBJECT('choice_index', 1), 1,
            v_student_a, v_now - INTERVAL 8 MINUTE),
           (v_base + 1, v_base + 1, 1, JSON_OBJECT('choice_index', 0), 0,
            v_student_b, v_now - INTERVAL 7 MINUTE),
           (v_base + 2, v_base + 2, 1, NULL, NULL,
            v_student_a, v_now - INTERVAL 6 MINUTE);

    -- assignment: preview, classroom work and homework use the same engine.
    INSERT INTO edu_assignment
        (id, binding_id, teaching_class_id, course_space_id, lesson_id,
         session_id, assignment_kind, title, instructions, opens_at,
         closes_at, max_submissions, answer_opens_at, status,
         published_at, published_by_user_id, created_by_user_id)
    VALUES (v_base, v_base, v_base, v_base, v_base, NULL,
            'HOMEWORK', '课后作业：形成性评价', '完成单选题。',
            v_now - INTERVAL 7 DAY, v_now - INTERVAL 1 DAY, 2,
            v_now - INTERVAL 1 DAY, 'CLOSED',
            v_now - INTERVAL 7 DAY, v_teacher, v_teacher),
           (v_base + 1, v_base, v_base, v_base, v_base, NULL,
            'PREVIEW', '课前预习：阅读第一讲', '阅读课件并写一句收获。',
            v_now - INTERVAL 1 DAY, v_now + INTERVAL 3 DAY, 1,
            v_now + INTERVAL 3 DAY, 'PUBLISHED',
            v_now - INTERVAL 1 DAY, v_teacher, v_teacher),
           (v_base + 2, v_base, v_base, v_base, v_base, v_base,
            'CLASSWORK', '课堂小作业：写出反馈例子', '简述一个形成性反馈例子。',
            v_now - INTERVAL 10 MINUTE, v_now + INTERVAL 50 MINUTE, 1,
            v_now + INTERVAL 50 MINUTE, 'PUBLISHED',
            v_now - INTERVAL 10 MINUTE, v_teacher, v_teacher);
    INSERT INTO edu_assignment_item
        (id, assignment_id, course_space_id, sort_order, item_kind,
         prompt, question_id, question_version_id, max_score)
    VALUES (v_base, v_base, v_base, 1, 'QUESTION',
            '以下哪一项最能体现形成性评价？', v_base, v_base, 10),
           (v_base + 1, v_base + 1, v_base, 1, 'TEXT',
            '写一句你对第一讲的收获。', NULL, NULL, 0),
           (v_base + 2, v_base + 2, v_base, 1, 'TEXT',
            '简述一个教师反馈学生学习过程的例子。', NULL, NULL, 5);
    INSERT INTO edu_assignment_target
        (id, assignment_id, teaching_class_id, student_user_id,
         enrollment_id, captured_at)
    VALUES (v_base, v_base, v_base, v_student_a, v_base, v_now - INTERVAL 7 DAY),
           (v_base + 1, v_base, v_base, v_student_b, v_base + 1, v_now - INTERVAL 7 DAY),
           (v_base + 2, v_base, v_base, v_student_c, v_base + 2, v_now - INTERVAL 7 DAY),
           (v_base + 3, v_base + 1, v_base, v_student_a, v_base, v_now - INTERVAL 1 DAY),
           (v_base + 4, v_base + 1, v_base, v_student_b, v_base + 1, v_now - INTERVAL 1 DAY),
           (v_base + 5, v_base + 1, v_base, v_student_c, v_base + 2, v_now - INTERVAL 1 DAY),
           (v_base + 6, v_base + 2, v_base, v_student_a, v_base, v_now - INTERVAL 10 MINUTE),
           (v_base + 7, v_base + 2, v_base, v_student_b, v_base + 1, v_now - INTERVAL 10 MINUTE),
           (v_base + 8, v_base + 2, v_base, v_student_c, v_base + 2, v_now - INTERVAL 10 MINUTE);
    INSERT INTO edu_assignment_submission
        (id, assignment_id, target_id)
    VALUES (v_base, v_base, v_base), (v_base + 1, v_base, v_base + 1);
    INSERT INTO edu_submission_version
        (id, submission_id, assignment_id, version_no, submitted_at, is_late)
    VALUES (v_base, v_base, v_base, 1, v_now - INTERVAL 2 DAY, 0),
           (v_base + 1, v_base + 1, v_base, 1, v_now - INTERVAL 2 DAY, 0);
    INSERT INTO edu_submission_answer
        (id, submission_version_id, assignment_id, assignment_item_id, answer_json)
    VALUES (v_base, v_base, v_base, v_base, JSON_OBJECT('choice_index', 1)),
           (v_base + 1, v_base + 1, v_base, v_base, JSON_OBJECT('choice_index', 0));
    INSERT INTO edu_grade_revision
        (id, submission_version_id, revision_no, score, max_score,
         feedback, status, graded_by_user_id, confirmed_by_user_id, confirmed_at)
    VALUES (v_base, v_base, 1, 10, 10, '理解正确。', 'CONFIRMED',
            v_teacher, v_teacher, v_now - INTERVAL 12 HOUR),
           (v_base + 1, v_base + 1, 1, 0, 10, '请复习过程性反馈。',
            'CONFIRMED', v_teacher, v_teacher, v_now - INTERVAL 12 HOUR);
    INSERT INTO edu_grade_publication
        (id, submission_id, submission_version_id, grade_revision_id,
         publication_no, published_by_user_id, published_at)
    VALUES (v_base, v_base, v_base, v_base, 1,
            v_teacher, v_now - INTERVAL 11 HOUR),
           (v_base + 1, v_base + 1, v_base + 1, v_base + 1, 1,
            v_teacher, v_now - INTERVAL 11 HOUR);
    INSERT INTO edu_idempotency_record
        (id, actor_user_id, operation_kind, scope_id, key_hash, request_hash,
         result_id, status, created_at, expires_at)
    VALUES (v_base, v_student_a, 'ASSIGNMENT_SUBMIT', v_base,
            UNHEX(SHA2('demo-submit-a', 256)),
            UNHEX(SHA2('demo-answer-a', 256)), v_base, 'SUCCEEDED',
            v_now - INTERVAL 2 DAY, v_now + INTERVAL 30 DAY);

    -- learning and analysis: two submissions out of three targets, one wrong answer.
    INSERT INTO edu_learning_event
        (id, binding_id, student_user_id, event_kind, source_kind, source_id,
         source_version_id, client_event_key, occurred_at)
    VALUES (v_base, v_base, v_student_a, 'RESOURCE_OPEN', 'RESOURCE_RELEASE',
            v_base, v_base, 'demo-resource-open-a', v_now - INTERVAL 3 DAY),
           (v_base + 1, v_base, v_student_a, 'ASSIGNMENT_SUBMIT', 'ASSIGNMENT',
            v_base, v_base, 'demo-homework-submit-a', v_now - INTERVAL 2 DAY),
           (v_base + 2, v_base, v_student_b, 'ASSIGNMENT_SUBMIT', 'ASSIGNMENT',
            v_base, v_base + 1, 'demo-homework-submit-b', v_now - INTERVAL 2 DAY);
    INSERT INTO edu_video_watch_interval
        (id, binding_id, student_user_id, release_id, resource_version_id,
         start_ms, end_ms, recorded_at)
    VALUES (v_base, v_base, v_student_a, v_base + 2, v_base + 1,
            0, 60000, v_now - INTERVAL 3 DAY);
    INSERT INTO edu_wrong_question
        (id, student_user_id, assignment_id, submission_id, target_id,
         submission_version_id, assignment_item_id, question_id,
         question_version_id, recorded_at)
    VALUES (v_base, v_student_b, v_base, v_base + 1, v_base + 1,
            v_base + 1, v_base, v_base, v_base, v_now - INTERVAL 11 HOUR);
    INSERT INTO edu_metric_snapshot
        (id, binding_id, metric_code, definition_version, subject_kind,
         subject_id, window_starts_at, window_ends_at, evidence_cutoff_at,
         numerator, denominator, sample_count, metric_value,
         source_watermark, calculated_at)
    VALUES (v_base, v_base, 'HOMEWORK_SUBMISSION_RATE', 1, 'CLASS', v_base,
            v_now - INTERVAL 7 DAY, v_now, v_now,
            2, 3, 3, 0.6667, 'demo-assignment-1', v_now);

    -- ai: metadata and citation are synthetic; no provider call or object exists.
    INSERT INTO edu_ai_task
        (id, requester_user_id, requester_role, binding_id, conversation_key,
         request_key, question_hash, question_storage_key, model_code,
         prompt_template_version, retrieval_index_version, status,
         answer_storage_key, content_retention_until, input_tokens,
         output_tokens, created_at, finished_at)
    VALUES (v_base, v_teacher, 'TEACHER', v_base,
            UNHEX(REPEAT('11', 16)), UNHEX(REPEAT('22', 16)),
            UNHEX(SHA2('demo teaching question', 256)), 'demo-only/ai/question-1',
            'DEMO_NO_PROVIDER', 'demo-v1', 'demo-index-v1', 'SUCCEEDED',
            'demo-only/ai/answer-1', v_now + INTERVAL 7 DAY,
            100, 50, v_now - INTERVAL 1 MINUTE, v_now);
    INSERT INTO edu_ai_citation
        (id, ai_task_id, binding_id, release_id, resource_version_id,
         page_no, sort_order)
    VALUES (v_base, v_base, v_base, v_base, v_base, 1, 1);

    -- announcement and inbox notices complete the workbench demo.
    INSERT INTO edu_course_announcement
        (id, binding_id, title, body, status, opens_at,
         published_by_user_id, created_by_user_id)
    VALUES (v_base, v_base, '课堂提醒（演示）',
            '请在课堂中完成签到与随堂题。此公告仅为虚构测试数据。',
            'PUBLISHED', v_now - INTERVAL 1 DAY, v_teacher, v_teacher);
    INSERT INTO edu_user_notice
        (id, recipient_user_id, binding_id, notice_kind, source_id,
         title, visible_at)
    VALUES (v_base, v_student_a, v_base, 'ANNOUNCEMENT', v_base,
            '课堂提醒（演示）', v_now - INTERVAL 1 DAY),
           (v_base + 1, v_student_a, v_base, 'GRADE', v_base,
            '课后作业成绩已发布（演示）', v_now - INTERVAL 11 HOUR);

    COMMIT;
END//
DELIMITER ;
CALL edu_demo_seed_20261022();
DROP PROCEDURE edu_demo_seed_20261022;

-- Expected: term=1, classes=2, students=4, assignments=3,
-- submissions=2, grades=2, AI tasks=1.
SELECT DATABASE() AS schema_name,
       (SELECT COUNT(*) FROM edu_term) AS terms,
       (SELECT COUNT(*) FROM edu_teaching_class) AS classes,
       (SELECT COUNT(*) FROM edu_class_student) AS students,
       (SELECT COUNT(*) FROM edu_assignment) AS assignments,
       (SELECT COUNT(*) FROM edu_assignment_submission) AS submissions,
       (SELECT COUNT(*) FROM edu_grade_publication) AS grades,
       (SELECT COUNT(*) FROM edu_ai_task) AS ai_tasks;
