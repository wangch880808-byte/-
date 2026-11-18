import edu from './http'

/**
 * 跨组共用的作用域查询（第 3、4、5、6 组页面也会直接调这些）。
 * 身份一律由服务端会话决定，不接受传入 userId 冒充。
 */

/** 当前学期；未设置时返回 null */
export function getCurrentTerm() {
  return edu({ url: '/education/scope/term/current', method: 'get' })
}

/** 当前账号可教 / 可学的教学班，含 teacher、student 与权限位 */
export function listMyClasses() {
  return edu({ url: '/education/scope/my/classes', method: 'get' })
}

/** 当前账号在指定班级的身份：课堂加入、跟课入口先用它校验 */
export function getMyMembership(classId) {
  return edu({ url: '/education/scope/classes/' + classId + '/me', method: 'get' })
}

/** 有效成员名单（管理员或本班教师） */
export function listActiveMembers(classId) {
  return edu({ url: '/education/scope/classes/' + classId + '/members', method: 'get' })
}

/** 该班当前绑定的课程空间；未绑定返回 null */
export function getClassBinding(classId) {
  return edu({ url: '/education/scope/classes/' + classId + '/binding', method: 'get' })
}

/** 课程当前绑定的班级与人数（管理员或课程负责人） */
export function listCourseClasses(courseId) {
  return edu({ url: '/education/scope/courses/' + courseId + '/classes', method: 'get' })
}
