/**
 * 第 4 组课堂与同步跟课的路由（4-03 / 4-04）。
 *
 * 当前 Vue 工程只覆盖第 4 组负责的页面；其他组的业务路由由各自分支追加到本数组。
 */
export const businessRoutes = [
  {
    path: '/education/teacher/quick-start',
    component: () => import('@/views/education/teacher/QuickStart.vue'),
    meta: { title: '今日课程 / 快捷上课', roles: ['teacher'], nav: true }
  },
  {
    path: '/education/teacher/classroom',
    component: () => import('@/views/education/teacher/Classroom.vue'),
    meta: { title: '课堂教学', roles: ['teacher'], nav: true }
  },
  {
    path: '/education/student/join',
    component: () => import('@/views/education/student/JoinClass.vue'),
    meta: { title: '正在上课 / 一键加入', roles: ['student'], nav: true }
  },
  {
    path: '/education/student/classroom',
    component: () => import('@/views/education/student/SyncClassroom.vue'),
    meta: { title: '同步课堂', roles: ['student'], nav: true }
  }
]
