-- 2026-10-28 前后端联调：为 edu_term 增加「当前学期」标记
-- 背景：三端页面（学期管理）需要展示/切换当前学期，原 edu_term 只有起止日期，无法持久化“当前”状态。
-- 影响：新增 is_current 列与单当前学期唯一约束；不改动既有列和数据含义。
-- 执行：mysql -uroot -p zhijiao_m0 < migration-20261028-term-current.sql

USE zhijiao_m0;

ALTER TABLE edu_term
    ADD COLUMN is_current TINYINT(1) NOT NULL DEFAULT 0 AFTER ends_on,
    -- 生成列 + 唯一索引：只允许一行 is_current=1，0 值在生成列里为 NULL，不参与唯一约束
    ADD COLUMN current_flag TINYINT GENERATED ALWAYS AS (IF(is_current = 1, 1, NULL)) STORED,
    ADD UNIQUE KEY uq_edu_term_single_current (current_flag);

-- 演示数据：DEMO-TERM 设为当前学期
UPDATE edu_term SET is_current = 0;
UPDATE edu_term SET is_current = 1 WHERE code = 'DEMO-TERM';
