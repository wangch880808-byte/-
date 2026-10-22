-- Local demo only. Run after the RuoYi system tables and M0 teaching demo rows exist.
-- Set @demo_password_hash to a BCrypt hash in this MySQL session before running.
-- The hash is deliberately absent from Git; never commit a plaintext password.
-- High demo IDs advance MySQL AUTO_INCREMENT counters; use only in a disposable test DB.
-- No menu grants are added: role identity works, while getRouters may be empty.
USE zhijiao_m0;
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS edu_demo_login_seed_20261022;
DELIMITER //
CREATE PROCEDURE edu_demo_login_seed_20261022()
BEGIN
    DECLARE v_hash VARCHAR(100);
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    SET v_hash = @demo_password_hash;
    IF v_hash IS NULL OR CHAR_LENGTH(v_hash) <> 60
       OR LEFT(v_hash, 4) NOT IN ('$2a$', '$2b$', '$2y$') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Set @demo_password_hash to a BCrypt hash first';
    END IF;
    IF (SELECT COUNT(*) FROM edu_class_teacher
        WHERE user_id = 930000000001) <> 2
       OR (SELECT COUNT(*) FROM edu_class_student
           WHERE user_id IN (930000000101, 930000000102,
                             930000000103, 930000000104)) <> 4 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'M0 teaching demo IDs are missing or changed';
    END IF;
    IF EXISTS (SELECT 1 FROM sys_dept WHERE dept_id = 920000000001)
       OR EXISTS (SELECT 1 FROM sys_role
                  WHERE role_id IN (940000000001, 940000000002)
                     OR role_key IN ('teacher', 'student'))
       OR EXISTS (SELECT 1 FROM sys_user
                  WHERE user_id IN (930000000001, 930000000101,
                                    930000000102, 930000000103,
                                    930000000104)
                     OR user_name IN ('teacher_demo', 'student_demo_1',
                                      'student_demo_2', 'student_demo_3',
                                      'student_demo_4')) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Demo department, roles or accounts already exist';
    END IF;

    START TRANSACTION;
    INSERT INTO sys_dept
        (dept_id, parent_id, ancestors, dept_name, order_num,
         status, del_flag, create_by, create_time)
    VALUES (920000000001, 0, '0', '演示学校', 1,
            '0', '0', 'demo-seed', NOW());

    -- Scope 5 is "self" in RuoYi. Teaching-class access remains a separate check.
    INSERT INTO sys_role
        (role_id, role_name, role_key, role_sort, data_scope,
         menu_check_strictly, dept_check_strictly, status, del_flag,
         create_by, create_time, remark)
    VALUES (940000000001, '演示教师', 'teacher', 10, '5',
            1, 1, '0', '0', 'demo-seed', NOW(), '本机登录联调'),
           (940000000002, '演示学生', 'student', 11, '5',
            1, 1, '0', '0', 'demo-seed', NOW(), '本机登录联调');

    INSERT INTO sys_user
        (user_id, dept_id, user_name, nick_name, user_type,
         email, phonenumber, sex, avatar, password,
         status, del_flag, create_by, create_time, remark)
    VALUES (930000000001, 920000000001, 'teacher_demo', '演示教师', '00',
            '', '', '2', '', v_hash, '0', '0', 'demo-seed', NOW(), '虚构测试账号'),
           (930000000101, 920000000001, 'student_demo_1', '演示学生甲', '00',
            '', '', '2', '', v_hash, '0', '0', 'demo-seed', NOW(), '虚构测试账号'),
           (930000000102, 920000000001, 'student_demo_2', '演示学生乙', '00',
            '', '', '2', '', v_hash, '0', '0', 'demo-seed', NOW(), '虚构测试账号'),
           (930000000103, 920000000001, 'student_demo_3', '演示学生丙', '00',
            '', '', '2', '', v_hash, '0', '0', 'demo-seed', NOW(), '虚构测试账号'),
           (930000000104, 920000000001, 'student_demo_4', '演示学生丁', '00',
            '', '', '2', '', v_hash, '0', '0', 'demo-seed', NOW(), '虚构测试账号');

    INSERT INTO sys_user_role (user_id, role_id)
    VALUES (930000000001, 940000000001),
           (930000000101, 940000000002),
           (930000000102, 940000000002),
           (930000000103, 940000000002),
           (930000000104, 940000000002);
    COMMIT;
END//
DELIMITER ;
CALL edu_demo_login_seed_20261022();
DROP PROCEDURE edu_demo_login_seed_20261022;
SET @demo_password_hash = NULL;

-- Expected: 5 demo accounts, 2 roles, all five IDs present in education tables.
SELECT u.user_id, u.user_name, r.role_key, u.dept_id
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id = u.user_id
JOIN sys_role r ON r.role_id = ur.role_id
WHERE u.user_id IN (930000000001, 930000000101, 930000000102,
                    930000000103, 930000000104)
ORDER BY u.user_id;
