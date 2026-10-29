const assert = require('node:assert/strict');
const test = require('node:test');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const read = name => fs.readFileSync(path.join(__dirname, '../frontend', name), 'utf8');
const flush = () => new Promise(resolve => setImmediate(resolve));

class Element {
  constructor(tag = 'div') {
    this.tag = tag; this.children = []; this.dataset = {}; this.attributes = new Map(); this.listeners = new Map();
    this.hidden = false; this.textContent = ''; this.className = '';
    const classes = new Set();
    this.classList = { add: name => classes.add(name), remove: name => classes.delete(name),
      toggle(name) { if (classes.has(name)) { classes.delete(name); return false; } classes.add(name); return true; },
      contains: name => classes.has(name) };
  }
  appendChild(node) { this.children.push(node); return node; }
  replaceChildren(...nodes) { this.children = nodes; }
  setAttribute(key, value) { this.attributes.set(key, String(value)); }
  addEventListener(type, handler) { this.listeners.set(type, handler); }
  insertAdjacentHTML() {}
}

function shellFixture(role = 'student', queued = []) {
  const elements = new Map(), listeners = new Map(), saved = new Map();
  const get = key => { if (!elements.has(key)) elements.set(key, new Element()); return elements.get(key); };
  let context = null;
  const window = {
    EDU_SHELL: { role, avatar: '测', menu: [{ name: '工作台', children: ['总览', '通知', '课程'] }] }, EDU_PAGES: queued,
    location: { href: 'http://localhost/' + role + '.html' },
    history: { pushState(state, title, url) { window.location.href = new URL(url, window.location.href).href; } },
    EduContext: { get: () => context, mount() {}, getStatus: () => ({ message: '请选择课程' }) },
    addEventListener(type, callback) { listeners.set(type, callback); }
  };
  const sandbox = {
    window, AbortController, URL, console,
    document: { body: new Element('body'), getElementById: get, createElement: tag => new Element(tag), querySelector: () => get('avatar') },
    localStorage: { getItem: key => saved.get(key), setItem: (key, value) => saved.set(key, value), removeItem: key => saved.delete(key) }
  };
  vm.runInNewContext(read('shell/auth.js'), sandbox);
  vm.runInNewContext(read('shell/shell.js'), sandbox);
  return {
    shell: window.EduShell, window, get, listeners,
    context(value) { context = value; listeners.get('edu:context-change')(); },
    authorize(permissions = ['demo:list'], routes = [{ path: '/demo' }], roles = [role]) {
      window.EduShell.authorize({ user: { userId: '100' }, roles, permissions }, routes);
    }
  };
}
function page(extra = {}) { return { permission: 'demo:list', route: 'demo', mount() {}, ...extra }; }

test('late business registration activates an authorized default page, while absent pages stay closed', () => {
  const fixture = shellFixture(); let mounts = 0;
  fixture.authorize();
  assert.equal(fixture.get('pageContent').children[0].textContent, '当前暂无可用功能');
  fixture.shell.registerPage(0, 0, page({ mount() { mounts += 1; } }));
  assert.equal(mounts, 1);
  assert.equal(fixture.shell.getCurrent().i, 0);
  assert.throws(() => fixture.shell.registerPage(0, 0, page()), /已注册/);
});

test('late registration honors the requested deep link and never mounts a denied page', () => {
  const fixture = shellFixture(); let mounts = 0;
  fixture.window.location.href += '?g=0&i=1'; fixture.authorize([], []);
  fixture.shell.registerPage(0, 1, page({ mount() { mounts += 1; } }));
  assert.equal(mounts, 0);
  fixture.authorize(); assert.equal(mounts, 1);
});

test('a multi-role account cannot mix administrative modules into the student view', () => {
  const fixture = shellFixture(); let mounts = 0;
  fixture.shell.registerPage(0, 0, page({ roles: ['admin'], mount() { mounts += 1; } }));
  fixture.authorize(['demo:list'], [{ path: '/demo' }], ['student', 'admin']);
  assert.equal(mounts, 0); assert.equal(fixture.shell.getCurrent(), null);
});

test('the shared page lifecycle aborts requests and invalidates callbacks on navigation and suspend', () => {
  const fixture = shellFixture(); const scopes = []; let unmounts = 0;
  fixture.shell.registerPage(0, 0, page({ mount(host, ctx, lifecycle) { scopes.push(lifecycle); }, unmount() { unmounts += 1; } }));
  fixture.shell.registerPage(0, 1, page()); fixture.authorize();
  assert.equal(scopes[0].isCurrent(), true);
  fixture.shell.navigate(0, 1);
  assert.equal(scopes[0].signal.aborted, true); assert.equal(scopes[0].isCurrent(), false); assert.equal(unmounts, 1);
  fixture.shell.navigate(0, 0); fixture.shell.suspend();
  assert.equal(scopes[1].signal.aborted, true); assert.equal(scopes[1].isCurrent(), false);
  assert.equal(fixture.get('pageContent').children.length, 0);
});

