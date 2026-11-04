import request from '@/utils/request'

export const listUser = params => request({ url: '/system/user/list', params })
export const getUser = userId => request({ url: userId == null ? '/system/user/' : `/system/user/${userId}` })
export const addUser = data => request({ url: '/system/user', method: 'post', data })
export const updateUser = data => request({ url: '/system/user', method: 'put', data })
export const delUser = userIds => request({ url: `/system/user/${userIds}`, method: 'delete' })
export const resetUserPwd = data => request({ url: '/system/user/resetPwd', method: 'put', data })
export const changeUserStatus = data => request({ url: '/system/user/changeStatus', method: 'put', data })
export const deptTreeSelect = () => request({ url: '/system/user/deptTree' }).then(r => r.data || [])
