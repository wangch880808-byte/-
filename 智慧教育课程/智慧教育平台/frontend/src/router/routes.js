export const businessRoutes = [
  { path: '/education/admin/term', component: () => import('@/views/education/admin/TermList.vue'), meta: { title: '学期管理', roles: ['admin'], nav: true } },
  { path: '/education/admin/class', component: () => import('@/views/education/admin/ClassList.vue'), meta: { title: '班级管理', roles: ['admin'], nav: true } },
  { path: '/education/admin/teacher', component: () => import('@/views/education/admin/TeacherList.vue'), meta: { title: '教师管理', roles: ['admin'], nav: true } },
  { path: '/education/admin/student', component: () => import('@/views/education/admin/StudentList.vue'), meta: { title: '学生管理', roles: ['admin'], nav: true } },
  { path: '/education/admin/dept', component: () => import('@/views/education/admin/DeptManage.vue'), meta: { title: '组织架构', roles: ['admin'], nav: true } },
  { path: '/education/teacher/course', component: () => import('@/views/education/teacher/CourseList.vue'), meta: { title: '我的课程', roles: ['teacher'], nav: true } },
  { path: '/education/teacher/course/detail/:courseId', component: () => import('@/views/education/teacher/CourseDetail.vue'), meta: { title: '课程详情', roles: ['teacher'], activeMenu: '/education/teacher/course' } },
  { path: '/education/student/course', component: () => import('@/views/education/student/MyCourses.vue'), meta: { title: '在修课程', roles: ['student'], nav: true } },
  { path: '/education/student/course/detail/:courseId', component: () => import('@/views/education/student/CourseDetail.vue'), meta: { title: '课程详情', roles: ['student'], activeMenu: '/education/student/course' } },
  { path: '/system/user', component: () => import('@/views/system/Directory.vue'), meta: { title: '系统用户', roles: ['admin'], permission: 'system:user:list', directory: 'user', nav: true } },
  { path: '/system/dept', component: () => import('@/views/system/Directory.vue'), meta: { title: '系统部门', roles: ['admin'], permission: 'system:dept:list', directory: 'dept', nav: true } }
]
