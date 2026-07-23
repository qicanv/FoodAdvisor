import { describe, expect, it } from 'vitest'

import { buildLoginRedirect } from './request'

describe('request authentication redirect', () => {
  it('preserves the protected page as an encoded redirect parameter', () => {
    expect(buildLoginRedirect('/diner/ai-dining', '?session=12'))
      .toBe('/diner?redirect=%2Fdiner%2Fai-dining%3Fsession%3D12')
  })
})
