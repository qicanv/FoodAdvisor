import request from './request'

export const getAdminMerchants = params => {
  return request.get('/api/admin/merchants', { params })
}

export const getAdminMerchant = id => {
  return request.get(`/api/admin/merchants/${id}`)
}

export const createAdminMerchant = data => {
  return request.post('/api/admin/merchants', data)
}

export const updateAdminMerchant = (id, data) => {
  return request.put(`/api/admin/merchants/${id}`, data)
}

export const updateAdminMerchantStatus = (id, params) => {
  return request.put(`/api/admin/merchants/${id}/status`, null, { params })
}

export const deleteAdminMerchant = id => {
  return request.delete(`/api/admin/merchants/${id}`)
}

export const getAdminMerchantStatistics = () => {
  return request.get('/api/admin/merchants/statistics')
}
