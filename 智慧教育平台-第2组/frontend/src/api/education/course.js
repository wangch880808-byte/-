import edu from './http'

/**
 * 课程空间列表。数据范围由服务端按当前登录人裁剪：
 * 管理员全部 / 教师本人创建 / 学生已发布且本班已绑定。
 */
export function listCourse(query) {
  return edu({ url: '/education/course/list', method: 'get', params: query })
}

export function getCourse(id) {
  return edu({ url: '/education/course/' + id, method: 'get' })
}

export function addCourse(data) {
  return edu({ url: '/education/course', method: 'post', data })
}

export function updateCourse(id, data) {
  return edu({ url: '/education/course/' + id, method: 'put', data })
}

/** status: DRAFT 撤回 / ACTIVE 发布 / ARCHIVED 归档 */
export function changeCourseStatus(id, status) {
  return edu({ url: '/education/course/' + id + '/status', method: 'put', data: { status } })
}

/** 绑定教学班；解绑用的是绑定记录 id，不是班级 id */
export function bindCourseClass(courseId, classId) {
  return edu({ url: '/education/course/' + courseId + '/classes', method: 'post', data: { classId } })
}

export function unbindCourseClass(courseId, bindingId) {
  return edu({ url: '/education/course/' + courseId + '/classes/' + bindingId, method: 'delete' })
}
