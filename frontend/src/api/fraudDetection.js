import request from './request'

// 触发检测扫描
export const triggerScan = (data) => {
  return request.post('/api/admin/fraud-detection/scan', data || {})
}

// 刷评案例列表
export const getFraudCases = (params) => {
  return request.get('/api/admin/fraud-detection/cases', { params })
}

// 刷评案例详情
export const getFraudCaseDetail = (caseId) => {
  return request.get(`/api/admin/fraud-detection/cases/${caseId}`)
}

// 提交人工复核
export const submitReview = (caseId, data) => {
  return request.post(`/api/admin/fraud-detection/cases/${caseId}/review`, data)
}

// 批量修改评论状态
export const batchUpdateReviewStatus = (data) => {
  return request.put('/api/admin/fraud-detection/reviews/status', data)
}
