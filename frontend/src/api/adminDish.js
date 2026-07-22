import request from './request'

export const getAdminDishes = params => {
  return request.get('/api/admin/dishes', { params })
}

export const getAdminDish = id => {
  return request.get(`/api/admin/dishes/${id}`)
}

export const createAdminDish = data => {
  return request.post('/api/admin/dishes', data)
}

export const updateAdminDish = (id, data) => {
  return request.put(`/api/admin/dishes/${id}`, data)
}

export const updateAdminDishStatus = (id, data) => {
  return request.put(`/api/admin/dishes/${id}/status`, data)
}

export const restoreAdminDish = (id, data) => {
  return request.put(`/api/admin/dishes/${id}/restore`, data)
}

export const getAdminDishStatusHistory = id => {
  return request.get(`/api/admin/dishes/${id}/status-history`)
}

export const deleteAdminDish = id => {
  return request.delete(`/api/admin/dishes/${id}`)
}
