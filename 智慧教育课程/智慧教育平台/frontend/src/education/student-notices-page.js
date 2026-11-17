import { registerBusinessPage } from './ui-pages'
import { getUnreadCountForHeader } from '@/api/education/notice'

/**
 * 学生通知业务页注册（6-04 最小接入）。
 *
 * 该页提供通知列表、详情、已读、课程公告页签和公告来源跳转；
 * 非公告来源路由后续接入。工作台
 * 「学习通知 / 课程公告」分区通过 HomeView 渲染同一组件；直达路由和顶栏入口
 * 由 #27 的业务页注册、角色/权限/服务端路由三项检查统一控制。
 *
 * TODO(6-04-notice-menu)：生产 `sys_menu` 还没有 `education:notice:list` 权限和
 * `education/notices` 路由，浏览器夹具会提供这两项。解除条件：第 1 组/负责人写入
 * 菜单与角色授权，且 `getRouters` 能返回 `education/notices` 后删除夹具中的补丁。
 */
registerBusinessPage({
  id: 'student-notices',
  title: '学习通知',
  group: '学习工作台',
  roles: ['student'],
  path: 'notices',
  permission: 'education:notice:list',
  serviceRoute: 'education/notices',
  component: () => import('@/views/education/student/StudentNotices.vue'),
  headerEntry: {
    label: '通知',
    tab: 'notices',
    getUnreadCount: getUnreadCountForHeader
  }
})
