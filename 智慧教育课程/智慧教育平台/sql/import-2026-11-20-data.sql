-- 2026-11-20 后录入的教务组织与授权增量
-- 在已执行项目 sql/install-fresh.sql 的独立演示库中运行。
-- 使用同一份脚本可重复导入。任何已有记录冲突会中止并回滚本次业务写入。
-- 这个数据脚本会恢复源库对原演示组织和账号的停用记录，以及解除的授权关系。
-- 可共享版保留初始化昵称，清空新增组织的负责人字段。
-- 密码、登录与审计日志、Redis 临时数据和旧联调记录未纳入。

SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS `edu_import_admin_data_20261120`;
DELIMITER $$
CREATE PROCEDURE `edu_import_admin_data_20261120`()
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        DROP TEMPORARY TABLE IF EXISTS `tmp_edu_admin_depts_20261120`;
        RESIGNAL;
    END;

    START TRANSACTION;
    CREATE TEMPORARY TABLE `tmp_edu_admin_depts_20261120` LIKE `sys_dept`;
    INSERT INTO `tmp_edu_admin_depts_20261120`
        (`dept_id`, `parent_id`, `ancestors`, `dept_name`, `order_num`, `leader`, `phone`, `email`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`, `dept_type`, `grade_year`, `education_level`, `study_years`, `class_remark`)
    VALUES
        (920000000002, 0, '0', '演示学校', 1, NULL, NULL, NULL, '0', '2', 'admin', '2026-11-20 09:29:52', '', NULL, NULL, NULL, NULL, NULL, NULL),
        (920000000003, 920000000002, '0,920000000002', '人工智能学院', 0, '', NULL, NULL, '0', '2', 'admin', '2026-11-20 09:29:52', '', NULL, NULL, NULL, NULL, NULL, NULL),
        (920000000004, 920000000003, '0,920000000002,920000000003', '智能科学与技术', 0, NULL, NULL, NULL, '0', '2', 'admin', '2026-11-20 09:29:52', '', NULL, NULL, NULL, NULL, NULL, NULL),
        (920000000005, 0, '0', '周口师范学院', 1, '', '', '', '0', '0', 'admin', '2026-11-20 09:31:46', 'admin', '2026-11-20 09:32:09', 'SCHOOL', NULL, NULL, NULL, NULL),
        (920000000006, 920000000005, '0,920000000005', '人工智能学院', 1, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'COLLEGE', NULL, NULL, NULL, NULL),
        (920000000007, 920000000005, '0,920000000005', '计算机学院', 2, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'COLLEGE', NULL, NULL, NULL, NULL),
        (920000000008, 920000000005, '0,920000000005', '电子信息学院', 3, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'COLLEGE', NULL, NULL, NULL, NULL),
        (920000000009, 920000000005, '0,920000000005', '教师教育学院', 4, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'COLLEGE', NULL, NULL, NULL, NULL),
        (920000000010, 920000000006, '0,920000000005,920000000006', '智能科学与技术', 1, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'MAJOR', NULL, NULL, NULL, NULL),
        (920000000011, 920000000006, '0,920000000005,920000000006', '人工智能', 2, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'MAJOR', NULL, NULL, NULL, NULL),
        (920000000012, 920000000006, '0,920000000005,920000000006', '数据科学与大数据技术', 3, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'MAJOR', NULL, NULL, NULL, NULL),
        (920000000013, 920000000007, '0,920000000005,920000000007', '计算机科学与技术', 1, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'MAJOR', NULL, NULL, NULL, NULL),
        (920000000014, 920000000007, '0,920000000005,920000000007', '软件工程', 2, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'MAJOR', NULL, NULL, NULL, NULL),
        (920000000015, 920000000007, '0,920000000005,920000000007', '网络工程', 3, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'MAJOR', NULL, NULL, NULL, NULL),
        (920000000016, 920000000008, '0,920000000005,920000000008', '电子信息工程', 1, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'MAJOR', NULL, NULL, NULL, NULL),
        (920000000017, 920000000008, '0,920000000005,920000000008', '通信工程', 2, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'MAJOR', NULL, NULL, NULL, NULL),
        (920000000018, 920000000009, '0,920000000005,920000000009', '小学教育', 1, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'MAJOR', NULL, NULL, NULL, NULL),
        (920000000019, 920000000009, '0,920000000005,920000000009', '教育技术学', 2, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'MAJOR', NULL, NULL, NULL, NULL),
        (920000000020, 920000000010, '0,920000000005,920000000006,920000000010', '2024级智能科学与技术本科一班', 1, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2024, '本科', 4, NULL),
        (920000000021, 920000000010, '0,920000000005,920000000006,920000000010', '2024级智能科学与技术本科二班', 2, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2024, '本科', 4, NULL),
        (920000000022, 920000000010, '0,920000000005,920000000006,920000000010', '2025级智能科学与技术本科一班', 3, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2025, '本科', 4, NULL),
        (920000000023, 920000000010, '0,920000000005,920000000006,920000000010', '2025级智能科学与技术本科二班', 4, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2025, '本科', 4, NULL),
        (920000000024, 920000000010, '0,920000000005,920000000006,920000000010', '2026级智能科学与技术本科一班', 5, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2026, '本科', 4, NULL),
        (920000000025, 920000000010, '0,920000000005,920000000006,920000000010', '2026级智能科学与技术本科二班', 6, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2026, '本科', 4, NULL),
        (920000000026, 920000000011, '0,920000000005,920000000006,920000000011', '2024级人工智能本科一班', 1, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2024, '本科', 4, NULL),
        (920000000027, 920000000011, '0,920000000005,920000000006,920000000011', '2024级人工智能本科二班', 2, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2024, '本科', 4, NULL),
        (920000000028, 920000000011, '0,920000000005,920000000006,920000000011', '2025级人工智能本科一班', 3, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2025, '本科', 4, NULL),
        (920000000029, 920000000011, '0,920000000005,920000000006,920000000011', '2025级人工智能本科二班', 4, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2025, '本科', 4, NULL),
        (920000000030, 920000000011, '0,920000000005,920000000006,920000000011', '2026级人工智能本科一班', 5, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2026, '本科', 4, NULL),
        (920000000031, 920000000011, '0,920000000005,920000000006,920000000011', '2026级人工智能本科二班', 6, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2026, '本科', 4, NULL),
        (920000000032, 920000000012, '0,920000000005,920000000006,920000000012', '2024级数据科学与大数据技术本科一班', 1, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2024, '本科', 4, NULL),
        (920000000033, 920000000012, '0,920000000005,920000000006,920000000012', '2024级数据科学与大数据技术本科二班', 2, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2024, '本科', 4, NULL),
        (920000000034, 920000000012, '0,920000000005,920000000006,920000000012', '2025级数据科学与大数据技术本科一班', 3, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2025, '本科', 4, NULL),
        (920000000035, 920000000012, '0,920000000005,920000000006,920000000012', '2025级数据科学与大数据技术本科二班', 4, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2025, '本科', 4, NULL),
        (920000000036, 920000000012, '0,920000000005,920000000006,920000000012', '2026级数据科学与大数据技术本科一班', 5, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2026, '本科', 4, NULL),
        (920000000037, 920000000012, '0,920000000005,920000000006,920000000012', '2026级数据科学与大数据技术本科二班', 6, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2026, '本科', 4, NULL),
        (920000000038, 920000000013, '0,920000000005,920000000007,920000000013', '2024级计算机科学与技术本科一班', 1, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2024, '本科', 4, NULL),
        (920000000039, 920000000013, '0,920000000005,920000000007,920000000013', '2024级计算机科学与技术本科二班', 2, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2024, '本科', 4, NULL),
        (920000000040, 920000000013, '0,920000000005,920000000007,920000000013', '2025级计算机科学与技术本科一班', 3, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2025, '本科', 4, NULL),
        (920000000041, 920000000013, '0,920000000005,920000000007,920000000013', '2025级计算机科学与技术本科二班', 4, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2025, '本科', 4, NULL),
        (920000000042, 920000000013, '0,920000000005,920000000007,920000000013', '2026级计算机科学与技术本科一班', 5, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2026, '本科', 4, NULL),
        (920000000043, 920000000013, '0,920000000005,920000000007,920000000013', '2026级计算机科学与技术本科二班', 6, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2026, '本科', 4, NULL),
        (920000000044, 920000000014, '0,920000000005,920000000007,920000000014', '2024级软件工程本科一班', 1, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2024, '本科', 4, NULL),
        (920000000045, 920000000014, '0,920000000005,920000000007,920000000014', '2024级软件工程本科二班', 2, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2024, '本科', 4, NULL),
        (920000000046, 920000000014, '0,920000000005,920000000007,920000000014', '2025级软件工程本科一班', 3, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2025, '本科', 4, NULL),
        (920000000047, 920000000014, '0,920000000005,920000000007,920000000014', '2025级软件工程本科二班', 4, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2025, '本科', 4, NULL),
        (920000000048, 920000000014, '0,920000000005,920000000007,920000000014', '2026级软件工程本科一班', 5, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2026, '本科', 4, NULL),
        (920000000049, 920000000014, '0,920000000005,920000000007,920000000014', '2026级软件工程本科二班', 6, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2026, '本科', 4, NULL),
        (920000000050, 920000000015, '0,920000000005,920000000007,920000000015', '2024级网络工程本科一班', 1, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2024, '本科', 4, NULL),
        (920000000051, 920000000015, '0,920000000005,920000000007,920000000015', '2024级网络工程本科二班', 2, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2024, '本科', 4, NULL),
        (920000000052, 920000000015, '0,920000000005,920000000007,920000000015', '2025级网络工程本科一班', 3, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2025, '本科', 4, NULL),
        (920000000053, 920000000015, '0,920000000005,920000000007,920000000015', '2025级网络工程本科二班', 4, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2025, '本科', 4, NULL),
        (920000000054, 920000000015, '0,920000000005,920000000007,920000000015', '2026级网络工程本科一班', 5, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2026, '本科', 4, NULL),
        (920000000055, 920000000015, '0,920000000005,920000000007,920000000015', '2026级网络工程本科二班', 6, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2026, '本科', 4, NULL),
        (920000000056, 920000000016, '0,920000000005,920000000008,920000000016', '2024级电子信息工程本科一班', 1, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2024, '本科', 4, NULL),
        (920000000057, 920000000016, '0,920000000005,920000000008,920000000016', '2024级电子信息工程本科二班', 2, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2024, '本科', 4, NULL),
        (920000000058, 920000000016, '0,920000000005,920000000008,920000000016', '2025级电子信息工程本科一班', 3, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2025, '本科', 4, NULL),
        (920000000059, 920000000016, '0,920000000005,920000000008,920000000016', '2025级电子信息工程本科二班', 4, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2025, '本科', 4, NULL),
        (920000000060, 920000000016, '0,920000000005,920000000008,920000000016', '2026级电子信息工程本科一班', 5, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2026, '本科', 4, NULL),
        (920000000061, 920000000016, '0,920000000005,920000000008,920000000016', '2026级电子信息工程本科二班', 6, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2026, '本科', 4, NULL),
        (920000000062, 920000000017, '0,920000000005,920000000008,920000000017', '2024级通信工程本科一班', 1, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2024, '本科', 4, NULL),
        (920000000063, 920000000017, '0,920000000005,920000000008,920000000017', '2024级通信工程本科二班', 2, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2024, '本科', 4, NULL),
        (920000000064, 920000000017, '0,920000000005,920000000008,920000000017', '2025级通信工程本科一班', 3, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2025, '本科', 4, NULL),
        (920000000065, 920000000017, '0,920000000005,920000000008,920000000017', '2025级通信工程本科二班', 4, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2025, '本科', 4, NULL),
        (920000000066, 920000000017, '0,920000000005,920000000008,920000000017', '2026级通信工程本科一班', 5, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2026, '本科', 4, NULL),
        (920000000067, 920000000017, '0,920000000005,920000000008,920000000017', '2026级通信工程本科二班', 6, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2026, '本科', 4, NULL),
        (920000000068, 920000000018, '0,920000000005,920000000009,920000000018', '2024级小学教育本科一班', 1, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2024, '本科', 4, NULL),
        (920000000069, 920000000018, '0,920000000005,920000000009,920000000018', '2024级小学教育本科二班', 2, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2024, '本科', 4, NULL),
        (920000000070, 920000000018, '0,920000000005,920000000009,920000000018', '2025级小学教育本科一班', 3, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2025, '本科', 4, NULL),
        (920000000071, 920000000018, '0,920000000005,920000000009,920000000018', '2025级小学教育本科二班', 4, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2025, '本科', 4, NULL),
        (920000000072, 920000000018, '0,920000000005,920000000009,920000000018', '2026级小学教育本科一班', 5, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2026, '本科', 4, NULL),
        (920000000073, 920000000018, '0,920000000005,920000000009,920000000018', '2026级小学教育本科二班', 6, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2026, '本科', 4, NULL),
        (920000000074, 920000000019, '0,920000000005,920000000009,920000000019', '2024级教育技术学本科一班', 1, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2024, '本科', 4, NULL),
        (920000000075, 920000000019, '0,920000000005,920000000009,920000000019', '2024级教育技术学本科二班', 2, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2024, '本科', 4, NULL),
        (920000000076, 920000000019, '0,920000000005,920000000009,920000000019', '2025级教育技术学本科一班', 3, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2025, '本科', 4, NULL),
        (920000000077, 920000000019, '0,920000000005,920000000009,920000000019', '2025级教育技术学本科二班', 4, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2025, '本科', 4, NULL),
        (920000000078, 920000000019, '0,920000000005,920000000009,920000000019', '2026级教育技术学本科一班', 5, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2026, '本科', 4, NULL),
        (920000000079, 920000000019, '0,920000000005,920000000009,920000000019', '2026级教育技术学本科二班', 6, NULL, NULL, NULL, '0', '0', 'admin', '2026-11-20 09:31:46', '', NULL, 'CLASS', 2026, '本科', 4, NULL);

    IF EXISTS (
        SELECT 1
          FROM `tmp_edu_admin_depts_20261120` p
          JOIN `sys_dept` d ON d.dept_id = p.dept_id
         WHERE NOT (
               d.`parent_id` <=> p.`parent_id`
               AND d.`ancestors` <=> p.`ancestors`
               AND d.`dept_name` <=> p.`dept_name`
               AND d.`order_num` <=> p.`order_num`
               AND d.`leader` <=> p.`leader`
               AND d.`phone` <=> p.`phone`
               AND d.`email` <=> p.`email`
               AND d.`status` <=> p.`status`
               AND d.`del_flag` <=> p.`del_flag`
               AND d.`create_by` <=> p.`create_by`
               AND d.`create_time` <=> p.`create_time`
               AND d.`update_by` <=> p.`update_by`
               AND d.`update_time` <=> p.`update_time`
               AND d.`dept_type` <=> p.`dept_type`
               AND d.`grade_year` <=> p.`grade_year`
               AND d.`education_level` <=> p.`education_level`
               AND d.`study_years` <=> p.`study_years`
               AND d.`class_remark` <=> p.`class_remark`
         )
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Organization IDs already contain different data';
    END IF;

    INSERT INTO `sys_dept` (`dept_id`, `parent_id`, `ancestors`, `dept_name`, `order_num`, `leader`, `phone`, `email`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`, `dept_type`, `grade_year`, `education_level`, `study_years`, `class_remark`)
    SELECT p.`dept_id`, p.`parent_id`, p.`ancestors`, p.`dept_name`, p.`order_num`, p.`leader`, p.`phone`, p.`email`, p.`status`, p.`del_flag`, p.`create_by`, p.`create_time`, p.`update_by`, p.`update_time`, p.`dept_type`, p.`grade_year`, p.`education_level`, p.`study_years`, p.`class_remark`
      FROM `tmp_edu_admin_depts_20261120` p
      LEFT JOIN `sys_dept` d ON d.dept_id = p.dept_id
     WHERE d.dept_id IS NULL
     ORDER BY p.dept_id;

    IF NOT EXISTS (SELECT 1 FROM `sys_dept` WHERE `dept_id` <=> 100 AND `dept_name` <=> '若依科技' AND `parent_id` <=> 0 AND `del_flag` <=> '2') THEN
        UPDATE `sys_dept`
           SET `del_flag` = '2'
         WHERE `dept_id` <=> 100 AND `dept_name` <=> '若依科技' AND `parent_id` <=> 0 AND `del_flag` <=> '0';
        IF ROW_COUNT() <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting demo record: sys_dept #100';
        END IF;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM `sys_dept` WHERE `dept_id` <=> 101 AND `dept_name` <=> '深圳总公司' AND `parent_id` <=> 100 AND `del_flag` <=> '2') THEN
        UPDATE `sys_dept`
           SET `del_flag` = '2'
         WHERE `dept_id` <=> 101 AND `dept_name` <=> '深圳总公司' AND `parent_id` <=> 100 AND `del_flag` <=> '0';
        IF ROW_COUNT() <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting demo record: sys_dept #101';
        END IF;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM `sys_dept` WHERE `dept_id` <=> 102 AND `dept_name` <=> '长沙分公司' AND `parent_id` <=> 100 AND `del_flag` <=> '2') THEN
        UPDATE `sys_dept`
           SET `del_flag` = '2'
         WHERE `dept_id` <=> 102 AND `dept_name` <=> '长沙分公司' AND `parent_id` <=> 100 AND `del_flag` <=> '0';
        IF ROW_COUNT() <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting demo record: sys_dept #102';
        END IF;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM `sys_dept` WHERE `dept_id` <=> 103 AND `dept_name` <=> '研发部门' AND `parent_id` <=> 101 AND `del_flag` <=> '2') THEN
        UPDATE `sys_dept`
           SET `del_flag` = '2'
         WHERE `dept_id` <=> 103 AND `dept_name` <=> '研发部门' AND `parent_id` <=> 101 AND `del_flag` <=> '0';
        IF ROW_COUNT() <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting demo record: sys_dept #103';
        END IF;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM `sys_dept` WHERE `dept_id` <=> 104 AND `dept_name` <=> '市场部门' AND `parent_id` <=> 101 AND `del_flag` <=> '2') THEN
        UPDATE `sys_dept`
           SET `del_flag` = '2'
         WHERE `dept_id` <=> 104 AND `dept_name` <=> '市场部门' AND `parent_id` <=> 101 AND `del_flag` <=> '0';
        IF ROW_COUNT() <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting demo record: sys_dept #104';
        END IF;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM `sys_dept` WHERE `dept_id` <=> 105 AND `dept_name` <=> '测试部门' AND `parent_id` <=> 101 AND `del_flag` <=> '2') THEN
        UPDATE `sys_dept`
           SET `del_flag` = '2'
         WHERE `dept_id` <=> 105 AND `dept_name` <=> '测试部门' AND `parent_id` <=> 101 AND `del_flag` <=> '0';
        IF ROW_COUNT() <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting demo record: sys_dept #105';
        END IF;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM `sys_dept` WHERE `dept_id` <=> 106 AND `dept_name` <=> '财务部门' AND `parent_id` <=> 101 AND `del_flag` <=> '2') THEN
        UPDATE `sys_dept`
           SET `del_flag` = '2'
         WHERE `dept_id` <=> 106 AND `dept_name` <=> '财务部门' AND `parent_id` <=> 101 AND `del_flag` <=> '0';
        IF ROW_COUNT() <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting demo record: sys_dept #106';
        END IF;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM `sys_dept` WHERE `dept_id` <=> 107 AND `dept_name` <=> '运维部门' AND `parent_id` <=> 101 AND `del_flag` <=> '2') THEN
        UPDATE `sys_dept`
           SET `del_flag` = '2'
         WHERE `dept_id` <=> 107 AND `dept_name` <=> '运维部门' AND `parent_id` <=> 101 AND `del_flag` <=> '0';
        IF ROW_COUNT() <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting demo record: sys_dept #107';
        END IF;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM `sys_dept` WHERE `dept_id` <=> 108 AND `dept_name` <=> '市场部门' AND `parent_id` <=> 102 AND `del_flag` <=> '2') THEN
        UPDATE `sys_dept`
           SET `del_flag` = '2'
         WHERE `dept_id` <=> 108 AND `dept_name` <=> '市场部门' AND `parent_id` <=> 102 AND `del_flag` <=> '0';
        IF ROW_COUNT() <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting demo record: sys_dept #108';
        END IF;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM `sys_dept` WHERE `dept_id` <=> 109 AND `dept_name` <=> '财务部门' AND `parent_id` <=> 102 AND `del_flag` <=> '2') THEN
        UPDATE `sys_dept`
           SET `del_flag` = '2'
         WHERE `dept_id` <=> 109 AND `dept_name` <=> '财务部门' AND `parent_id` <=> 102 AND `del_flag` <=> '0';
        IF ROW_COUNT() <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting demo record: sys_dept #109';
        END IF;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM `sys_dept` WHERE `dept_id` <=> 920000000001 AND `dept_name` <=> '演示学校' AND `parent_id` <=> 0 AND `del_flag` <=> '2') THEN
        UPDATE `sys_dept`
           SET `del_flag` = '2'
         WHERE `dept_id` <=> 920000000001 AND `dept_name` <=> '演示学校' AND `parent_id` <=> 0 AND `del_flag` <=> '0';
        IF ROW_COUNT() <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting demo record: sys_dept #920000000001';
        END IF;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM `sys_user` WHERE `user_id` <=> 1 AND `user_name` <=> 'admin' AND `dept_id` <=> NULL) THEN
        UPDATE `sys_user`
           SET `dept_id` = NULL
         WHERE `user_id` <=> 1 AND `user_name` <=> 'admin' AND `dept_id` <=> 920000000001;
        IF ROW_COUNT() <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting demo record: sys_user #1';
        END IF;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM `sys_user` WHERE `user_id` <=> 2 AND `user_name` <=> 'ry' AND `del_flag` <=> '2') THEN
        UPDATE `sys_user`
           SET `del_flag` = '2'
         WHERE `user_id` <=> 2 AND `user_name` <=> 'ry' AND `del_flag` <=> '0';
        IF ROW_COUNT() <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting demo record: sys_user #2';
        END IF;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM `sys_user` WHERE `user_id` <=> 930000000001 AND `user_name` <=> 'teacher' AND `dept_id` <=> 920000000006) THEN
        UPDATE `sys_user`
           SET `dept_id` = 920000000006
         WHERE `user_id` <=> 930000000001 AND `user_name` <=> 'teacher' AND `dept_id` <=> 920000000001;
        IF ROW_COUNT() <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting demo record: sys_user #930000000001';
        END IF;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM `sys_user` WHERE `user_id` <=> 930000000101 AND `user_name` <=> 'student' AND `dept_id` <=> 920000000020) THEN
        UPDATE `sys_user`
           SET `dept_id` = 920000000020
         WHERE `user_id` <=> 930000000101 AND `user_name` <=> 'student' AND `dept_id` <=> 920000000001;
        IF ROW_COUNT() <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting demo record: sys_user #930000000101';
        END IF;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM `sys_user` WHERE `user_id` <=> 930000000102 AND `user_name` <=> 'student2' AND `del_flag` <=> '2') THEN
        UPDATE `sys_user`
           SET `del_flag` = '2'
         WHERE `user_id` <=> 930000000102 AND `user_name` <=> 'student2' AND `del_flag` <=> '0';
        IF ROW_COUNT() <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting demo record: sys_user #930000000102';
        END IF;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM `sys_user` WHERE `user_id` <=> 930000000103 AND `user_name` <=> 'student3' AND `del_flag` <=> '2') THEN
        UPDATE `sys_user`
           SET `del_flag` = '2'
         WHERE `user_id` <=> 930000000103 AND `user_name` <=> 'student3' AND `del_flag` <=> '0';
        IF ROW_COUNT() <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting demo record: sys_user #930000000103';
        END IF;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM `sys_user` WHERE `user_id` <=> 930000000104 AND `user_name` <=> 'student4' AND `del_flag` <=> '2') THEN
        UPDATE `sys_user`
           SET `del_flag` = '2'
         WHERE `user_id` <=> 930000000104 AND `user_name` <=> 'student4' AND `del_flag` <=> '0';
        IF ROW_COUNT() <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting demo record: sys_user #930000000104';
        END IF;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM `sys_role` WHERE `role_id` <=> 2 AND `role_key` <=> 'common' AND `del_flag` <=> '2') THEN
        UPDATE `sys_role`
           SET `del_flag` = '2'
         WHERE `role_id` <=> 2 AND `role_key` <=> 'common' AND `del_flag` <=> '0';
        IF ROW_COUNT() <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting demo record: sys_role #2';
        END IF;
    END IF;

    DELETE FROM `sys_role_dept`
     WHERE (`role_id`, `dept_id`) IN (
           (2, 100),
           (2, 101),
           (2, 105)
     );

    DELETE FROM `sys_role_menu`
     WHERE (`role_id`, `menu_id`) IN (
           (2, 1),
           (2, 2),
           (2, 3),
           (2, 4),
           (2, 100),
           (2, 101),
           (2, 102),
           (2, 103),
           (2, 104),
           (2, 105),
           (2, 106),
           (2, 107),
           (2, 108),
           (2, 109),
           (2, 110),
           (2, 111),
           (2, 112),
           (2, 113),
           (2, 114),
           (2, 115),
           (2, 116),
           (2, 117),
           (2, 500),
           (2, 501),
           (2, 1000),
           (2, 1001),
           (2, 1002),
           (2, 1003),
           (2, 1004),
           (2, 1005),
           (2, 1006),
           (2, 1007),
           (2, 1008),
           (2, 1009),
           (2, 1010),
           (2, 1011),
           (2, 1012),
           (2, 1013),
           (2, 1014),
           (2, 1015),
           (2, 1016),
           (2, 1017),
           (2, 1018),
           (2, 1019),
           (2, 1020),
           (2, 1021),
           (2, 1022),
           (2, 1023),
           (2, 1024),
           (2, 1025),
           (2, 1026),
           (2, 1027),
           (2, 1028),
           (2, 1029),
           (2, 1030),
           (2, 1031),
           (2, 1032),
           (2, 1033),
           (2, 1034),
           (2, 1035),
           (2, 1036),
           (2, 1037),
           (2, 1038),
           (2, 1039),
           (2, 1040),
           (2, 1041),
           (2, 1042),
           (2, 1043),
           (2, 1044),
           (2, 1045),
           (2, 1046),
           (2, 1047),
           (2, 1048),
           (2, 1049),
           (2, 1050),
           (2, 1051),
           (2, 1052),
           (2, 1053),
           (2, 1054),
           (2, 1055),
           (2, 1056),
           (2, 1057),
           (2, 1058),
           (2, 1059),
           (2, 1060)
     );

    DELETE FROM `sys_user_post`
     WHERE (`user_id`, `post_id`) IN (
           (2, 2)
     );

    DELETE FROM `sys_user_role`
     WHERE (`user_id`, `role_id`) IN (
           (2, 2),
           (930000000102, 940000000002),
           (930000000103, 940000000002),
           (930000000104, 940000000002)
     );

    DROP TEMPORARY TABLE `tmp_edu_admin_depts_20261120`;
    COMMIT;
END$$
DELIMITER ;
CALL `edu_import_admin_data_20261120`();
DROP PROCEDURE `edu_import_admin_data_20261120`;
