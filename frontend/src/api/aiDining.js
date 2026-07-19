import request from './request'

export const createDiningSession = (title = '') => {
  const normalizedTitle = title.trim()
  return request.post(
    '/api/diner/sessions',
    normalizedTitle ? { title: normalizedTitle } : {}
  )
}

export const getDiningMessages = sessionId => {
  return request.get(`/api/diner/sessions/${sessionId}/messages`)
}

export const sendDiningMessage = (
  sessionId,
  content,
  requestId,
  location = null
) => {
  const body = { content, requestId }
  if (
    location?.userLatitude !== undefined &&
    location?.userLongitude !== undefined
  ) {
    body.userLatitude = location.userLatitude
    body.userLongitude = location.userLongitude
  }
  return request.post(
    `/api/diner/sessions/${sessionId}/messages`,
    body,
    { timeout: 60000 }
  )
}

export const adjustDiningRecommendation = (
  sessionId,
  sourceMessageId,
  field,
  value,
  location = null
) => {
  const body = { sourceMessageId, field, value }
  if (
    location?.userLatitude !== undefined &&
    location?.userLongitude !== undefined
  ) {
    body.userLatitude = location.userLatitude
    body.userLongitude = location.userLongitude
  }
  return request.post(
    `/api/diner/sessions/${sessionId}/recommendations/adjust`,
    body,
    { timeout: 60000 }
  )
}

export const getRecommendationEvidences = (recommendationId, merchantId) => {
  return request.get(
    `/api/diner/recommendations/${recommendationId}/evidences`,
    { params: { merchantId } }
  )
}
