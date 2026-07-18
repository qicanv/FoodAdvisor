import request from './request'

export const getAdminLogs = params => {
  return request.get('/api/admin/logs', { params })
}