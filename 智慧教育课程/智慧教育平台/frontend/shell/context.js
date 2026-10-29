/* 课程/教学班上下文的前端适配。接口默认值来自 2A 待确认草案；接口缺失时关闭业务访问。 */
(function () {
  'use strict';
  var endpoints = Object.assign({ options: '/education/context/options', check: '/education/context/check' }, window.EDU_CONTEXT_ENDPOINTS || {});
  var current = null;
  var courses = [];
  var term = null;
  var scope = null;
  var storageKey = null;
  var host = null;
  var controller = null;
  var version = 0;
  var pending = null;
  var listeners = new Set();
  var status = { state: 'idle', message: '正在核验身份' };

  function id(value) {
    if (typeof value === 'number' && !Number.isSafeInteger(value)) throw new Error('接口 ID 超出安全整数范围，请后端返回字符串');
    if ((typeof value !== 'string' && typeof value !== 'number') || !/^[1-9]\d*$/.test(String(value))) throw new Error('接口 ID 格式不正确');
    return String(value);
  }
  function get() { return current ? Object.freeze(Object.assign({}, current)) : null; }
  function setStatus(state, message) { status = { state: state, message: message || '' }; render(); }
  function replaceUrl(selection) {
    var url = new URL(window.location.href);
    ['courseSpaceId', 'classId', 'bindingId'].forEach(function (key) {
      if (selection && selection[key]) url.searchParams.set(key, selection[key]); else url.searchParams.delete(key);
    });
    window.history.replaceState(null, '', url.pathname + url.search + url.hash);
  }
  function persist(selection) {
    if (!storageKey) return;
    try {
      if (selection) sessionStorage.setItem(storageKey, JSON.stringify({ courseSpaceId: selection.courseSpaceId, classId: selection.classId, bindingId: selection.bindingId }));
      else sessionStorage.removeItem(storageKey);
    } catch (error) { /* 不能保存时仍通过 URL 恢复并重新校验。 */ }
  }
  function transition(next) {
    if (!current && !next) { persist(null); return; }
    current = next ? Object.freeze(Object.assign({}, next)) : null;
    EduApi.cancelAll(); persist(current); replaceUrl(current);
    listeners.forEach(function (listener) { try { listener(get()); } catch (error) { console.error('[EduContext] 上下文监听失败', error); } });
    window.dispatchEvent(new CustomEvent('edu:context-change', { detail: { context: get() } }));
    render();
  }
  function clear(message) {
    version += 1;
    if (controller) controller.abort();
    controller = null; pending = null;
    transition(null); persist(null); replaceUrl(null);
    setStatus(courses.length ? 'ready' : 'empty', message || '请选择课程与教学班');
  }
  function decodeOptions(data) {
    if (!data || !Array.isArray(data.courses)) throw new Error('上下文选项响应不完整');
    var seenCourses = new Set();
    return data.courses.map(function (course) {
      if (typeof course.title !== 'string' || !Array.isArray(course.classes)) throw new Error('课程选项格式不正确');
      var courseId = id(course.courseSpaceId);
      if (seenCourses.has(courseId)) throw new Error('课程选项重复，请刷新后重试');
      seenCourses.add(courseId);
      var seenClasses = new Set();
      return {
        courseSpaceId: courseId, title: course.title, status: course.status,
        classes: course.classes.map(function (item) {
          if (typeof item.className !== 'string' || (item.memberRole !== 'teacher' && item.memberRole !== 'student')) throw new Error('教学班选项格式不正确');
          var classId = id(item.classId);
          var pair = classId + ':' + item.memberRole;
          if (seenClasses.has(pair)) throw new Error('教学班选项重复，请刷新后重试');
          seenClasses.add(pair);
          return Object.assign({}, item, { classId: classId, termId: id(item.termId), bindingId: item.bindingId === undefined ? undefined : id(item.bindingId) });
        }).filter(function (item) { return item.memberRole === scope.role; })
      };
    });
  }
  function fail(error) {
    if (error.cancelled || error.unauthorized) return;
    if (error.forbidden) { clear('该课程或教学班已不可用，请重新选择'); return; }
    // 若依会把未命中的接口包成 HTTP 200 + code 500（例如 "No static resource ..."），
    // 所以 404 不能只看 HTTP 状态；同时不要把服务端内部路径直接显示给用户。
    var missing = error.status === 404 || error.code === 404
      || /No static resource|Not Found/i.test(error.message || '');
    if (missing) {
      setStatus('unavailable', '课程与教学班服务尚未就绪，请联系管理员确认');
      return;
    }
    if (window.console && window.console.warn) window.console.warn('[EduContext] 加载失败', error);
    setStatus('error', '课程与教学班加载失败，请稍后重试');
  }
  async function select(courseId, classId, bindingId) {
    if (!scope || (scope.role !== 'teacher' && scope.role !== 'student')) return null;
    var selectedVersion = ++version;
    if (controller) controller.abort();
    controller = new AbortController();
    var signal = controller.signal;
    transition(null);
    pending = { courseSpaceId: courseId ? String(courseId) : '', classId: classId ? String(classId) : '' };
    try {
      if (!courseId) { clear(); return null; }
      var course = courses.find(function (item) { return item.courseSpaceId === id(courseId); });
      if (!course) throw Object.assign(new Error('当前课程不可用'), { forbidden: true });
      var next = { courseSpaceId: course.courseSpaceId, courseTitle: course.title, classId: null };
      if (classId) {
        var matches = course.classes.filter(function (item) { return item.classId === id(classId); });
        if (matches.length !== 1) throw new Error('教学班绑定不唯一，请确认上下文契约');
        var target = matches[0];
        if (bindingId && (!target.bindingId || id(bindingId) !== target.bindingId)) throw Object.assign(new Error('课程与教学班绑定已变化，请重新选择'), { forbidden: true });
        setStatus('checking', '正在验证教学班权限…');
        var query = new URLSearchParams({ classId: target.classId, courseSpaceId: course.courseSpaceId });
        if (target.bindingId) query.set('bindingId', target.bindingId);
        var result = await EduApi.get(endpoints.check + '?' + query, { signal: signal, silent403: true });
        if (selectedVersion !== version || signal.aborted) return null;
        var checked = result.data;
        if (!checked || id(checked.courseSpaceId) !== course.courseSpaceId || id(checked.classId) !== target.classId
            || id(checked.termId) !== target.termId || checked.memberRole !== scope.role
            || (target.bindingId && (!checked.bindingId || id(checked.bindingId) !== target.bindingId))) throw new Error('上下文校验结果不一致，请重新选择');
        Object.assign(next, {
          classId: target.classId, className: target.className, termId: target.termId, bindingId: checked.bindingId === undefined ? undefined : id(checked.bindingId),
          memberRole: checked.memberRole, teacherRole: checked.teacherRole,
          canPublish: scope.role === 'teacher' && checked.canPublish === true,
          canGrade: scope.role === 'teacher' && checked.canGrade === true
        });
      }
      if (selectedVersion !== version || signal.aborted) return null;
      pending = null; transition(next); setStatus('ready', next.classId ? '已验证当前教学班' : '请选择教学班'); return get();
    } catch (error) {
      if (selectedVersion !== version || signal.aborted) return null;
      pending = null; persist(null); replaceUrl(null); fail(error); return null;
    }
  }

  async function initialize(identity) {
    var url = new URL(window.location.href);
    var desired = url.searchParams.has('classId') || url.searchParams.has('courseSpaceId')
      ? { courseSpaceId: url.searchParams.get('courseSpaceId'), classId: url.searchParams.get('classId') } : null;
    scope = { userId: String(identity.userId), role: identity.role };
    storageKey = 'zhijiao.context.' + scope.userId + '.' + scope.role;
    if (!desired) { try { desired = JSON.parse(sessionStorage.getItem(storageKey) || 'null'); } catch (error) {} }
    var loadVersion = ++version;
    if (controller) controller.abort();
    controller = new AbortController(); var signal = controller.signal;
    transition(null); courses = []; term = null; pending = null;
    if (scope.role === 'admin') { persist(null); setStatus('disabled', '系统管理'); return; }
    setStatus('loading', '正在加载课程与教学班…');
    try {
      var response = await EduApi.get(endpoints.options, { signal: signal, silent403: true });
      if (loadVersion !== version || signal.aborted) return;
      courses = decodeOptions(response.data); term = response.data.term || null;
      setStatus(courses.length ? 'ready' : 'empty', courses.length ? '请选择课程与教学班' : '暂无可访问的课程');
      if (desired) {
        var courseId = desired.courseSpaceId;
        if (!courseId && desired.classId) {
          var candidates = courses.filter(function (course) { return course.classes.some(function (item) { return item.classId === String(desired.classId); }); });
          if (candidates.length !== 1) { clear('教学班对应课程不唯一或已不可用，请重新选择'); return; }
          courseId = candidates[0].courseSpaceId;
        }
        await select(courseId, desired.classId, desired.bindingId);
      } else replaceUrl(null);
    } catch (error) {
      if (loadVersion !== version || signal.aborted) return;
      persist(null); replaceUrl(null); fail(error);
    }
  }

  function render() {
    if (!host) return;
    host.replaceChildren();
    var termLabel = document.createElement('span'); termLabel.className = 'context-term';
    termLabel.textContent = term ? (term.termName || term.termCode || '当前学期') : '';
    host.appendChild(termLabel);
    if (status.state !== 'disabled' && status.state !== 'idle' && courses.length) {
      var courseSelect = document.createElement('select'); courseSelect.id = 'contextCourse'; courseSelect.setAttribute('aria-label', '当前课程');
      var classSelect = document.createElement('select'); classSelect.id = 'contextClass'; classSelect.setAttribute('aria-label', '当前教学班');
      function option(select, value, text) { var item = document.createElement('option'); item.value = value; item.textContent = text; select.appendChild(item); }
      option(courseSelect, '', '选择课程');
      courses.forEach(function (course) { option(courseSelect, course.courseSpaceId, course.title); });
      var selected = pending || current;
      courseSelect.value = selected ? selected.courseSpaceId : '';
      option(classSelect, '', '选择教学班');
      var course = courses.find(function (item) { return selected && item.courseSpaceId === selected.courseSpaceId; });
      if (course) course.classes.forEach(function (item) { option(classSelect, item.classId, item.className); });
      classSelect.value = selected && selected.classId ? selected.classId : '';
      courseSelect.disabled = !courses.length || status.state === 'loading';
      classSelect.disabled = !course || !course.classes.length || status.state === 'loading';
      courseSelect.addEventListener('change', function () { select(courseSelect.value, null); });
      classSelect.addEventListener('change', function () { select(courseSelect.value, classSelect.value); });
      host.appendChild(courseSelect); host.appendChild(classSelect);
    }
    var message = document.createElement('span'); message.className = 'context-status'; message.textContent = status.message;
    message.setAttribute('role', 'status'); host.appendChild(message);
    if (scope && scope.role !== 'admin') {
      var refresh = document.createElement('button'); refresh.type = 'button'; refresh.className = 'context-refresh'; refresh.textContent = '刷新课程';
      refresh.disabled = status.state === 'loading'; refresh.addEventListener('click', function () { initialize(scope); }); host.appendChild(refresh);
    }
  }

  window.EduContext = {
    initialize: initialize, refresh: function () { return scope ? initialize(scope) : Promise.resolve(); },
    get: get, set: function (value) { return select(value && value.courseSpaceId, value && value.classId, value && value.bindingId); },
    clear: clear, getStatus: function () { return Object.assign({}, status); },
    onChange: function (listener) { listeners.add(listener); return function () { listeners.delete(listener); }; },
    mount: function (target) { host = target; render(); },
    suspend: function () { version += 1; if (controller) controller.abort(); current = null; pending = null; render(); }
  };
})();
