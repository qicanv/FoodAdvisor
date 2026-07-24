import request from './request'

/**
 * 商家分析结果反馈 API（EPIC-06 Story 5）
 *
 * 商家用户可以对情感分析、关键词提取、差评归因、竞品对比、
 * 经营建议、评价摘要和商家亮点等分析结果标记为准确或不准确，
 * 并填写具体问题说明。
 */

/**
 * 商家端：提交或更新分析结果反馈。
 *
 * 同一商家对同一分析类型的同一分析记录，再次提交时更新原记录。
 *
 * @param {number} merchantId - 商家ID
 * @param {object} data - 反馈数据
 * @param {string} data.analysisType - 分析类型：SENTIMENT / KEYWORD / ISSUE_ATTRIBUTION / COMPETITOR / BUSINESS_SUGGESTION / REVIEW_SUMMARY / HIGHLIGHT
 * @param {number} [data.analysisId] - 关联的分析记录ID（选填）
 * @param {string} data.feedbackType - 反馈类型：ACCURATE / INACCURATE
 * @param {string} [data.content] - 具体问题说明（选填，最多2000字符）
 */
export const submitAnalysisFeedback = (merchantId, data) => {
  return request.post(
    `/api/merchant-console/merchants/${merchantId}/analysis-feedback`,
    data
  )
}

/**
 * 商家端：查询自己店铺的分析反馈列表。
 *
 * @param {number} merchantId - 商家ID
 * @param {object} [params] - 查询参数
 * @param {string} [params.analysisType] - 按分析类型筛选
 * @param {string} [params.feedbackType] - 按反馈类型筛选
 * @param {number} [params.pageNum=1] - 页码
 * @param {number} [params.pageSize=20] - 每页条数
 */
export const getMerchantAnalysisFeedback = (merchantId, params = {}) => {
  return request.get(
    `/api/merchant-console/merchants/${merchantId}/analysis-feedback`,
    { params }
  )
}

/**
 * 商家端：查询单条反馈详情。
 *
 * @param {number} merchantId - 商家ID
 * @param {number} feedbackId - 反馈记录ID
 */
export const getAnalysisFeedbackDetail = (merchantId, feedbackId) => {
  return request.get(
    `/api/merchant-console/merchants/${merchantId}/analysis-feedback/${feedbackId}`
  )
}

/**
 * 管理员端：按分析类型和反馈类型汇总统计。
 */
export const getAnalysisFeedbackStatistics = () => {
  return request.get('/api/admin/analysis-feedback/statistics')
}

/**
 * 管理员端：查询所有反馈列表。
 *
 * @param {object} [params] - 查询参数
 * @param {string} [params.analysisType] - 按分析类型筛选
 * @param {string} [params.feedbackType] - 按反馈类型筛选
 * @param {number} [params.merchantId] - 按商家筛选
 * @param {number} [params.pageNum=1] - 页码
 * @param {number} [params.pageSize=20] - 每页条数
 */
export const getAllAnalysisFeedback = (params = {}) => {
  return request.get('/api/admin/analysis-feedback', { params })
}
