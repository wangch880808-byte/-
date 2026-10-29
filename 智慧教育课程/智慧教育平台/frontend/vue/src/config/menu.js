export const roleLabels = {
  admin: '管理员',
  teacher: '教师',
  student: '学生'
}

/**
 * 菜单只列当前 Vue 工程已接入或明确待迁移的入口。
 * 未迁移页面显示占位说明，不伪造业务数据。
 */
export const menuByRole = {
  admin: [
    {
      title: '教务组织',
      items: [
        { title: '组织架构管理', path: 'depts', permission: 'system:dept:list' }
      ]
    },
    {
      title: '系统管理',
      items: [
        { title: '用户管理', path: 'users', permission: 'system:user:list' },
        { title: '角色管理', path: 'roles', permission: 'system:role:list' },
        { title: '菜单管理', path: 'menus', permission: 'system:menu:list' },
        { title: '审计日志', path: 'logs', permission: 'monitor:operlog:list' }
      ]
    }
  ],
  teacher: [
    { title: '教学工作台', items: [{ title: '教学总览', path: '', permission: '' }] }
  ],
  student: [
    { title: '学习工作台', items: [{ title: '学习总览', path: '', permission: '' }] }
  ]
}
