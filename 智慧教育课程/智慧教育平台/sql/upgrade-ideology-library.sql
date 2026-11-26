-- 课程思政素材库（阶段一）
-- 1) 在公共资源分类中新增 IDEOLOGY（课程思政）分类，共 6 个思政主题
-- 2) 预置一批思政外链素材，教师也可在同一分类下自行上传（无需审核）
-- 可重复执行，通过 NOT EXISTS 防护

DROP PROCEDURE IF EXISTS upgrade_ideology_library;

DELIMITER $$
CREATE PROCEDURE upgrade_ideology_library()
BEGIN
    DECLARE v_admin_id BIGINT;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'edu_public_resource_category'
    ) THEN
        SELECT '缺少公共资源库表，请先执行 upgrade-public-resource-library.sql' AS notice;
    ELSE

        SELECT user_id INTO v_admin_id FROM sys_user WHERE user_name = 'admin' LIMIT 1;
        IF v_admin_id IS NULL THEN
            SELECT MIN(user_id) INTO v_admin_id FROM sys_user;
        END IF;

        -- ============ 1. 思政主题分类 ============
        INSERT INTO edu_public_resource_category (parent_id, name, kind, sort_order, status)
        SELECT 0, '家国情怀与政治认同', 'IDEOLOGY', 1, '0' FROM DUAL
        WHERE NOT EXISTS (SELECT 1 FROM edu_public_resource_category WHERE kind = 'IDEOLOGY' AND name = '家国情怀与政治认同');

        INSERT INTO edu_public_resource_category (parent_id, name, kind, sort_order, status)
        SELECT 0, '科学精神与创新意识', 'IDEOLOGY', 2, '0' FROM DUAL
        WHERE NOT EXISTS (SELECT 1 FROM edu_public_resource_category WHERE kind = 'IDEOLOGY' AND name = '科学精神与创新意识');

        INSERT INTO edu_public_resource_category (parent_id, name, kind, sort_order, status)
        SELECT 0, '工匠精神与职业操守', 'IDEOLOGY', 3, '0' FROM DUAL
        WHERE NOT EXISTS (SELECT 1 FROM edu_public_resource_category WHERE kind = 'IDEOLOGY' AND name = '工匠精神与职业操守');

        INSERT INTO edu_public_resource_category (parent_id, name, kind, sort_order, status)
        SELECT 0, '文化自信与传统美德', 'IDEOLOGY', 4, '0' FROM DUAL
        WHERE NOT EXISTS (SELECT 1 FROM edu_public_resource_category WHERE kind = 'IDEOLOGY' AND name = '文化自信与传统美德');

        INSERT INTO edu_public_resource_category (parent_id, name, kind, sort_order, status)
        SELECT 0, '法治意识与社会责任', 'IDEOLOGY', 5, '0' FROM DUAL
        WHERE NOT EXISTS (SELECT 1 FROM edu_public_resource_category WHERE kind = 'IDEOLOGY' AND name = '法治意识与社会责任');

        INSERT INTO edu_public_resource_category (parent_id, name, kind, sort_order, status)
        SELECT 0, '生态文明与可持续发展', 'IDEOLOGY', 6, '0' FROM DUAL
        WHERE NOT EXISTS (SELECT 1 FROM edu_public_resource_category WHERE kind = 'IDEOLOGY' AND name = '生态文明与可持续发展');

        -- ============ 2. 预置思政素材（外链） ============
        INSERT INTO edu_public_resource
            (title, category_id, resource_kind, description, tags, difficulty, status, submitter_user_id)
        SELECT '红色档案：建党百年历史文献专题', c.id, 'LINK',
               '以档案文献呈现中国共产党百年奋斗历程，可用于导入课程思政第一课、党史学习专题。',
               '家国情怀,党史学习,理想信念', 'EASY', 'APPROVED', v_admin_id
          FROM edu_public_resource_category c
         WHERE c.kind = 'IDEOLOGY' AND c.name = '家国情怀与政治认同'
           AND NOT EXISTS (SELECT 1 FROM edu_public_resource r WHERE r.title = '红色档案：建党百年历史文献专题');

        INSERT INTO edu_public_resource
            (title, category_id, resource_kind, description, tags, difficulty, status, submitter_user_id)
        SELECT '中国探月工程：嫦娥工程纪实', c.id, 'LINK',
               '介绍嫦娥探月工程的技术突破与航天人奋斗故事，适合融入工科、理科课程的家国情怀与科技报国教育。',
               '航天精神,科技强国,家国情怀', 'MEDIUM', 'APPROVED', v_admin_id
          FROM edu_public_resource_category c
         WHERE c.kind = 'IDEOLOGY' AND c.name = '家国情怀与政治认同'
           AND NOT EXISTS (SELECT 1 FROM edu_public_resource r WHERE r.title = '中国探月工程：嫦娥工程纪实');

        INSERT INTO edu_public_resource
            (title, category_id, resource_kind, description, tags, difficulty, status, submitter_user_id)
        SELECT '屠呦呦与青蒿素：一次跨越数十年的科学攻坚', c.id, 'LINK',
               '以青蒿素发现历程讲解科学方法、团队协作与坚持真理，适合融入实验类、医学类课程。',
               '科学精神,严谨求实,团队协作', 'MEDIUM', 'APPROVED', v_admin_id
          FROM edu_public_resource_category c
         WHERE c.kind = 'IDEOLOGY' AND c.name = '科学精神与创新意识'
           AND NOT EXISTS (SELECT 1 FROM edu_public_resource r WHERE r.title = '屠呦呦与青蒿素：一次跨越数十年的科学攻坚');

        INSERT INTO edu_public_resource
            (title, category_id, resource_kind, description, tags, difficulty, status, submitter_user_id)
        SELECT '袁隆平与杂交水稻：把论文写在祖国大地上', c.id, 'LINK',
               '讲述杂交水稻攻关历程与科学家精神，适合融入农林、生物、工程类课程的创新意识教育。',
               '科学家精神,创新意识,实干担当', 'EASY', 'APPROVED', v_admin_id
          FROM edu_public_resource_category c
         WHERE c.kind = 'IDEOLOGY' AND c.name = '科学精神与创新意识'
           AND NOT EXISTS (SELECT 1 FROM edu_public_resource r WHERE r.title = '袁隆平与杂交水稻：把论文写在祖国大地上');

        INSERT INTO edu_public_resource
            (title, category_id, resource_kind, description, tags, difficulty, status, submitter_user_id)
        SELECT '大国工匠年度人物事迹展播', c.id, 'LINK',
               '展现高技能人才精益求精的职业追求，适合融入制造、建筑、电子信息等专业课的工匠精神教育。',
               '工匠精神,精益求精,职业操守', 'EASY', 'APPROVED', v_admin_id
          FROM edu_public_resource_category c
         WHERE c.kind = 'IDEOLOGY' AND c.name = '工匠精神与职业操守'
           AND NOT EXISTS (SELECT 1 FROM edu_public_resource r WHERE r.title = '大国工匠年度人物事迹展播');

        INSERT INTO edu_public_resource
            (title, category_id, resource_kind, description, tags, difficulty, status, submitter_user_id)
        SELECT '典籍里的中国：中华优秀传统文化专题', c.id, 'LINK',
               '以典籍故事呈现中华文明脉络，适合融入人文、艺术、通识类课程的文化自信教育。',
               '文化自信,传统文化,家国情怀', 'EASY', 'APPROVED', v_admin_id
          FROM edu_public_resource_category c
         WHERE c.kind = 'IDEOLOGY' AND c.name = '文化自信与传统美德'
           AND NOT EXISTS (SELECT 1 FROM edu_public_resource r WHERE r.title = '典籍里的中国：中华优秀传统文化专题');

        INSERT INTO edu_public_resource
            (title, category_id, resource_kind, description, tags, difficulty, status, submitter_user_id)
        SELECT '中华人民共和国民法典（全文）', c.id, 'LINK',
               '民法典全文与权威解读，适合融入经管、工程、信息类课程的法治意识与社会责任教育。',
               '法治意识,社会责任,规则意识', 'MEDIUM', 'APPROVED', v_admin_id
          FROM edu_public_resource_category c
         WHERE c.kind = 'IDEOLOGY' AND c.name = '法治意识与社会责任'
           AND NOT EXISTS (SELECT 1 FROM edu_public_resource r WHERE r.title = '中华人民共和国民法典（全文）');

        INSERT INTO edu_public_resource
            (title, category_id, resource_kind, description, tags, difficulty, status, submitter_user_id)
        SELECT '绿水青山就是金山银山：生态文明建设案例', c.id, 'LINK',
               '生态文明建设典型案例汇编，适合融入环境、能源、化工、土木类课程的可持续发展教育。',
               '生态文明,可持续发展,社会责任', 'MEDIUM', 'APPROVED', v_admin_id
          FROM edu_public_resource_category c
         WHERE c.kind = 'IDEOLOGY' AND c.name = '生态文明与可持续发展'
           AND NOT EXISTS (SELECT 1 FROM edu_public_resource r WHERE r.title = '绿水青山就是金山银山：生态文明建设案例');

        -- ============ 3. 为预置素材补齐版本记录 ============
        INSERT INTO edu_public_resource_version
            (resource_id, resource_kind, version_no, storage_key, external_uri, mime_type, processing_status, created_by_user_id)
        SELECT r.id, 'LINK', 1, NULL,
               CASE r.title
                   WHEN '红色档案：建党百年历史文献专题' THEN 'https://www.12371.cn/'
                   WHEN '中国探月工程：嫦娥工程纪实' THEN 'https://www.cnsa.gov.cn/'
                   WHEN '屠呦呦与青蒿素：一次跨越数十年的科学攻坚' THEN 'https://www.cas.cn/'
                   WHEN '袁隆平与杂交水稻：把论文写在祖国大地上' THEN 'https://www.caas.cn/'
                   WHEN '大国工匠年度人物事迹展播' THEN 'https://tv.cctv.com/'
                   WHEN '典籍里的中国：中华优秀传统文化专题' THEN 'https://tv.cctv.com/'
                   WHEN '中华人民共和国民法典（全文）' THEN 'http://www.npc.gov.cn/'
                   WHEN '绿水青山就是金山银山：生态文明建设案例' THEN 'https://www.mee.gov.cn/'
               END,
               'text/uri-list', 'READY', r.submitter_user_id
          FROM edu_public_resource r
          JOIN edu_public_resource_category c ON c.id = r.category_id AND c.kind = 'IDEOLOGY'
         WHERE NOT EXISTS (
                   SELECT 1 FROM edu_public_resource_version v
                    WHERE v.resource_id = r.id AND v.version_no = 1
               );

    END IF;
END$$
DELIMITER ;

CALL upgrade_ideology_library();

DROP PROCEDURE IF EXISTS upgrade_ideology_library;
