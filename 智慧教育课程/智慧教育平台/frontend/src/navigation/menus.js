const pending = () => import('@/views/common/Pending.vue')
const remote = () => import('@/views/common/RemoteList.vue')
const server = () => import('@/views/system/ServerStatus.vue')
const users = () => import('@/views/system/user/index.vue')
const roles = () => import('@/views/system/role/index.vue')
const course = () => import('@/views/education/course/CourseDetail.vue')
const prepare = () => import('@/views/education/teacher/Prepare.vue')
const teacherNews = () => import('@/views/education/teacher/Announcements.vue')
const studentNews = () => import('@/views/education/student/Announcements.vue')

const deptColumns = [
  { prop: 'deptName', label: '部门' },
  { prop: 'leader', label: '负责人' },
  { prop: 'phone', label: '电话' },
  { prop: 'status', label: '状态' },
  { prop: 'orderNum', label: '排序' }
]

function item(title, path, component, extra = {}) {
  return { title, path, component, ...extra }
}

function gap(title, path, reason) {
  return item(title, path, pending, { reason })
}

function table(title, path, url, columns) {
  return item(title, path, remote, { list: { url, columns, shape: 'rows' } })
}

/** 与原先 admin/teacher/student.html 的菜单一致。已接通的项调用现有接口。 */
export const roleMenus = {
  admin: [
    { name: '工作台', children: [
      gap('管理总览', '/education/admin/overview', '总览聚合接口尚未提供，页面不显示估算数字。'),
      gap('待办事项', '/education/admin/todos', '待办聚合接口尚未提供。'),
      item('运行概况', '/monitor/server', server)
    ] },
    { name: '教务组织', children: [
      item('组织架构管理', '/system/dept', remote, { list: { url: '/system/dept/list', columns: deptColumns, shape: 'tree' } }),
      gap('班级管理', '/education/admin/classes', '教学班管理接口尚未提供。'),
      gap('教师管理', '/education/admin/teachers', '教学班任课教师接口尚未提供。账号请在用户管理中查看。'),
      gap('学生管理', '/education/admin/students', '教学班学生名单接口尚未提供。账号请在用户管理中查看。'),
      gap('留言互动', '/education/admin/messages', '留言接口尚未提供。'),
      gap('学期管理', '/education/admin/terms', '学期接口尚未提供。')
    ] },
    { name: '能力培养', children: [
      gap('评价指标管理', '/education/admin/metrics', '评价指标接口尚未提供。'),
      gap('证书配置', '/education/admin/certificates', '证书接口尚未提供。'),
      gap('教学过程分析', '/education/admin/process', '教学过程分析接口尚未提供。'),
      gap('教学问题研判', '/education/admin/issues', '教学问题研判接口尚未提供。'),
      gap('实训课程', '/education/admin/training-courses', '实训课程接口尚未提供。'),
      gap('实训任务', '/education/admin/training-tasks', '实训任务接口尚未提供。'),
      gap('管理员看板', '/education/admin/board', '管理员看板接口尚未提供。'),
      gap('教师工作台', '/education/admin/teacher-desk', '管理员查看教师工作台的接口尚未提供。')
    ] },
    { name: '学生学习', children: [
      gap('校园资讯', '/education/admin/news', '校园资讯接口尚未提供。'),
      gap('学习首页', '/education/admin/study-home', '学习首页管理接口尚未提供。'),
      gap('我的课程', '/education/admin/courses', '管理员课程只读接口尚未提供。'),
      gap('任务中心', '/education/admin/tasks', '任务中心接口尚未提供。'),
      gap('能力分析', '/education/admin/ability', '能力分析接口尚未提供。'),
      gap('单项能力评测', '/education/admin/assessment', '单项能力评测接口尚未提供。'),
      gap('个人中心', '/education/admin/profile', '个人中心业务接口尚未单独提供。'),
      gap('我的留言', '/education/admin/my-messages', '留言接口尚未提供。')
    ] },
    { name: 'AI 大模型', children: [
      gap('AI 对话', '/education/admin/ai-chat', 'AI 对话由项目负责人单独交付，当前没有可调用接口。'),
      gap('控制台', '/education/admin/ai-console', '模型控制台由项目负责人单独交付，当前没有可调用接口。')
    ] },
    { name: '基础设施', children: [
      table('配置管理', '/system/config', '/system/config/list', [
        { prop: 'configId', label: '编号' }, { prop: 'configName', label: '名称' },
        { prop: 'configKey', label: '键名' }, { prop: 'configValue', label: '键值' }
      ]),
      gap('文件管理', '/system/files', '文件列表接口尚未提供。'),
      item('监控中心', '/monitor/center', server),
      table('定时任务', '/monitor/job', '/monitor/job/list', [
        { prop: 'jobId', label: '编号' }, { prop: 'jobName', label: '任务名称' },
        { prop: 'jobGroup', label: '组' }, { prop: 'cronExpression', label: '表达式' }, { prop: 'status', label: '状态' }
      ])
    ] },
    { name: '系统管理', children: [
      item('用户管理', '/system/user', users),
      item('角色管理', '/system/role', roles),
      item('菜单管理', '/system/menu', remote, { list: { url: '/system/menu/list', shape: 'tree', columns: [
        { prop: 'menuName', label: '菜单' }, { prop: 'path', label: '路径' }, { prop: 'perms', label: '权限' }, { prop: 'status', label: '状态' }
      ] } }),
      table('字典管理', '/system/dict', '/system/dict/type/list', [
        { prop: 'dictId', label: '编号' }, { prop: 'dictName', label: '字典名称' },
        { prop: 'dictType', label: '字典类型' }, { prop: 'status', label: '状态' }
      ]),
      table('通知中心', '/system/notice', '/system/notice/list', [
        { prop: 'noticeId', label: '编号' }, { prop: 'noticeTitle', label: '标题' },
        { prop: 'noticeType', label: '类型' }, { prop: 'status', label: '状态' }, { prop: 'createTime', label: '创建时间' }
      ]),
      table('审计日志', '/monitor/operlog', '/monitor/operlog/list', [
        { prop: 'operId', label: '编号' }, { prop: 'title', label: '模块' },
        { prop: 'operName', label: '操作人' }, { prop: 'operIp', label: '地址' },
        { prop: 'status', label: '状态' }, { prop: 'operTime', label: '时间' }
      ]),
      gap('第三方登录', '/system/oauth', '第三方登录接口尚未提供。')
    ] }
  ],
  teacher: [
    { name: '教学工作台', children: [
      gap('教学总览', '/education/teacher/overview', '教学总览聚合接口尚未提供，页面不显示估算数字。'),
      item('今日课程 / 快捷上课', '/education/teacher/quick-start', () => import('@/views/education/teacher/QuickStart.vue')),
      gap('教学待办', '/education/teacher/todos', '教学待办接口尚未提供。'),
      item('班级消息 / 公告', '/education/teacher/announcements', teacherNews),
      gap('AI 教学助手', '/education/teacher/assistant', 'AI 教学助手由项目负责人单独交付，当前没有可调用接口。')
    ] },
    { name: '我的课程', children: [
      item('课程详情', '/education/teacher/course', course),
      gap('课程建设', '/education/teacher/build', '课程建设接口尚未提供。'),
      item('课前准备', '/education/teacher/prepare', prepare),
      item('课堂教学', '/education/teacher/classroom', () => import('@/views/education/teacher/Classroom.vue')),
      gap('课后作业与反馈', '/education/teacher/assignments', '作业与反馈接口尚未提供。'),
      gap('学情分析与教学改进', '/education/teacher/learning', '学情分析接口尚未提供。'),
      gap('AI 教学助手', '/education/teacher/course-assistant', 'AI 教学助手由项目负责人单独交付，当前没有可调用接口。')
    ] }
  ],
  student: [
    { name: '学习工作台', children: [
      gap('学习总览 / 今日待办', '/education/student/overview', '学习总览聚合接口尚未提供，页面不显示估算数字。'),
      item('正在上课 / 一键加入', '/education/student/join', () => import('@/views/education/student/JoinClass.vue')),
      gap('作业与预习截止提醒', '/education/student/deadlines', '作业与预习截止接口尚未提供。'),
      item('学习通知 / 课程公告', '/education/student/announcements', studentNews),
      gap('AI 学习助手', '/education/student/assistant', 'AI 学习助手由项目负责人单独交付，当前没有可调用接口。')
    ] },
    { name: '我的课程', children: [
      item('课程详情', '/education/student/course', course),
      gap('课程资料', '/education/student/resources', '已发布资料列表接口尚未提供。'),
      gap('课前学习', '/education/student/prepare', '预习任务接口尚未提供。'),
      item('同步课堂', '/education/student/classroom', () => import('@/views/education/student/SyncClassroom.vue')),
      gap('课后学习', '/education/student/review', '课后学习接口尚未提供。'),
      gap('我的学情', '/education/student/learning', '个人学情接口尚未提供。'),
      gap('AI 学习助手', '/education/student/course-assistant', 'AI 学习助手由项目负责人单独交付，当前没有可调用接口。')
    ] }
  ]
}

export const businessRoutes = Object.entries(roleMenus).flatMap(([role, groups]) =>
  groups.flatMap(group => group.children.map(child => ({
    path: child.path,
    component: child.component,
    meta: {
      title: child.title,
      roles: [role],
      nav: true,
      group: group.name,
      reason: child.reason || '',
      list: child.list || null
    }
  })))
)
