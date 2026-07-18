import request from './request'

export const getMerchants = params => {
  return request.get('/api/merchants', { params })
}

export const getMerchantDetail = merchantId => {
  return request.get(`/api/merchants/${merchantId}`)
}

export const searchMerchants = params => {
  return request.get('/api/merchants/search', { params })
}