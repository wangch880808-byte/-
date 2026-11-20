/**
 * 教师端 / 学生端的菜单层级定义。
 * 一级：工作台、我的课程；二级：工作台分区、课程列表、课程详情分区。
 * 二级菜单与内容页面一一对应：工作台分区对应同一页面的不同区块，
 * 课程详情分区对应课程详情页内的不同内容区。
 */
export const workbenchSections = {
  teacher: [
    { key: 'overview', title: '教学总览' },
    { key: 'today', title: '今日课程 / 快捷上课' },
    { key: 'todo', title: '教学待办' },
    { key: 'notice', title: '班级消息 / 公告' },
    { key: 'ai', title: 'AI 教学助手' }
  ],
  student: [
    { key: 'overview', title: '学习总览 / 今日待办' },
    { key: 'today', title: '正在上课 / 一键加入' },
    { key: 'deadline', title: '作业与预习截止提醒' },
    { key: 'notice', title: '学习通知 / 课程公告' },
    { key: 'ai', title: 'AI 学习助手' }
  ]
}

export const courseSections = {
  teacher: [
    { key: 'outline', title: '课程概览' },
    { key: 'studio', title: '课程建设' },
    { key: 'prepare', title: '课前准备' },
    { key: 'classroom', title: '课堂教学' },
    { key: 'homework', title: '课后作业与反馈' },
    { key: 'stat', title: '学情分析与教学改进' },
    { key: 'ai', title: 'AI 教学助手' }
  ],
  student: [
    { key: 'outline', title: '课程概览' },
    { key: 'material', title: '课程资料' },
    { key: 'prepare', title: '课前学习' },
    { key: 'classroom', title: '同步课堂' },
    { key: 'homework', title: '课后学习' },
    { key: 'stat', title: '我的学情' },
    { key: 'ai', title: 'AI 学习助手' }
  ]
}

export function workbenchTitle(role) {
  return role === 'student' ? '学习工作台' : '教学工作台'
}
