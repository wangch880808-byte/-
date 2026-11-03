const assert = require('node:assert/strict');
const test = require('node:test');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const read = name => fs.readFileSync(path.join(__dirname, '../frontend', name), 'utf8');

function auth() {
  const sandbox = { window: {} };
  vm.runInNewContext(read('shell/auth.js'), sandbox);
  return sandbox.window.EduAuth;
}
const admin = { user: { userId: 1 }, roles: ['admin'], permissions: ['system:menu:list'] };
const menuPage = { roles: ['admin'], permission: 'system:menu:list', route: 'system/menu' };

test('permissions are denied until identity and authorized routes are present', () => {
  const policy = auth();
  assert.equal(policy.has('system:menu:list'), false);
  policy.set(admin, []);
  assert.equal(policy.has('system:menu:add'), false);
  assert.equal(policy.canOpen(menuPage), false);
  policy.set(admin, [{ path: '/system', children: [{ path: 'menu', component: 'system/menu/index' }] }]);
  assert.equal(policy.canOpen(menuPage), true);
});

test('super permission does not open hidden or absent routes', () => {
  const policy = auth();
  policy.set({ ...admin, permissions: ['*:*:*'] }, [{ path: '/system', hidden: true, children: [{ path: 'menu' }] }]);
  assert.equal(policy.canOpen(menuPage), false);
  policy.set({ ...admin, permissions: ['*:*:*'] }, [{ path: '/system/menu' }]);
  assert.equal(policy.canOpen(menuPage), true);
});

test('student roles cannot open administrative pages even with a mismatched permission response', () => {
  const policy = auth();
  policy.set({ user: { userId: 'student' }, roles: ['student'], permissions: ['*:*:*'] }, [{ path: '/system/menu' }]);
  assert.equal(policy.canOpen(menuPage), false);
  policy.reset();
  assert.equal(policy.canOpen(menuPage), false);
});

function options(courses) {
  return { code: 200, data: { term: { termId: '9', termName: 'Test term' }, courses: courses || [
    { courseSpaceId: '1', title: 'Course A', status: 'ACTIVE', classes: [
      { classId: '10', className: 'Class A', termId: '9', memberRole: 'teacher', teacherRole: 'LEAD', canPublish: true, canGrade: false },
      { classId: '20', className: 'Class B', termId: '9', memberRole: 'teacher', teacherRole: 'CO_TEACHER', canPublish: false, canGrade: false }
    ] }
  ] } };
}
function checked(courseSpaceId = '1', classId = '10') {
  return { code: 200, data: { courseSpaceId, classId, termId: '9', memberRole: 'teacher', teacherRole: 'LEAD', canPublish: true, canGrade: false } };
}
function contextFixture(handler, href = 'http://localhost/teacher.html', storage = new Map()) {
  const calls = [];
  const events = [];
  const window = {
    location: { href },
    history: { replaceState(state, title, url) { window.location.href = new URL(url, window.location.href).href; } },
    dispatchEvent(event) { events.push(event); }
  };
  const api = {
    get(url, init) { calls.push({ url, init }); return handler(url, init); },
    cancelAll() { calls.push({ cancel: true }); }
  };
  const sandbox = {
    window, URL, URLSearchParams, AbortController, console, EduApi: api,
    CustomEvent: class { constructor(type, options) { this.type = type; this.detail = options.detail; } },
    sessionStorage: { getItem: key => storage.get(key), setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key) }
  };
  vm.runInNewContext(read('shell/context.js'), sandbox);
  return { context: window.EduContext, window, calls, events, storage };
}
const teacher = { userId: '100', role: 'teacher' };

