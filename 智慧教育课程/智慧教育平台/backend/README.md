# 后端工程与本地运行

后端统一位于本目录。已接入 RuoYi-Vue `springboot3` 分支的若依 3.9.2（Spring Boot 3.5.16、Java 17 编译目标），包含六个基础模块和已接入构建的 `ruoyi-education`。登录页及三端原型导航运行页由 `ruoyi-admin` 同源提供；教学业务 HTTP 接口仍未实现，`ruoyi-education` 当前已有 6-02 公告领域的 Entity、Mapper 读写、状态规则和教师公告 Service。下表中“规划职责”描述后续教学业务的分工。

## 在 D 盘运行登录页

本机联调库 `zhijiao_m0` 同时包含 V3.0 M0 教育业务表、若依 `sys_*`/`QRTZ_*` 基础表和演示账号；`zhijiao_login_dev` 是此前的独立登录测试库，当前启动配置已改用 `zhijiao_m0`。Redis 需在 `localhost:6379` 运行。`backend/.env` 为本机私有配置，已被 Git 忽略；换机器时复制 [.env.example](.env.example) 为 `.env`，填写数据库连接与随机令牌密钥。若依上游 SQL 位于 [sql/upstream/ruoyi](../sql/upstream/ruoyi/)；脚本含 `DROP TABLE`，只能用于新建的空测试库，不能对现有演示库重复执行。

在 PowerShell 中执行：

```powershell
cd 'c:\Users\15241\Desktop\智慧教育课程\智慧教育平台\backend'
mvn -B -pl ruoyi-admin -am package
.\run-local.ps1
```

打开 <http://127.0.0.1:8080/login.html>。页面调用若依真实的 `GET /captchaImage`、`POST /login`、`GET /getInfo`、`GET /getRouters`、`POST /logout`；登录时向 `/login` 发送所选 `role`（`admin`、`teacher` 或 `student`），服务端确认账号具有该角色后才发令牌。若依旧调用方不传 `role` 时仍沿用原有登录流程。登录后按角色进入 `/admin.html`、`/teacher.html` 或 `/student.html`，运行页每次加载重新验证令牌和角色。三个页面沿用 [`console-navigation-v1`](../prototypes/console-navigation-v1/) 的导航原型；其静态菜单还未绑定若依授权路由，内容区和教学业务接口仍待开发。`admin` 沿用若依超级管理员账号，教师与学生演示账号使用项目角色并与教学数据的 `user_id` 对应。本机已对三角色成功登录、错入口拒绝、身份查询和退出进行接口联调。联调凭据见 [三端演示账号](../docs/demo-login-accounts.txt)；实际部署须更换密码、创建受限管理员账号并按权限矩阵授权。

## 基础模块与依赖

| 模块 | 规划职责 | 依赖与集成边界 |
|---|---|---|
| ruoyi-common | 上游公共类型、工具、通用契约 | 不依赖教学业务；禁止塞入课程/作业专属规则 |
| ruoyi-system | 上游用户、角色、菜单、部门及系统管理服务 | 复用身份与组织能力，不承载教学班与正式成绩业务 |
| ruoyi-framework | 上游安全、Web 配置、权限与基础设施整合 | 沿用选定版本依赖；不能反向依赖 ruoyi-education |
| ruoyi-admin | 上游应用入口、管理端装配和对外接入 | 装配基础框架及教育业务，不承担九领域的核心逻辑 |
| ruoyi-education | 已接入的最小教学业务 Maven 模块；当前含 6-02 教师公告读写 Service、Mapper、状态规则和严格草稿权限 | 依赖 common；不依赖 admin；发布/归档幂等；尚无 Controller、DTO、学生通知或通知 fan-out |
| ruoyi-quartz | 上游调度模块，已导入 | 本轮只供基础工程运行，不配置教学调度任务 |
| ruoyi-generator | 上游代码生成模块，已导入 | 本轮不生成教学占位代码 |

