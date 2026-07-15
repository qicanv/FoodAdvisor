import request from './request'

export const getRestaurants = () => {
  return request.get('/api/restaurants')
}

export const getMerchants = params => {
  return request.get('/api/merchants', { params })
}

export const getMerchantDetail = merchantId => {
  return request.get(`/api/merchants/${merchantId}`)
}
