import request from './request'

/** 获取商家评论情感分析汇总 */
export const getSentimentSummary = (params) => {
  return request.get('/api/merchant-console/reviews/sentiment-summary', { params })
}

/** 获取评论情感分析明细列表 */
export const getSentimentReviews = (params) => {
  return request.get('/api/merchant-console/reviews/sentiment-list', { params })
}

/** 触发批量情感分析（对未分析的评论） */
export const triggerBatchAnalysis = (data) => {
  return request.post('/api/merchant-console/reviews/batch-analyze', data, {
    timeout: 60000,
  })
}

/** 获取单条评论的详细分析结果 */
export const getReviewAnalysisDetail = (reviewId) => {
  return request.get(`/api/merchant-console/reviews/${reviewId}/sentiment`)
}
