import request from './request'

// 获取预警列表
export const getSensitiveAlerts = (params) => {
  return request.get('/api/admin/sensitive-alerts', { params })
}

// 获取预警详情
export const getSensitiveAlertDetail = (id) => {
  return request.get(`/api/admin/sensitive-alerts/${id}`)
}

// 更新预警处理状态
export const updateAlertStatus = (id, data) => {
  return request.put(`/api/admin/sensitive-alerts/${id}/status`, data)
}

// 手动触发敏感话题检测
export const detectSensitiveTopics = (data) => {
  return request.post('/api/admin/sensitive-alerts/detect', data || {})
}

// 获取待处理预警数量
export const getPendingCount = () => {
  return request.get('/api/admin/sensitive-alerts/pending-count')
}

// 获取话题类型枚举
export const getTopicTypes = () => {
  return request.get('/api/admin/sensitive-alerts/topic-types')
}
