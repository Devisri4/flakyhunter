import { describe, expect, it } from 'vitest'
import { change, formatDuration, passRate, shortSha } from './format'

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

  it('shortens real commit SHAs but keeps other labels whole', () => {
    expect(shortSha('abc1234def')).toBe('abc1234')
    expect(shortSha('3ddc48e8f2a14b0c9d7e6f5a4b3c2d1e0f9a8b7c')).toBe('3ddc48e')
    expect(shortSha('sample10')).toBe('sample10')
    expect(shortSha('release-2.1')).toBe('release-2.1')
    expect(shortSha(null)).toBe('—')
  })
})

describe('change', () => {
  it('describes the difference to the previous period', () => {
    expect(change(97.5, 95)).toBe('+2.5 pts')
    expect(change(90, 91)).toBe('−1 pts')
    expect(change(90, 90)).toBe('±0 pts')
    expect(change(90, null)).toBeNull()
  })
})
