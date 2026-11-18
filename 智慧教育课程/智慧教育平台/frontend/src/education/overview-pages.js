import { registerBusinessPage } from './ui-pages'

/**
 * 6-03 教师/学生总览业务页注册。
 *
 * 工作台 overview 分区和独立路径使用同一组件。角色、权限和服务端路由三项
 * 同时满足才显示/可直达；服务端 @PreAuthorize 仍会逐请求复核。
 *
 * TODO(6-03-overview-menu)：权限编码已由 #24 的 @PreAuthorize 使用，但
 * sys_menu、角色授权和 getRouters 尚未配置；下面 serviceRoute 只是候选值。
 * 解除条件：第 1 组/负责人写入教师、学生总览菜单和权限，且 getRouters
 * 返回 education/teacher/overview、education/student/today-todos 后复核并
 * 删除浏览器夹具补丁。
 */
registerBusinessPage({
  id: 'teacher-overview',
  title: '教学总览',
  roles: ['teacher'],
  path: 'teaching-overview',
  permission: 'education:overview:teacher',
  serviceRoute: 'education/teacher/overview',
  component: () => import('@/views/education/teacher/TeacherOverview.vue')
})

registerBusinessPage({
  id: 'student-overview',
  title: '学习总览 / 今日待办',
  roles: ['student'],
  path: 'today-todos',
  permission: 'education:overview:student',
  serviceRoute: 'education/student/today-todos',
  component: () => import('@/views/education/student/StudentOverview.vue')
})
