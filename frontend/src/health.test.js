import { describe, expect, it } from 'vitest'
import { describeHealth } from './health'

describe('describeHealth', () => {
  it('shows API and database status', () => {
    expect(describeHealth({ status: 'UP', components: { db: { status: 'UP' }, ping: { status: 'UP' } } }))
      .toEqual(['API: UP', 'Database: UP'])
  })

  it('works without components', () => {
    expect(describeHealth({ status: 'DOWN' })).toEqual(['API: DOWN'])
  })
})
