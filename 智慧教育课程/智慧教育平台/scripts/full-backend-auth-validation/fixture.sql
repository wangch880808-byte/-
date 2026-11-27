SET NAMES utf8mb4;

-- 最小授权夹具：仅用于一次性联调库，不代表生产菜单/权限已完成配置。
INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, update_by, update_time, remark)
VALUES
    (950000000001, '教师公告新增', 0, 1, '', NULL, '', '', 1, 0, 'F', '0', '0',
     'education:announcement:add', '#', 'auth-test', NOW(), '', NULL, '联调夹具'),
    (950000000002, '教师公告编辑', 0, 2, '', NULL, '', '', 1, 0, 'F', '0', '0',
     'education:announcement:edit', '#', 'auth-test', NOW(), '', NULL, '联调夹具'),
    (950000000003, '教师公告发布', 0, 3, '', NULL, '', '', 1, 0, 'F', '0', '0',
     'education:announcement:publish', '#', 'auth-test', NOW(), '', NULL, '联调夹具'),
    (950000000004, '教师公告归档', 0, 4, '', NULL, '', '', 1, 0, 'F', '0', '0',
     'education:announcement:archive', '#', 'auth-test', NOW(), '', NULL, '联调夹具'),
    (950000000005, '教师公告查询', 0, 5, '', NULL, '', '', 1, 0, 'F', '0', '0',
     'education:announcement:list', '#', 'auth-test', NOW(), '', NULL, '联调夹具');

INSERT INTO sys_role_menu (role_id, menu_id)
VALUES (940000000001, 950000000001),
       (940000000001, 950000000002),
       (940000000001, 950000000003),
       (940000000001, 950000000004),
       (940000000001, 950000000005);

INSERT INTO sys_role
    (role_id, role_name, role_key, role_sort, data_scope, menu_check_strictly,
     dept_check_strictly, status, del_flag, create_by, create_time, remark)
VALUES (940000000003, '联调助理', 'assistant', 20, '5', 1, 1, '0', '0',
        'auth-test', NOW(), '联调夹具：无公告按钮权限');

SET @demo_hash = (SELECT password FROM sys_user WHERE user_name = 'teacher');

INSERT INTO sys_user
    (user_id, dept_id, user_name, nick_name, user_type, email, phonenumber, sex,
     avatar, password, status, del_flag, create_by, create_time,
     pwd_update_date, remark)
VALUES
    (930000000201, 920000000001, 'teacher_limited', '受限教师', '00', '', '', '2',
     '', @demo_hash, '0', '0', 'auth-test', NOW(), NOW(), '联调夹具'),
    (930000000202, 920000000001, 'teacher_other', '其他班教师', '00', '', '', '2',
     '', @demo_hash, '0', '0', 'auth-test', NOW(), NOW(), '联调夹具'),
    (930000000203, 920000000001, 'teacher_team', '团队教师', '00', '', '', '2',
     '', @demo_hash, '0', '0', 'auth-test', NOW(), NOW(), '联调夹具'),
    (930000000204, 920000000001, 'assistant', '无公告权限助理', '00', '', '', '2',
     '', @demo_hash, '0', '0', 'auth-test', NOW(), NOW(), '联调夹具'),
    (930000000205, 920000000001, 'teacher_nonclass', '非任课教师', '00', '', '', '2',
     '', @demo_hash, '0', '0', 'auth-test', NOW(), NOW(), '联调夹具'),
    (930000000301, 920000000001, 'student_c', '联调学生甲', '00', '', '', '2',
     '', @demo_hash, '0', '0', 'auth-test', NOW(), NOW(), '联调夹具'),
    (930000000302, 920000000001, 'student_c2', '联调学生乙', '00', '', '', '2',
     '', @demo_hash, '0', '0', 'auth-test', NOW(), NOW(), '联调夹具');

INSERT INTO sys_user_role (user_id, role_id)
VALUES (930000000201, 940000000001),
       (930000000202, 940000000001),
       (930000000203, 940000000001),
       (930000000204, 940000000003),
       (930000000205, 940000000001),
       (930000000301, 940000000002),
       (930000000302, 940000000002);

INSERT INTO edu_class_teacher
    (teaching_class_id, user_id, teacher_role, can_publish, can_grade, can_export,
     valid_from, valid_to, granted_by_user_id)
VALUES
    (910000000001, 930000000201, 'CO_TEACHER', 0, 1, 0, NOW() - INTERVAL 10 DAY, NULL, 930000000001),
    (910000000002, 930000000202, 'LEAD', 1, 1, 1, NOW() - INTERVAL 10 DAY, NULL, 930000000001);

INSERT INTO edu_course_teacher
    (course_space_id, user_id, team_role, joined_at, added_by_user_id)
VALUES (910000000001, 930000000203, 'MEMBER', NOW() - INTERVAL 10 DAY, 930000000001);

-- 专用干净教学班 C：无演示公告/通知，供成功链路精确计数。
INSERT INTO edu_teaching_class
    (id, organization_id, term_id, official_course_id, code, name, class_kind, status)
VALUES (910000000003, 920000000001, 910000000001, 910000000001,
        'IT-C', '联调教学班 C', 'FORMAL', 'ACTIVE');

INSERT INTO edu_course_class_binding
    (id, organization_id, teaching_class_id, course_space_id, bound_by_user_id, published_at)
VALUES (910000000003, 920000000001, 910000000003, 910000000001,
        930000000001, NOW() - INTERVAL 14 DAY);

INSERT INTO edu_class_teacher
    (teaching_class_id, user_id, teacher_role, can_publish, can_grade, can_export,
     valid_from, valid_to, granted_by_user_id)
VALUES (910000000003, 930000000001, 'LEAD', 1, 1, 1,
        NOW() - INTERVAL 10 DAY, NULL, 930000000001);

INSERT INTO edu_class_student
    (teaching_class_id, user_id, joined_at, recorded_by_user_id)
VALUES (910000000003, 930000000301, NOW() - INTERVAL 8 DAY, 930000000001),
       (910000000003, 930000000302, NOW() - INTERVAL 8 DAY, 930000000001);

SELECT u.user_name, r.role_key,
       (SELECT GROUP_CONCAT(m.perms ORDER BY m.menu_id)
        FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id = rm.menu_id
        WHERE rm.role_id = r.role_id) AS perms
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id = u.user_id
JOIN sys_role r ON r.role_id = ur.role_id
WHERE u.user_name IN ('teacher', 'student', 'teacher_limited', 'teacher_other',
                      'teacher_team', 'assistant', 'teacher_nonclass')
ORDER BY u.user_name;
