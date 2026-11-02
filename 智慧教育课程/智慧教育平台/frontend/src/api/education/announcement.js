import request from '@/utils/request'

export const listTeacherAnnouncements = params =>
  request({ url: '/education/teacher/announcements', params })

export const createAnnouncement = data =>
  request({ url: '/education/teacher/announcements', method: 'post', data })

export const publishAnnouncement = id =>
  request({ url: `/education/teacher/announcements/${id}/publish`, method: 'post' })

export const archiveAnnouncement = id =>
  request({ url: `/education/teacher/announcements/${id}/archive`, method: 'post' })

export const listStudentAnnouncements = params =>
  request({ url: '/education/student/announcements', params })

export const getStudentAnnouncement = id =>
  request({ url: `/education/student/announcements/${id}` }).then(body => body.data)
