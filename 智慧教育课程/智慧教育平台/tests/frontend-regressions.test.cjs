const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const root = path.resolve(__dirname, '..');
const source = file => fs.readFileSync(path.join(root, 'frontend', file), 'utf8');
const flush = () => new Promise(resolve => setImmediate(resolve));
const json = data => new Response(JSON.stringify(data), { headers: { 'Content-Type': 'application/json' } });

function apiFixture(fetch) {
  const redirects = [];
  const events = [];
  const storage = new Map([['zhijiao.login.token', 'synthetic-token'], ['zhijiao.login.role', 'admin']]);
  const sandbox = {
    window: { location: { origin: 'http://localhost', href: 'http://localhost/admin.html', replace: url => redirects.push(url) }, dispatchEvent: event => events.push(event) },
    sessionStorage: { getItem: key => storage.get(key), removeItem: key => storage.delete(key) },
    CustomEvent: class { constructor(type, options) { this.type = type; this.detail = options.detail; } },
    fetch, AbortController, FormData, URL, setTimeout, clearTimeout
  };
  vm.runInNewContext(source('shell/api.js'), sandbox);
  return { api: sandbox.window.EduApi, redirects, events, storage, window: sandbox.window };
}

test('JSON requests retain Bearer authentication and encode payloads', async () => {
  let sent;
  const fixture = apiFixture(async (url, options) => { sent = { url, options }; return json({ code: 200, data: [] }); });
  const result = await fixture.api.post('/synthetic', { title: 'test' });
  assert.equal(result.code, 200);
  assert.equal(sent.options.headers.Authorization, 'Bearer synthetic-token');
  assert.equal(sent.options.body, '{"title":"test"}');
});

for (const [label, response] of [
  ['HTTP 401 HTML', () => new Response('<html>Unauthorized</html>', { status: 401 })],
  ['business 401', () => json({ code: 401 })]
]) {
  test(label + ' clears the session and redirects', async () => {
    const fixture = apiFixture(async () => response());
    await assert.rejects(fixture.api.get('/synthetic'), error => error.unauthorized === true);
    assert.equal(fixture.storage.size, 0);
    assert.deepEqual(fixture.redirects, ['/login.html?logout=expired']);
  });
}

for (const [label, response] of [
  ['HTTP 403 HTML', () => new Response('<html>Forbidden</html>', { status: 403 })],
  ['business 403', () => json({ code: 403, msg: 'denied' })]
]) {
  test(label + ' notifies the shell without clearing login', async () => {
    const fixture = apiFixture(async () => response());
    await assert.rejects(fixture.api.get('/synthetic'), error => error.forbidden === true);
    assert.equal(fixture.events.length, 1);
    assert.equal(fixture.events[0].type, 'edu:forbidden');
    assert.equal(fixture.storage.size, 2);
    await assert.rejects(fixture.api.get('/synthetic', { silent403: true }), error => error.forbidden === true);
    assert.equal(fixture.events.length, 1);
  });
}

test('raw exports preserve binary and JSON response bodies', async () => {
  const binary = apiFixture(async () => new Response('synthetic-binary', { headers: { 'Content-Type': 'application/octet-stream' } }));
  assert.equal(await (await binary.api.get('/export', { raw: true })).text(), 'synthetic-binary');
  const success = apiFixture(async () => json({ code: 200, data: 'kept' }));
  assert.equal((await (await success.api.get('/export', { raw: true })).json()).data, 'kept');
  const denied = apiFixture(async () => json({ code: 401 }));
  await assert.rejects(denied.api.get('/export', { raw: true }), error => error.unauthorized === true);
});

test('cancelAll stops all current requests without timeout or permission events', async () => {
  const signals = [];
  const fixture = apiFixture((url, options) => new Promise((resolve, reject) => {
    signals.push(options.signal);
    options.signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')));
  }));
  const outcomes = Promise.allSettled([fixture.api.get('/one'), fixture.api.get('/two')]);
  fixture.api.cancelAll();
  for (const result of await outcomes) {
    assert.equal(result.status, 'rejected');
    assert.equal(result.reason.cancelled, true);
    assert.equal(result.reason.timeout, undefined);
  }
  assert.ok(signals.every(signal => signal.aborted));
  assert.equal(fixture.events.length, 0);
});

test('an already cancelled caller never starts a request', async () => {
  let calls = 0;
  const fixture = apiFixture(async () => { calls += 1; return json({ code: 200 }); });
  const controller = new AbortController();
  controller.abort();
  await assert.rejects(fixture.api.get('/synthetic', { signal: controller.signal }), error => error.cancelled === true);
  assert.equal(calls, 0);
});

