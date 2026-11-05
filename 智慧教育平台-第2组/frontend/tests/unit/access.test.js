import test from 'node:test'
import assert from 'node:assert/strict'
import { canAccess, pickRole, safeRedirect } from '../../src/utils/access.js'
test('角色与细粒度权限必须同时匹配', () => {
  const meta = { roles: ['admin'], permission: 'system:user:list' }
  assert.equal(canAccess(meta, ['teacher'], ['*:*:*']), false)
  assert.equal(canAccess(meta, ['admin'], []), false)
  assert.equal(canAccess(meta, ['admin'], ['system:user:list']), true)
  assert.equal(canAccess(meta, ['admin'], ['*:*:*']), true)
})
test('登录入口只能选服务端返回的角色', () => {
  assert.equal(pickRole(['teacher'], 'admin'), 'teacher')
  assert.equal(pickRole(['student', 'teacher'], 'student'), 'student')
  assert.equal(pickRole(['unrecognized']), '')
})
test('拒绝外部跳转及登录循环，保留课程深链', () => {
  for (const target of ['https://example.com', '//example.com', '/login', '/education\\evil', null]) assert.equal(safeRedirect(target, '/home'), '/home')
  assert.equal(safeRedirect('/education/teacher/course/detail/7', '/home'), '/education/teacher/course/detail/7')
})