test('context changes synchronously remove the previous page and pass a verified snapshot', () => {
  const fixture = shellFixture(); const mounts = [];
  fixture.shell.registerPage(0, 0, page({ requiresContext: 'class', mount(host, ctx, lifecycle) { mounts.push({ ctx, lifecycle }); } }));
  fixture.authorize(); assert.equal(mounts.length, 0);
  fixture.context({ courseSpaceId: '1', classId: '10' });
  fixture.context({ courseSpaceId: '1', classId: '20' });
  assert.equal(mounts[0].lifecycle.isCurrent(), false);
  assert.equal(mounts[0].lifecycle.signal.aborted, true);
  assert.equal(mounts[1].ctx.classId, '20');
  fixture.context(null);
  assert.equal(mounts[1].lifecycle.signal.aborted, true);
  assert.equal(fixture.get('pageContent').children[0].textContent, '请选择课程与教学班');
});

test('notification entries and their providers require registration, identity, permission and route', async () => {
  const fixture = shellFixture(); let calls = 0;
  fixture.shell.registerPage(0, 1, page({ headerEntry: { label: '通知', getUnreadCount() { calls += 1; return 3; } } }));
  assert.equal(fixture.get('headerActions').children.length, 0);
  fixture.authorize([], []); await flush(); assert.equal(calls, 0);
  fixture.authorize(['demo:list'], []); await flush(); assert.equal(calls, 0);
  fixture.authorize(); await flush(); assert.equal(calls, 1);
  const link = fixture.get('headerActions').children[0];
  assert.equal(link.children[1].textContent, '3'); assert.match(link.attributes.get('aria-label'), /3 条未读/);
  link.listeners.get('click')({ button: 0, preventDefault() {} });
  assert.equal(fixture.shell.getCurrent().i, 1);
});

test('notification links can omit counts and failures never masquerade as zero unread', async () => {
  for (const count of [0, 145, -1, '5', new Error('service unavailable')]) {
    const fixture = shellFixture();
    fixture.shell.registerPage(0, 1, page({ headerEntry: { label: '<通知>', getUnreadCount() { if (count instanceof Error) throw count; return count; } } }));
    fixture.authorize(); await flush();
    const link = fixture.get('headerActions').children[0], badge = link.children[1];
    assert.equal(link.children[0].textContent, '<通知>');
    if (count === 0) { assert.equal(badge.hidden, true); assert.match(link.attributes.get('aria-label'), /无未读/); }
    else if (count === 145) assert.equal(badge.textContent, '99+');
    else { assert.equal(badge.textContent, '!'); assert.match(link.attributes.get('aria-label'), /暂不可用/); }
  }
  const plain = shellFixture(); plain.shell.registerPage(0, 1, page({ headerEntry: { label: '通知' } })); plain.authorize();
  assert.equal(plain.get('headerActions').children[0].children.length, 1);
});

test('notification counts are cancelled and stale responses cannot leak across contexts or logout', async () => {
  const fixture = shellFixture(); const pending = [];
  fixture.shell.registerPage(0, 1, page({ headerEntry: { label: '通知', getUnreadCount(ctx, opts) { return new Promise(resolve => pending.push({ resolve, signal: opts.signal })); } } }));
  fixture.authorize(); await flush();
  fixture.context({ courseSpaceId: '1', classId: '10' }); await flush();
  assert.equal(pending[0].signal.aborted, true);
  pending[0].resolve(80); await flush();
  assert.equal(fixture.get('headerActions').children[0].children[1].textContent, '…');
  pending[1].resolve(2); await flush();
  assert.equal(fixture.get('headerActions').children[0].children[1].textContent, '2');
  fixture.shell.refreshHeader(); await flush(); fixture.shell.suspend();
  assert.equal(pending[2].signal.aborted, true); pending[2].resolve(90); await flush();
  assert.equal(fixture.get('headerActions').children.length, 0);
});

test('a revoked notification endpoint hides its entry and a context page waits for a class', async () => {
  const fixture = shellFixture(); let calls = 0;
  fixture.shell.registerPage(0, 1, page({ requiresContext: 'class', headerEntry: { label: '通知', async getUnreadCount() { calls += 1; throw Object.assign(new Error('denied'), { forbidden: true }); } } }));
  fixture.authorize(); await flush(); assert.equal(calls, 0);
  fixture.context({ courseSpaceId: '1', classId: '10' }); await flush();
  assert.equal(calls, 1); assert.equal(fixture.get('headerActions').children[0].hidden, true);
});

test('business modules are mounted only after the initial context check completes', async () => {
  let finishContext; const events = [];
  const nodes = new Map(); const get = key => { if (!nodes.has(key)) nodes.set(key, new Element()); return nodes.get(key); };
  const api = { getToken: () => 'synthetic-token', get: async url => url === '/getInfo'
    ? { user: { userId: '100', userName: 'synthetic' }, roles: ['teacher'] } : { data: [] } };
  const sandbox = {
    AbortController, EduApi: api,
    window: { EduShell: { suspend() {}, authorize() { events.push('mount'); } },
      EduContext: { suspend() {}, initialize() { return new Promise(resolve => { finishContext = resolve; }); } }, addEventListener() {} },
    document: { body: Object.assign(new Element(), { dataset: { role: 'teacher' } }), getElementById: get, querySelector: () => get('avatar') },
    sessionStorage: { getItem: () => 'teacher', setItem() {} }
  };
  vm.runInNewContext(read('role-home.js'), sandbox); await flush();
  assert.equal(events.length, 0); finishContext(); await flush();
  assert.deepEqual(events, ['mount']);
});
