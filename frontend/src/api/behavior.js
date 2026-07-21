import request from './request'

export const getBehaviorOverview = (params) => {
  return request.get('/api/behavior/overview', { params })
}

export const getHotSearchKeywords = (params) => {
  return request.get('/api/behavior/hot-keywords', { params })
}

export const getHotScenarios = (params) => {
  return request.get('/api/behavior/hot-scenarios', { params })
}

export const getHotMerchants = (params) => {
  return request.get('/api/behavior/hot-merchants', { params })
}

export const getRecommendationStats = (params) => {
  return request.get('/api/behavior/recommendation-stats', { params })
}

export const reportBehaviorEvent = (eventData) => {
  return request.post('/api/behavior/event', eventData)
}

export const getBehaviorStats = (params) => {
  return request.get('/api/behavior/stats', { params })
}

export const getBehaviorLogs = (params) => {
  return request.get('/api/behavior/logs', { params })
}