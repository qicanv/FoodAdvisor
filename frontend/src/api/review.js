import request from './request'

export const submitMerchantReview = (merchantId, formData) => {
  return request.post(`/api/reviews/merchants/${merchantId}`, formData, {
    timeout: 30000,
  })
}
