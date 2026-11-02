-- 6-05 校园资讯、留言、系统通知六表 DDL（草案）
-- 待评审，禁止执行。M1 未排期，本文件不构成数据库迁移执行授权。
-- 未在真实 MySQL 执行；不声称 MySQL 8.0.46 已验证。
-- 仅新增 6-05 独立表；不修改 M0 schema；不含迁移自动挂接、真实学生数据或凭据。

-- 1. 校园资讯：组织范围发布、撤回、归档；正文由服务端清洗后存储。
CREATE TABLE edu_campus_news (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '资讯主键',
    organization_id BIGINT NOT NULL COMMENT '组织ID；若依部门逻辑引用，不建跨组外键',
    title VARCHAR(200) NOT NULL COMMENT '标题',
    summary VARCHAR(500) NULL COMMENT '摘要；可为空',
    body MEDIUMTEXT NOT NULL COMMENT '正文；服务端按白名单清洗后存储',
    cover_storage_key VARCHAR(512) NULL COMMENT '封面私有对象键；不是公开URL',
    audience_kind VARCHAR(20) NOT NULL DEFAULT 'ORGANIZATION' COMMENT '受众类型：ORGANIZATION/ROLE/TEACHING_CLASS',
    audience_ref_id BIGINT NULL COMMENT '受众引用；组织级为空，角色/教学班为逻辑ID',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PUBLISHED/WITHDRAWN/ARCHIVED',
    published_at DATETIME(3) NULL COMMENT '发布时间UTC',
    published_by_user_id BIGINT NULL COMMENT '发布人用户ID',
    withdrawn_at DATETIME(3) NULL COMMENT '撤回时间UTC',
    withdrawn_by_user_id BIGINT NULL COMMENT '撤回人用户ID',
    withdraw_reason VARCHAR(500) NULL COMMENT '撤回原因；撤回时必填',
    archived_at DATETIME(3) NULL COMMENT '归档时间UTC',
    archived_by_user_id BIGINT NULL COMMENT '归档人用户ID',
    created_by_user_id BIGINT NOT NULL COMMENT '创建人用户ID',
    updated_by_user_id BIGINT NOT NULL COMMENT '最后修改人用户ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
    row_version INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_news_id_org (id, organization_id),
    KEY idx_edu_news_org_status_time (organization_id, status, published_at, id),
    KEY idx_edu_news_audience (organization_id, audience_kind, audience_ref_id, status),
    CONSTRAINT chk_edu_news_status CHECK
        (status IN ('DRAFT', 'PUBLISHED', 'WITHDRAWN', 'ARCHIVED')),
    CONSTRAINT chk_edu_news_audience CHECK
        ((audience_kind = 'ORGANIZATION' AND audience_ref_id IS NULL)
         OR (audience_kind IN ('ROLE', 'TEACHING_CLASS') AND audience_ref_id IS NOT NULL)),
    CONSTRAINT chk_edu_news_publish CHECK
        ((status = 'DRAFT' AND published_at IS NULL AND published_by_user_id IS NULL)
         OR (status IN ('PUBLISHED', 'WITHDRAWN')
             AND published_at IS NOT NULL AND published_by_user_id IS NOT NULL)
         OR status = 'ARCHIVED'),
    CONSTRAINT chk_edu_news_withdraw CHECK
        ((status = 'WITHDRAWN' AND withdrawn_at IS NOT NULL
             AND withdrawn_by_user_id IS NOT NULL AND withdraw_reason IS NOT NULL)
         OR (status IN ('DRAFT', 'PUBLISHED') AND withdrawn_at IS NULL
             AND withdrawn_by_user_id IS NULL AND withdraw_reason IS NULL)
         OR status = 'ARCHIVED'),
    CONSTRAINT chk_edu_news_archive CHECK
        ((status = 'ARCHIVED' AND archived_at IS NOT NULL AND archived_by_user_id IS NOT NULL)
         OR (status <> 'ARCHIVED' AND archived_at IS NULL AND archived_by_user_id IS NULL)),
    CONSTRAINT chk_edu_news_times CHECK
        ((withdrawn_at IS NULL OR published_at IS NULL OR withdrawn_at >= published_at)
         AND (archived_at IS NULL OR archived_at >= created_at)),
    CONSTRAINT chk_edu_news_row_version CHECK (row_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='校园资讯；组织范围发布/撤回/归档，待评审';

-- 2. 留言线程：发起人、目标角色、范围、状态和双方已读时间。
CREATE TABLE edu_message_thread (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '线程主键',
    organization_id BIGINT NOT NULL COMMENT '组织ID；若依部门逻辑引用',
    thread_no VARCHAR(40) NOT NULL COMMENT '组织内业务编号；展示/引用，不单独承担请求幂等',
    create_idempotency_key VARCHAR(80) NOT NULL COMMENT '创建线程幂等键；组织+发起人范围内唯一',
    create_request_hash BINARY(32) NOT NULL COMMENT '创建请求载荷哈希；同键不同载荷冲突',
    scope_kind VARCHAR(20) NOT NULL COMMENT '范围：PLATFORM/COURSE/TEACHING_CLASS',
    scope_ref_id BIGINT NULL COMMENT '课程空间/教学班逻辑ID；PLATFORM为空',
    initiator_user_id BIGINT NOT NULL COMMENT '发起人用户ID',
    initiator_role VARCHAR(16) NOT NULL COMMENT '发起人角色：TEACHER/STUDENT',
    target_role VARCHAR(16) NOT NULL COMMENT '目标处理角色：TEACHER/ADMIN',
    target_user_id BIGINT NULL COMMENT '指定处理人；为空表示组织内角色池',
    subject VARCHAR(200) NOT NULL COMMENT '留言主题',
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN/WAITING/CLOSED',
    last_reply_at DATETIME(3) NULL COMMENT '最后回复时间UTC',
    last_reply_by_user_id BIGINT NULL COMMENT '最后回复人用户ID',
    reply_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '回复数量；事务内维护',
    initiator_last_read_at DATETIME(3) NULL COMMENT '发起人最后已读时间UTC',
    target_last_read_at DATETIME(3) NULL COMMENT '目标角色最后已读时间UTC；多处理人时共享',
    closed_at DATETIME(3) NULL COMMENT '关闭时间UTC',
    closed_by_user_id BIGINT NULL COMMENT '关闭人用户ID',
    close_reason VARCHAR(500) NULL COMMENT '关闭原因；关闭时必填',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
    row_version INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_message_thread_no (organization_id, thread_no),
    UNIQUE KEY uq_edu_message_thread_create_idempotency
        (organization_id, initiator_user_id, create_idempotency_key),
    UNIQUE KEY uq_edu_message_thread_id_org (id, organization_id),
    KEY idx_edu_message_thread_org_status (organization_id, status, last_reply_at, id),
    KEY idx_edu_message_thread_initiator (initiator_user_id, created_at, id),
    KEY idx_edu_message_thread_target (target_user_id, status, last_reply_at, id),
    KEY idx_edu_message_thread_scope (organization_id, scope_kind, scope_ref_id, status),
    CONSTRAINT chk_edu_message_thread_scope CHECK
        ((scope_kind = 'PLATFORM' AND scope_ref_id IS NULL)
         OR (scope_kind IN ('COURSE', 'TEACHING_CLASS') AND scope_ref_id IS NOT NULL)),
    CONSTRAINT chk_edu_message_thread_status CHECK
        (status IN ('OPEN', 'WAITING', 'CLOSED')),
    CONSTRAINT chk_edu_message_thread_initiator_role CHECK
        (initiator_role IN ('TEACHER', 'STUDENT')),
    CONSTRAINT chk_edu_message_thread_target_role CHECK
        (target_role IN ('TEACHER', 'ADMIN')),
    CONSTRAINT chk_edu_message_thread_close CHECK
        ((status = 'CLOSED' AND closed_at IS NOT NULL
             AND closed_by_user_id IS NOT NULL AND close_reason IS NOT NULL)
         OR (status <> 'CLOSED' AND closed_at IS NULL
             AND closed_by_user_id IS NULL AND close_reason IS NULL)),
    CONSTRAINT chk_edu_message_thread_times CHECK
        ((last_reply_at IS NULL OR last_reply_at >= created_at)
         AND (closed_at IS NULL OR closed_at >= created_at)),
    CONSTRAINT chk_edu_message_thread_reply_count CHECK (reply_count >= 0),
    CONSTRAINT chk_edu_message_thread_row_version CHECK (row_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='留言线程主记录；创建幂等，首条正文在回复表';

-- 3. 留言回复：参与者可见回复或管理员内部备注；软删除保留审计。
CREATE TABLE edu_message_reply (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '回复主键',
    organization_id BIGINT NOT NULL COMMENT '组织ID；用于线程同组织约束',
    thread_id BIGINT NOT NULL COMMENT '线程ID',
    sequence_no INT UNSIGNED NOT NULL COMMENT '线程内回复序号；首条正文为1，事务内递增',
    sender_user_id BIGINT NOT NULL COMMENT '回复人用户ID',
    sender_role VARCHAR(16) NOT NULL COMMENT '回复人角色：TEACHER/STUDENT/ADMIN',
    idempotency_key VARCHAR(80) NOT NULL COMMENT '回复幂等键；同线程+发送人内唯一',
    request_hash BINARY(32) NOT NULL COMMENT '回复请求载荷哈希；同键不同载荷冲突',
    body TEXT NOT NULL COMMENT '回复正文；服务端按格式清洗/转义',
    visibility VARCHAR(16) NOT NULL DEFAULT 'PARTICIPANTS' COMMENT '可见性：PARTICIPANTS/ADMIN_INTERNAL',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
    deleted_at DATETIME(3) NULL COMMENT '软删除时间UTC',
    deleted_by_user_id BIGINT NULL COMMENT '软删除人用户ID',
    delete_reason VARCHAR(500) NULL COMMENT '软删除原因',
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_message_reply_id_thread (id, thread_id),
    UNIQUE KEY uq_edu_message_reply_sequence (thread_id, sequence_no),
    UNIQUE KEY uq_edu_message_reply_idempotency
        (thread_id, sender_user_id, idempotency_key),
    KEY idx_edu_message_reply_thread (thread_id, created_at, id),
    KEY idx_edu_message_reply_sender (sender_user_id, created_at, id),
    CONSTRAINT fk_edu_message_reply_thread FOREIGN KEY (thread_id, organization_id)
        REFERENCES edu_message_thread (id, organization_id),
    CONSTRAINT chk_edu_message_reply_sequence CHECK (sequence_no > 0),
    CONSTRAINT chk_edu_message_reply_sender_role CHECK
        (sender_role IN ('TEACHER', 'STUDENT', 'ADMIN')),
    CONSTRAINT chk_edu_message_reply_visibility CHECK
        (visibility IN ('PARTICIPANTS', 'ADMIN_INTERNAL')),
    CONSTRAINT chk_edu_message_reply_delete CHECK
        ((deleted_at IS NULL AND deleted_by_user_id IS NULL AND delete_reason IS NULL)
         OR (deleted_at IS NOT NULL AND deleted_by_user_id IS NOT NULL)),
    CONSTRAINT chk_edu_message_reply_deleted_time CHECK
        (deleted_at IS NULL OR deleted_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='留言回复/内部备注；序号和幂等键防重，软删除保留审计';

-- 4. 系统通知模板：组织内编码+版本；同一编码最多一个 ACTIVE。
CREATE TABLE edu_notification_template (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模板主键',
    organization_id BIGINT NOT NULL COMMENT '组织ID；若依部门逻辑引用',
    template_code VARCHAR(60) NOT NULL COMMENT '组织内稳定模板编码',
    name VARCHAR(120) NOT NULL COMMENT '模板名称',
    channel VARCHAR(16) NOT NULL DEFAULT 'IN_APP' COMMENT '渠道：IN_APP/EMAIL/SMS；M1仅启用IN_APP',
    title_template VARCHAR(200) NOT NULL COMMENT '标题模板；只允许${variable}占位符',
    body_template TEXT NOT NULL COMMENT '正文模板；只允许${variable}占位符',
    variable_schema_json JSON NULL COMMENT '变量声明：名称/类型/必填/最大长度；禁止表达式',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/ACTIVE/ARCHIVED',
    template_version INT UNSIGNED NOT NULL COMMENT '模板版本号；递增保留历史',
    active_template_code VARCHAR(60) GENERATED ALWAYS AS
        (CASE WHEN status = 'ACTIVE' THEN template_code ELSE NULL END) STORED
        COMMENT '仅ACTIVE时等于template_code，用于唯一活跃版本',
    created_by_user_id BIGINT NOT NULL COMMENT '创建人用户ID',
    updated_by_user_id BIGINT NOT NULL COMMENT '最后修改人用户ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
    row_version INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_notification_template_code_version
        (organization_id, template_code, template_version),
    UNIQUE KEY uq_edu_notification_template_active
        (organization_id, active_template_code),
    UNIQUE KEY uq_edu_notification_template_id_org_version_channel
        (id, organization_id, template_version, channel),
    KEY idx_edu_notification_template_org_status
        (organization_id, status, channel),
    CONSTRAINT chk_edu_notification_template_channel CHECK
        (channel IN ('IN_APP', 'EMAIL', 'SMS')),
    CONSTRAINT chk_edu_notification_template_status CHECK
        (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT chk_edu_notification_template_version CHECK (template_version > 0),
    CONSTRAINT chk_edu_notification_template_row_version CHECK (row_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='系统通知模板及版本；同组织同编码仅一个ACTIVE';

-- 5. 逻辑发送任务：一个模板版本、一个受众、一个幂等键；与每次尝试分离。
CREATE TABLE edu_notification_send_job (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '发送任务主键',
    organization_id BIGINT NOT NULL COMMENT '组织ID；若依部门逻辑引用',
    template_id BIGINT NOT NULL COMMENT '模板ID',
    template_version INT UNSIGNED NOT NULL COMMENT '模板版本号；固定发送时版本',
    channel VARCHAR(16) NOT NULL COMMENT '发送渠道；创建时从模板固定，M1为IN_APP',
    sender_user_id BIGINT NOT NULL COMMENT '发起人用户ID',
    idempotency_key VARCHAR(80) NOT NULL COMMENT '幂等键；组织内唯一',
    request_hash BINARY(32) NOT NULL COMMENT '请求载荷哈希；同键不同载荷冲突',
    audience_kind VARCHAR(20) NOT NULL COMMENT '受众：ORGANIZATION/ROLE/TEACHING_CLASS/USER_LIST',
    audience_ref_id BIGINT NULL COMMENT '受众引用；组织级为空',
    audience_json JSON NULL COMMENT '受众明细；USER_LIST等使用',
    payload_json JSON NOT NULL COMMENT '已校验的模板变量；不含凭据/敏感原文',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/PROCESSING/SUCCEEDED/PARTIAL/FAILED/UNKNOWN/CANCELLED',
    scheduled_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '计划发送时间UTC',
    lease_owner VARCHAR(80) NULL COMMENT '持有处理权的worker标识；空表示未领取',
    lease_expires_at DATETIME(3) NULL COMMENT '领取租约到期时间UTC；过期可恢复',
    next_retry_at DATETIME(3) NULL COMMENT '下次自动重试时间UTC；UNKNOWN为空',
    started_at DATETIME(3) NULL COMMENT '开始处理时间UTC',
    finished_at DATETIME(3) NULL COMMENT '结束时间UTC',
    total_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '目标接收人数',
    success_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '成功人数',
    failure_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '失败人数',
    unknown_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '未知结果人数',
    last_error_code VARCHAR(60) NULL COMMENT '最后错误码；受控短文本',
    last_error_message VARCHAR(500) NULL COMMENT '最后错误摘要；不含敏感数据',
    cancelled_at DATETIME(3) NULL COMMENT '取消时间UTC',
    cancelled_by_user_id BIGINT NULL COMMENT '取消人用户ID',
    cancel_reason VARCHAR(500) NULL COMMENT '取消原因',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
    row_version INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_notification_send_job_idempotency
        (organization_id, idempotency_key),
    UNIQUE KEY uq_edu_notification_send_job_id_org_channel
        (id, organization_id, channel),
    KEY idx_edu_notification_send_job_org_status
        (organization_id, status, scheduled_at, id),
    KEY idx_edu_notification_send_job_template
        (organization_id, template_id, template_version, channel),
    KEY idx_edu_notification_send_job_lease (status, lease_expires_at),
    KEY idx_edu_notification_send_job_retry (status, next_retry_at),
    CONSTRAINT fk_edu_notification_send_job_template
        FOREIGN KEY (template_id, organization_id, template_version, channel)
        REFERENCES edu_notification_template
            (id, organization_id, template_version, channel),
    CONSTRAINT chk_edu_notification_send_job_channel CHECK
        (channel IN ('IN_APP', 'EMAIL', 'SMS')),
    CONSTRAINT chk_edu_notification_send_job_status CHECK
        (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'PARTIAL',
                    'FAILED', 'UNKNOWN', 'CANCELLED')),
    CONSTRAINT chk_edu_notification_send_job_audience CHECK
        ((audience_kind = 'ORGANIZATION' AND audience_ref_id IS NULL)
         OR (audience_kind IN ('ROLE', 'TEACHING_CLASS') AND audience_ref_id IS NOT NULL)
         OR (audience_kind = 'USER_LIST' AND audience_json IS NOT NULL)),
    CONSTRAINT chk_edu_notification_send_job_counts CHECK
        (total_count >= 0 AND success_count >= 0
         AND failure_count >= 0 AND unknown_count >= 0
         AND success_count + failure_count + unknown_count <= total_count),
    CONSTRAINT chk_edu_notification_send_job_cancel CHECK
        ((status = 'CANCELLED' AND cancelled_at IS NOT NULL
             AND cancelled_by_user_id IS NOT NULL AND cancel_reason IS NOT NULL)
         OR (status <> 'CANCELLED' AND cancelled_at IS NULL
             AND cancelled_by_user_id IS NULL AND cancel_reason IS NULL)),
    CONSTRAINT chk_edu_notification_send_job_lease CHECK
        ((status = 'PROCESSING' AND lease_owner IS NOT NULL
             AND lease_expires_at IS NOT NULL)
         OR (status <> 'PROCESSING' AND lease_owner IS NULL
             AND lease_expires_at IS NULL)),
    CONSTRAINT chk_edu_notification_send_job_retry CHECK
        (next_retry_at IS NULL OR status IN ('PENDING', 'FAILED', 'PARTIAL')),
    CONSTRAINT chk_edu_notification_send_job_times CHECK
        ((finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at)
         AND (lease_expires_at IS NULL OR lease_expires_at >= created_at)
         AND (next_retry_at IS NULL OR next_retry_at >= created_at)),
    CONSTRAINT chk_edu_notification_send_job_row_version CHECK (row_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='系统通知逻辑发送任务；固定渠道，与每次发送尝试分离';

-- 6. 发送记录：每接收人/每次尝试一行；保留投递、送达、已读和重试历史。
CREATE TABLE edu_notification_send_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '发送记录主键',
    organization_id BIGINT NOT NULL COMMENT '组织ID；用于任务同组织约束',
    job_id BIGINT NOT NULL COMMENT '逻辑发送任务ID',
    recipient_user_id BIGINT NOT NULL COMMENT '接收人用户ID',
    attempt_no INT UNSIGNED NOT NULL COMMENT '尝试序号；从1递增，重试新增行',
    channel VARCHAR(16) NOT NULL COMMENT '渠道：IN_APP/EMAIL/SMS',
    delivery_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/SENT/DELIVERED/READ/FAILED/UNKNOWN',
    provider_message_id VARCHAR(120) NULL COMMENT '供应商消息ID；用于对账',
    error_code VARCHAR(60) NULL COMMENT '错误码；受控短文本',
    error_message VARCHAR(500) NULL COMMENT '错误摘要；不含敏感数据',
    sent_at DATETIME(3) NULL COMMENT '平台接受/发送时间UTC',
    delivered_at DATETIME(3) NULL COMMENT '渠道确认送达时间UTC',
    read_at DATETIME(3) NULL COMMENT '用户显式已读时间UTC；仅站内',
    next_retry_at DATETIME(3) NULL COMMENT '下次重试时间UTC；仅当前有效FAILED',
    superseded_at DATETIME(3) NULL COMMENT '被新尝试替代时间UTC；NULL表示当前有效逻辑通知',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
    row_version INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    active_notification_key VARCHAR(80) GENERATED ALWAYS AS
        (CASE WHEN superseded_at IS NULL
            THEN CONCAT(organization_id, ':', job_id, ':', recipient_user_id)
            ELSE NULL END) STORED
        COMMENT '当前有效逻辑通知键；唯一约束保证每个job+接收人最多一条当前记录',
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_notification_send_record_attempt
        (job_id, recipient_user_id, attempt_no),
    UNIQUE KEY uq_edu_notification_send_record_active
        (active_notification_key),
    KEY idx_edu_notification_send_record_recipient_active
        (recipient_user_id, superseded_at, read_at, delivery_status, created_at, id),
    KEY idx_edu_notification_send_record_job
        (job_id, delivery_status, attempt_no),
    KEY idx_edu_notification_send_record_provider
        (channel, provider_message_id),
    KEY idx_edu_notification_send_record_retry (next_retry_at),
    CONSTRAINT fk_edu_notification_send_record_job
        FOREIGN KEY (job_id, organization_id, channel)
        REFERENCES edu_notification_send_job (id, organization_id, channel),
    CONSTRAINT chk_edu_notification_send_record_channel CHECK
        (channel IN ('IN_APP', 'EMAIL', 'SMS')),
    CONSTRAINT chk_edu_notification_send_record_status CHECK
        (delivery_status IN ('PENDING', 'SENT', 'DELIVERED', 'READ', 'FAILED', 'UNKNOWN')),
    CONSTRAINT chk_edu_notification_send_record_attempt CHECK (attempt_no BETWEEN 1 AND 4),
    CONSTRAINT chk_edu_notification_send_record_status_times CHECK
        ((delivery_status = 'PENDING' AND sent_at IS NULL
             AND delivered_at IS NULL AND read_at IS NULL)
         OR (delivery_status = 'SENT' AND sent_at IS NOT NULL
             AND delivered_at IS NULL AND read_at IS NULL)
         OR (delivery_status = 'DELIVERED' AND sent_at IS NOT NULL
             AND delivered_at IS NOT NULL AND read_at IS NULL)
         OR (delivery_status = 'READ' AND sent_at IS NOT NULL
             AND delivered_at IS NOT NULL AND read_at IS NOT NULL)
         OR (delivery_status IN ('FAILED', 'UNKNOWN') AND read_at IS NULL)),
    CONSTRAINT chk_edu_notification_send_record_read_channel CHECK
        (read_at IS NULL OR channel = 'IN_APP'),
    CONSTRAINT chk_edu_notification_send_record_retry CHECK
        (next_retry_at IS NULL
         OR (delivery_status = 'FAILED' AND superseded_at IS NULL)),
    CONSTRAINT chk_edu_notification_send_record_times CHECK
        ((sent_at IS NULL OR sent_at >= created_at)
         AND (delivered_at IS NULL OR delivered_at >= sent_at)
         AND (read_at IS NULL OR read_at >= delivered_at)
         AND (next_retry_at IS NULL OR next_retry_at >= created_at)
         AND (superseded_at IS NULL OR superseded_at >= created_at)),
    CONSTRAINT chk_edu_notification_send_record_row_version CHECK (row_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='系统通知逻辑通知的每接收人/每次尝试记录；历史尝试保留审计';
