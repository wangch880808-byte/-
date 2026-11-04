import assert from 'node:assert/strict'
import test from 'node:test'
import {
  formatAnswer,
  formatCountdown,
  newIdempotencyKey,
  quizKind,
  remainingSeconds,
  statusText
} from '../../src/utils/classroom.js'

test('status and countdown helpers', () => {
  assert.equal(statusText('RUNNING'), '进行中')
  assert.equal(formatCountdown(125), '2:05')
  assert.equal(formatCountdown(0), '已截止')
})

test('quiz kind and answer formatting', () => {
  assert.equal(quizKind({ questionKind: 'MULTI_CHOICE' }), 'MULTI')
  assert.equal(quizKind({ questionKind: 'TRUE_FALSE' }), 'JUDGE')
  assert.equal(formatAnswer({ correctOption: 'B' }), 'B')
  assert.equal(formatAnswer(['A', 'C']), 'A、C')
})

test('idempotency key and remaining seconds', () => {
  const key = newIdempotencyKey()
  assert.ok(key.length > 8)
  const closes = new Date(Date.now() + 90_000).toISOString()
  const left = remainingSeconds(closes, new Date().toISOString())
  assert.ok(left > 80 && left <= 90)
})
