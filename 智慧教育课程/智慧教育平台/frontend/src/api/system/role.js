import request from '@/utils/request'

export const listRole = params => request({ url: '/system/role/list', params })
export const getRole = roleId => request({ url: `/system/role/${roleId}` }).then(r => r.data)
export const addRole = data => request({ url: '/system/role', method: 'post', data })
export const updateRole = data => request({ url: '/system/role', method: 'put', data })
export const delRole = roleIds => request({ url: `/system/role/${roleIds}`, method: 'delete' })
export const changeRoleStatus = data => request({ url: '/system/role/changeStatus', method: 'put', data })
export const optionselect = () => request({ url: '/system/role/optionselect' }).then(r => r.data || [])
export const roleMenuTreeselect = roleId => request({ url: `/system/menu/roleMenuTreeselect/${roleId}` })
export const menuTreeselect = () => request({ url: '/system/menu/treeselect' }).then(r => r.data || [])
