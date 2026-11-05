import { describe, expect, it } from 'vitest'
import { formatDuration, passRate, shortSha } from './format'

describe('format', () => {
  it('formats durations', () => {
    expect(formatDuration(250)).toBe('250 ms')
    expect(formatDuration(1500)).toBe('1.5 s')
    expect(formatDuration(42_000)).toBe('42 s')
    expect(formatDuration(125_000)).toBe('2 min 5 s')
  })

  it('computes pass rate without skipped tests', () => {
    expect(passRate({ passed: 8, failed: 1, flaky: 1 })).toBe(90)
    expect(passRate({ passed: 0, failed: 0, flaky: 0 })).toBeNull()
  })

  it('shortens commit SHAs', () => {
    expect(shortSha('abc1234def')).toBe('abc1234')
    expect(shortSha(null)).toBe('—')
  })
})
