import edu from './http'

/* 教师 / 学生 / 组织：管理员端名册页使用。 */

export function listTeacher(query) {
  return edu({ url: '/education/teacher/list', method: 'get', params: query })
}

export function listTeacherClasses(userId) {
  return edu({ url: '/education/teacher/' + userId + '/classes', method: 'get' })
}

export function listStudent(query) {
  return edu({ url: '/education/student/list', method: 'get', params: query })
}

export function listStudentAccounts(query) {
  return edu({ url: '/education/student/accounts', method: 'get', params: query })
}

export function updateStudentAccount(userId, data) {
  return edu({ url: '/education/student/accounts/' + userId, method: 'put', data })
}

export function disableStudentAccount(userId) {
  return edu({ url: '/education/student/accounts/' + userId + '/disable', method: 'put' })
}

export function listStudentClasses(userId) {
  return edu({ url: '/education/student/' + userId + '/classes', method: 'get' })
}

/** 部门扁平列表，前端自己组树 */
export function getOrgTree(params) {
  return edu({ url: '/education/organization/tree', method: 'get', params })
}

/** 正式课程目录下拉 */
export function listOfficialCourse(query) {
  return edu({ url: '/education/official-course/list', method: 'get', params: query })
}
