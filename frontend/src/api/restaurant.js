import request from './request'

export const getMerchants = params => {
  return request.get('/api/merchants', { params })
}

export const getMerchantDetail = merchantId => {
  return request.get(`/api/merchants/${merchantId}`)
}

export const getMerchantReviewSummary = merchantId => {
  return request.get(`/api/merchants/${merchantId}/review-summary`)
}

export const getMerchantReviewSummaryEvidences = (
  merchantId,
  summaryId = null
) => {
  const params = summaryId ? { summaryId } : {}
  return request.get(
    `/api/merchants/${merchantId}/review-summary/evidences`,
    { params }
  )
}

export const refreshMerchantReviewSummary = merchantId => {
  return request.post(`/api/merchants/${merchantId}/review-summary/refresh`)
}

export const searchMerchants = params => {
  return request.get('/api/merchants/search', { params })
}
