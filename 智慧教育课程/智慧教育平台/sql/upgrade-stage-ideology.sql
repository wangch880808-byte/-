-- 课程思政「环节思政融入点」（阶段二）
-- 在每个已发布教学环节（资料阅读 / 预习任务 / 讨论）上标注思政元素与融入说明，
-- 思政元素复用公共资源分类 kind = 'IDEOLOGY'，不新建元素字典。
-- 可重复执行。

DROP PROCEDURE IF EXISTS upgrade_stage_ideology;

DELIMITER $$
CREATE PROCEDURE upgrade_stage_ideology()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'edu_stage_ideology'
    ) THEN
        CREATE TABLE edu_stage_ideology (
            id BIGINT NOT NULL AUTO_INCREMENT,
            binding_id BIGINT NOT NULL COMMENT '教学班绑定ID',
            course_space_id BIGINT NULL COMMENT '课程空间ID',
            stage_kind VARCHAR(16) NOT NULL COMMENT '环节类型：RESOURCE 资料阅读 / QUIZ 预习任务 / DISCUSSION 讨论',
            stage_id BIGINT NOT NULL COMMENT '环节标识：RESOURCE=resourceId，QUIZ=assignmentId，DISCUSSION=topicId',
            category_id BIGINT NOT NULL COMMENT '思政元素（公共资源分类 kind=IDEOLOGY）',
            note VARCHAR(500) NULL COMMENT '融入说明（学生可见）',
            created_by_user_id BIGINT NULL COMMENT '设置教师用户ID',
            created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
            PRIMARY KEY (id),
            UNIQUE KEY uk_stage_ideology (binding_id, stage_kind, stage_id, category_id),
            KEY idx_stage_ideology_stage (binding_id, stage_kind, stage_id),
            KEY idx_stage_ideology_category (category_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='环节思政融入点';
    END IF;
END$$
DELIMITER ;

CALL upgrade_stage_ideology();

DROP PROCEDURE IF EXISTS upgrade_stage_ideology;
