# 6-02 管理员组织范围课程只读契约（第二组）

本契约在**第一组已授权学校范围**内批量返回教学班、当前有效课程绑定和当前
有效学生数。它不接收客户端传入的用户、角色或组织参数，不读取
`sys_dept`，也不重复实现组织权限规则。

## 1. 公共类型

包：`com.ruoyi.education.course.api`

- `EduAdminOrganizationScopeService`
  - `EduAdminOrganizationScopeResult listAuthorizedRanges(Collection<Long> organizationIds)`
- `EduAdminOrganizationScopeResult`
  - `status`：`OK` / `NO_DATA` / `UNAVAILABLE`
  - `rangeCount`：范围条数；非 OK 为 null
  - `studentCount`：各行学生数合计；非 OK 为 null；OK 时允许真实 0
  - `ranges`：范围明细
  - `asOf`：查询时间，UTC
  - `reason`：UNAVAILABLE 的简短原因
- `EduAdminOrganizationClassRange`
  - `organizationId`、`teachingClassId`、`className`、`classCode`、`classStatus`
  - `bindingId`、`courseSpaceId`、`courseName`；未绑定时前两者为 null
  - `studentCount`：该教学班当前有效学生数，非负

实现：`com.ruoyi.education.course.api.impl.EduAdminOrganizationScopeServiceImpl`
（`ruoyi-education` 内 Spring Bean）。Mapper：
`com.ruoyi.education.course.mapper.AdminOrganizationScopeMapper`。

## 2. 查询口径

- 只返回 `edu_teaching_class.status = 'ACTIVE'` 的教学班。
- 只展开当前有效绑定：
  `published_at <= at` 且 `(ended_at IS NULL OR ended_at > at)`。
- 当前有效学生：
  `joined_at <= at` 且 `(left_at IS NULL OR left_at > at)`。
- 一个当前有效绑定一条记录；同一教学班多个绑定会重复出现。
- 没有当前绑定的教学班仍保留一条 `bindingId/courseSpaceId` 为 null 的记录。
- `rangeCount` 是记录条数，`studentCount` 是逐条学生数之和；不按教学班去重，
  与第六组现有范围计数口径一致。
- 组织 ID 必须在第一组已授权学校范围内；本契约不提供 HTTP 端点，也不对
  客户端暴露。

## 3. 状态

| 场景 | 返回 |
| --- | --- |
| 授权组织内存在 ACTIVE 教学班范围 | `OK`，含范围和计数 |
| 输入组织为空，或授权组织内确实没有范围 | `NO_DATA`，两个计数为 null |
| 数据库/来源查询失败，或行字段不完整 | `UNAVAILABLE`，两个计数为 null |
| `organizationIds` 为 null 或含非正整数 | `IllegalArgumentException`（调用方编程错误） |

## 4. 调用示例

第六组在请求线程先捕获第一组快照，再把学校 ID 传给本契约：

```java
AdminOrganizationScope organizationScope =
        organizationScopeProvider.captureCurrent();

if (organizationScope.status() == AdminOrganizationScopeStatus.UNAVAILABLE) {
    // 总览 item UNAVAILABLE
    return;
}
if (organizationScope.status() == AdminOrganizationScopeStatus.NO_DATA) {
    // 总览 item NO_DATA
    return;
}

EduAdminOrganizationScopeResult result =
        courseScopeService.listAuthorizedRanges(organizationScope.organizationIds());

if (result.status() == EduAdminOrganizationScopeStatus.UNAVAILABLE) {
    // 总览 item UNAVAILABLE；不要写 0
}
if (result.status() == EduAdminOrganizationScopeStatus.NO_DATA) {
    // 总览 item NO_DATA
}
List<EduAdminOrganizationClassRange> ranges = result.ranges();
```

不要把第一组 `captureCurrent()` 放进异步源线程；本契约可以接收捕获后的
学校 ID 并在异步线程执行，因为不再依赖 `PermissionContextHolder`。
