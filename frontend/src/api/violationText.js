import request from './request'

// 获取违规检测记录
export const getRiskRecords = (contentType, contentId) => {
  return request.get('/api/admin/violation-text/risk-records', {
    params: { contentType, contentId }
  })
}

// 获取违规检测统计
export const getViolationStats = () => {
  return request.get('/api/admin/violation-text/stats')
}