基础模块来自 [若依官方 RuoYi-Vue 仓库](https://github.com/yangzongzhuan/RuoYi-Vue) 的固定提交 `a51a838b71b446ea27256900efe7ed2faa2a02fd`。

后续装配方向：`ruoyi-admin → ruoyi-framework / ruoyi-education`，`ruoyi-education → ruoyi-common`，必要时 `ruoyi-education → ruoyi-system`。上游基础模块之间的实际依赖以已导入的 POM 为准。教育模块如需安全框架适配，优先在入口/适配边界完成，不创建 framework 与 education 的循环依赖。

当前登录 Controller 位于 `ruoyi-admin`。新增教学接口时先核对组件扫描与安全配置，业务留在 education；两处不得重复暴露同一接口。

## 教育业务领域

九个领域属于 **同一个 `ruoyi-education` 模块内的业务包**，不拆成九个独立 Maven 模块。当前 `course` 包已有 6-02 教师公告的 Entity、Mapper 读写、状态规则、Service 和授权适配端口；没有 Controller、前端 DTO、学生通知或通知 fan-out。其余领域仍为规划目录。

```text
ruoyi-education/
├── pom.xml
├── README.md
└── src/main/java/com/ruoyi/education/
    ├── course/
    │   ├── authorization/
    │   ├── domain/
    │   ├── exception/
    │   ├── mapper/
    │   ├── policy/
    │   └── service/
    ├── resource/
    ├── prepare/
    ├── classroom/
    ├── activity/
    ├── assignment/
    ├── learning/
    ├── analysis/
    └── ai/
```

包根 `com.ruoyi.education` 已通过父工程构建和组件扫描验证；`course` 领域按实际任务逐步增加分层。其他领域只有产生实际实现需求时才新增代码。

| 领域 | 负责对象和职责 | 主要协作与边界 |
|---|---|---|
| course | 课程空间、正式课程关联、教学班、学期、教师与学生成员、章节、知识点 | 提供内容归属和教学授权；账号和部门复用 system，不重建登录体系 |
| resource | 课件、视频、资料、题库、评分标准、资源版本与发布关系、转换状态 | 引用 course 归属；提供带版本资源与题目快照；不重复管理各阶段任务 |
| prepare | 备课方案、预习发布与完成编排、课堂活动预设 | 引用 course/resource；持有预设配置并经契约发起实例；不自行启动有效签到 |
| classroom | 课堂会话、讲授页码与资源版本、公开投影状态、学生同步与重连 | 引用 course/resource；不持有正式成绩或复制活动提交表；课堂可无备课方案 |
| activity | 签到、实时随堂题、课堂活动实例、开放截止及目标快照 | 引用 course/resource；可关联 classroom，也支持独立活动；小作业委托 assignment |
| assignment | 课堂小作业及课后作业、统一提交版本、批改、教师确认和成绩发布 | 引用 course/resource；提供统一提交与评价能力；AI 仅返回建议，不直接写正式成绩 |
| learning | 学习轨迹、错题、补学任务与学习证据 | 消费预习/活动/作业有效结果，保留来源；重做新增记录、不覆盖原分数 |
| analysis | 班级、学生、知识点聚合和可追溯统计 | 读取授权证据及只读投影；不反向写业务提交或生成无依据综合掌握分 |
| ai | AI 备课建议、课程答疑、作业初评的模型适配与任务记录 | 接收调用方已授权的上下文并复核范围；返回有来源和版本的建议，失败可回退人工 |

## 领域协作规则

允许的主要调用方向为：`resource → course`；`prepare → course/resource`；`classroom → course/resource`；`activity → course/resource/assignment`；`assignment → course/resource/ai`；`learning → 上游业务的公开结果契约`；`analysis → 授权只读证据`。这是设计方向，具体依赖在接口评审时细化。

- course 与 resource 提供归属/可见性契约，上层不得直接读取其 Mapper。
- classroom 负责会话状态；结束课堂需要关闭活动时，由入口用例编排调用课堂和活动服务，避免两领域互相注入。
- prepare 提供备课与模板快照；激活模板由入口用例编排调用 activity，activity 不反向读取 prepare 私有表。
- activity 的小作业入口调用 assignment；assignment 不依赖 activity，使用外部来源标识关联。
- assignment 调用 ai 初评，ai 不回调修改 assignment 的成绩；由 assignment 校验任务结果、保存建议并执行教师确认。
- learning 与 analysis 通过业务服务公开查询或约定的事件/投影获得数据。事件机制、消息中间件及异步方案尚未选定，不把表直接关联查询当作默认公共接口。
- analysis 产生的“下一次备课/补学”建议由入口用例创建草稿，相关领域拥有写入权；不建立分析与业务双向循环。

## 后续接入要求

接入真实若依时记录上游仓库、版本/提交、许可证和模块差异；由真实父 POM 注册教育模块并检查依赖方向、包扫描、Mapper 扫描和权限接入。启动与构建命令只能依据实际工程验证后填写。完整步骤见 [开发路线](../docs/development-plan.md)。
