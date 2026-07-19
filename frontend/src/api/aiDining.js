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
