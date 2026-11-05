# 第 2 组交付索引（学期 / 教学班 / 成员 / 课程 / 授课授权）

> 对应 2-01～2-04。2A：数据、后端、服务端权限、契约；2B：页面、交互、联调。

## 本轮新增/改动

| 路径 | 类型 | 说明 |
|---|---|---|
| `docs/group2/api-provided.md` | 新增 | **我们给别组的契约**：全部端点、字段、权限模型、Java 内部接口、四类用例 |
| `docs/group2/api-required.md` | 新增 | **别组要给我们的**：R-1.x（1 组 9 项）、R-3.x、R-4.x、R-5.x、R-6.x，含阻塞级别 |
| `docs/group2/plan.md` | 新增 | 任务拆解、估时、缺口改法、权限标识清单、G0/G1 检查表、Issue 模板 |
| `docs/group2/vue-merge-guide.md` | 新增 | Vue 页面如何并入 RuoYi-Vue3 工程（含菜单 SQL 示例、路由表） |
| `backend/ruoyi-education/.../course/api/*.java` | 新增 | 跨领域只读契约 `EduCourseAccessService` + 6 个 record 快照 |
| `backend/ruoyi-education/.../course/api/impl/EduCourseAccessServiceImpl.java` | 新增 | 契约实现（每次现查，不缓存，撤权即时生效） |
| `backend/ruoyi-admin/.../education/EduScopeController.java` | 新增 | `/education/scope/**` 7 个只读端点 |
| `backend/ruoyi-education/.../mapper/education/EduPortalMapper.xml` | 改动 | 补 `organizationId`、`classStatus`、`memberId` 三个别名（向后兼容） |
| `frontend/src/api/education/*.js` | 新增 | 请求实例 + 5 个 API 模块（term / teachingClass / course / directory / scope） |
| `frontend/src/views/education/**` | 新增 | 管理员 5 页、教师 2 页、学生 2 页 |
| `frontend/src/components/education/**` | 新增 | `CourseOutline`、`CourseFormDialog` |

## 验证证据（截至本轮）

| 项 | 结果 | 方式 |
|---|---|---|
| 后端编译 | 通过 | `javac --release 17`，157 个依赖 jar、303 个源文件；新增 9 个文件后仍通过 |
| Mapper XML | 通过 | 27 个 mapper XML 全部可被 XML 解析器解析 |
| Vue SFC 语法 | 通过 | `@vue/compiler-sfc` parse + compileScript + compileTemplate，11 个文件 0 错误 |
| Vue 工程构建 | 通过 | Vite 7 + Vue 3.5 + Element Plus 实际构建，`✓ built`，所有页面 chunk 正常产出 |
| 运行联调 | **未做** | 本机未启动 MySQL/Redis/Spring Boot，接口未发真实请求 |
| 浏览器验收 | **未做** | 构建产物未在浏览器点开，交互与样式需 2B 接入后自测 |

> 结论请按"编译/构建通过、运行未验证"理解，不要写成"功能已完成"。

## 怎么用

**2A**：先看 `api-provided.md` §3/§4 对齐现有实现，再看 `plan.md` §3 的 8 个缺口决定先补哪个。
**2B**：看 `vue-merge-guide.md`，把 `frontend/src` 三块复制进工程，跑 `npm run build`，再按 `plan.md` §5 走用例。
**别组**：只看 `api-provided.md` §0/§4/§5，三分钟知道该调什么。

## 需要别人先给（否则卡住）

1. **R-1.3 权限标识**：没有菜单和 perms，`@PreAuthorize` 不能加（加了教师/学生全 403）。
2. **R-1.4 名册接口或书面允许 join**：我们现在直接 join 了 `sys_user/sys_role/sys_dept`，属越界，需裁决。
3. **R-3.1 章节写入归属**：课程详情目录只能读不能写，2-04 与 3-03 都卡在这里。
4. **R-1.9 演示账号**：缺 `edu_admin` 与"不在任何班"的学生，四类用例跑不全。

## 需要产品/负责人裁决

见 `api-required.md` §7：行政班是否独立成表（Q1）、课程发布是否必须绑班（Q2）、一班能否绑多课程（Q3）、归档是否只读（Q4）、ID 是否字符串化（Q5）、是否允许删课（Q6）。

## 已知不一致

- 仓库 README 仍写「`frontend/src/` 下的 Vue 目录仍是分工预留」。本轮往里放了页面代码，但**没有**建前端工程（无 `package.json`/`vite.config`），"正式前端工程未开始"这句仍然成立。
- 静态页（`frontend/pages-*.js`）与 Vue 页并存，菜单切换后静态页应下线，避免两套 UI 漂移。
