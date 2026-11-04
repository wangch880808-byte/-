import assert from 'node:assert/strict'
import test from 'node:test'
import { canAccess, pickRole, ROLE_HOME, safeRedirect } from '../../src/utils/access.js'

test('pickRole prefers selected entrance when present', () => {
  assert.equal(pickRole(['admin', 'teacher'], 'teacher'), 'teacher')
  assert.equal(pickRole(['student'], 'teacher'), 'student')
  assert.equal(pickRole([], 'admin'), '')
})

test('canAccess checks role and permission', () => {
  assert.equal(canAccess({ roles: ['teacher'] }, ['teacher'], []), true)
  assert.equal(canAccess({ roles: ['admin'] }, ['teacher'], []), false)
  assert.equal(canAccess({ permission: 'system:user:list' }, ['admin'], ['*:*:*']), true)
})

test('safeRedirect only allows local business paths', () => {
  assert.equal(safeRedirect('/education/teacher/classroom', ROLE_HOME.teacher), '/education/teacher/classroom')
  assert.equal(safeRedirect('https://evil.example/', ROLE_HOME.student), ROLE_HOME.student)
  assert.equal(safeRedirect('/login', ROLE_HOME.admin), ROLE_HOME.admin)
})
