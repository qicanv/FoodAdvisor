import request from './request'

/** 提交举报 */
export const submitReport = (data) => {
  return request.post('/api/reports', data)
}

/** 获取我的举报列表 */
export const getMyReports = (params) => {
  return request.get('/api/reports/my', { params })
}

/** 获取举报详情 */
export const getReportDetail = (reportId) => {
  return request.get(`/api/reports/${reportId}`)
}
