import request from './request'

export const getBehaviorStats = (params) => {
  return request.get('/api/admin/behavior/stats', { params })
}

export const getBehaviorLogs = (params) => {
  return request.get('/api/admin/behavior/logs', { params })
}

export const logSearch = (data) => {
  return request.post('/api/behavior/log/search', data)
}

export const logMerchantClick = (data) => {
  return request.post('/api/behavior/log/merchant-click', data)
}

export const logSceneEntry = (data) => {
  return request.post('/api/behavior/log/scene-entry', data)
}

export const logTopicClick = (data) => {
  return request.post('/api/behavior/log/topic-click', data)
}

export const logTagClick = (data) => {
  return request.post('/api/behavior/log/tag-click', data)
}

export const logFeedback = (data) => {
  return request.post('/api/behavior/log/feedback', data)
}
