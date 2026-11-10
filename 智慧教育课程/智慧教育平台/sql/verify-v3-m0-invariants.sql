-- Run only in an empty disposable database after both V3.0 M0 schema scripts.
-- This script inserts synthetic rows inside one transaction and rolls them back.
-- A failed assertion raises SQLSTATE 45000. No production data is required.
DELIMITER //
CREATE PROCEDURE verify_edu_m0_invariants()
BEGIN
    DECLARE rejected BOOLEAN DEFAULT FALSE;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;
    INSERT INTO edu_term (id, organization_id, code, name, starts_on, ends_on)
    VALUES (1, 11, 'T1', 'Term one', '2025-01-01', '2027-01-01'),
           (2, 22, 'T2', 'Term two', '2025-01-01', '2027-01-01');
    INSERT INTO edu_official_course (id, organization_id, code, name)
    VALUES (1, 11, 'C1', 'Course one'), (2, 22, 'C2', 'Course two');
    INSERT INTO edu_course_space (id, organization_id, official_course_id, owner_user_id, title)
    VALUES (1, 11, 1, 1001, 'Space one'), (2, 22, 2, 2001, 'Space two');
    INSERT INTO edu_teaching_class
        (id, organization_id, term_id, official_course_id, code, name, class_kind)
    VALUES (1, 11, 1, 1, 'TC1', 'Class one', 'FORMAL'),
           (2, 22, 2, 2, 'TC2', 'Class two', 'FORMAL');
    INSERT INTO edu_class_student
        (id, teaching_class_id, user_id, joined_at, recorded_by_user_id)
    VALUES (1, 1, 101, '2025-01-01', 1001), (2, 2, 201, '2025-01-01', 2001);
    INSERT INTO edu_course_class_binding
        (id, organization_id, teaching_class_id, course_space_id, bound_by_user_id, published_at)
    VALUES (1, 11, 1, 1, 1001, '2025-01-01');

    SET rejected = FALSE;
    BEGIN
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION SET rejected = TRUE;
        INSERT INTO edu_course_class_binding
            (id, organization_id, teaching_class_id, course_space_id, bound_by_user_id, published_at)
        VALUES (2, 11, 1, 2, 1001, '2025-01-01');
    END;
    IF NOT rejected THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cross-organization binding accepted'; END IF;

    INSERT INTO edu_resource (id, course_space_id, title, resource_kind, created_by_user_id)
    VALUES (1, 1, 'Slides', 'FILE', 1001);
    INSERT INTO edu_resource_version
        (id, resource_id, resource_kind, version_no, storage_key, created_by_user_id)
    VALUES (1, 1, 'FILE', 1, 'private/slides-v1', 1001);
    SET rejected = FALSE;
    BEGIN
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION SET rejected = TRUE;
        INSERT INTO edu_resource_version
            (id, resource_id, resource_kind, version_no, external_uri, created_by_user_id)
        VALUES (2, 1, 'LINK', 2, 'https://example.invalid/slides', 1001);
    END;
    IF NOT rejected THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Resource type mismatch accepted'; END IF;

    INSERT INTO edu_resource_class_release
        (id, binding_id, course_space_id, resource_id, resource_version_id,
         released_by_user_id, released_at, opens_at, closes_at)
    VALUES (1, 1, 1, 1, 1, 1001, '2025-01-01', '2025-01-01', '2025-01-02');
    SET rejected = FALSE;
    BEGIN
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION SET rejected = TRUE;
        INSERT INTO edu_resource_class_release
            (id, binding_id, course_space_id, resource_id, resource_version_id,
             released_by_user_id, released_at, opens_at)
        VALUES (2, 1, 1, 1, 1, 1001, '2025-01-03', '2025-01-03');
    END;
    IF NOT rejected THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate live release accepted'; END IF;
    UPDATE edu_resource_class_release
    SET ended_at = '2025-01-02', end_kind = 'EXPIRED'
    WHERE id = 1;
    INSERT INTO edu_resource_class_release
        (id, binding_id, course_space_id, resource_id, resource_version_id,
         released_by_user_id, released_at, opens_at)
    VALUES (2, 1, 1, 1, 1, 1001, '2025-01-03', '2025-01-03');

    INSERT INTO edu_assignment
        (id, binding_id, teaching_class_id, course_space_id, assignment_kind, title,
         opens_at, closes_at, status, published_at, published_by_user_id, created_by_user_id)
    VALUES (1, 1, 1, 1, 'HOMEWORK', 'Homework', '2025-01-01', '2027-01-01',
            'PUBLISHED', '2025-01-01', 1001, 1001);
    SET rejected = FALSE;
    BEGIN
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION SET rejected = TRUE;
        INSERT INTO edu_assignment_target
            (id, assignment_id, teaching_class_id, student_user_id, enrollment_id, captured_at)
        VALUES (1, 1, 1, 201, 2, '2025-01-01');
    END;
    IF NOT rejected THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cross-class assignment target accepted'; END IF;
    INSERT INTO edu_assignment_target
        (id, assignment_id, teaching_class_id, student_user_id, enrollment_id, captured_at)
    VALUES (1, 1, 1, 101, 1, '2025-01-01');
    INSERT INTO edu_assignment_submission (id, assignment_id, target_id)
    VALUES (1, 1, 1);
    INSERT INTO edu_submission_version
        (id, submission_id, assignment_id, version_no, submitted_at, superseded_at)
    VALUES (1, 1, 1, 1, '2025-01-04', '2025-01-05');
    INSERT INTO edu_submission_version
        (id, submission_id, assignment_id, version_no, submitted_at)
    VALUES (2, 1, 1, 2, '2025-01-05');
    INSERT INTO edu_grade_revision
        (id, submission_version_id, revision_no, score, max_score, graded_by_user_id)
    VALUES (1, 1, 1, 8, 10, 1001);
    SET rejected = FALSE;
    BEGIN
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION SET rejected = TRUE;
        INSERT INTO edu_grade_publication
            (id, submission_id, submission_version_id, grade_revision_id,
             publication_no, published_by_user_id, published_at)
        VALUES (1, 1, 1, 1, 1, 1001, '2025-01-06');
    END;
    IF NOT rejected THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Draft grade published'; END IF;
    UPDATE edu_grade_revision
    SET status = 'CONFIRMED', confirmed_by_user_id = 1001, confirmed_at = '2025-01-06'
    WHERE id = 1;
    SET rejected = FALSE;
    BEGIN
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION SET rejected = TRUE;
        INSERT INTO edu_grade_publication
            (id, submission_id, submission_version_id, grade_revision_id,
             publication_no, published_by_user_id, published_at)
        VALUES (1, 1, 2, 1, 1, 1001, '2025-01-06');
    END;
    IF NOT rejected THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Grade/submission mismatch accepted'; END IF;
    INSERT INTO edu_grade_publication
        (id, submission_id, submission_version_id, grade_revision_id,
         publication_no, published_by_user_id, published_at)
    VALUES (1, 1, 1, 1, 1, 1001, '2025-01-06');

    INSERT INTO edu_question (id, course_space_id, title, created_by_user_id)
    VALUES (1, 1, 'Question', 1001);
    INSERT INTO edu_question_version
        (id, question_id, version_no, question_kind, stem, options_json,
         answer_json, max_score, created_by_user_id)
    VALUES (1, 1, 1, 'SINGLE', 'Choose A', '["A", "B"]',
            '{"correct":"A"}', 10, 1001);
    INSERT INTO edu_assignment_item
        (id, assignment_id, course_space_id, sort_order, item_kind, prompt,
         question_id, question_version_id, max_score)
    VALUES (1, 1, 1, 1, 'QUESTION', 'Choose A', 1, 1, 10);
    INSERT INTO edu_submission_answer
        (id, submission_version_id, assignment_id, assignment_item_id, answer_json)
    VALUES (1, 2, 1, 1, '{"choice":"B"}');
    SET rejected = FALSE;
    BEGIN
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION SET rejected = TRUE;
        INSERT INTO edu_wrong_question
            (id, student_user_id, assignment_id, submission_id, target_id,
             submission_version_id, assignment_item_id, question_id,
             question_version_id, recorded_at)
        VALUES (1, 201, 1, 1, 1, 2, 1, 1, 1, '2025-01-06');
    END;
    IF NOT rejected THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Wrong-question evidence attributed to other student'; END IF;
    INSERT INTO edu_wrong_question
        (id, student_user_id, assignment_id, submission_id, target_id,
         submission_version_id, assignment_item_id, question_id,
         question_version_id, recorded_at)
    VALUES (1, 101, 1, 1, 1, 2, 1, 1, 1, '2025-01-06');

    INSERT INTO edu_activity_instance
        (id, binding_id, teaching_class_id, course_space_id, activity_kind,
         title, question_id, question_version_id, opens_at, closes_at,
         status, created_by_user_id)
    VALUES (1, 1, 1, 1, 'QUIZ', 'Quick quiz', 1, 1,
            '2025-01-01', '2027-01-01', 'OPEN', 1001);
    SET rejected = FALSE;
    BEGIN
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION SET rejected = TRUE;
        INSERT INTO edu_activity_instance
            (id, binding_id, teaching_class_id, course_space_id, activity_kind,
             title, question_id, question_version_id, opens_at, closes_at,
             status, created_by_user_id)
        VALUES (2, 1, 1, 1, 'QUIZ', 'Concurrent quiz', 1, 1,
                '2025-01-01', '2027-01-01', 'OPEN', 1001);
    END;
    IF NOT rejected THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Second open quiz accepted'; END IF;
    SET rejected = FALSE;
    BEGIN
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION SET rejected = TRUE;
        INSERT INTO edu_activity_target
            (id, activity_id, teaching_class_id, student_user_id, enrollment_id, captured_at)
        VALUES (1, 1, 1, 201, 2, '2025-01-01');
    END;
    IF NOT rejected THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cross-class activity target accepted'; END IF;
    INSERT INTO edu_activity_target
        (id, activity_id, teaching_class_id, student_user_id, enrollment_id, captured_at)
    VALUES (1, 1, 1, 101, 1, '2025-01-01');

    INSERT INTO edu_resource_page
        (id, resource_version_id, page_no, preview_storage_key)
    VALUES (1, 1, 1, 'private/slides-v1/page-1');
    INSERT INTO edu_class_session
        (id, binding_id, status, started_by_user_id, started_at)
    VALUES (1, 1, 'RUNNING', 1001, '2025-01-07');
    INSERT INTO edu_session_projection
        (session_id, binding_id, state_seq, release_id, resource_version_id, page_no)
    VALUES (1, 1, 1, 2, 1, 1);
    INSERT INTO edu_session_event
        (id, session_id, seq_no, event_kind, visibility, public_payload_json,
         actor_user_id, occurred_at)
    VALUES (1, 1, 1, 'PAGE_CHANGED', 'PUBLIC', '{"page":1}', 1001, '2025-01-07');
    SET rejected = FALSE;
    BEGIN
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION SET rejected = TRUE;
        INSERT INTO edu_session_event
            (id, session_id, seq_no, event_kind, visibility,
             actor_user_id, occurred_at)
        VALUES (2, 1, 1, 'PAGE_CHANGED', 'PUBLIC', 1001, '2025-01-07');
    END;
    IF NOT rejected THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate session sequence accepted'; END IF;

    INSERT INTO edu_ai_task
        (id, requester_user_id, requester_role, binding_id, conversation_key,
         request_key, question_hash, model_code, prompt_template_version,
         status, answer_storage_key, content_retention_until, created_at, finished_at)
    VALUES (1, 101, 'STUDENT', 1, UNHEX(REPEAT('11', 16)),
            UNHEX(REPEAT('22', 16)), UNHEX(REPEAT('33', 32)),
            'test-model', 'm0-v1', 'SUCCEEDED', 'private/ai-answer-1',
            '2025-02-07', '2025-01-07', '2025-01-07');
    INSERT INTO edu_ai_citation
        (id, ai_task_id, binding_id, release_id, resource_version_id, page_no, sort_order)
    VALUES (1, 1, 1, 2, 1, 1, 1);

    ROLLBACK;
END//
DELIMITER ;
CALL verify_edu_m0_invariants();
DROP PROCEDURE verify_edu_m0_invariants;
SELECT 'V3.0 M0 invariant checks passed' AS result;
