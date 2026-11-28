# 6-01 已接通总览指标来源导航（2026-11-28）

本文记录当前四项已接通总览指标的来源入口、范围明细接口和边界。指标公式、三态和
未去重口径沿用 `OverviewQueryService` 与 `CourseScopePort` 的现有实现，不在此另立
统计口径。

## 1. 指标与来源

| 指标 | 卡片口径 | 来源入口 |
| --- | --- | --- |
| `teacher.class.count` | 授权范围条数 `scopes.size()`，未按教学班去重 | 总览内“查看范围明细” |
| `teacher.student.count` | 各范围 `studentCount` 求和，未跨班去重；任一为 null 时卡片为 NO_DATA | 总览内“查看范围明细” |
| `student.class.count` | 学生有效在班范围条数，未按教学班去重 | 总览内“查看范围明细” |
| `student.notice.unread` | 本人全部未读通知数；0 为真实零 | 已注册业务页面 `student-notices`，查询 `scope=all&tab=notices` |

来源入口只使用 `@/education/ui-pages` 中已注册的业务页面和 `canOpen`。后端
`actionPath` 不参与前端导航，不能作为任意 URL 执行；上述四项的 `actionPath`
已置为 `null`。UNAVAILABLE、响应异常和指标级违约保持原样，不生成入口。

学生通知来源页进入后固定“本人全部当前可见通知”：`scope=all` 会忽略旧的
`courseSpaceId/classId/bindingId` 上下文；页签往返、公告来源跳转和顶栏通知入口
继续保留 `scope=all`，不会恢复旧教学班。通知详情、单条已读、全部已读和公告
跳转沿用原页面能力。

## 2. 范围明细接口

### 教师

`GET /education/teacher/overview/scopes`

- 权限：`education:overview:teacher`
- 参数：可选 `classId`，正 Long；非正整数 400，非本人授权范围 403。
- 身份：服务端认证上下文；不接收 `userId`、`teacherId` 或组织参数。

### 学生

`GET /education/student/today-todos/scopes`

- 权限：`education:overview:student`
- 参数：无；不接收 `userId`、`classId`、`bindingId` 或组织参数。
- 身份：服务端认证上下文。

### 响应

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "asOf": "2026-11-28T08:00:00Z",
    "status": "OK",
    "classCount": 2,
    "studentCount": 4,
    "ranges": [
      {
        "teachingClassId": "910000000001",
        "bindingId": "910000000001",
        "courseSpaceId": "910000000001",
        "className": "演示教学班 A",
        "courseName": null,
        "studentCount": 3
      }
    ]
  }
}
```

- `classCount` 与对应班级数卡片同口径；`studentCount` 与教师学生数卡片同口径。
- 学生端点没有全量学生数契约时，`studentCount` 为 `null`，不是 0。
- `NO_DATA`/`UNAVAILABLE` 时 `classCount`、`studentCount` 均为 `null`，`ranges` 为空。
- ID 以字符串返回，避免 19 位 Long 精度损失。
- 明细只消费 `CourseScopePort` 已公开的教学班、绑定和汇总学生数，不读取课程、
  教学班或成员私有表，也不返回学生名单。

## 3. 管理员统计：等待第一组公开契约

本轮不把管理员统计接到当前 `OverviewIdentityProvider.currentOrganizationId()`
（即 `deptId`）上，原因：

- `deptId` 与课程使用的 `organizationId/学校范围`不是同一口径，不能直接相等。
- `IEduTeachingClassService.list` 的管理员分支返回全量，不能作为范围授权依据。
- 第一组 `EduSchoolScope` 当前位于 `ruoyi-admin`，`ruoyi-education` 不能反向依赖。

需要第一组/第二组提供的公开契约至少包含：

1. 以服务端认证身份为唯一输入，返回当前管理员可管理的组织/学校范围；不接受客户端
   `organizationId`、`deptId` 或任意范围覆盖。
2. 在该范围内返回授权教学班/课程绑定范围（含 `teachingClassId`、`bindingId`、
   `courseSpaceId`、`organizationId`）和有效学生数汇总，或明确说明计数口径。
3. 区分 `OK`、`NO_DATA`、`UNAVAILABLE`、`403` 的授权失败语义；来源不可用时不得
   返回 0。
4. 明确范围数据的时点/版本或 `asOf`，避免与课程范围、教学班状态不一致。
5. 契约放在 `ruoyi-education` 可依赖的模块或稳定接口包，不通过
   `ruoyi-education -> ruoyi-admin` 反向依赖实现。

在上述契约提供前，`admin.class.count`、`admin.student.count`、
`admin.session.running`、`admin.assignment.pending`、`admin.grade.pending` 保持
UNAVAILABLE，`admin.todo.pending` 保持 NO_DATA / 口径待确认；管理员页面不增加
来源跳转。

## 4. 验证边界

- 后端服务/控制器：覆盖范围明细计数、classId 过滤、越权 403、NO_DATA、
  UNAVAILABLE、真实零和身份缺失。
- 前端夹具：覆盖学生全部范围（旧班级不窄化）、详情/已读、教师/学生范围明细、
  classId 同步、真实零、缺权限/缺路由隐藏入口、UNAVAILABLE 无入口、403 和离开
  页面后的迟到响应。
- 不重跑既有 89 项、24 项完整回归；不把夹具通过表述为真实生产联调。
