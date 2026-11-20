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

export function deleteCourse(id) {
  return edu({ url: '/education/course/' + id, method: 'delete' })
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

const courseManagementRequest = (courseId, suffix, method = 'get', data, params) =>
  edu({ url: '/education/course/' + courseId + suffix, method, data, params, headers: { silent403: true } })

export const renameCourseTeachingClass = (courseId, classId, name) =>
  courseManagementRequest(courseId, '/teaching-classes/' + classId, 'put', { name })
export const deleteCourseTeachingClass = (courseId, classId) =>
  courseManagementRequest(courseId, '/teaching-classes/' + classId, 'delete')
export const changeCourseClassStudents = (courseId, classId, data) =>
  courseManagementRequest(courseId, '/teaching-classes/' + classId + '/students/change', 'post', data)
export const getCourseTeam = courseId => courseManagementRequest(courseId, '/team')
export const listCourseTeamCandidates = (courseId, params) =>
  courseManagementRequest(courseId, '/team/candidates', 'get', undefined, params)
export const saveCourseTeacher = (courseId, data) => courseManagementRequest(courseId, '/team', 'post', data)
export const removeCourseTeacher = (courseId, userId) => courseManagementRequest(courseId, '/team/' + userId, 'delete')

export function createCourseTeachingClass(courseId, data) {
  return edu({ url: '/education/course/' + courseId + '/teaching-classes', method: 'post', data, headers: { silent403: true } })
}

function courseClassStudentsUrl(courseId, classId) {
  return '/education/course/' + courseId + '/teaching-classes/' + classId + '/students'
}

export function listCourseClassStudentSources(courseId, classId) {
  return edu({ url: courseClassStudentsUrl(courseId, classId) + '/sources', method: 'get', headers: { silent403: true } })
}

export function listCourseClassStudentOrganizations(courseId, classId) {
  return edu({ url: courseClassStudentsUrl(courseId, classId) + '/organizations', method: 'get', headers: { silent403: true } })
}

export function listCourseClassStudentCandidates(courseId, classId, params) {
  return edu({ url: courseClassStudentsUrl(courseId, classId) + '/candidates', method: 'get', params, headers: { silent403: true } })
}

export function addCourseClassStudents(courseId, classId, data) {
  return edu({ url: courseClassStudentsUrl(courseId, classId), method: 'post', data, headers: { silent403: true } })
}
