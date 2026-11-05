# 智慧教育课程平台

本项目面向教师、学生与学校管理员，规划将课程建设、课前准备、PPT 授课与同步跟课、课堂活动、课后作业和学情反馈整合为连续的教学工作台。本机项目根目录为 `D:\智慧教育课程-GitHub\智慧教育课程\智慧教育平台`。

**当前状态：已补齐 Vue 3 前端工程，接入已有 2B 教学页面；其余教学业务待开发。** 已接入若依 3.9.2 后端、静态登录页，以及由 `console-navigation-v1` 三端导航原型改造的运行页面；本机 `zhijiao_m0` 已验证管理员、教师、学生的验证码、角色登录、身份查询和退出，教师与学生账号 ID 关联了教学班演示数据。管理员「学期 / 班级 / 教师 / 学生 / 组织架构」、教师「我的课程」、学生「在修课程」已接入真实接口，接口清单与已验证范围见 [前后端联调说明](docs/frontend-backend-integration.md)；其余页面内容区仍是原型占位，静态导航不是已授权业务菜单。Vue 工程启动见 [前端说明](frontend/README.md)，补全范围见 [工程记录](docs/vue-frontend-completion.md)。后端运行步骤见 [后端工程与本地运行](backend/README.md)。

## 教学业务闭环

课程空间与教学班授权 → 资源及知识点建设 → 备课与预习 → 开始课堂、演示 PPT、学生同步跟页 → 签到、随堂题与课堂小作业 → 课后作业与批改 → 教师确认并发布成绩 → 错题、补学与学情反馈 → 下一次备课草稿。

以上均为规划能力。PPT 状态同步与音视频直播分别评审；AI 建议不能直接成为正式成绩。课堂小作业与课后作业规划共用提交、批改和评价能力。

## 需求与范围依据

- [原始需求分析](../智慧教育课程需求分析.docx)：需求编号、业务约束和验收依据，保留于外层工作区。
- [功能架构优化方案 V3.0](../功能架构优化方案.md)：最新信息架构、三端导航和教学工作流建议，保留于外层工作区。
- [精简架构与数据约束](docs/architecture.md)：上述来源的工程映射，不替代原始成果。

原需求的 P0/P1/P2 与架构方案的 M0/M1/M2 不是同一套分级。同步课堂、自建课程及部分 AI 功能的优先级差异必须经过范围评审，不能将建议当作已批准交付承诺。

## 技术架构规划

- 后端已导入若依 RuoYi-Vue `springboot3` 分支固定提交，版本 3.9.2，Spring Boot 3.5.16，Java 17 编译目标；`ruoyi-education` 承载教学业务服务与 Mapper，2B 页面所需接口已实现。
- `frontend/login.html` 和三端运行页由后端同源提供；2B 页面已调用真实接口，其余页面仍是原型占位，正式 Vue 前端工程已补齐，独立入口为 `/app/index.html`，已接入现有教学页面。
- 数据库工作统一置于 `sql/`；[V3.0 M0 数据库设计](docs/database-design.md)与两段教育业务建表脚本覆盖首版教学闭环。本机 `zhijiao_m0` 已整合教育业务演示表和若依登录表，`zhijiao_login_dev` 仅保留为此前的独立测试库。
- 课件转换、实时同步、文件存储与 AI 适配均为后续技术评审内容，暂不指定服务或协议实现。
- 当前后端可用 Maven 构建，运行需要 MySQL 8、Redis 和本地私有 `.env`；前端使用 Vue 3.5.44、Vite 7.3.7、Vue Router 4、Pinia 3 和 Element Plus，依赖版本见 `frontend/package.json`。

## 项目目录

