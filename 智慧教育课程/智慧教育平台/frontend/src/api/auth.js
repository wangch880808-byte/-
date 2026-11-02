import request from '@/utils/request'
export const getCaptcha = () => request({ url: '/captchaImage', auth: false })
export const login = data => request({ url: '/login', method: 'post', data, auth: false })
export const getInfo = () => request({ url: '/getInfo', skipSessionRedirect: true })
export const getRouters = () => request({ url: '/getRouters', skipSessionRedirect: true })
export const logout = () => request({ url: '/logout', method: 'post', skipSessionRedirect: true })
