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

export const sendDiningMessage = (sessionId, content, requestId) => {
  return request.post(
    `/api/diner/sessions/${sessionId}/messages`,
    { content, requestId },
    { timeout: 60000 }
  )
}

export const adjustDiningRecommendation = (
  sessionId,
  sourceMessageId,
  field,
  value
) => {
  return request.post(
    `/api/diner/sessions/${sessionId}/recommendations/adjust`,
    { sourceMessageId, field, value },
    { timeout: 60000 }
  )
}
