// 浏览器回归使用隔离的响应夹具，不连接真实数据库，不写入真实账号。
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const assert = require('node:assert/strict');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const root = path.resolve(__dirname, '../frontend/vue/dist');
const results = [];
const server = http.createServer((req, res) => {
  let relative;
  try { relative = decodeURIComponent(new URL(req.url, 'http://localhost').pathname).replace(/^\/vue\/?/, ''); } catch { res.writeHead(400); return res.end(); }
  const target = path.resolve(root, relative || 'index.html');
  if (!target.startsWith(root + path.sep) && target !== path.join(root, 'index.html')) { res.writeHead(403); return res.end(); }
  if (!fs.existsSync(target) || !fs.statSync(target).isFile()) { res.writeHead(404); return res.end(); }
  res.setHeader('Content-Type', ({ '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.png': 'image/png' })[path.extname(target)] || 'application/octet-stream');
  fs.createReadStream(target).pipe(res);
});
(async () => {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = 'http://127.0.0.1:' + server.address().port;
  const browser = await chromium.launch({ headless: true, executablePath: process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe' });
  try {
    const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', err => errors.push(err.message));
    let role = 'admin', denied = false, lastUser = null;
    const allRoutes = [
      { path: 'system', children: ['user', 'role', 'menu', 'dept'].map(item => ({ path: item, component: 'system/' + item + '/index' })) },
      { path: 'monitor', children: [{ path: 'operlog', component: 'monitor/operlog/index' }] },
      { path: 'education', children: [{ path: 'course', component: 'education/course/index' }] }
    ];
    const depts = [{ deptId: 1, parentId: 0, deptName: '测试组织', orderNum: 0, status: '0' }];
    const roles = [{ roleId: 4, roleName: '教师', roleKey: 'teacher', roleSort: 1, status: '0', dataScope: '5' }];
    await page.route(origin + '/**', async route => {
      const req = route.request();
      const url = new URL(req.url());
      const pathname = url.pathname;
      if (pathname.startsWith('/vue')) return route.continue();
      let data = { code: 200 };
      if (pathname === '/captchaImage') data.captchaEnabled = false;
      else if (pathname === '/login') { role = req.postDataJSON().role; data.token = 'fixture-' + role; }
      else if (pathname === '/getInfo') data = { code: 200, roles: [role], permissions: role === 'admin' ? ['*:*:*'] : ['education:course:list'], user: { userId: role === 'admin' ? 1 : 9, userName: 'fixture-' + role, nickName: '验收夹具' } };
      else if (pathname === '/getRouters') data.data = allRoutes;
      else if (pathname === '/system/user/list') data = denied ? { code: 403 } : { code: 200, rows: [{ userId: 9, userName: 'fixture-user', nickName: '测试用户', status: '0', dept: { deptName: '测试组织' } }], total: 1 };
      else if (pathname === '/system/user/' || pathname === '/system/user') {
        if (req.method() === 'GET') Object.assign(data, { roles, posts: [], data: {} });
        else lastUser = req.postDataJSON();
      }
      else if (pathname === '/system/role/list') Object.assign(data, { rows: roles, total: roles.length });
      else if (pathname === '/system/menu/list') data.data = [{ menuId: 100, parentId: 0, menuName: '测试菜单', menuType: 'C', path: 'fixture', status: '0', orderNum: 0 }];
      else if (pathname === '/system/dept/list') data.data = depts;
      else if (pathname === '/monitor/operlog/list') Object.assign(data, { rows: [{ operId: 1, title: '用户管理', operName: 'fixture-admin', businessType: 2, status: 0, operTime: '2026-11-02 10:00:00' }], total: 1 });
      else if (pathname === '/education/context/options') data.data = { term: { termName: '测试学期' }, courses: [{ courseSpaceId: '11', title: '测试课程', classes: [{ classId: '12', termId: '3', bindingId: '13', memberRole: role, className: '测试教学班' }] }] };
      else if (pathname === '/education/context/check') data.data = { courseSpaceId: '11', classId: '12', termId: '3', bindingId: '13', memberRole: role, canPublish: true, canGrade: true };
      else if (pathname === '/fixture/unread') data.data = 3;
      else if (pathname !== '/logout') data = { code: 404, msg: 'Fixture endpoint absent' };
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(data) });
    });
    async function run(name, fn) { await fn(); results.push(name); process.stdout.write('PASS ' + name + '\n'); }
    async function login() {
      await page.locator('#username').fill('fixture-account');
      await page.locator('#password').fill('fixture-password');
      await page.locator('button[type=submit]').click();
      await page.waitForURL('**/#/' + role);
    }
    await run('三角色登录中的管理员入口及授权导航', async () => {
      await page.goto(origin + '/vue/');
      await login();
      await page.locator('.app-sidebar').getByRole('link', { name: '用户管理', exact: true }).click();
      await page.getByRole('cell', { name: 'fixture-user', exact: true }).waitFor();
    });
    await run('用户新增必填校验及真实请求载荷', async () => {
      await page.getByRole('button', { name: '新增用户', exact: true }).click();
      await page.locator('.modal-foot').getByRole('button', { name: '保存', exact: true }).click();
      assert.match(await page.locator('.modal-error').first().innerText(), /账号/);
      await page.locator('#f-userName').fill('fixture-created');
      await page.locator('#f-nickName').fill('夹具新增用户');
      await page.locator('#f-password').fill('fixture-password');
      await page.locator('.check-list input[type=checkbox]').first().check();
      await page.locator('.modal-foot').getByRole('button', { name: '保存', exact: true }).click();
      await page.locator('.modal-mask').waitFor({ state: 'hidden' });
      assert.equal(lastUser.userName, 'fixture-created');
      assert.deepEqual(lastUser.roleIds, [4]);
    });
    await run('403 后旧表格与弹窗被移除', async () => {
      denied = true;
      await page.getByRole('button', { name: '查询', exact: true }).click();
      await page.getByRole('alert').waitFor();
      assert.equal(await page.getByRole('cell', { name: 'fixture-user', exact: true }).count(), 0);
      assert.equal(await page.locator('.modal-mask').count(), 0);
      denied = false;
      await page.getByRole('button', { name: '重新核验', exact: true }).click();
      await page.getByRole('cell', { name: 'fixture-user', exact: true }).waitFor();
    });
    if (process.env.GROUP1_QA_DIR) {
      fs.mkdirSync(process.env.GROUP1_QA_DIR, { recursive: true });
      await page.screenshot({ path: path.join(process.env.GROUP1_QA_DIR, 'admin-desktop.png'), fullPage: true });
    }
    for (const [title, cell] of [['角色管理', 'teacher'], ['菜单管理', '测试菜单'], ['组织架构管理', '测试组织'], ['审计日志', 'fixture-admin']]) {
      await run(title + '加载与页签', async () => {
        await page.locator('.app-sidebar').getByRole('link', { name: title, exact: true }).click();
        await page.getByRole('cell', { name: cell, exact: true }).first().waitFor();
        assert.ok((await page.locator('.workspace-tabs').innerText()).includes(title));
      });
    }
    await run('审计操作者和时间筛选', async () => {
      await page.getByPlaceholder('按操作人员搜索').fill('fixture-admin');
      await page.getByLabel('开始日期').fill('2026-11-01');
      await page.getByLabel('结束日期').fill('2026-11-02');
      const promise = page.waitForRequest(req => req.url().includes('/monitor/operlog/list?') && req.url().includes('beginTime'));
      await page.getByRole('button', { name: '查询', exact: true }).click();
      const req = await promise;
      assert.equal(new URL(req.url()).searchParams.get('params[beginTime]'), '2026-11-01 00:00:00');
    });
    await run('窄屏导航及页面宽度', async () => {
      await page.setViewportSize({ width: 390, height: 844 });
      await page.getByRole('button', { name: '展开或收起导航' }).click();
      assert.equal(await page.locator('.app-sidebar.open').count(), 1);
      const header = await page.locator('.app-header').boundingBox();
      const drawer = await page.locator('.app-sidebar').boundingBox();
      assert.ok(drawer.y >= header.y + header.height - 1);
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1));
      if (process.env.GROUP1_QA_DIR) await page.screenshot({ path: path.join(process.env.GROUP1_QA_DIR, 'admin-mobile.png'), fullPage: true });
      await page.setViewportSize({ width: 1280, height: 800 });
    });
    await run('退出后旧业务内容不可见', async () => {
      await page.getByRole('button', { name: '退出登录', exact: true }).click();
      await page.waitForURL('**/#/login?logout=ok');
      assert.equal(await page.locator('.ui-table').count(), 0);
    });
    await run('教师上下文、页面注册及顶部通知入口', async () => {
      await page.getByText('教师', { exact: true }).click();
      role = 'teacher';
      await login();
      await page.evaluate(() => {
        window.EduVue.registerPage({ id: 'fixture', path: 'fixture', title: '夹具课程页', roles: ['teacher'],
          permission: 'education:course:list', serviceRoute: 'education/course', requiresContext: 'class',
          component: () => Promise.resolve({ render: () => 'Fixture business content' }),
          headerEntry: { label: '夹具通知', getUnreadCount: async (_context, { request }) => (await request('/fixture/unread')).data }
        });
      });
      await page.getByLabel('当前课程').selectOption('11');
      await page.getByLabel('当前教学班').selectOption('12');
      await page.getByText('已验证当前教学班', { exact: true }).waitFor();
      await page.locator('.app-sidebar').getByRole('link', { name: '夹具课程页', exact: true }).click();
      await page.getByText('Fixture business content', { exact: true }).waitFor();
      await page.getByRole('button', { name: /夹具通知3/ }).waitFor();
    });
    await run('切换课程先清旧页，刷新重新核验绑定', async () => {
      await page.getByLabel('当前教学班').selectOption('');
      assert.equal(await page.getByText('Fixture business content', { exact: true }).count(), 0);
      await page.getByLabel('当前教学班').selectOption('12');
      await page.getByText('Fixture business content', { exact: true }).waitFor();
      await page.reload();
      await page.getByText('已验证当前教学班', { exact: true }).waitFor();
    });
    await run('教师改 URL 不能打开管理页', async () => {
      await page.goto(origin + '/vue/#/teacher/users');
      await page.waitForURL('**/#/teacher?denied=1');
      assert.equal(await page.getByRole('button', { name: '新增用户', exact: true }).count(), 0);
    });
    await run('学生登录、上下文与管理页拒绝', async () => {
      await page.getByRole('button', { name: '退出登录', exact: true }).click();
      await page.waitForURL('**/#/login?logout=ok');
      await page.getByText('学生', { exact: true }).click();
      role = 'student';
      await login();
      await page.getByLabel('当前课程').selectOption('11');
      await page.getByLabel('当前教学班').selectOption('12');
      await page.getByText('已验证当前教学班', { exact: true }).waitFor();
      await page.goto(origin + '/vue/#/student/users');
      await page.waitForURL('**/#/student?denied=1');
      assert.equal(await page.locator('.ui-table').count(), 0);
    });
    assert.deepEqual(errors, []);
    process.stdout.write(JSON.stringify({ passed: results.length, failures: 0, fixtureOnly: true, cases: results }) + '\n');
    await context.close();
  } finally { await browser.close(); }
})().catch(error => { process.stderr.write(error.stack + '\n'); process.exitCode = 1; }).finally(() => server.close());
