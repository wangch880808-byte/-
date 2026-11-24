-- 公共教学资源库（最小可用版本）
-- 1) 公共资源分类表
-- 2) 公共资源主表
-- 3) 公共资源版本表
-- 4) 公共资源预览页表
-- 5) 公共资源收藏表
-- 首次新增表使用，可重复执行（通过存储过程 + IF NOT EXISTS 防护）

DROP PROCEDURE IF EXISTS upgrade_public_resource_library;

DELIMITER $$
CREATE PROCEDURE upgrade_public_resource_library()
BEGIN

    -- ============ 1. 公共资源分类表 ============
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'edu_public_resource_category'
    ) THEN
        CREATE TABLE edu_public_resource_category (
            id BIGINT NOT NULL AUTO_INCREMENT,
            parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父分类ID，0表示顶级',
            name VARCHAR(60) NOT NULL COMMENT '分类名称',
            kind VARCHAR(16) NOT NULL DEFAULT 'RESOURCE' COMMENT '分类类型：RESOURCE 资源分类 / SUBJECT 学科分类',
            sort_order INT NOT NULL DEFAULT 0 COMMENT '排序号',
            status CHAR(1) NOT NULL DEFAULT '0' COMMENT '状态：0 正常 / 1 停用',
            created_by_user_id BIGINT NULL COMMENT '创建者用户ID',
            created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
            PRIMARY KEY (id),
            KEY idx_public_category_parent (parent_id),
            KEY idx_public_category_kind (kind)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公共资源分类表';
    END IF;

    -- 初始化默认分类
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'edu_public_resource_category'
    ) AND NOT EXISTS (
        SELECT 1 FROM edu_public_resource_category WHERE kind = 'RESOURCE' AND parent_id = 0 LIMIT 1
    ) THEN
        INSERT INTO edu_public_resource_category (parent_id, name, kind, sort_order, status) VALUES
            (0, '课件教案', 'RESOURCE', 1, '0'),
            (0, '试题试卷', 'RESOURCE', 2, '0'),
            (0, '教学视频', 'RESOURCE', 3, '0'),
            (0, '在线图书', 'RESOURCE', 4, '0'),
            (0, '工具软件', 'RESOURCE', 5, '0'),
            (0, '其他资源', 'RESOURCE', 99, '0');
    END IF;

    -- ============ 2. 公共资源主表 ============
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'edu_public_resource'
    ) THEN
        CREATE TABLE edu_public_resource (
            id BIGINT NOT NULL AUTO_INCREMENT,
            title VARCHAR(200) NOT NULL COMMENT '资源标题',
            category_id BIGINT NULL COMMENT '分类ID',
            resource_kind VARCHAR(16) NOT NULL COMMENT '资源类型：FILE 文件 / LINK 外链',
            description TEXT NULL COMMENT '资源描述',
            tags VARCHAR(500) NULL COMMENT '标签，逗号分隔',
            difficulty VARCHAR(16) NULL COMMENT '难度：EASY / MEDIUM / HARD',
            view_count INT NOT NULL DEFAULT 0 COMMENT '浏览次数',
            download_count INT NOT NULL DEFAULT 0 COMMENT '下载次数',
            rating_avg DECIMAL(3,2) NOT NULL DEFAULT 0.00 COMMENT '平均评分',
            rating_count INT NOT NULL DEFAULT 0 COMMENT '评分人数',
            status VARCHAR(16) NOT NULL DEFAULT 'APPROVED' COMMENT '状态：APPROVED 已发布 / PENDING 待审核 / REJECTED 已驳回 / ARCHIVED 已归档',
            submitter_user_id BIGINT NOT NULL COMMENT '提交者用户ID',
            reviewer_user_id BIGINT NULL COMMENT '审核者用户ID',
            review_comment VARCHAR(500) NULL COMMENT '审核意见',
            submitted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '提交时间',
            reviewed_at DATETIME(3) NULL COMMENT '审核时间',
            created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
            PRIMARY KEY (id),
            KEY idx_public_resource_category (category_id),
            KEY idx_public_resource_status (status),
            KEY idx_public_resource_submitter (submitter_user_id),
            KEY idx_public_resource_kind (resource_kind),
            KEY idx_public_resource_created (created_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公共教学资源库主表';
    END IF;

    -- ============ 3. 公共资源版本表 ============
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'edu_public_resource_version'
    ) THEN
        CREATE TABLE edu_public_resource_version (
            id BIGINT NOT NULL AUTO_INCREMENT,
            resource_id BIGINT NOT NULL COMMENT '关联资源ID',
            resource_kind VARCHAR(16) NOT NULL COMMENT '类型：FILE / LINK',
            version_no INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '版本号',
            storage_key VARCHAR(512) NULL COMMENT '文件存储键（FILE类型）',
            external_uri VARCHAR(1024) NULL COMMENT '外部链接URI（LINK类型）',
            sha256 CHAR(64) NULL COMMENT '文件哈希',
            mime_type VARCHAR(120) NULL COMMENT 'MIME类型',
            byte_size BIGINT UNSIGNED NULL COMMENT '文件大小',
            processing_status VARCHAR(16) NOT NULL DEFAULT 'READY' COMMENT '处理状态：PENDING / READY / FAILED',
            created_by_user_id BIGINT NOT NULL COMMENT '创建者用户ID',
            created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            PRIMARY KEY (id),
            KEY idx_public_version_resource (resource_id),
            UNIQUE KEY uk_public_version_resource_no (resource_id, version_no)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公共资源版本表';
    END IF;

    -- ============ 4. 公共资源预览页表 ============
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'edu_public_resource_page'
    ) THEN
        CREATE TABLE edu_public_resource_page (
            id BIGINT NOT NULL AUTO_INCREMENT,
            resource_version_id BIGINT NOT NULL COMMENT '资源版本ID',
            page_no INT NOT NULL COMMENT '页码',
            preview_storage_key VARCHAR(512) NOT NULL COMMENT '预览图存储键',
            width_px INT NULL COMMENT '宽度像素',
            height_px INT NULL COMMENT '高度像素',
            PRIMARY KEY (id),
            KEY idx_public_page_version (resource_version_id),
            UNIQUE KEY uk_public_page_version_no (resource_version_id, page_no)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公共资源预览页表';
    END IF;

    -- ============ 5. 公共资源收藏表 ============
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'edu_public_resource_favorite'
    ) THEN
        CREATE TABLE edu_public_resource_favorite (
            id BIGINT NOT NULL AUTO_INCREMENT,
            resource_id BIGINT NOT NULL COMMENT '资源ID',
            user_id BIGINT NOT NULL COMMENT '用户ID',
            created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            PRIMARY KEY (id),
            UNIQUE KEY uk_public_favorite_user_resource (user_id, resource_id),
            KEY idx_public_favorite_resource (resource_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公共资源收藏表';
    END IF;

END$$
DELIMITER ;

CALL upgrade_public_resource_library();
DROP PROCEDURE IF EXISTS upgrade_public_resource_library;
