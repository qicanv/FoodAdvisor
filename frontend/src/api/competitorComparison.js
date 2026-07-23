import request from './request'

/**
 * 获取候选竞品列表（同区域、同品类、正常营业）
 * GET /api/merchant-console/merchants/{merchantId}/competitor-comparison/candidates
 */
export const getCompetitorCandidates = (merchantId) => {
  return request.get(`/api/merchant-console/merchants/${merchantId}/competitor-comparison/candidates`)
}

/**
 * 执行竞品对比分析
 * POST /api/merchant-console/merchants/{merchantId}/competitor-comparison/compare
 * @param {number} merchantId - 本店ID
 * @param {number[]} competitorMerchantIds - 竞品ID列表（1~3家）
 */
export const performCompetitorComparison = (merchantId, competitorMerchantIds) => {
  return request.post(
    `/api/merchant-console/merchants/${merchantId}/competitor-comparison/compare`,
    { competitorMerchantIds },
    { timeout: 120000 }
  )
}
