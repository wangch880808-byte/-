# 6-02 学生公告/通知授权：Druid 配置时间语义验证

日期：2026-11-25

分支：`proto/6-02-notice-druid-timezone`

验证基线：`4e6383c002af56d19f4382dd5debdd42e58c52d3`（#40 head，TREE `83bd1930819ddedb6fb74b376c18ae3121497598`）

## 1. 目的与结论

本轮验证当前仓库 Druid 数据源配置下，学生公告/通知授权链路的事件接收范围、入退班边界、通知可见时间和已读时间是否正确。

结论限定为：**当前仓库配置在隔离 MySQL 8.0.43、合成数据、最小 Spring 上下文中的验证通过**。A 组在 JVM `UTC` 和 `Asia/Shanghai` 下均为 4/4 通过；未发现需要 B 组的偏移或边界错误。

该结论不代表：

- 完整生产应用启动验证；
- 真实 HTTP、登录、角色拦截或审计；
- 生产 Druid 全量自动配置、监控过滤器或生产连接池运行状态；
- 线上部署验证；
- MySQL 8.0.46 验证。

## 2. 实际配置与版本

读取并使用的仓库文件/类：

- `backend/ruoyi-admin/src/main/resources/application.yml`
- `backend/ruoyi-admin/src/main/resources/application-druid.yml`
- `backend/ruoyi-framework/src/main/java/com/ruoyi/framework/config/DruidConfig.java`
- `backend/ruoyi-framework/src/main/java/com/ruoyi/framework/config/properties/DruidProperties.java`

版本：

- Java 17（Microsoft OpenJDK 17.0.14）
- Spring Boot 3.5.16
- Druid `druid-spring-boot-3-starter` / `druid` 1.2.28
- MySQL Connector/J 9.7.0
- MySQL Server 8.0.43

`application-druid.yml` 当前 master URL 的关键连接参数：

```text
useUnicode=true
characterEncoding=utf8
zeroDateTimeBehavior=convertToNull
useSSL=false
serverTimezone=UTC
allowPublicKeyRetrieval=true
```

Druid 池参数来自同一 YAML：`initialSize=5`、`minIdle=10`、`maxActive=20`、`validationQuery=SELECT 1 FROM DUAL`。

## 3. 实际装配方式

测试未手工 `new DruidDataSource`。最小 Spring 上下文加载实际 `application.yml` 与 `application-druid.yml` 的 YAML 属性源，注册：

- `org.springframework.boot.autoconfigure.jdbc.DataSourceProperties`（真实 Boot 属性绑定）；
- `DruidProperties`；
- `DruidConfig`；
- 测试配置提供的真实 MyBatis `SqlSessionFactory` / `SqlSessionTemplate`。

由 `DruidConfig` 创建实际入口：

- `masterDataSource`：真实 `DruidDataSource`；
- `dynamicDataSource`：真实 `DynamicDataSource`，作为业务 Mapper/Service 的 `@Primary` 数据源。

测试从 `dynamicDataSource` 取连接并装载精简 schema，业务侧使用：

- 第 2 组 `EduMembershipTimelineServiceImpl`、`EduMembershipTimelineMapper` 及 XML；
- `MembershipTimelineStudentAnnouncementAuthorizationAdapter`；
- `AnnouncementNoticeFanoutService`；
- `UpstreamNoticeEventService`；
- `StudentNoticeService`、`StudentNoticeMapper` 及 XML。

固定 `Clock`、SecurityContext 中的测试用户、精简 schema 是测试夹具；不代表真实登录、角色拦截或完整生产 schema。

最小上下文未加载 Web、Redis、Controller、Druid 监控页面/过滤器自动配置；`DruidConfig` 与 `DruidProperties` 的池参数和实际 `masterDataSource`/`dynamicDataSource` 已装配。

## 4. A 组连接规则

A 组从实际 `application-druid.yml` 读取默认 master URL，只替换：

- host/port：隔离实例 `127.0.0.1:56352`；
- database：每测试随机 `notice_druid_it_<hex>`；
- username/password：一次性隔离实例临时凭据。

保留默认 URL 的全部查询参数。A 组没有：

- 执行 `SET time_zone`；
- 使用 `UtcSessionDataSource`；
- 添加 `connectionTimeZone` 或 `forceConnectionTimeZoneToSession`；
- 修改 YAML、`DruidConfig`、`DruidProperties` 或生产 SQL。

数据库每次测试随机库，结束删除；未连接现有业务库，未修改全局时区。

## 5. 实测记录

### JVM `Asia/Shanghai`