test('selection checks the course/class pair, keeps IDs as strings and stores only selection IDs', async () => {
  const fixture = contextFixture(async url => url.includes('/options') ? options() : checked());
  await fixture.context.initialize(teacher);
  assert.equal(fixture.context.get(), null);
  await fixture.context.set({ courseSpaceId: '1', classId: '10' });
  assert.equal(fixture.context.get().courseSpaceId, '1');
  assert.equal(fixture.context.get().canPublish, true);
  assert.equal(fixture.context.get().canGrade, false);
  assert.ok(Object.isFrozen(fixture.context.get()));
  const check = fixture.calls.find(call => call.url && call.url.includes('/check'));
  const query = new URL(check.url, 'http://localhost').searchParams;
  assert.equal(query.get('courseSpaceId'), '1'); assert.equal(query.get('classId'), '10');
  assert.equal(check.init.silent403, true);
  assert.equal(fixture.storage.get('zhijiao.context.100.teacher').includes('className'), false);
});

test('empty options are an empty state; missing endpoints are unavailable; failures stay errors', async () => {
  for (const [response, state] of [
    [() => Promise.resolve(options([])), 'empty'],
    [() => Promise.reject(Object.assign(new Error('missing'), { status: 404 })), 'unavailable'],
    [() => Promise.reject(Object.assign(new Error('No static resource education/context/options.'), { status: 200, code: 500 })), 'unavailable'],
    [() => Promise.reject(new Error('database unavailable')), 'error'],
    [() => Promise.resolve({ data: { courses: {} } }), 'error']
  ]) {
    const fixture = contextFixture(response);
    await fixture.context.initialize(teacher);
    assert.equal(fixture.context.getStatus().state, state);
    assert.equal(fixture.context.get(), null);
  }
});

test('a missing endpoint wrapped as 200/500 does not leak the server path', async () => {
  const fixture = contextFixture(() => Promise.reject(Object.assign(
    new Error('No static resource education/context/options.'), { status: 200, code: 500 })));
  await fixture.context.initialize(teacher);
  const status = fixture.context.getStatus();
  assert.equal(status.state, 'unavailable');
  assert.equal(/No static resource|education\/context/i.test(status.message), false);
});

test('a class-only link shared by two courses cannot choose a course silently', async () => {
  const courses = options().data.courses;
  const duplicate = { ...courses[0], courseSpaceId: '2', title: 'Other course' };
  const fixture = contextFixture(async () => options([...courses, duplicate]), 'http://localhost/teacher.html?classId=10');
  await fixture.context.initialize(teacher);
  assert.equal(fixture.context.get(), null);
  assert.equal(fixture.calls.filter(call => call.url && call.url.includes('/check')).length, 0);
  assert.equal(new URL(fixture.window.location.href).searchParams.has('classId'), false);
});

test('a valid deep link and persisted selection are verified again instead of trusted', async () => {
  let checks = 0;
  const fixture = contextFixture(async url => { if (url.includes('/options')) return options(); checks += 1; return checked(); }, 'http://localhost/teacher.html?classId=10&courseSpaceId=1');
  await fixture.context.initialize(teacher);
  assert.equal(fixture.context.get().classId, '10');
  fixture.context.suspend();
  assert.equal(fixture.context.get(), null);
  await fixture.context.initialize(teacher);
  assert.equal(checks, 2);
});

test('withdrawn permission clears selection and deep link without any login redirect', async () => {
  let revoked = false;
  const fixture = contextFixture(async url => {
    if (url.includes('/options')) return options();
    if (revoked) throw Object.assign(new Error('denied'), { forbidden: true });
    return checked();
  });
  await fixture.context.initialize(teacher); await fixture.context.set({ courseSpaceId: '1', classId: '10' });
  revoked = true; await fixture.context.refresh();
  assert.equal(fixture.context.get(), null);
  assert.equal(fixture.storage.has('zhijiao.context.100.teacher'), false);
  assert.equal(new URL(fixture.window.location.href).searchParams.has('classId'), false);
  assert.equal(fixture.window.location.href.includes('login'), false);
  assert.equal(fixture.events.some(event => event.type === 'edu:forbidden'), false);
});

