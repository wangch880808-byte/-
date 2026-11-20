/**
 * ConfirmDialog 公共弹窗隔离冒烟。
 *
 * 运行：
 *   npx vite --host 127.0.0.1 --port 4175
 *   CONFIRM_DIALOG_BASE=http://127.0.0.1:4175/tests/confirm-dialog-smoke.html \
 *     node tests/confirm-dialog-smoke.spec.mjs
 *
 * 只验证组件语义，不连接后端，不经过生产路由。
 */
import assert from 'node:assert/strict'
import { chromium } from 'playwright'

const BASE = process.env.CONFIRM_DIALOG_BASE || 'http://127.0.0.1:4175/tests/confirm-dialog-smoke.html'
const SCREENSHOT = process.env.CONFIRM_DIALOG_SCREENSHOT || ''

const browser = await chromium.launch({ channel: 'msedge', headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1280, height: 800 } })
  await page.goto(BASE, { waitUntil: 'networkidle' })
  await page.waitForSelector('#disabled-case .modal', { timeout: 15000 })

  const disabled = page.locator('#disabled-case')
  const warnings = disabled.locator('.confirm-warnings li')
  assert.equal(await warnings.count(), 2, 'warnings 必须继续展示')
  assert.match(await disabled.locator('.confirm-warnings').innerText(), /警告一/)
  assert.equal(await disabled.locator('.modal-foot button.primary').isDisabled(), true,
    'confirmDisabled 必须禁用确认按钮')
  assert.equal(await disabled.locator('.modal-foot button').nth(0).isDisabled(), false,
    'confirmDisabled 不得禁用取消按钮')
  assert.equal(await disabled.locator('.modal-close').isDisabled(), false,
    'confirmDisabled 不得禁用关闭按钮')

  const busy = page.locator('#busy-case')
  assert.equal(await busy.locator('.modal-foot button.primary').isDisabled(), true,
    'busy 必须禁用确认按钮')
  assert.equal(await busy.locator('.modal-foot button').nth(0).isDisabled(), true,
    'busy 必须继续禁用取消按钮')
  assert.equal(await busy.locator('.modal-close').isDisabled(), true,
    'busy 必须继续禁用关闭按钮')

  if (SCREENSHOT) await page.screenshot({ path: SCREENSHOT, fullPage: true })
  console.log('PASS ConfirmDialog warnings、confirmDisabled 与 busy 语义')
} finally {
  await browser.close()
}
