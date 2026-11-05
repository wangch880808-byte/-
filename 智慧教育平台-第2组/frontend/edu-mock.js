/* ==========================================================================
   edu-mock.js —— 演示数据层（第 2 组 2B）
   --------------------------------------------------------------------------
   为什么要有它？
   2A 的接口没写完之前，页面不能一直等。有了这一层，双击 html 就能看到完整交互：
   列表、筛选、分页、新建、移出成员、四种状态，全都能点。
   接口好了以后什么都不用改：只要地址栏不带 ?mock，请求就走真实后端。

   怎么用：
     ?mock=1        正常数据
     ?mock=empty    空数据（看空状态）
     ?mock=forbidden 无权限（看 403 提示）
     ?mock=error    请求失败（看失败提示 + 重试）
   右下角紫色角标可以切换场景，点一下换一个并刷新页面。
   关掉：?mock=0
   ========================================================================== */
(function (window, document) {
  'use strict';

  var STORE_KEY = 'zhijiao.edu.mock';
  var SCENARIOS = ['normal', 'empty', 'forbidden', 'error'];
  var SCENARIO_LABEL = { normal: '正常数据', empty: '空数据', forbidden: '无权限', error: '请求失败' };

  /* ---- 开关：地址栏参数优先，其次读上次的选择 ---- */
  var param = (location.search.match(/[?&]mock=([^&]*)/) || [])[1];
  var scenario = null;
  if (param !== undefined) {
    scenario = decodeURIComponent(param);
    if (scenario === '0' || scenario === 'false') {
      try { sessionStorage.removeItem(STORE_KEY); } catch (e) { /* 忽略 */ }
      scenario = null;
    } else {
      if (scenario === '1' || scenario === '') scenario = 'normal';
      try { sessionStorage.setItem(STORE_KEY, scenario); } catch (e) { /* 忽略 */ }
    }
  } else {
    try { scenario = sessionStorage.getItem(STORE_KEY); } catch (e) { scenario = null; }
  }
  var enabled = SCENARIOS.indexOf(scenario) !== -1;
  if (!enabled) { window.EduMock = { isEnabled: function () { return false; } }; return; }

  /* ---- 假登录态：让 role-home.js 的核验也能通过 ---- */
  try {
    if (!sessionStorage.getItem('zhijiao.login.token')) {
      sessionStorage.setItem('zhijiao.login.token', 'mock-token');
      sessionStorage.setItem('zhijiao.login.role', document.body.dataset.role || 'admin');
    }
  } catch (e) { /* 忽略 */ }

  /* ---- 假数据 ---- */
  var db = {
    terms: [
      { id: 1, code: '2025-2026-1', name: '2025-2026 学年第一学期', startsOn: '2025-09-01', endsOn: '2026-01-15', current: false },
      { id: 2, code: '2025-2026-2', name: '2025-2026 学年第二学期', startsOn: '2026-02-23', endsOn: '2026-07-10', current: false },
      { id: 3, code: '2026-2027-1', name: '2026-2027 学年第一学期', startsOn: '2026-09-01', endsOn: '2027-01-15', current: true }
    ],
    officialCourses: [
      { id: 1, code: 'SE101', name: '软件工程导论', status: 'ACTIVE' },
      { id: 2, code: 'CS201', name: '数据结构与算法', status: 'ACTIVE' },
      { id: 3, code: 'MA102', name: '高等数学（二）', status: 'ACTIVE' }
    ],
    teachers: [
      { userId: 101, userName: 'T2019007', nickName: '王建国', deptName: '计算机学院', phone: '138****2211', classCount: 2 },
      { userId: 102, userName: 'T2020013', nickName: '李慧敏', deptName: '计算机学院', phone: '139****8890', classCount: 1 },
      { userId: 103, userName: 'T2015002', nickName: '张若川', deptName: '数学学院', phone: '137****4455', classCount: 1 },
      { userId: 104, userName: 'T2021031', nickName: '陈晓宇', deptName: '计算机学院', phone: '136****1188', classCount: 0 }
    ],
    students: [
      { userId: 201, userName: '2023010001', nickName: '赵一鸣', className: '2026 秋-软件工程 1 班', classCount: 2 },
      { userId: 202, userName: '2023010002', nickName: '钱思远', className: '2026 秋-软件工程 1 班', classCount: 1 },
      { userId: 203, userName: '2023010003', nickName: '孙小雨', className: '2026 秋-软件工程 2 班', classCount: 2 },
      { userId: 204, userName: '2023010004', nickName: '李慕白', className: '2026 秋-数据结构 1 班', classCount: 1 },
      { userId: 205, userName: '2023010005', nickName: '周timing', className: '', classCount: 0 }
    ],
    classes: [
      { id: 11, name: '2026 秋-软件工程 1 班', code: 'SE-2026-01', termId: 3, semesterName: '2026-2027 学年第一学期', courseId: 1, courseName: '软件工程导论', teacherId: 101, teacherName: '王建国', studentCount: 2, status: 'ACTIVE', classKind: 'FORMAL' },
      { id: 12, name: '2026 秋-软件工程 2 班', code: 'SE-2026-02', termId: 3, semesterName: '2026-2027 学年第一学期', courseId: 1, courseName: '软件工程导论', teacherId: 101, teacherName: '王建国', studentCount: 2, status: 'ACTIVE', classKind: 'FORMAL' },
      { id: 13, name: '2026 秋-数据结构 1 班', code: 'DS-2026-01', termId: 3, semesterName: '2026-2027 学年第一学期', courseId: 2, courseName: '数据结构与算法', teacherId: 102, teacherName: '李慧敏', studentCount: 1, status: 'ACTIVE', classKind: 'FORMAL' },
      { id: 14, name: '2026 秋-高数试点班', code: 'MA-2026-T1', termId: 3, semesterName: '2026-2027 学年第一学期', courseId: 3, courseName: '高等数学（二）', teacherId: 103, teacherName: '张若川', studentCount: 0, status: 'DRAFT', classKind: 'TRIAL' },
      { id: 15, name: '2025 秋-软件工程 1 班', code: 'SE-2025-01', termId: 1, semesterName: '2025-2026 学年第一学期', courseId: 1, courseName: '软件工程导论', teacherId: 101, teacherName: '王建国', studentCount: 0, status: 'ARCHIVED', classKind: 'FORMAL' }
    ],
    members: [
      { classId: 11, studentId: 201, studentNo: '2023010001', studentName: '赵一鸣', status: 'ACTIVE', joinedAt: '2026-09-02', leftAt: null },
      { classId: 11, studentId: 202, studentNo: '2023010002', studentName: '钱思远', status: 'ACTIVE', joinedAt: '2026-09-02', leftAt: null },
      { classId: 11, studentId: 203, studentNo: '2023010003', studentName: '孙小雨', status: 'LEFT', joinedAt: '2026-09-02', leftAt: '2026-10-11' },
      { classId: 12, studentId: 201, studentNo: '2023010001', studentName: '赵一鸣', status: 'ACTIVE', joinedAt: '2026-09-05', leftAt: null },
      { classId: 12, studentId: 203, studentNo: '2023010003', studentName: '孙小雨', status: 'ACTIVE', joinedAt: '2026-09-05', leftAt: null },
      { classId: 13, studentId: 204, studentNo: '2023010004', studentName: '李慕白', status: 'ACTIVE', joinedAt: '2026-09-03', leftAt: null }
    ],
    classTeachers: [
      { classId: 11, userId: 101, userName: 'T2019007', nickName: '王建国', teacherRole: 'LEAD', canPublish: 1, canGrade: 1, canExport: 1, validFrom: '2026-08-20', validTo: null },
      { classId: 11, userId: 104, userName: 'T2021031', nickName: '陈晓宇', teacherRole: 'CO_TEACHER', canPublish: 1, canGrade: 0, canExport: 0, validFrom: '2026-09-01', validTo: null },
      { classId: 12, userId: 101, userName: 'T2019007', nickName: '王建国', teacherRole: 'LEAD', canPublish: 1, canGrade: 1, canExport: 0, validFrom: '2026-08-20', validTo: null },
      { classId: 13, userId: 102, userName: 'T2020013', nickName: '李慧敏', teacherRole: 'LEAD', canPublish: 1, canGrade: 1, canExport: 0, validFrom: '2026-08-25', validTo: null }
    ],
    departments: [
      { id: 100, name: '示范大学', parentId: null, teacherCount: 4, studentCount: 5 },
      { id: 110, name: '计算机学院', parentId: 100, teacherCount: 3, studentCount: 4 },
      { id: 120, name: '数学学院', parentId: 100, teacherCount: 1, studentCount: 1 },
      { id: 111, name: '软件工程系', parentId: 110, teacherCount: 2, studentCount: 4 },
      { id: 112, name: '数据科学系', parentId: 110, teacherCount: 1, studentCount: 0 }
    ],
    courses: [
      { id: 501, name: '软件工程导论', status: 'ACTIVE', semesterName: '2026-2027 学年第一学期', teacherName: '王建国', className: '2026 秋-软件工程 1 班', classCount: 2, studentCount: 4, updatedAt: '2026-10-20', summary: '从需求到交付的完整工程方法，强调可验证的交付物。', learningObjectives: '能写出可评审的需求说明；能用 UML 表达设计；能组织一次迭代。', officialCourseId: 1, officialCourseName: 'SE101 软件工程导论' },
      { id: 502, name: '数据结构与算法', status: 'ACTIVE', semesterName: '2026-2027 学年第一学期', teacherName: '李慧敏', className: '2026 秋-数据结构 1 班', classCount: 1, studentCount: 1, updatedAt: '2026-10-12', summary: '线性表、树、图与常见排序查找算法。', learningObjectives: '能针对问题选择合适的结构；能分析时间与空间复杂度。', officialCourseId: 2, officialCourseName: 'CS201 数据结构与算法' },
      { id: 503, name: '软件工程导论（备课中）', status: 'DRAFT', semesterName: '2026-2027 学年第一学期', teacherName: '王建国', className: '—', classCount: 0, studentCount: 0, updatedAt: '2026-10-25', summary: '', learningObjectives: '', officialCourseId: 1, officialCourseName: 'SE101 软件工程导论' }
    ]
  };

  var nextId = 900;

  /* ---- 工具 ---- */
  function ok(data) { return Promise.resolve(data); }
  function fail(message, kind) {
    var error = new Error(message);
    error.kind = kind || 'business';
    return Promise.reject(error);
  }
  function clone(value) { return JSON.parse(JSON.stringify(value)); }
  function paginate(rows, params) {
    var pageNum = Number((params && params.pageNum) || 1);
    var pageSize = Number((params && params.pageSize) || 10);
    var start = (pageNum - 1) * pageSize;
    return { rows: rows.slice(start, start + pageSize), total: rows.length };
  }
  function contains(value, keyword) {
    if (!keyword) return true;
    return String(value === null || value === undefined ? '' : value).indexOf(keyword) !== -1;
  }
  function today() { return new Date().toISOString().slice(0, 10); }

  /* ---- 路由表：path → 处理函数 ---- */
  function route(method, path, params, body) {
    var match;

    /* --- 登录相关：给 role-home.js 用 --- */
    if (path === '/getInfo' || path === '/getRouters') {
      return ok(path === '/getInfo'
        ? { user: { nickName: '演示账号', userName: 'demo' }, roles: [document.body.dataset.role || 'admin'] }
        : []);
    }
    if (path === '/logout') return ok(true);

    /* --- 学期 --- */
    if (method === 'GET' && path === '/term/list') {
      var terms = db.terms.filter(function (item) { return contains(item.name + item.code, params.keyword); });
      return ok(paginate(terms, params));
    }
    if (method === 'POST' && path === '/term') {
      var newTerm = { id: ++nextId, name: body.name, code: body.code, startsOn: body.startsOn, endsOn: body.endsOn, current: false };
      db.terms.push(newTerm);
      return ok(newTerm);
    }
    if (method === 'PUT' && (match = path.match(/^\/term\/(\d+)\/current$/))) {
      db.terms.forEach(function (item) { item.current = String(item.id) === match[1]; });
      return ok(true);
    }
    if (method === 'PUT' && (match = path.match(/^\/term\/(\d+)$/))) {
      var term = db.terms.find(function (item) { return String(item.id) === match[1]; });
      if (!term) return fail('学期不存在');
      Object.assign(term, body);
      return ok(term);
    }

    /* --- 正式课程下拉 --- */
    if (method === 'GET' && path === '/official-course/list') {
      return ok({ rows: clone(db.officialCourses), total: db.officialCourses.length });
    }

    /* --- 教学班 --- */
    if (method === 'GET' && path === '/teaching-class/list') {
      var classes = db.classes.filter(function (item) {
        return contains(item.name + item.code, params.keyword)
          && (!params.termId || String(item.termId) === String(params.termId))
          && (!params.status || item.status === params.status);
      });
      return ok(paginate(classes, params));
    }
    if (method === 'GET' && path === '/teaching-class/mine') {
      // 演示：假设当前教师是王建国（101）
      return ok({ rows: clone(db.classes.filter(function (item) { return item.teacherId === 101; })), total: 2 });
    }
    if (method === 'POST' && path === '/teaching-class') {
      var term = db.terms.find(function (item) { return String(item.id) === String(body.termId); });
      var course = db.officialCourses.find(function (item) { return String(item.id) === String(body.officialCourseId); });
      var created = {
        id: ++nextId, name: body.name, code: body.code, termId: body.termId,
        semesterName: term ? term.name : '', courseId: body.officialCourseId,
        courseName: course ? course.name : '', teacherId: null, teacherName: '',
        studentCount: 0, status: body.status || 'DRAFT', classKind: body.classKind || 'FORMAL'
      };
      db.classes.unshift(created);
      return ok(created);
    }
    if (method === 'PUT' && (match = path.match(/^\/teaching-class\/(\d+)$/))) {
      var target = db.classes.find(function (item) { return String(item.id) === match[1]; });
      if (!target) return fail('教学班不存在');
      Object.assign(target, body);
      var boundTerm = db.terms.find(function (item) { return String(item.id) === String(body.termId); });
      if (boundTerm) target.semesterName = boundTerm.name;
      return ok(target);
    }

    /* --- 班级成员 --- */
    if (method === 'GET' && (match = path.match(/^\/teaching-class\/(\d+)\/members$/))) {
      var members = db.members.filter(function (item) { return String(item.classId) === match[1]; });
      return ok({ rows: clone(members), total: members.length });
    }
    if (method === 'POST' && (match = path.match(/^\/teaching-class\/(\d+)\/members$/))) {
      var student = db.students.find(function (item) { return String(item.userId) === String(body.studentId); });
      if (!student) return fail('学生不存在');
      var exists = db.members.some(function (item) {
        return String(item.classId) === match[1] && String(item.studentId) === String(body.studentId) && item.status === 'ACTIVE';
      });
      if (exists) return fail('该学生已在本班，不能重复加入'); // 模拟服务端唯一约束
      db.members.push({
        classId: Number(match[1]), studentId: student.userId, studentNo: student.userName,
        studentName: student.nickName, status: 'ACTIVE', joinedAt: body.joinedAt || today(), leftAt: null
      });
      var classRow = db.classes.find(function (item) { return String(item.id) === match[1]; });
      if (classRow) classRow.studentCount += 1;
      return ok(true);
    }
    if (method === 'DELETE' && (match = path.match(/^\/teaching-class\/(\d+)\/members\/(\d+)$/))) {
      var member = db.members.find(function (item) {
        return String(item.classId) === match[1] && String(item.studentId) === match[2] && item.status === 'ACTIVE';
      });
      if (!member) return fail('该学生不在本班');
      member.status = 'LEFT';
      member.leftAt = today();
      var owner = db.classes.find(function (item) { return String(item.id) === match[1]; });
      if (owner && owner.studentCount > 0) owner.studentCount -= 1;
      return ok(true);
    }

    /* --- 任课教师授权 --- */
    if (method === 'GET' && (match = path.match(/^\/teaching-class\/(\d+)\/teachers$/))) {
      var classTeachers = db.classTeachers.filter(function (item) { return String(item.classId) === match[1]; });
      return ok({ rows: clone(classTeachers), total: classTeachers.length });
    }
    if (method === 'POST' && (match = path.match(/^\/teaching-class\/(\d+)\/teachers$/))) {
      var teacher = db.teachers.find(function (item) { return String(item.userId) === String(body.userId); });
      if (!teacher) return fail('教师不存在');
      db.classTeachers.push({
        classId: Number(match[1]), userId: teacher.userId, userName: teacher.userName, nickName: teacher.nickName,
        teacherRole: body.teacherRole, canPublish: body.canPublish, canGrade: body.canGrade,
        canExport: body.canExport, validFrom: today(), validTo: body.validTo || null
      });
      return ok(true);
    }
    if (method === 'DELETE' && (match = path.match(/^\/teaching-class\/(\d+)\/teachers\/(\d+)$/))) {
      db.classTeachers = db.classTeachers.filter(function (item) {
        return !(String(item.classId) === match[1] && String(item.userId) === match[2]);
      });
      return ok(true);
    }

    /* --- 教师 / 学生 --- */
    if (method === 'GET' && path === '/teacher/list') {
      var teachers = db.teachers.filter(function (item) {
        if (params.deptId) {
          var dept = db.departments.find(function (d) { return String(d.id) === String(params.deptId); });
          if (dept && item.deptName.indexOf(dept.name.replace('学院', '')) === -1 && item.deptName !== dept.name) return false;
        }
        return contains(item.userName + item.nickName, params.keyword);
      });
      return ok(paginate(teachers, params));
    }
    if (method === 'GET' && (match = path.match(/^\/teacher\/(\d+)\/classes$/))) {
      var teaching = db.classTeachers.filter(function (item) { return String(item.userId) === match[1]; })
        .map(function (item) {
          var row = db.classes.find(function (c) { return c.id === item.classId; }) || {};
          return {
            classId: item.classId, className: row.name, termName: row.semesterName,
            teacherRole: item.teacherRole, canPublish: item.canPublish, canGrade: item.canGrade,
            canExport: item.canExport, validTo: item.validTo
          };
        });
      return ok({ rows: teaching, total: teaching.length });
    }
    if (method === 'GET' && path === '/student/list') {
      var students = db.students.filter(function (item) { return contains(item.userName + item.nickName, params.keyword); });
      return ok(paginate(students, params));
    }
    if (method === 'GET' && (match = path.match(/^\/student\/(\d+)\/classes$/))) {
      var joined = db.members.filter(function (item) { return String(item.studentId) === match[1]; })
        .map(function (item) {
          var row = db.classes.find(function (c) { return c.id === item.classId; }) || {};
          return {
            classId: item.classId, className: row.name, termName: row.semesterName,
            joinedAt: item.joinedAt, leftAt: item.leftAt, status: item.status
          };
        });
      return ok({ rows: joined, total: joined.length });
    }

    /* --- 组织架构 --- */
    if (method === 'GET' && path === '/organization/tree') {
      return ok(clone(db.departments));
    }

    /* --- 课程 --- */
    if (method === 'GET' && path === '/course/list') {
      var role = document.body.dataset.role;
      var list = db.courses.filter(function (item) {
        // 教师只看自己教的；学生只看已发布的课
        if (role === 'admin') return true;
        if (role === 'teacher') return item.teacherName === '王建国';
        return item.status === 'ACTIVE';
      });
      return ok({ rows: clone(list), total: list.length });
    }
    if (method === 'GET' && (match = path.match(/^\/course\/(\d+)$/))) {
      var found = db.courses.find(function (item) { return String(item.id) === match[1]; });
      if (!found) return fail('课程不存在', 'business');
      var detail = clone(found);
      detail.units = [
        { id: 1, unitKind: 'CHAPTER', title: '第 1 章 需求与范围', children: [
          { id: 2, unitKind: 'SECTION', title: '1.1 干系人与目标' },
          { id: 3, unitKind: 'SECTION', title: '1.2 需求优先级' }
        ] },
        { id: 4, unitKind: 'CHAPTER', title: '第 2 章 设计与建模', children: [] }
      ];
      detail.knowledgePoints = [
        { id: 1, code: 'KP-01', name: '需求可验证性' },
        { id: 2, code: 'KP-02', name: '用例建模' },
        { id: 3, code: 'KP-03', name: '迭代计划' }
      ];
      detail.classes = db.classes
        .filter(function (item) { return item.courseId === found.officialCourseId; })
        .map(function (item) {
          return { id: item.id, name: item.name, termName: item.semesterName, studentCount: item.studentCount, publishedAt: '2026-09-01' };
        });
      return ok(detail);
    }
    if (method === 'POST' && path === '/course') {
      var newCourse = {
        id: ++nextId, name: body.name, status: 'DRAFT', semesterName: '2026-2027 学年第一学期',
        teacherName: '王建国', className: '—', classCount: 0, studentCount: 0,
        updatedAt: today(), summary: body.summary, learningObjectives: body.learningObjectives,
        officialCourseId: body.officialCourseId,
        officialCourseName: (db.officialCourses.find(function (i) { return String(i.id) === String(body.officialCourseId); }) || {}).name || ''
      };
      db.courses.unshift(newCourse);
      return ok(newCourse);
    }
    if (method === 'PUT' && (match = path.match(/^\/course\/(\d+)\/status$/))) {
      var changing = db.courses.find(function (item) { return String(item.id) === match[1]; });
      if (!changing) return fail('课程不存在');
      changing.status = body.status;
      return ok(true);
    }
    if (method === 'PUT' && (match = path.match(/^\/course\/(\d+)$/))) {
      var editing = db.courses.find(function (item) { return String(item.id) === match[1]; });
      if (!editing) return fail('课程不存在');
      Object.assign(editing, body);
      return ok(editing);
    }
    if (method === 'POST' && (match = path.match(/^\/course\/(\d+)\/classes$/))) {
      var binding = db.courses.find(function (item) { return String(item.id) === match[1]; });
      if (binding) binding.classCount += 1;
      return ok(true);
    }
    if (method === 'DELETE' && /^\/course\/\d+\/classes\/\d+$/.test(path)) {
      return ok(true);
    }

    return fail('演示数据里没有这个接口：' + method + ' ' + path, 'business');
  }

  /* ---- 对外：拦截请求 ---- */
  function handle(method, path, params, body) {
    return new Promise(function (resolve) {
      setTimeout(function () { resolve(); }, 320); // 模拟网络延迟，能看到骨架屏
    }).then(function () {
      if (scenario === 'error') return fail('演示模式：模拟服务器 500', 'network');
      if (scenario === 'forbidden') return fail('你没有权限执行该操作', 'forbidden');
      if (scenario === 'empty') {
        // 空数据：列表返回空，详情返回 null，页面各自显示空状态
        if (/\/list$/.test(path) || /\/members$/.test(path) || /\/classes$/.test(path)) {
          return ok({ rows: [], total: 0 });
        }
        if (method === 'GET' && /\/course\/\d+$/.test(path)) return ok(null);
        if (method === 'GET' && path === '/organization/tree') return ok([]);
      }
      return route(method, path, params || {}, body || {});
    });
  }

  /* ---- 右下角角标：切换场景 ---- */
  function badge() {
    var node = document.createElement('button');
    node.type = 'button';
    node.className = 'edu-mock-badge';
    node.title = '点击切换演示场景：正常 / 空数据 / 无权限 / 失败';
    node.textContent = '演示数据：' + (SCENARIO_LABEL[scenario] || scenario);
    node.addEventListener('click', function () {
      var index = SCENARIOS.indexOf(scenario);
      var next = SCENARIOS[(index + 1) % SCENARIOS.length];
      try { sessionStorage.setItem(STORE_KEY, next); } catch (e) { /* 忽略 */ }
      location.reload();
    });
    document.body.appendChild(node);
  }
  if (document.body) badge();
  else document.addEventListener('DOMContentLoaded', badge);

  /* ---- 接管登录核验 ----
     role-home.js 用的是原生 fetch（不是 EduApi），演示模式下要让它也能通过，
     否则页面会一直停在「正在核验登录信息」。 */
  function fakeResponse(body) {
    var payload = JSON.stringify(body);
    if (typeof window.Response === 'function') {
      return Promise.resolve(new window.Response(payload, {
        status: 200, headers: { 'Content-Type': 'application/json' }
      }));
    }
    // 极老浏览器兜底：返回一个结构相同的假响应
    return Promise.resolve({
      ok: true, status: 200,
      json: function () { return Promise.resolve(body); },
      text: function () { return Promise.resolve(payload); }
    });
  }

  var rawFetch = window.fetch ? window.fetch.bind(window) : null;
  window.fetch = function (input, init) {
    var url = typeof input === 'string' ? input : (input && input.url) || '';
    var path = url.replace(/^https?:\/\/[^/]+/, '').split('?')[0];
    var role = (document.body && document.body.dataset.role) || 'admin';
    if (path === '/getInfo') {
      return fakeResponse({ code: 200, user: { nickName: '演示账号', userName: 'demo' }, roles: [role] });
    }
    if (path === '/getRouters') return fakeResponse({ code: 200, data: [] });
    if (path === '/logout') return fakeResponse({ code: 200 });
    return rawFetch ? rawFetch(input, init) : Promise.reject(new Error('当前环境不支持 fetch'));
  };

  window.EduMock = { isEnabled: function () { return true; }, handle: handle, db: db };
})(window, document);