- 测试：`NoticeDruidTimezoneIntegrationTest`，4/4 通过
- MySQL：8.0.43
- global time_zone：`SYSTEM`
- session time_zone：`SYSTEM`
- system time_zone：中国标准时间（+08:00）
- `TIMEDIFF(NOW(), UTC_TIMESTAMP())`：`08:00:00`
- isolation：`REPEATABLE-READ`
- 池：`initialSize=5`、`minIdle=10`、`maxActive=20`
- 连接复用记录：同时借出 `CONNECTION_ID=56,55`，归还后重新借用 `56`；`active=0`、`pooling=5`、`create=5`
- 业务结果：已知 DATETIME(3) 值、入退班 ±1ms、事件 occurredAt/读取 now 分离、visibleAt/readAt 毫秒值、池复用后授权与查询结果均符合 UTC 约定

### JVM `UTC`

- 测试：`NoticeDruidTimezoneIntegrationTest`，4/4 通过
- global time_zone：`SYSTEM`
- session time_zone：`SYSTEM`
- system time_zone：中国标准时间（+08:00）
- `TIMEDIFF(NOW(), UTC_TIMESTAMP())`：`08:00:00`
- isolation：`REPEATABLE-READ`
- 池：`initialSize=5`、`minIdle=10`、`maxActive=20`
- 连接复用记录：同时借出 `CONNECTION_ID=84,83`，归还后重新借用 `84`；`active=0`、`pooling=5`、`create=5`
- 业务结果：与 `Asia/Shanghai` 一致，无 8 小时偏移

两种 JVM 时区下，数据库会话均不是 UTC（`SYSTEM`，实际 +08:00），但当前 Connector/J + `serverTimezone=UTC` 配置下业务查询和写入结果保持 UTC 语义。该观察只说明本链路在当前隔离配置下正确，不能把 `session.time_zone` 当作业务正确性的唯一依据。

## 6. 覆盖场景

新增测试类：

`backend/ruoyi-admin/src/test/java/com/ruoyi/admin/notice/NoticeDruidTimezoneIntegrationTest.java`

新增测试夹具：

`backend/ruoyi-admin/src/test/resources/mysql/notice-druid-minimal-schema.sql`

覆盖：

1. 以 SQL 字面量写入 `DATETIME(3)`，从真实 Druid 连接读回，校验 `2026-11-20 08:00:00.123`、`10:00:00.456`、绑定 `published_at/ended_at`，避免写入和读取同时偏移仍误判一致。
2. `joinedAt` 包含、`leftAt` 不包含，覆盖边界及前后 1ms；真实查询和授权结果一致。
3. 事件 9 点发生、学生 10 点退班、之后投递：接收人按 9 点确定；11 点读取按当前成员段拒绝，已投递通知仍保留。
4. 公告 fan-out 的 `visibleAt=09:00:00.123` 和已读 `readAt=09:30:00.000` 保持毫秒值，无 8 小时偏移。
5. 通过实际 `dynamicDataSource` 同时取得两个物理连接并记录 `CONNECTION_ID`；归还后重新借用，校验连接有效、会话时区、业务授权和查询结果。

未发现需要 B 组的最小配置调整。A 组通过后未扩展无关场景。

## 7. 运行与打包

- 目标测试：
  - `mvn -B -o -pl ruoyi-admin -am -DskipTests test-compile`
  - `mvn -B -o -pl ruoyi-admin -am -Dtest=NoticeDruidTimezoneIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -Dnotice.mysql.url=jdbc:mysql://127.0.0.1:56352/notice_druid -Dnotice.mysql.user=root test`
  - 分别附加 `-Duser.timezone=UTC` 和 `-Duser.timezone=Asia/Shanghai`
- 默认无 `notice.mysql.url`：4 项跳过，符合预期。
- `ruoyi-admin` 打包成功；测试类和测试 SQL 位于 `src/test/**`，未进入 `ruoyi-admin.jar` 或 `ruoyi-education-3.9.2.jar`。
- 本轮未重复运行全部既有回归。

## 8. 清理

- 每测试随机库已删除；基础库 `notice_druid` 已删除。
- 隔离 MySQL 实例已通过 `mysqladmin shutdown` 停止，进程退出，`56352` 无监听。
- 数据/日志目录 `G:/mysql-6-02-druid-tz` 已核对归属后删除。
- 本机既有 3306 MySQL 服务未被停止或修改。

## 9. 边界

固定 Clock、测试身份、SecurityContext、精简 schema、最小 Spring 上下文和显式测试凭据是测试夹具。真实 MySQL 通过仅代表数据库集成证据；不代表真实 HTTP、登录、角色权限、审计、生产事件调度、生产 Druid 监控/过滤器或 MySQL 8.0.46 已通过。

本轮未修改业务实现、Mapper/XML、生产 SQL、`application*.yml`、`DruidConfig`、`DruidProperties`、既有 9 项 MySQL 测试或依赖版本。如后续需要修改 Mapper SQL 或生产 Druid 配置，应另立任务并复审。
