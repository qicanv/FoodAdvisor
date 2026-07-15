import request from './request'

export const submitMerchantReview = (merchantId, formData, userId) => {
  return request.post(`/api/reviews/merchants/${merchantId}`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
      'X-User-Id': userId,
    },
    timeout: 30000,
  })
}