test('late selection responses cannot overwrite a newer class', async () => {
  let deliverFirst;
  let firstSignal;
  const fixture = contextFixture((url, init) => {
    if (url.includes('/options')) return Promise.resolve(options());
    const classId = new URL(url, 'http://localhost').searchParams.get('classId');
    if (classId === '10') { firstSignal = init.signal; return new Promise(resolve => { deliverFirst = resolve; }); }
    return Promise.resolve(checked('1', classId));
  });
  await fixture.context.initialize(teacher);
  const first = fixture.context.set({ courseSpaceId: '1', classId: '10' });
  await fixture.context.set({ courseSpaceId: '1', classId: '20' });
  assert.equal(firstSignal.aborted, true);
  deliverFirst(checked()); await first;
  assert.equal(fixture.context.get().classId, '20');
});

test('mismatched server course identity fails closed', async () => {
  const fixture = contextFixture(async url => url.includes('/options') ? options() : checked('999'));
  await fixture.context.initialize(teacher); await fixture.context.set({ courseSpaceId: '1', classId: '10' });
  assert.equal(fixture.context.get(), null); assert.equal(fixture.context.getStatus().state, 'error');
});

test('omitted or revoked operation flags never reuse older option grants', async () => {
  for (const flags of [{}, { canPublish: false, canGrade: false }]) {
    const fixture = contextFixture(async url => {
      if (url.includes('/options')) {
        const response = options(); response.data.courses[0].classes[0].canGrade = true; return response;
      }
      const response = checked(); delete response.data.canPublish; delete response.data.canGrade;
      Object.assign(response.data, flags); return response;
    });
    await fixture.context.initialize(teacher);
    await fixture.context.set({ courseSpaceId: '1', classId: '10' });
    assert.equal(fixture.context.get().canPublish, false);
    assert.equal(fixture.context.get().canGrade, false);
  }
});

test('an advertised binding must be returned by check and stale saved binding is rejected', async () => {
  for (const binding of [undefined, '999']) {
    const fixture = contextFixture(async url => {
      if (url.includes('/options')) {
        const response = options(); response.data.courses[0].classes[0].bindingId = '111'; return response;
      }
      const response = checked(); response.data.bindingId = binding; return response;
    });
    await fixture.context.initialize(teacher); await fixture.context.set({ courseSpaceId: '1', classId: '10' });
    assert.equal(fixture.context.get(), null);
    assert.equal(fixture.context.getStatus().state, 'error');
  }
  const fixture = contextFixture(async url => {
    assert.ok(url.includes('/options'), 'stale binding must be rejected before requesting check');
    const response = options(); response.data.courses[0].classes[0].bindingId = '222'; return response;
  }, 'http://localhost/teacher.html?courseSpaceId=1&classId=10&bindingId=111');
  await fixture.context.initialize(teacher);
  assert.equal(fixture.context.get(), null);
  assert.equal(new URL(fixture.window.location.href).searchParams.has('bindingId'), false);
});

test('the authoritative binding returned by check is kept in context, storage and URL', async () => {
  const fixture = contextFixture(async url => {
    if (url.includes('/options')) return options();
    const response = checked(); response.data.bindingId = '111'; return response;
  });
  await fixture.context.initialize(teacher); await fixture.context.set({ courseSpaceId: '1', classId: '10' });
  assert.equal(fixture.context.get().bindingId, '111');
  assert.equal(new URL(fixture.window.location.href).searchParams.get('bindingId'), '111');
  assert.equal(JSON.parse(fixture.storage.get('zhijiao.context.100.teacher')).bindingId, '111');
});

test('duplicate options and zero IDs fail closed rather than selecting an arbitrary object', async () => {
  for (const mutate of [
    data => data.courses.push(data.courses[0]),
    data => data.courses[0].classes.push(data.courses[0].classes[0]),
    data => { data.courses[0].courseSpaceId = '0'; }
  ]) {
    const fixture = contextFixture(async () => { const response = options(); mutate(response.data); return response; });
    await fixture.context.initialize(teacher);
    assert.equal(fixture.context.get(), null); assert.equal(fixture.context.getStatus().state, 'error');
  }
});

