# 6-02 通知写入真实 MySQL 加固结果

> 日期：2026-10-30｜负责人：6A｜状态：MySQL 8.0.43 已实测；MySQL 8.0.46 未验证；真实上游/HTTP/登录/角色/审计未联调

## 1. 环境与范围

- MySQL：`8.0.43`，InnoDB，默认 `REPEATABLE-READ`。
- JDK：17.0.17。
- Redis：本机 `127.0.0.1:6379`（仅上下文测试使用）。
- 测试库为临时实例上的随机数据库，测试结束删除；不使用真实学生数据。
- 未获得 MySQL 8.0.46 二进制或实例，**8.0.46 未验证**；不得用 8.0.43 结果冒充。

## 2. 真实 MySQL 写入验证

测试类：`NoticeMySqlHardeningTest`，通过系统属性启用：

```text
-Dnotice.mysql.url=jdbc:mysql://127.0.0.1:3399/notice_repro?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
-Dnotice.mysql.user=root
-Dnotice.mysql.password=
```

结果（MySQL 8.0.43）：

- 公告 fan-out：首次写入 2 条；重复 fan-out 不增加行数；已有 `read_at`、标题不变；新公告插入的自增 ID 继续递增。
- 上游四类事件：PREVIEW/ACTIVITY/ASSIGNMENT/GRADE 各写入成功；重复投递不增加行数，不覆盖 `read_at`/标题。
- 回滚：使用临时触发器让第二批次失败，第一批 500 条随事务回滚；表内不残留该事件通知。
- 幂等实现：唯一键 `(recipient_user_id, notice_kind, source_id)` + `ON DUPLICATE KEY UPDATE id = id`。
- 注意：MySQL Connector/J 默认 `useAffectedRows=false` 时，重复键的 `ON DUPLICATE` 返回值可能是匹配行数（如 1/2），**不是精确新增数**。幂等结论以“通知行数不增 + readAt 不变”验证，服务返回值不能当作精确插入数。

## 3. 并发两连接验证

场景：连接 A 在事务内执行 `markAllRead`（当前读 `SELECT ... FOR UPDATE`），保持事务 5 秒；连接 B 使用 `innodb_lock_wait_timeout=2` 执行 fan-out 和上游事件插入。

实际结果：

| 操作 | 结果 | 耗时 |
| --- | --- | --- |
| fan-out 插入 | `Lock wait timeout exceeded` | 约 2084 ms |
| 上游事件插入 | `Lock wait timeout exceeded` | 约 2040 ms |
| A 提交后 fan-out | 成功 | — |
| A 提交后事件插入 | 成功 | — |

结论：

- read-all 当前读的 gap lock 确实会阻塞 fan-out/事件批量插入；本次未观察到死锁，表现为锁等待超时。
- 未实现自动重试。原因：InnoDB lock wait timeout/deadlock 会回滚整个 Spring 事务，服务内部在已回滚事务中重试不安全；应由公告发布/上游事件调用方在事务回滚后重试整个操作，唯一键保证重试幂等。该设计已写入 fan-out/事件服务行内注释。
- 该阻塞仍是联调风险，需在真实 fan-out 并发、生产索引和数据量下复测。

## 4. 合成数据耗时

临时 MySQL 8.0.43，本机测试实例，非生产基准：

| 场景 | 500 人 | 5000 人 |
| --- | --- | --- |
| fan-out 写入耗时 | 约 305 ms | 约 1100 ms |
| 单学生通知列表查询（count + 100 行页） | 约 114 ms | 约 57 ms |
| 单学生未读数查询 | 约 12 ms | 约 34 ms |

说明：耗时为单次运行，受本机磁盘、缓存和 MySQL 临时实例影响；5000 条查询结果已断言 `total=5000`、`unread=5000`。未使用真实学生数据。

## 5. EXPLAIN 结论

测试表按 `docs/database-design.md` 的通知表字段和现有索引建立，数据量 5000 行，班级绑定 `100`、学生 `9001`。

| SQL | type | key | rows | Extra |
| --- | --- | --- | --- | --- |
| 列表 count | ref | `uq_edu_notice_recipient_source` | 约 2500 | Using where |
| 列表分页 | ref | `uq_edu_notice_recipient_source` | 约 2500 | Using where; Using filesort |
| 未读数 count | ref | `uq_edu_notice_recipient_source` | 约 2500 | Using where |
| read-all UPDATE | ref | `uq_edu_notice_recipient_source` | 约 2500 | Using where |
| read-all 当前读 ID | ref | `uq_edu_notice_recipient_source` | 约 2500 | Using where |
| fan-out/事件 INSERT | ALL | NULL | — | EXPLAIN 不显示唯一键检查 |

结论：

- 现有唯一键 `uq_edu_notice_recipient_source` 被用于接收人过滤；`idx_edu_notice_recipient_read`、`idx_edu_notice_binding` 未被选中。
- 列表分页出现 `Using filesort`；可评估 `(recipient_user_id, visible_at, id)` 索引，但本 PR 不直接改生产。
- 插入语句的 `EXPLAIN` 不显示唯一键校验信息；实际幂等由真实 MySQL 写入测试验证。
- 索引草案：`docs/6-02-notice-index-draft.sql`，**待审核，不执行**。

## 6. Spring Boot 上下文

- 新增 `ruoyi-admin` 启动级 `@SpringBootTest`（`RuoYiApplicationContextTest`），默认跳过；提供 MySQL/Redis 属性后执行。
- 使用 MySQL 8.0.43 临时库（加载 `sql/upstream/ruoyi/ry_20260417.sql` 与 `quartz.sql`）和本机 Redis，上下文启动成功：1 项测试通过，约 20.1 秒。
- 未验证：真实 HTTP、真实登录、角色拦截、`@Log` 审计落库。阻塞原因：
  - 真实登录/账号/Token 依赖第 1 组联调配置；
  - `education:announcement:*`/`education:notice:*` 菜单、角色、权限数据未配置；
  - 审计落库需要真实角色、账号和操作链路，当前上下文测试为 `MOCK`，不发 HTTP。
- 不把上下文启动成功写成完整生产联调完成。

## 7. 未验证与待联合项

- MySQL 8.0.46。
- 真实上游投递、鉴权、重试与对账。
- 生产 fan-out 并发、生产索引和真实数据量下的锁等待/死锁。
- 真实 HTTP/登录/角色拦截/审计落库。
- `contentSnapshot` 不落库（通知表无正文字段）。