test('authenticated requests never transmit a token to a different origin', async () => {
  let calls = 0;
  const fixture = apiFixture(async () => { calls += 1; return json({ code: 200 }); });
  await assert.rejects(fixture.api.get('https://example.invalid/api'), error => error.client === true);
  await assert.rejects(fixture.api.get('//example.invalid/api'), error => error.client === true);
  assert.equal(calls, 0);
});

test('timeout remains distinguishable from caller cancellation', async () => {
  const fixture = apiFixture((url, options) => new Promise((resolve, reject) => {
    options.signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')));
  }));
  await assert.rejects(fixture.api.get('/synthetic', { timeout: 5 }), error => error.timeout === true && !error.cancelled);
});

test('a late forbidden response from a cancelled request cannot replace the current page', async () => {
  let deliver;
  const fixture = apiFixture(() => new Promise(resolve => { deliver = resolve; }));
  const outcome = assert.rejects(fixture.api.get('/old-context'), error => error.cancelled === true);
  fixture.api.cancelAll();
  deliver(json({ code: 403 }));
  await outcome;
  assert.equal(fixture.events.length, 0);
});

test('a real permission rejection stays forbidden when shell cleanup aborts its page request', async () => {
  const controller = new AbortController();
  const fixture = apiFixture(async () => json({ code: 403 }));
  fixture.window.dispatchEvent = () => controller.abort();
  await assert.rejects(fixture.api.get('/denied', { signal: controller.signal }), error => error.forbidden === true && !error.cancelled);
});

class Element {
  constructor(tag = 'div') {
    this.tag = tag;
    this.children = [];
    this.listeners = new Map();
    this.value = '';
    this.disabled = false;
    this.style = {};
    this.dataset = {};
    this.attributes = {};
    this.classList = { add() {}, remove() {}, toggle() {} };
  }
  appendChild(child) { this.children.push(child); child.parentNode = this; return child; }
  setAttribute(key, value) { this.attributes[key] = value; }
  replaceChildren(...children) { this.children = []; children.forEach(child => this.appendChild(child)); }
  addEventListener(type, fn) { this.listeners.set(type, fn); }
  querySelector(selector) { return this.querySelectorAll(selector)[0] || null; }
  querySelectorAll(selector) {
    const matches = [];
    function visit(node) {
      for (const child of node.children) {
        if (selector === 'input[type="checkbox"]' && child.tag === 'input' && child.type === 'checkbox') matches.push(child);
        visit(child);
      }
    }
    visit(this); return matches;
  }
  focus() {}
  remove() { if (this.parentNode) this.parentNode.children = this.parentNode.children.filter(child => child !== this); }
  get parentElement() { return this.parentNode; }
  closest(tag) { let node = this; while (node) { if (node.tag === tag) return node; node = node.parentNode; } return null; }
  find(predicate) { return predicate(this) ? this : this.children.map(child => child.find(predicate)).find(Boolean); }
}

function pageHost() {
  const elements = new Map();
  return {
    innerHTML: '',
    querySelector(id) {
      if (!elements.has(id)) elements.set(id, new Element());
      return elements.get(id);
    }
  };
}

for (const [file, prefix] of [['user-list.js', 'u'], ['role-list.js', 'r'], ['oper-log.js', 'l']]) {
  test(file + ' aborts on unmount and ignores old responses after remount', async () => {
    const requests = [];
    const sandbox = {
      window: {}, URLSearchParams, AbortController, AdminUI: { can() { return true; } },
      EduApi: { get(url, options) { return new Promise(resolve => requests.push({ resolve, signal: options.signal })); } }
    };
    vm.runInNewContext(source('pages/admin/' + file), sandbox);
    const page = sandbox.window.EDU_PAGES[0].page;
    page.mount(pageHost());
    page.unmount();
    assert.equal(requests[0].signal.aborted, true);
    const currentHost = pageHost();
    page.mount(currentHost);
    // Deliberately ignore abort at the transport boundary to simulate an already delivered response.
    requests[0].resolve({ rows: [{ userName: 'stale private data' }], total: 100 });
    await flush();
    assert.equal(currentHost.querySelector('#' + prefix + 'State').textContent, '加载中…');
    currentHost.querySelector('#' + prefix + 'Search').listeners.get('click')();
    assert.equal(requests.length, 2, 'the old finally must not unlock the current pending request');
    requests[1].resolve({ rows: [], total: 0 });
    await flush();
    assert.equal(currentHost.querySelector('#' + prefix + 'State').textContent, '暂无数据');
    page.unmount();
  });
}

function adminFixture() {
  const body = new Element('body');
  const listeners = new Map();
  const document = {
    body, activeElement: null, createElement: tag => new Element(tag), querySelector: () => null,
    addEventListener: (type, handler) => listeners.set(handler, type),
    removeEventListener: (type, handler) => listeners.delete(handler)
  };
  const sandbox = { window: {}, document, Node: Element, setTimeout, clearTimeout };
  vm.runInNewContext(source('pages/admin/admin-ui.js'), sandbox);
  return { ui: sandbox.window.AdminUI, body, listeners };
}

