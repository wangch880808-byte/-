import { test, expect } from '@playwright/test'

// 仅浏览器测试拦截接口；应用本身始终请求真实后端。
async function backend(page, { role = 'admin', courseCode = 200, httpStatus = 200, identityNetworkError = false, loginError = false, logoutError = false, captcha = false } = {}) {
  const calls = []
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/dev-api/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/dev-api', '')
    calls.push({ path, method: request.method(), body: request.postDataJSON(), authorization: request.headers().authorization })
    if (path === '/getInfo' && identityNetworkError) return route.abort()
    let result = { code: 200, data: [] }
    if (path === '/captchaImage') result = { code: 200, captchaEnabled: captcha, uuid: 'test-captcha', img: 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a2ioAAAAASUVORK5CYII=' }
    else if (path === '/login') result = loginError ? { code: 500, msg: '账号或密码错误' } : { code: 200, token: 'test-session-token' }
    else if (path === '/getInfo') result = { code: 200, roles: [role], permissions: role === 'admin' ? ['*:*:*'] : [], user: { nickName: '测试用户', userName: role } }
    else if (path === '/education/term/list') result = { code: 200, rows: [{ id: 1, name: '2026 秋季学期', code: '2026-FALL', startsOn: '2026-09-01', endsOn: '2027-01-15', current: true }], total: 21 }
    else if (path === '/education/course/list') {
      if (courseCode !== 200) return route.fulfill({ status: httpStatus, json: { code: courseCode, msg: courseCode === 403 ? '你没有权限查看课程' : '会话已过期' } })
      result = { code: 200, rows: [{ id: 7, name: '软件工程导论', semesterName: '2026 秋季学期', status: 'ACTIVE', teacherName: '任课教师', classCount: 2, studentCount: 60 }], total: 1 }
    }
    else if (path === '/education/course/7') result = { code: 200, data: { id: 7, name: '软件工程导论', status: 'ACTIVE', teacherName: '任课教师', units: [], knowledgePoints: [], classes: [] } }
    else if (path === '/education/organization/tree') result = { code: 200, data: [{ id: 1, name: '计算机学院', parentId: 0 }] }
    else if (path === '/system/user/list') result = { code: 200, rows: [{ userId: 2, userName: 'teacher', nickName: '任课教师', status: '0' }], total: 1 }
    else if (path === '/system/dept/list') result = { code: 200, data: [{ deptId: 1, deptName: '计算机学院', status: '0' }] }
    else if (path.endsWith('/list')) result = { code: 200, rows: [], total: 0 }
    else if (path === '/logout' && logoutError) return route.abort()
    return route.fulfill({ json: result })
  })
  return { calls, errors }
}
async function session(page, role) {
  await page.addInitScript(role => {
    sessionStorage.setItem('zhijiao.login.token', 'test-session-token')
    sessionStorage.setItem('zhijiao.login.role', role)
  }, role)
}
test('未登录访问业务深链，跳转登录并保留目标', async ({ page }) => {
  await backend(page)
  await page.goto('/app/#/education/teacher/course/detail/7')
  await expect(page.getByRole('heading', { name: '管理员登录' })).toBeVisible()
  await expect(page).toHaveURL(/login\?redirect=/)
})
test('管理员登录、查询与分页，新增学期弹窗可用', async ({ page }) => {
  const { calls, errors } = await backend(page)
  await page.goto('/app/#/login')
  await page.getByRole('textbox', { name: '账号', exact: true }).fill('admin')
  await page.locator('input[name=password]').fill('test-password')
  await page.getByRole('button', { name: '登录工作空间' }).click()
  await expect(page.getByRole('heading', { name: '学期管理', exact: true })).toBeVisible()
  await expect(page.getByText('2026 秋季学期', { exact: true })).toBeVisible()
  expect(calls.find(call => call.path === '/login').body.role).toBe('admin')
  expect(calls.find(call => call.path === '/getInfo').authorization).toBe('Bearer test-session-token')
  await page.getByRole('button', { name: '下一页' }).click()
  await expect.poll(() => calls.filter(call => call.path === '/education/term/list').length).toBe(2)
  await page.getByRole('button', { name: '新建学期' }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  expect(errors).toEqual([])
})
for (const role of ['teacher', 'student']) {
  test(`${role} 课程列表、详情和刷新可用，禁止跨角色访问`, async ({ page }) => {
    const { errors } = await backend(page, { role })
    await session(page, role)
    await page.goto(`/app/#/education/${role}/course`)
    await page.getByText('软件工程导论', { exact: true }).first().click()
    await expect(page).toHaveURL(new RegExp(`/${role}/course/detail/7`))
    await expect(page.getByRole('heading', { name: '软件工程导论' })).toBeVisible()
    await page.reload()
    await expect(page.getByRole('heading', { name: '软件工程导论' })).toBeVisible()
    await page.goto('/app/#/education/admin/term')
    await expect(page).toHaveURL(/\/403$/)
    await expect(page.getByText('无访问权限', { exact: true }).last()).toBeVisible()
    expect(errors).toEqual([])
  })
}
for (const status of [200, 401]) {
  test(`业务接口 HTTP ${status} 返回401时清理令牌并跳登录`, async ({ page }) => {
    await backend(page, { role: 'teacher', courseCode: 401, httpStatus: status })
    await session(page, 'teacher')
    await page.goto('/app/#/education/teacher/course')
    await expect(page.getByRole('heading', { name: '管理员登录' })).toBeVisible()
    expect(await page.evaluate(() => sessionStorage.getItem('zhijiao.login.token'))).toBeNull()
  })
}
test('403业务错误显示无权限，不清除登录', async ({ page }) => {
  await backend(page, { role: 'teacher', courseCode: 403 })
  await session(page, 'teacher')
  await page.goto('/app/#/education/teacher/course')
  await expect(page.getByText('你没有权限查看课程')).toBeVisible()
  expect(await page.evaluate(() => sessionStorage.getItem('zhijiao.login.token'))).toBe('test-session-token')
})
test('身份请求断网保留令牌并提供重试', async ({ page }) => {
  await backend(page, { role: 'teacher', identityNetworkError: true })
  await session(page, 'teacher')
  await page.goto('/app/#/education/teacher/course')
  await expect(page.getByText('暂时无法连接服务')).toBeVisible()
  expect(await page.evaluate(() => sessionStorage.getItem('zhijiao.login.token'))).toBe('test-session-token')
  await page.unroute('**/dev-api/**')
  await backend(page, { role: 'teacher' })
  await page.getByRole('button', { name: '重新连接' }).click()
  await expect(page.getByRole('heading', { name: '我的课程', exact: true })).toBeVisible()
})
test('管理员全部菜单及系统查询页面无运行异常', async ({ page }) => {
  const { errors } = await backend(page)
  await session(page, 'admin')
  for (const path of ['term', 'class', 'teacher', 'student', 'dept']) {
    await page.goto('/app/#/education/admin/' + path)
    await expect(page.locator('.app-container')).toBeVisible()
    await expect(page.getByText('页面出现异常')).toHaveCount(0)
  }
  for (const path of ['user', 'dept']) {
    await page.goto('/app/#/system/' + path)
    await expect(page.getByText('系统目录查询')).toBeVisible()
  }
  expect(errors).toEqual([])
})
test('登录失败后刷新验证码并允许重新提交', async ({ page }) => {
  const { calls } = await backend(page, { loginError: true })
  await page.goto('/app/#/login')
  await page.getByRole('textbox', { name: '账号', exact: true }).fill('wrong')
  await page.locator('input[name=password]').fill('wrong')
  await page.getByRole('button', { name: '登录工作空间' }).click()
  await expect(page.getByText('账号或密码错误')).toBeVisible()
  await expect.poll(() => calls.filter(call => call.path === '/captchaImage').length).toBe(2)
  await expect(page.getByRole('button', { name: '登录工作空间' })).toBeEnabled()
})
test('退出接口断网时仍清理本地登录', async ({ page }) => {
  await backend(page, { role: 'student', logoutError: true })
  await session(page, 'student')
  await page.goto('/app/#/education/student/course')
  await page.getByRole('button', { name: '退出登录' }).click()
  await expect(page.getByRole('heading', { name: '管理员登录' })).toBeVisible()
  expect(await page.evaluate(() => sessionStorage.getItem('zhijiao.login.token'))).toBeNull()
})
test('手机登录页与导航可用，页面无横向溢出', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await backend(page, { role: 'student' })
  await page.goto('/app/#/login')
  await expect(page.getByRole('button', { name: '登录工作空间' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await session(page, 'student')
  await page.reload()
  await page.goto('/app/#/education/student/course')
  await page.getByRole('button', { name: '展开导航' }).click()
  await expect(page.getByRole('navigation', { name: '主导航' })).toBeInViewport()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})
test('验证码启用时发送服务端uuid、输入值和教师角色', async ({ page }) => {
  const { calls } = await backend(page, { role: 'teacher', captcha: true })
  await page.goto('/app/#/login')
  await page.getByRole('button', { name: '教师', exact: true }).click()
  await page.getByRole('textbox', { name: '账号', exact: true }).fill('teacher')
  await page.locator('input[name=password]').fill('test-password')
  await page.getByRole('textbox', { name: '验证码', exact: true }).fill('1234')
  await page.getByRole('button', { name: '登录工作空间' }).click()
  await expect(page.getByRole('heading', { name: '我的课程', exact: true })).toBeVisible()
  expect(calls.find(call => call.path === '/login').body).toMatchObject({ role: 'teacher', code: '1234', uuid: 'test-captcha' })
})
test('服务端角色与所选入口不符，撤销令牌并停留登录页', async ({ page }) => {
  const { calls } = await backend(page, { role: 'student' })
  await page.goto('/app/#/login')
  await page.getByRole('textbox', { name: '账号', exact: true }).fill('student')
  await page.locator('input[name=password]').fill('test-password')
  await page.getByRole('button', { name: '登录工作空间' }).click()
  await expect(page.getByText('此账号没有所选入口的角色，请选择正确入口或联系管理员')).toBeVisible()
  expect(calls.some(call => call.path === '/logout')).toBe(true)
  expect(await page.evaluate(() => sessionStorage.getItem('zhijiao.login.token'))).toBeNull()
})
test('生产构建保留index.html深链，刷新及根路径接口正常', async ({ page }) => {
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route('http://127.0.0.1:4173/**', async route => {
    const path = new URL(route.request().url()).pathname
    let json
    if (path === '/getInfo') json = { code: 200, roles: ['teacher'], permissions: [], user: { nickName: '测试教师' } }
    else if (path === '/getRouters') json = { code: 200, data: [] }
    else if (path === '/education/course/list') json = { code: 200, rows: [], total: 0 }
    else return route.continue()
    return route.fulfill({ json })
  })
  await session(page, 'teacher')
  await page.goto('http://127.0.0.1:4173/app/index.html#/education/teacher/course')
  await expect(page.getByRole('heading', { name: '我的课程', exact: true })).toBeVisible()
  await page.reload()
  await expect(page.getByText('你还没有创建课程')).toBeVisible()
  await expect(page).toHaveURL(/\/app\/index.html#\/education\/teacher\/course$/)
  expect(errors).toEqual([])
})
