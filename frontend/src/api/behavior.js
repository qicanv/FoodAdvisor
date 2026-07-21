import request from './request'

// 管理端行为分析接口
export const getBehaviorOverview = (params) => {
  return request.get('/api/behavior/overview', { params })
}

export const getBehaviorStats = (params) => {
  return request.get('/api/admin/behavior/stats', { params })
}

export const getBehaviorLogs = (params) => {
  return request.get('/api/admin/behavior/logs', { params })
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
  return request.get('/api/behavior/recommendation-stats', {
    params,
  })
}

export const reportBehaviorEvent = (eventData) => {
  return request.post('/api/behavior/event', eventData)
}

// 食客端行为记录接口
export const logSearch = (data) => {
  return request.post('/api/behavior/log/search', data)
}

export const logMerchantClick = (data) => {
  return request.post(
    '/api/behavior/log/merchant-click',
    data
  )
}

export const logSceneEntry = (data) => {
  return request.post(
    '/api/behavior/log/scene-entry',
    data
  )
}

export const logTopicClick = (data) => {
  return request.post(
    '/api/behavior/log/topic-click',
    data
  )
}

export const logTagClick = (data) => {
  return request.post(
    '/api/behavior/log/tag-click',
    data
  )
}