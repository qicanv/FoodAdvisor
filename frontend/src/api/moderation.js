import request from './request'

export const getReviewList = (params) => {
  return request.get('/api/moderation/reviews', { params })
}

export const getReviewDetail = (id) => {
  return request.get(`/api/moderation/reviews/${id}`)
}

export const getActiveMerchants = () => {
  return request.get('/api/moderation/merchants')
}

export const getPendingCount = () => {
  return request.get('/api/moderation/pending-count')
}

export const getStats = () => {
  return request.get('/api/moderation/stats')
}

export const moderateReview = (id, action, remark) => {
  return request.post(`/api/moderation/reviews/${id}/action`, { action, remark })
}