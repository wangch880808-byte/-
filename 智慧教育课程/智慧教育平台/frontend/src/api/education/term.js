import edu from './http'

/** 学期分页：管理员 */
export function listTerm(query) {
  return edu({ url: '/education/term/list', method: 'get', params: query })
}

export function addTerm(data) {
  return edu({ url: '/education/term', method: 'post', data })
}

export function updateTerm(id, data) {
  return edu({ url: '/education/term/' + id, method: 'put', data })
}

/** 设为当前学期：服务端先清空再置位，同一时刻只有一个 */
export function setCurrentTerm(id) {
  return edu({ url: '/education/term/' + id + '/current', method: 'put' })
}
