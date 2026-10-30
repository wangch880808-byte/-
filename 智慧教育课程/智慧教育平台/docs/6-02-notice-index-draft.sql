-- 6-02 学生通知索引草案
-- 状态：待审核，禁止直接在生产执行。
-- 目的：优化学生通知列表按 visible_at DESC, id DESC 排序时的 filesort。
-- 现有索引：
--   uq_edu_notice_recipient_source (recipient_user_id, notice_kind, source_id)
--     用于 fan-out/事件写入幂等；不要删除。
--   idx_edu_notice_recipient_read (recipient_user_id, read_at, visible_at)
--     用于未读统计/read-all 候选过滤。
--   idx_edu_notice_binding (binding_id)
--     用于按绑定过滤的候选路径。
-- 锁注意：新增二级索引会增加 INSERT 的索引维护写放大；read-all 当前读产生
-- 的 gap lock 可能落在新索引范围上，必须重新做并发插入验证后再评审。
-- 审核条件：MySQL 8.0.46 + 生产数据量 EXPLAIN 通过，且写入延迟可接受。
ALTER TABLE edu_user_notice
    ADD INDEX idx_edu_notice_recipient_visible (recipient_user_id, visible_at, id);