```text
智慧教育平台/
├── README.md
├── AGENTS.md
├── CONTRIBUTING.md
├── backend/
│   ├── README.md
│   ├── pom.xml、run-local.ps1、.env.example
│   ├── ruoyi-admin/、ruoyi-common/、ruoyi-system/
│   ├── ruoyi-framework/、ruoyi-quartz/、ruoyi-generator/
│   └── ruoyi-education/
│       ├── README.md
│       └── src/main/java/com/ruoyi/education/
│           ├── course/       ├── resource/    ├── prepare/
│           ├── classroom/    ├── activity/    ├── assignment/
│           ├── learning/     ├── analysis/    └── ai/
├── frontend/
│   ├── README.md
│   ├── login.html、login.js、logo.png
│   ├── admin.html、teacher.html、student.html
│   ├── role-home.js
│   └── src/
│       ├── views/education/{teacher,student,admin}/
│       ├── api/education/
│       └── components/education/
├── sql/（教育业务脚本与 upstream/ruoyi/）
├── docs/
│   ├── architecture.md
│   ├── api-contracts.md
│   ├── development-plan.md
│   ├── migration-report.md
│   ├── historical-inventory.csv
│   ├── backup-verification.csv
│   └── workspace-verification.json
└── prototypes/README.md
```

`com.ruoyi.education` 已包含学期、教学班、课程空间的服务与 Mapper，以及三端页面共用的 `EduPortalService`；接口控制器在 `ruoyi-admin` 的 `web.controller.education` 包。`frontend/src/` 已包含应用入口、路由、登录状态、共用布局和原有教学页面，具体范围及启动说明见 `frontend/README.md`。

## 三端职责

| 端 | 主要职责 | 权限边界 |
|---|---|---|
| 教师 | 课程资源、备课、预习、授课、活动、批改、发布评价和教学改进 | 仅操作获授权课程与教学班，正式成绩变更留痕 |
| 学生 | 待办、授权资源、预习、同步课堂、提交作业、查看反馈和补学 | 仅访问本人及本班授权内容，不提前获取答案 |
| 管理员 | 组织人员、正式课程、教学班、教学授权、资源治理和运行反馈 | 教务与超级管理员权限分开；默认不能修改教师已确认成绩 |

## 模块划分与协作入口

`ruoyi-education` 内规划九个领域：`course`、`resource`、`prepare`、`classroom`、`activity`、`assignment`、`learning`、`analysis`、`ai`。职责、依赖与调用边界见 [后端规划](backend/README.md)；三端页面与公共能力归属见 [前端规划](frontend/README.md)。

- 开发代理先读 [AGENTS.md](AGENTS.md)，团队成员先读 [CONTRIBUTING.md](CONTRIBUTING.md)。
- 前后端联调从 [接口协作约定](docs/api-contracts.md) 开始。
- 数据库变更遵守 [SQL 工作区规范](sql/README.md)，原型登记遵守 [原型工作区规范](prototypes/README.md)。
- 历史盘点、完整备份、迁移校验与待确认清理项见 [迁移报告](docs/migration-report.md)。

## 后续开发阶段

1. 评审两份来源的范围差异，指定领域负责人；若依后端版本已确定，Vue 前端版本与基础工程已确定。
2. 保持现有登录后端的来源与变更记录，评审三角色权限矩阵和受限教务管理员方案，继续验证正式 Vue 前端与后端的真实业务联调。
3. 先完成领域模型、权限矩阵、接口契约与数据库设计评审。
4. 按教学闭环推进课程与资源、同步课堂、活动、预习、作业和学情；AI 及增强能力按已批准范围排期。
5. 验证跨班权限、断线恢复、并发提交、教师确认成绩、真实设备体验，再进入试点。

详细门槛与待决事项见 [开发路线](docs/development-plan.md)。当前已验证登录链路，教学业务尚无运行验证结论。


## Vue 前端快速启动

在项目根目录运行 `start-frontend.cmd`，打开 <http://127.0.0.1:5173/app/>。后端默认代理到 8080；Vue 工程构建命令为 `start-frontend.cmd build`。完整说明见 [前端 README](frontend/README.md)。前端工程已补齐，尚未提供的教学业务仍按原分工开发。
