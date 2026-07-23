import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import AiDiningView from './AiDiningView.vue'

const api = vi.hoisted(() => ({
  createDiningSession: vi.fn(),
  getDiningMessages: vi.fn(),
  getRecommendationEvidences: vi.fn(),
  sendDiningMessage: vi.fn(),
  adjustDiningRecommendation: vi.fn(),
}))

vi.mock('../../api/aiDining', () => api)
vi.mock('../../api/behavior', () => ({
  logMerchantClick: vi.fn(() => Promise.resolve()),
  logSearch: vi.fn(() => Promise.resolve()),
}))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

const success = overrides => ({
  success: true,
  data: {
    userMessageId: 10,
    assistantMessageId: 11,
    assistantText: '已完成',
    responseType: 'RECOMMENDATION',
    currentConstraints: { cuisines: ['川菜'] },
    recommendation: {
      recommendationId: 20,
      semanticStatus: 'FULL',
      results: [],
      adjustmentSuggestions: [],
      limitingConditions: [],
    },
    ...overrides,
  },
})

async function mounted(history = []) {
  localStorage.setItem('foodadvisor.aiDining.session.anonymous', '1')
  api.getDiningMessages.mockResolvedValue({
    success: true,
    data: { messages: history },
  })
  const wrapper = mount(AiDiningView)
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
  api.getRecommendationEvidences.mockResolvedValue({
    success: true,
    data: [],
  })
})

describe('AiDiningView', () => {
  it('rejects blank input without sending', async () => {
    const wrapper = await mounted()
    await wrapper.find('textarea').setValue('   ')
    await wrapper.find('form').trigger('submit')
    expect(api.sendDiningMessage).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('不能只输入空格')
  })

  it('suppresses double send while the first request is pending', async () => {
    let resolve
    api.sendDiningMessage.mockReturnValue(
      new Promise(done => { resolve = done })
    )
    const wrapper = await mounted()
    await wrapper.find('textarea').setValue('四个人吃川菜')
    wrapper.find('form').trigger('submit')
    wrapper.find('form').trigger('submit')
    await Promise.resolve()
    expect(api.sendDiningMessage).toHaveBeenCalledTimes(1)
    resolve(success())
    await flushPromises()
  })

  it('reuses requestId when retrying the same failed send', async () => {
    api.sendDiningMessage
      .mockResolvedValueOnce({ success: false, message: '暂时失败' })
      .mockResolvedValueOnce(success())
    const wrapper = await mounted()
    await wrapper.find('textarea').setValue('两个人吃烧烤')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    await wrapper.find('.retry-button').trigger('click')
    await flushPromises()
    expect(api.sendDiningMessage.mock.calls[0][2])
      .toBe(api.sendDiningMessage.mock.calls[1][2])
  })

  it('clears an old error after success', async () => {
    api.sendDiningMessage.mockResolvedValue(success())
    const wrapper = await mounted()
    await wrapper.find('textarea').setValue(' ')
    await wrapper.find('form').trigger('submit')
    await wrapper.find('textarea').setValue('直接推荐')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.find('.error-banner').exists()).toBe(false)
  })

  it('shows a nonfatal degradation notice', async () => {
    api.sendDiningMessage.mockResolvedValue(success({
      degraded: true,
      extractor: 'RULE_FALLBACK',
    }))
    const wrapper = await mounted()
    await wrapper.find('textarea').setValue('直接推荐')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.find('.notice-banner').text()).toContain('规则安全降级')
  })

  it('renders recommendation cards and an evidence entry for every card', async () => {
    api.sendDiningMessage.mockResolvedValue(success({
      recommendation: {
        recommendationId: 20,
        semanticStatus: 'FULL',
        results: [{
          merchantId: 12,
          merchantName: '真实测试店',
          operationStatus: 'OPERATING',
          riskNotes: ['预算接近上限'],
          recommendationBases: [],
        }],
        adjustmentSuggestions: [],
        limitingConditions: [],
      },
    }))
    const wrapper = await mounted()
    await wrapper.find('textarea').setValue('推荐川菜')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('真实测试店')
    expect(wrapper.text()).toContain('预算接近上限')
    expect(wrapper.find('.evidence-button').exists()).toBe(true)
  })

  it('shows an empty evidence state', async () => {
    const wrapper = await mounted([{
      id: 1,
      role: 'ASSISTANT',
      content: '推荐结果',
      recommendationId: 20,
      recommendations: [{ merchantId: 12, merchantName: '店铺' }],
    }])
    await wrapper.find('.evidence-button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('暂无可查看依据')
  })

  it('keeps adjustment suggestions separate from limiting conditions', async () => {
    const wrapper = await mounted([{
      id: 1,
      role: 'ASSISTANT',
      content: '无匹配',
      adjustmentSuggestions: [{ id: 'a', displayText: '预算提高12元' }],
      limitingConditions: [{ field: 'budget', description: '当前预算80元' }],
    }])
    expect(wrapper.find('.suggestion-panel').text()).toContain('预算提高12元')
    expect(wrapper.find('.limiting-panel').text()).toContain('当前预算80元')
  })

  it('restores recommendations and degradation state from history', async () => {
    const wrapper = await mounted([{
      id: 1,
      role: 'ASSISTANT',
      content: '历史推荐',
      degraded: true,
      extractor: 'RULE_FALLBACK',
      recommendations: [{ merchantId: 12, merchantName: '历史店铺' }],
    }])
    expect(wrapper.text()).toContain('历史店铺')
    expect(wrapper.text()).toContain('规则安全降级')
  })
})
