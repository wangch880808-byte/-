import edu from './http'

const base = '/education/administrative-class'

export function listAdministrativeClasses(params) {
  return edu({ url: base + '/list', method: 'get', params })
}

export function updateAdministrativeClass(id, data) {
  return edu({ url: base + '/' + id, method: 'put', data })
}

export function listClassRoster(id, params) {
  return edu({ url: base + '/' + id + '/roster', method: 'get', params })
}

export function moveClassStudents(id, userIds, adding) {
  return edu({ url: base + '/' + id + '/roster', method: 'put', data: { userIds, adding } })
}

export function exportClassRoster(id) {
  return edu({ url: base + '/' + id + '/roster/export', method: 'post', responseType: 'blob' })
}

export function importClassRoster(id, file) {
  const data = new FormData()
  data.append('file', file)
  return edu({ url: base + '/' + id + '/roster/import', method: 'post', data, timeout: 120000 })
}
