# 第 2 组 Vue 页面接入说明（RuoYi-Vue3 + Element Plus）

> 本仓库里 `frontend/` 目前是**静态联调页**（`*.html` + `pages-*.js`），`frontend/src/` 是给正式 Vue 工程预留的目录。
> 本轮把第 2 组的页面按 RuoYi-Vue3 约定写在 `frontend/src/` 下，**可直接复制进你们的前端工程**。

## 1. 复制哪些文件

把 `frontend/src/` 下这三块整体复制到前端工程的 `src/`：

```text
src/api/education/          →  src/api/education/
src/components/education/   →  src/components/education/
src/views/education/        →  src/views/education/
```

复制后目录应为：

```text
src/
├── api/education/
│   ├── http.js             ← 教学业务请求实例（含 401/403 语义）
│   ├── term.js
│   ├── teachingClass.js
│   ├── course.js
│   ├── directory.js
│   └── scope.js
├── components/education/
│   ├── CourseOutline.vue   ← 课程目录（章节树 + 知识点），教师/学生共用
│   └── CourseFormDialog.vue← 新建/编辑课程弹窗
└── views/education/
    ├── admin/{TermList,ClassList,TeacherList,StudentList,DeptManage}.vue
    ├── teacher/{CourseList,CourseDetail}.vue
    └── student/{MyCourses,CourseDetail}.vue
```

## 2. 前置依赖

| 依赖 | 说明 |
|---|---|
| `element-plus` | 已全局引入即可，页面没有按需 import 组件 |
| `vue-router` | 详情页用 `route.params.courseId` |
| `@/utils/auth` | `getToken()` / `removeToken()`，RuoYi-Vue3 自带 |
| `@/store` | 401 时 `dispatch('LogOut')`，RuoYi-Vue3 自带 |
| `@/utils/request` | 不使用，见 §3 |

`http.js` 用 `import.meta.env.VITE_APP_BASE_API` 作为 baseURL，与若依 `.env.development` 的 `/dev-api` 一致，无需额外配置。

## 3. 为什么不直接用 `@/utils/request`

若依公共请求层对 `code !== 200` 统一 `ElNotification.error` + `reject('error')`，页面拿不到 403 与 500 的区别。教学页面必须区分：

- 403 → 显示「无权限」，**不重试、不跳登录**；
- 401 → 清令牌回登录页；
- 500 → 显示服务端 `msg`（如「同一学期内班级编码不能重复」）。

所以 `http.js` 复用同一套 `getToken` + `VITE_APP_BASE_API`，只重写响应语义。
**若 1-03 在公共 `request.js` 补了 403 分支，可删除 `http.js` 并把各 `api/education/*.js` 的 import 换成 `@/utils/request`，页面代码不用动。**

## 4. 页面与路由

| 页面 | 文件（相对 `src/views`） | 建议路由 path | 菜单归属 |
|---|---|---|---|
| 学期管理 | `education/admin/TermList` | `/education/admin/term` | 管理员 / 教务组织 |
| 班级管理 | `education/admin/ClassList` | `/education/admin/class` | 管理员 / 教务组织 |
| 教师管理 | `education/admin/TeacherList` | `/education/admin/teacher` | 管理员 / 教务组织 |
| 学生管理 | `education/admin/StudentList` | `/education/admin/student` | 管理员 / 教务组织 |
| 组织架构 | `education/admin/DeptManage` | `/education/admin/dept` | 管理员 / 教务组织 |
| 我的课程（教师） | `education/teacher/CourseList` | `/education/teacher/course` | 教师 / 我的课程 |
| 课程详情（教师） | `education/teacher/CourseDetail` | `/education/teacher/course/detail/:courseId` | 隐藏菜单 |
| 在修课程（学生） | `education/student/MyCourses` | `/education/student/course` | 学生 / 我的课程 |
| 课程详情（学生） | `education/student/CourseDetail` | `/education/student/course/detail/:courseId` | 隐藏菜单 |

### 4.1 后端菜单（`sys_menu`）示例

RuoYi-Vue3 的动态路由用 `component` 字段匹配 `src/views/**/*.vue`，所以 `component` 填**相对 `views` 的路径、不带 `.vue`**：

```sql
-- 父菜单：教务组织
INSERT INTO sys_menu(menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES ('教务组织', 0, 5, 'edu-org', NULL, 1, 0, 'M', '0', '0', '', 'tree', 'admin', sysdate(), '教学组织管理');

-- 学期管理（父菜单 ID 用上一条的 LAST_INSERT_ID()）
INSERT INTO sys_menu(menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES ('学期管理', @pid, 1, 'term', 'education/admin/TermList', 1, 0, 'C', '0', '0', 'education:term:list', 'date', 'admin', sysdate(), '');

-- 按钮权限（示例：学期新增）
INSERT INTO sys_menu(menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES ('学期新增', @termMenuId, 1, '', NULL, 1, 0, 'F', '0', '0', 'education:term:add', '#', 'admin', sysdate(), '');
```

> **这条 SQL 只是示例，别直接在生产库执行。**菜单由第 1 组（1-04 菜单管理）统一建并绑定角色；我们把「页面 → `component` → `perms`」这张表交给他们即可（见 plan.md §4）。

### 4.2 隐藏路由（详情页）

课程详情不进左侧菜单，用隐藏菜单（`visible='1'`）承载，或在前端静态路由里注册：

```js
// router/index.js（若选择静态注册）
{
  path: '/education/teacher/course/detail/:courseId',
  component: () => import('@/views/education/teacher/CourseDetail.vue'),
  hidden: true,
  meta: { title: '课程详情', activeMenu: '/education/teacher/course' }
}
```

## 5. 权限与角色

- 页面本身不做角色判断，靠**菜单是否分配** + **服务端 403** 双重控制。
- 暂未加 `v-hasPermi`：数据库还没建这些权限标识，加了会把教师/学生的按钮全隐藏。等 R-1.3 完成后，按按钮逐个包 `v-hasPermi="['education:term:add']"`。
- 管理端页面在服务端只放行 `admin`（当前）或 `edu_admin`（1-02 落地后）。教师/学生直连接口必然 403，页面会显示「无权限」而不是白屏。

## 6. 接入后必须跑的三件事

```bash
npm run dev      # 登录后按角色点开每个页面
npm run build    # 构建必须通过，新增 .vue 不允许有编译告警
```

3. 按 [plan.md §5](plan.md) 的四类用例走一遍，至少覆盖：空数据（新教师/未入班学生）、无权限（教师开管理页、外班学生开课程）、失败（重复编码、断网）。

## 7. 已知未验证项（不要当成"已完成"）

- 所有 `.vue` **未构建、未在浏览器运行过**（仓库内还没有前端工程与 `node_modules`）。
- `http.js` 的 401 分支依赖你们 `@/store` 的 `LogOut` action，接入时需确认 action 名一致。
- `pagination` 组件用的是若依全局注册的 `<pagination>`，若你们版本未全局注册，改用 `<el-pagination>` 并手写 `@current-change`。
- 菜单 SQL 是示例，`parent_id` 需要按实际插入结果调整。
- 学生端「资料 / 课前学习 / 同步课堂 / 课后学习」页签仍是占位说明，等第 3、4、5 组接口。