test('unsafe numeric IDs are rejected but large string IDs remain exact', async () => {
  const unsafe = options(); unsafe.data.courses[0].courseSpaceId = 9007199254740992;
  const rejected = contextFixture(async () => unsafe);
  await rejected.context.initialize(teacher);
  assert.equal(rejected.context.getStatus().state, 'error');
  const large = '9223372036854775806';
  const safe = options(); safe.data.courses[0].courseSpaceId = large;
  const accepted = contextFixture(async url => url.includes('/options') ? safe : checked(large));
  await accepted.context.initialize(teacher); await accepted.context.set({ courseSpaceId: large, classId: '10' });
  assert.equal(accepted.context.get().courseSpaceId, large);
});

test('selection is namespaced by account and role, and admin does not call education endpoints', async () => {
  const shared = new Map();
  shared.set('zhijiao.context.100.teacher', JSON.stringify({ courseSpaceId: '1', classId: '10' }));
  const fixture = contextFixture(async url => url.includes('/options') ? options() : checked(), 'http://localhost/teacher.html', shared);
  await fixture.context.initialize({ userId: '200', role: 'teacher' });
  assert.equal(fixture.context.get(), null);
  const adminContext = contextFixture(async () => { throw new Error('must not request'); });
  await adminContext.context.initialize({ userId: '1', role: 'admin' });
  assert.equal(adminContext.context.getStatus().state, 'disabled');
});

test('clearing an active context notifies consumers and removes saved and URL selection', async () => {
  const fixture = contextFixture(async url => url.includes('/options') ? options() : checked());
  await fixture.context.initialize(teacher); await fixture.context.set({ courseSpaceId: '1', classId: '10' });
  let received = 'none';
  const unsubscribe = fixture.context.onChange(value => { received = value; });
  fixture.context.clear(); unsubscribe();
  assert.equal(received, null); assert.equal(fixture.context.get(), null);
  assert.equal(fixture.storage.size, 0);
  assert.equal(new URL(fixture.window.location.href).searchParams.has('courseSpaceId'), false);
});

test('tree parent choices exclude the edited row and all descendants, including cyclic input', () => {
  const sandbox = { window: {} };
  vm.runInNewContext(read('pages/admin/tree-page.js'), sandbox);
  const helper = sandbox.window.AdminTreePage;
  const rows = [
    { menuId: '1', parentId: '0' }, { menuId: '2', parentId: '1' }, { menuId: '3', parentId: '2' }, { menuId: '4', parentId: '0' }
  ];
  assert.deepEqual(Array.from(helper.parentOptions(rows, 'menuId', '2'), item => item.row.menuId), ['1', '4']);
  assert.equal(helper.orderedRows([{ menuId: '8', parentId: '9' }, { menuId: '9', parentId: '8' }], 'menuId').length, 2);
});

test('session expiry during context initialization cannot reveal the authenticated layout again', async () => {
  let loggedIn = true;
  const classes = new Set();
  const nodes = new Map();
  const node = id => {
    if (!nodes.has(id)) nodes.set(id, { hidden: false, addEventListener() {} });
    return nodes.get(id);
  };
  const api = {
    getToken: () => loggedIn ? 'synthetic-token' : null,
    get: async url => url === '/getInfo'
      ? { user: { userId: '100', userName: 'synthetic' }, roles: ['teacher'], permissions: [] }
      : { data: [] }
  };
  const sandbox = {
    AbortController, EduApi: api,
    window: { EduShell: { suspend() {}, authorize() {} }, EduContext: { suspend() {}, async initialize() { loggedIn = false; } }, addEventListener() {} },
    document: {
      body: { dataset: { role: 'teacher' }, classList: { add: name => classes.add(name), remove: name => classes.delete(name) } },
      getElementById: node, querySelector: () => node('avatar'), addEventListener() {}
    },
    sessionStorage: { getItem: () => 'teacher', setItem() {} }
  };
  vm.runInNewContext(read('role-home.js'), sandbox);
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(classes.has('auth-pending'), true);
  assert.equal(node('authStatus').hidden, false);
});
