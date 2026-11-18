import { registerBusinessPage } from './ui-pages'

/**
 * 教师公告业务页注册（6-04 原型）。
 *
 * 工作台「班级消息 / 公告」分区和直达 `/teacher/announcements` 使用同一组件。
 * 角色、`education:announcement:list` 权限和服务端 `education/announcements`
 * 路由三项同时满足才显示/可直达。页面内的新建入口要求
 * `education:announcement:add` + 当前班级 canPublish；编辑入口要求
 * `education:announcement:edit` + DRAFT + 创建者本人或当前班级 canPublish；
 * 发布/归档入口分别要求 DRAFT/PUBLISHED、对应 publish/archive 权限和
 * canPublish。服务端仍逐请求复核。
 *
 * TODO(6-04-teacher-announcement-menu)：生产 `sys_menu` 还没有公告权限和路由；
 * 浏览器夹具提供。解除条件：第 1 组/负责人写入菜单与教师角色授权，覆盖
 * `education:announcement:list/add/edit/publish/archive`，且 `getRouters`
 * 返回 `education/announcements` 后删除夹具补丁。
 */
registerBusinessPage({
  id: 'teacher-announcements',
  title: '班级消息 / 公告',
  group: '教学工作台',
  roles: ['teacher'],
  path: 'announcements',
  permission: 'education:announcement:list',
  serviceRoute: 'education/announcements',
  component: () => import('@/views/education/teacher/TeacherAnnouncements.vue')
})