test('closing all modals removes private content, keyboard listeners and pending confirmations', async () => {
  const fixture = adminFixture();
  let closeCount = 0;
  const dialog = fixture.ui.modal({ content: 'synthetic private detail', onClose: () => { closeCount += 1; } });
  const confirmation = fixture.ui.confirm('synthetic action');
  fixture.ui.closeAll();
  dialog.close();
  assert.equal(await confirmation, false);
  assert.equal(fixture.body.children.length, 0);
  assert.equal(fixture.listeners.size, 0);
  assert.equal(closeCount, 1);
});

test('a busy or already closed modal cannot submit twice', async () => {
  const fixture = adminFixture();
  let finish;
  let calls = 0;
  const dialog = fixture.ui.modal({ onOk: () => { calls += 1; return new Promise(resolve => { finish = resolve; }); } });
  const ok = dialog.dialog.find(node => node.tag === 'button' && node.className === 'adm-btn primary');
  const submit = ok.listeners.get('click');
  const first = submit();
  await submit();
  assert.equal(calls, 1);
  fixture.ui.closeAll();
  finish(true);
  await first;
  await submit();
  assert.equal(calls, 1);
});

test('confirmation strings are inserted as plain text', () => {
  const fixture = adminFixture();
  const text = '<img src=x onerror="synthetic()">';
  const dialog = fixture.ui.modal({ content: text });
  assert.equal(dialog.body.textContent, text);
  assert.equal(dialog.body.children.length, 0);
  fixture.ui.closeAll();
});

test('department choices retain authorized orphan roots and stop at cycles', () => {
  const fixture = adminFixture();
  const options = fixture.ui.deptOptions([
    { deptId: '2', parentId: '1', deptName: 'Visible subtree' }, { deptId: '3', parentId: '2', deptName: 'Child' },
    { deptId: '8', parentId: '9', deptName: 'Cycle A' }, { deptId: '9', parentId: '8', deptName: 'Cycle B' }
  ]);
  assert.deepEqual(Array.from(options, option => option.value), ['2', '3', '8', '9']);
});

test('department grants do not inherit parent IDs while menu grants retain required ancestors', () => {
  const fixture = adminFixture();
  const nodes = [{ id: 1, label: 'Parent', children: [{ id: 2, label: 'Child' }] }];
  assert.deepEqual(Array.from(fixture.ui.menuTree(nodes, [2]).getCheckedIds()), [2, 1]);
  assert.deepEqual(Array.from(fixture.ui.menuTree(nodes, [2], { includeAncestors: false }).getCheckedIds()), [2]);
});

test('the forbidden view unmounts the active page and closes its dialogs', () => {
  const elements = new Map();
  const get = id => {
    if (!elements.has(id)) elements.set(id, new Element());
    return elements.get(id);
  };
  get('pageContent').querySelector = () => get('hint');
  const group = new Element();
  group.querySelectorAll = () => [new Element()];
  let unmounts = 0;
  let mounts = 0;
  let closes = 0;
  const storage = new Map();
  const window = {
    EDU_SHELL: { role: 'admin', menu: [{ name: 'Group', children: ['Page'] }] },
    EDU_PAGES: [{ g: 0, i: 0, page: { permission: 'demo:list', route: 'demo', mount() { mounts += 1; }, unmount() { unmounts += 1; } } }],
    AdminUI: { closeAll() { closes += 1; } }, addEventListener() {}
  };
  window.location = { href: 'http://localhost/admin.html' };
  window.history = { pushState() {} };
  const document = {
    body: Object.assign(new Element(), { insertAdjacentHTML() {} }), getElementById: get, createElement: () => new Element(),
    querySelector: () => get('avatar'),
    querySelectorAll: selector => selector === '.menu-group' ? [group] : []
  };
  const sandbox = {
    window, document, console, URL,
    localStorage: { getItem: key => storage.get(key), setItem: (key, value) => storage.set(key, value) }
  };
  vm.runInNewContext(source('shell/auth.js'), sandbox);
  vm.runInNewContext(source('shell/shell.js'), sandbox);
  assert.equal(mounts, 0, 'no private page mounts before the server identity is verified');
  window.EduShell.authorize({ user: { userId: 1 }, roles: ['admin'], permissions: ['demo:list'] }, [{ path: '/demo' }]);
  assert.equal(mounts, 1);
  const before = closes;
  window.EduShell.showForbidden('denied');
  window.EduShell.showForbidden('denied again');
  assert.equal(unmounts, 1);
  assert.equal(closes, before + 2);
  assert.equal(get('pageContent').children[1].textContent, 'denied again');
});
