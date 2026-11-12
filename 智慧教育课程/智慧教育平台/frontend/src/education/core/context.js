// 不依赖 Vue，供课程上下文 UI 与回归测试共同使用。服务端仍逐请求校验数据范围。
export function objectId(value) {
  if ((typeof value !== 'string' && typeof value !== 'number') ||
      (typeof value === 'number' && !Number.isSafeInteger(value)) || !/^[1-9]\d*$/.test(String(value))) {
    throw new Error('接口 ID 不正确，请使用正整数字符串')
  }
  return String(value)
}

export function createContextController({ state, request, storage, onChange = () => {}, readSelection = () => null, writeSelection = () => {} }) {
  let version = 0
  let controller = null
  let identity = null
  let key = ''
  const endpoints = { options: '/education/context/options', check: '/education/context/check' }
  function saved() {
    try { return JSON.parse(storage?.getItem(key) || 'null') } catch { return null }
  }
  function transition(next, persist = true) {
    state.current = next ? Object.freeze({ ...next }) : null
    if (persist) {
      try {
        if (next) storage?.setItem(key, JSON.stringify({ courseSpaceId: next.courseSpaceId, classId: next.classId, bindingId: next.bindingId }))
        else if (key) storage?.removeItem(key)
      } catch { /* 无持久化权限时仍可使用当前上下文。 */ }
      writeSelection(state.current)
    }
    onChange(state.current)
  }
  function stop() { version++; controller?.abort(); controller = null }
  function fail(error) {
    if (error.cancelled || error.unauthorized) return
    state.status = error.forbidden ? 'ready' : (error.status === 404 || error.code === 404 || /No static resource|Not Found/i.test(error.message || '')) ? 'unavailable' : 'error'
    state.message = error.forbidden ? '该课程或教学班已不可用，请重新选择' : state.status === 'unavailable'
      ? '课程与教学班服务尚未就绪' : '课程与教学班加载失败，请重试'
  }
  function decode(data) {
    if (!data || !Array.isArray(data.courses)) throw new Error('上下文响应不完整')
    const seen = new Set()
    return data.courses.map(course => {
      const courseSpaceId = objectId(course.courseSpaceId)
      if (seen.has(courseSpaceId) || typeof course.title !== 'string' || !Array.isArray(course.classes)) throw new Error('课程选项不正确')
      seen.add(courseSpaceId)
      const classesSeen = new Set()
      const classes = course.classes.map(item => {
        const classId = objectId(item.classId)
        if (classesSeen.has(classId + ':' + item.memberRole) || typeof item.className !== 'string' || !['teacher', 'student'].includes(item.memberRole)) throw new Error('教学班选项不正确')
        classesSeen.add(classId + ':' + item.memberRole)
        return { ...item, classId, termId: objectId(item.termId), bindingId: item.bindingId == null ? undefined : objectId(item.bindingId) }
      }).filter(item => item.memberRole === identity.role)
      return { courseSpaceId, title: course.title, classes }
    })
  }
  async function select(courseId, classId, bindingId) {
    if (!identity || !['teacher', 'student'].includes(identity.role)) return null
    stop()
    const run = version
    controller = new AbortController()
    const signal = controller.signal
    transition(null)
    state.pending = { courseSpaceId: courseId ? String(courseId) : '', classId: classId ? String(classId) : '' }
    state.status = 'checking'; state.message = '正在核验课程与教学班…'
    try {
      if (!courseId) { clear(); return null }
      const course = state.courses.find(item => item.courseSpaceId === objectId(courseId))
      if (!course) throw Object.assign(new Error('课程不可用'), { forbidden: true })
      const next = { courseSpaceId: course.courseSpaceId, courseTitle: course.title, classId: null }
      if (classId) {
        const target = course.classes.find(item => item.classId === objectId(classId))
        if (!target || (bindingId && target.bindingId !== objectId(bindingId))) throw Object.assign(new Error('绑定已变化'), { forbidden: true })
        const params = new URLSearchParams({ courseSpaceId: course.courseSpaceId, classId: target.classId })
        if (target.bindingId) params.set('bindingId', target.bindingId)
        const { data } = await request(endpoints.check + '?' + params, { signal, silent403: true })
        if (signal.aborted || run !== version) return null
        if (!data || objectId(data.courseSpaceId) !== course.courseSpaceId || objectId(data.classId) !== target.classId ||
            objectId(data.termId) !== target.termId || data.memberRole !== identity.role ||
            (target.bindingId && objectId(data.bindingId) !== target.bindingId)) throw new Error('上下文校验不一致')
        Object.assign(next, { classId: target.classId, className: target.className, termId: target.termId,
          bindingId: data.bindingId == null ? undefined : objectId(data.bindingId), memberRole: data.memberRole,
          canPublish: identity.role === 'teacher' && data.canPublish === true,
          canGrade: identity.role === 'teacher' && data.canGrade === true })
      }
      if (signal.aborted || run !== version) return null
      state.pending = null; transition(next)
      state.status = 'ready'
      state.message = next.classId
        ? (identity.role === 'student' ? '已验证当前班级' : '已验证当前教学班')
        : (identity.role === 'student' ? '请选择班级' : '请选择教学班')
      return state.current
    } catch (error) {
      if (signal.aborted || run !== version) return null
      state.pending = null; transition(null); fail(error)
      return null
    }
  }
  async function initialize(nextIdentity, selection = readSelection()) {
    const desired = selection || (nextIdentity && key === 'zhijiao.context.' + nextIdentity.userId + '.' + nextIdentity.role ? saved() : null)
    stop()
    const run = version
    identity = nextIdentity
    key = identity ? 'zhijiao.context.' + identity.userId + '.' + identity.role : ''
    const restore = desired || saved()
    transition(null, false)
    state.courses = []; state.term = null; state.pending = null
    if (!identity || identity.role === 'admin') {
      state.status = 'disabled'; state.message = ''; transition(null); return
    }
    state.status = 'loading'; state.message = '正在加载课程与教学班…'
    controller = new AbortController()
    const signal = controller.signal
    try {
      const { data } = await request(endpoints.options, { signal, silent403: true })
      if (signal.aborted || run !== version) return
      state.courses = decode(data); state.term = data.term || null
      state.status = state.courses.length ? 'ready' : 'empty'
      state.message = state.courses.length ? '请选择课程与教学班' : '暂无可访问的课程'
      if (restore) {
        let courseId = restore.courseSpaceId
        if (!courseId && restore.classId) {
          const matches = state.courses.filter(course => course.classes.some(item => item.classId === String(restore.classId)))
          if (matches.length !== 1) { clear('教学班对应课程不唯一，请重新选择'); return }
          courseId = matches[0].courseSpaceId
        }
        await select(courseId, restore.classId, restore.bindingId)
      } else transition(null)
    } catch (error) {
      if (signal.aborted || run !== version) return
      transition(null); fail(error)
    }
  }
  function clear(message = '请选择课程与教学班') {
    stop(); state.pending = null; transition(null)
    state.status = state.courses.length ? 'ready' : 'empty'; state.message = message
  }
  function suspend() {
    stop(); state.pending = null; transition(null, false); state.status = 'idle'; state.message = ''
  }
  return { initialize, select, clear, suspend, configure: next => Object.assign(endpoints, next) }
}
