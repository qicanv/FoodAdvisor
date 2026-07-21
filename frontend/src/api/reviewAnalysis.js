import request from './request'

/** 获取商家评价列表（支持标签和情感筛选） */
export const getMerchantReviews = (merchantId, params = {}) => {
  return request.get('/api/reviews', {
    params: { merchantId, pageNum: 1, pageSize: 50, ...params },
  })
}

/** 触发单条评价AI分析 */
export const analyzeReview = (reviewId) => {
  return request.post(`/api/reviews/${reviewId}/analyze`, null, {
    timeout: 60000,
  })
}

/** 获取评价分析结果 */
export const getReviewAnalysis = (reviewId) => {
  return request.get(`/api/reviews/${reviewId}/analysis`)
}

/** 批量分析商家评价 */
export const batchAnalyzeReviews = (merchantId) => {
  return request.post(`/api/reviews/batch-analyze?merchantId=${merchantId}`, null, {
    timeout: 120000,
  })
}

/** 获取商家评价标签统计 */
export const getMerchantReviewTags = (merchantId) => {
  return request.get('/api/reviews/tags', { params: { merchantId } })
}

/** 获取单条评价的标签 */
export const getReviewTags = (reviewId) => {
  return request.get(`/api/reviews/${reviewId}/tags`)
}

/** 获取商家差评归因统计 */
export const getMerchantIssueStats = (merchantId, params = {}) => {
  return request.get(`/api/reviews/merchants/${merchantId}/issue-stats`, { params })
}

/** 获取某问题类别下的评价列表 */
export const getIssueCategoryReviews = (merchantId, categoryCode, params = {}) => {
  return request.get(
    `/api/reviews/merchants/${merchantId}/issue-categories/${categoryCode}/reviews`,
    { params: { pageNum: 1, pageSize: 20, ...params } }
  )
}

/** 获取全部可用的问题类别字典 */
export const getIssueCategories = () => {
  return request.get('/api/reviews/issue-categories')
}
