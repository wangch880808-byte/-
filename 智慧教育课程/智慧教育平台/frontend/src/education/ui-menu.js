export const roleLabels = { admin: '管理员', teacher: '教师', student: '学生' }

// 管理员导航按用户提供的版本排列；空白菜单只提供导航，不调用业务接口。
export const menuByRole = {
  admin: [
  {
    "title": "工作台",
    "items": [
      {
        "id": "overview",
        "title": "管理总览",
        "path": "overview"
      },
      {
        "id": "todos",
        "title": "待办事项",
        "path": "todos"
      },
      {
        "id": "operations",
        "title": "运行概况",
        "path": "operations"
      }
    ]
  },
  {
    "title": "教务组织",
    "items": [
      {
        "id": "depts",
        "title": "组织架构管理",
        "path": "depts"
      },
      {
        "id": "classes",
        "title": "班级管理",
        "path": "classes"
      },
      {
        "id": "teachers",
        "title": "教师管理",
        "path": "teachers"
      },
      {
        "id": "students",
        "title": "学生管理",
        "path": "students"
      },
      {
        "id": "discussions",
        "title": "留言互动",
        "path": "discussions"
      },
      {
        "id": "terms",
        "title": "学期管理",
        "path": "terms"
      }
    ]
  },
  {
    "title": "能力培养",
    "items": [
      {
        "id": "evaluation-indicators",
        "title": "评价指标管理",
        "path": "evaluation-indicators"
      },
      {
        "id": "certificates",
        "title": "证书配置",
        "path": "certificates"
      },
      {
        "id": "teaching-analysis",
        "title": "教学过程分析",
        "path": "teaching-analysis"
      },
      {
        "id": "teaching-issues",
        "title": "教学问题研判",
        "path": "teaching-issues"
      },
      {
        "id": "practical-courses",
        "title": "实训课程",
        "path": "practical-courses"
      },
      {
        "id": "practical-tasks",
        "title": "实训任务",
        "path": "practical-tasks"
      },
      {
        "id": "admin-dashboard",
        "title": "管理员看板",
        "path": "admin-dashboard"
      },
      {
        "id": "teacher-workspace",
        "title": "教师工作台",
        "path": "teacher-workspace"
      }
    ]
  },
  {
    "title": "学生学习",
    "items": [
      {
        "id": "campus-news",
        "title": "校园资讯",
        "path": "campus-news"
      },
      {
        "id": "learning-home",
        "title": "学习首页",
        "path": "learning-home"
      },
      {
        "id": "my-courses",
        "title": "我的课程",
        "path": "my-courses"
      },
      {
        "id": "tasks",
        "title": "任务中心",
        "path": "tasks"
      },
      {
        "id": "capability-analysis",
        "title": "能力分析",
        "path": "capability-analysis"
      },
      {
        "id": "ability-assessment",
        "title": "单项能力评测",
        "path": "ability-assessment"
      },
      {
        "id": "my-messages",
        "title": "我的留言",
        "path": "my-messages"
      }
    ]
  },
  {
    "title": "AI 大模型",
    "items": [
      {
        "id": "ai-chat",
        "title": "AI 对话",
        "path": "ai-chat"
      },
      {
        "id": "ai-console",
        "title": "控制台",
        "path": "ai-console"
      }
    ]
  },
  {
    "title": "基础设施",
    "items": [
      {
        "id": "configuration",
        "title": "配置管理",
        "path": "configuration"
      },
      {
        "id": "files",
        "title": "文件管理",
        "path": "files"
      },
      {
        "id": "monitoring",
        "title": "监控中心",
        "path": "monitoring"
      },
      {
        "id": "scheduled-jobs",
        "title": "定时任务",
        "path": "scheduled-jobs"
      }
    ]
  },
  {
    "title": "系统管理",
    "items": [
      {
        "id": "users",
        "title": "用户管理",
        "path": "users"
      },
      {
        "id": "roles",
        "title": "角色管理",
        "path": "roles"
      },
      {
        "id": "menus",
        "title": "菜单管理",
        "path": "menus"
      },
      {
        "id": "dictionaries",
        "title": "字典管理",
        "path": "dictionaries"
      },
      {
        "id": "notifications",
        "title": "通知中心",
        "path": "notifications"
      },
      {
        "id": "logs",
        "title": "审计日志",
        "path": "logs"
      },
      {
        "id": "third-party-login",
        "title": "第三方登录",
        "path": "third-party-login"
      }
    ]
  }
],
  teacher: [{ title: '教学工作台', items: [{ title: '教学总览', path: '', permission: '' }] }],
  student: [{ title: '学习工作台', items: [{ title: '学习总览', path: '', permission: '' }] }]
}

export const adminMenuItems = menuByRole.admin.flatMap(group =>
  group.items.map(item => ({ ...item, group: group.title })))

// 已接入页面仍须通过既有权限和服务端路由校验；无实现的项目保留为空白。
export function buildAdminGroups(pageList, canOpen) {
  const knownPaths = new Set(adminMenuItems.map(item => item.path))
  const groups = menuByRole.admin.map(group => ({
    title: group.title,
    items: group.items.map(item => {
      const page = pageList.find(page => page.roles.includes('admin') && page.path === item.path)
      return { ...item, group: group.title, businessPage: page || null, disabled: !!page && !canOpen(page) }
    })
  }))
  // 后续业务组注册的真实页面继续接入，避免导航调整使已有扩展页面丢失。
  for (const page of pageList) {
    if (page.sidebar === false || !page.roles.includes('admin') || knownPaths.has(page.path) || !canOpen(page)) continue
    let group = groups.find(group => group.title === (page.group || '工作台'))
    if (!group) { group = { title: page.group || '工作台', items: [] }; groups.push(group) }
    group.items.push({ ...page, businessPage: page, disabled: false })
  }
  return groups
}
