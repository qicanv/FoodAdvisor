import request from './request'

/**
 * 经营改进建议 API（EPIC-02 Story 8）
 */

/**
 * 获取商家的经营改进建议列表（只读缓存）
 * GET /api/merchants/{merchantId}/business-suggestions
 */
export const getBusinessSuggestions = (merchantId) => {
  return request.get(`/api/merchants/${merchantId}/business-suggestions`)
}

/**
 * 获取建议依据（溯源到统计数据或原始评论）
 * GET /api/merchants/{merchantId}/business-suggestions/evidences
 * @param {number} merchantId
 * @param {number} [suggestionId] - 可选，不传则返回所有活跃建议的依据
 */
export const getSuggestionEvidences = (merchantId, suggestionId) => {
  return request.get(`/api/merchants/${merchantId}/business-suggestions/evidences`, {
    params: suggestionId ? { suggestionId } : {},
  })
}

/**
 * 生成或刷新经营改进建议
 * POST /api/merchant-console/merchants/{merchantId}/business-suggestions/generate
 * @param {number} merchantId - 商家ID
 * @param {boolean} force - 是否强制重新生成
 */
export const generateBusinessSuggestions = (merchantId, force) => {
  return request.post(
    `/api/merchant-console/merchants/${merchantId}/business-suggestions/generate?force=${!!force}`,
    null,
    { timeout: 120000 }
  )
}
