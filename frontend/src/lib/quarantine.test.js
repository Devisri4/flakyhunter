import { describe, expect, it } from 'vitest'
import { grepInvertPattern } from './quarantine'

describe('grepInvertPattern', () => {
  it('joins quarantined titles into one escaped pattern', () => {
    const pattern = grepInvertPattern([
      { name: 'adds an item [firefox]' },
      { name: 'adds an item [chromium]' },
      { name: 'pays (with card) $5?' },
    ])
    expect(pattern).toBe('adds an item|pays \\(with card\\) \\$5\\?')
    expect(new RegExp(pattern).test('checkout.spec.ts › Cart › pays (with card) $5?')).toBe(true)
    expect(new RegExp(pattern).test('checkout.spec.ts › Cart › removes an item')).toBe(false)
  })

  it('is null when nothing is quarantined', () => {
    expect(grepInvertPattern([])).toBeNull()
  })
})
