import edu from './http'

/* ---------- 教学班：管理员 ---------- */
export function listTeachingClass(query) {
  return edu({ url: '/education/teaching-class/list', method: 'get', params: query })
}

export function getTeachingClass(id) {
  return edu({ url: '/education/teaching-class/' + id, method: 'get' })
}

export function addTeachingClass(data) {
  return edu({ url: '/education/teaching-class', method: 'post', data })
}

export function updateTeachingClass(id, data) {
  return edu({ url: '/education/teaching-class/' + id, method: 'put', data })
}

/** 当前教师被授权的教学班：教师端绑定课程下拉 */
export function listMyTeachingClass() {
  return edu({ url: '/education/teaching-class/mine', method: 'get' })
}

/* ---------- 成员：含已退出的历史记录 ---------- */
export function listClassMembers(classId) {
  return edu({ url: '/education/teaching-class/' + classId + '/members', method: 'get' })
}

export function addClassMember(classId, data) {
  return edu({ url: '/education/teaching-class/' + classId + '/members', method: 'post', data })
}

/** 退班只写 left_at，不删记录；reason 进入审计 */
export function removeClassMember(classId, studentId, reason) {
  return edu({
    url: '/education/teaching-class/' + classId + '/members/' + studentId,
    method: 'delete',
    params: { reason }
  })
}

/* ---------- 任课教师授权 ---------- */
export function listClassTeachers(classId) {
  return edu({ url: '/education/teaching-class/' + classId + '/teachers', method: 'get' })
}

export function addClassTeacher(classId, data) {
  return edu({ url: '/education/teaching-class/' + classId + '/teachers', method: 'post', data })
}

/** 撤权写 valid_to，不物理删除 */
export function removeClassTeacher(classId, userId) {
  return edu({ url: '/education/teaching-class/' + classId + '/teachers/' + userId, method: 'delete' })
}
