# 6-01 管理员组织范围公共契约（第一组）

本契约供第六组管理员总览和后续管理员统计使用。它只返回当前登录人被
`education:overview:admin` 明确授权的学校节点，不读取客户端传入的
`userId/deptId/organizationId`，不把学院或专业可见性回溯成整校授权。

## 1. 公共类型

包：`com.ruoyi.common.education.organization`

- `AdminOrganizationScopeProvider`
  - `AdminOrganizationScope captureCurrent()`
  - 常量 `ADMIN_OVERVIEW_PERMISSION = "education:overview:admin"`
- `AdminOrganizationScope`
  - `status`：`OK` / `NO_DATA` / `UNAVAILABLE`
  - `userId`：捕获时的当前用户
  - `organizations`：明确的学校节点列表，非 OK 时为空
  - `asOf`：捕获时间，UTC
  - `reason`：UNAVAILABLE 的简短原因，不包含 SQL 或凭据
  - `organizationIds()`：学校 deptId 列表
- `AdminOrganizationRef`：`deptId`、`parentId`、`deptName`

实现：`com.ruoyi.web.controller.education.AdminOrganizationScopeProviderImpl`
（`ruoyi-admin`，Spring Bean）。

## 2. 线程要求

`captureCurrent()` **必须在请求线程调用**。若依的部门数据权限通过
`PermissionContextHolder` 读取当前请求的权限串，而 `RequestContextHolder`
不会自动传播到总览的异步源线程。捕获后的 `AdminOrganizationScope` 是纯快照，
可以安全传给异步线程。

在无请求属性的线程调用时，实现返回 `UNAVAILABLE`，不会退化成空范围或全量。

## 3. 状态与权限

| 场景 | 返回 |
| --- | --- |
| 有 `education:overview:admin`，且至少一个学校节点明确可见 | `OK`，含学校列表 |
| 有权限，但没有学校节点明确可见（例如只授权到学院） | `NO_DATA` |
| 部门授权来源、数据库查询失败，或在异步线程误用 | `UNAVAILABLE` |
| 无可信登录身份，或没有 `education:overview:admin` | 抛 `ForbiddenException`（403） |

学校节点判定沿用第一组根学校规则：`parent_id=0`、`dept_type=SCHOOL`（旧数据
允许按根节点推断）、正常状态、未删除。学院、专业、班级行不会被回退成其根学校。

## 4. 调用示例

第六组应在派发异步范围查询前捕获快照：

```java
private final AdminOrganizationScopeProvider organizationScopeProvider;

AdminOrganizationScope scope =
        organizationScopeProvider.captureCurrent();

if (scope.status() == AdminOrganizationScopeStatus.UNAVAILABLE) {
    // 映射为总览 item UNAVAILABLE；不要写 0，也不要继续用空范围查课程。
    return;
}
if (scope.status() == AdminOrganizationScopeStatus.NO_DATA) {
    // 授权成功但没有明确学校范围：映射为 NO_DATA。
    return;
}

List<Long> organizationIds = scope.organizationIds();
// 把 organizationIds 作为参数传给第二组的批量课程范围契约。
```

不要把 `captureCurrent()` 放进 `OverviewSourceGuard` 的源线程；源线程只有
传播后的 SecurityContext，没有当前请求的 `RequestContextHolder`。
