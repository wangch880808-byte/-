import edu from './http'

/* 教师 / 学生 / 组织：管理员端名册页使用。账号本身的增删改仍走第 1 组系统接口。 */

export function listTeacher(query) {
  return edu({ url: '/education/teacher/list', method: 'get', params: query })
}

export function listTeacherClasses(userId) {
  return edu({ url: '/education/teacher/' + userId + '/classes', method: 'get' })
}

export function listStudent(query) {
  return edu({ url: '/education/student/list', method: 'get', params: query })
}

export function listStudentClasses(userId) {
  return edu({ url: '/education/student/' + userId + '/classes', method: 'get' })
}

/** 部门扁平列表，前端自己组树 */
export function getOrgTree() {
  return edu({ url: '/education/organization/tree', method: 'get' })
}

/** 正式课程目录下拉 */
export function listOfficialCourse(query) {
  return edu({ url: '/education/official-course/list', method: 'get', params: query })
}
