import request from './request'

const BASE_URL = '/api/admin/review-summary/faithfulness-test'

/**
 * 执行评价摘要忠实性测试（EPIC-06 Story 3）
 * POST /api/admin/review-summary/faithfulness-test/run
 *
 * 注意：该接口会同步调用 AI 服务的 LLM-as-Judge，
 * 响应时间通常 30~90 秒，因此设置 120 秒超时。
 *
 * @param {Object} data - { merchantId, summary, reviews }
 */
export const runFaithfulnessTest = (data) => {
  return request.post(`${BASE_URL}/run`, data, { timeout: 120000 })
}

/**
 * 获取商家的忠实性测试历史记录
 * GET /api/admin/review-summary/faithfulness-test/history/{merchantId}
 * @param {number} merchantId
 * @param {Object} params - 分页参数
 */
export const getTestHistory = (merchantId, params) => {
  return request.get(`${BASE_URL}/history/${merchantId}`, { params })
}

/**
 * 获取单次忠实性测试详情
 * GET /api/admin/review-summary/faithfulness-test/{testId}
 * @param {number} testId
 */
export const getTestDetail = (testId) => {
  return request.get(`${BASE_URL}/${testId}`)
}

/**
 * 将失败案例加入优化清单
 * POST /api/admin/review-summary/faithfulness-test/{testId}/optimization-items
 * @param {number} testId
 * @param {Object} data - { merchantId, items: [{ claimIndex, claimType, claimText, verdict, issueType, reasoning }] }
 */
export const addToOptimizationList = (testId, data) => {
  return request.post(`${BASE_URL}/${testId}/optimization-items`, data)
}

/**
 * 获取优化清单列表
 * GET /api/admin/review-summary/faithfulness-test/optimization-items
 * @param {Object} params - { merchantId, status, pageNum, pageSize }
 */
export const getOptimizationList = (params) => {
  return request.get(`${BASE_URL}/optimization-items`, { params })
}

/**
 * 更新优化清单项状态
 * PUT /api/admin/review-summary/faithfulness-test/optimization-items/{itemId}
 * @param {number} itemId
 * @param {Object} data - { status, resolution }
 */
export const updateOptimizationItem = (itemId, data) => {
  return request.put(`${BASE_URL}/optimization-items/${itemId}`, data)
}

/**
 * 对比两次忠实性测试结果
 * GET /api/admin/review-summary/faithfulness-test/compare
 * @param {number} baselineTestId
 * @param {number} candidateTestId
 */
export const compareTestResults = (baselineTestId, candidateTestId) => {
  return request.get(`${BASE_URL}/compare`, {
    params: { baselineTestId, candidateTestId }
  })
}

/**
 * 获取商家列表（管理员搜索商家，用于选择测试目标）
 * GET /api/admin/merchants
 * @param {Object} params - { pageNum, pageSize, keyword }
 */
export const searchMerchants = (params) => {
  return request.get('/api/admin/merchants', { params })
}

/**
 * 获取商家评价摘要（复用现有接口）
 * GET /api/merchants/{merchantId}/review-summary
 * @param {number} merchantId
 */
export const getMerchantSummary = (merchantId) => {
  return request.get(`/api/merchants/${merchantId}/review-summary`)
}

/**
 * 获取商家评价列表（公开接口，带原文内容）
 * GET /api/reviews?merchantId={merchantId}&pageSize={pageSize}
 * @param {number} merchantId
 * @param {Object} params - { pageSize }
 */
export const getMerchantReviews = (merchantId, params) => {
  return request.get('/api/reviews', {
    params: { merchantId, pageSize: params?.pageSize || 20, pageNum: 1 }
  })
}
