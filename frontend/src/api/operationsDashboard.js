import request from './request'

export const getDashboardOverview = params => {
  return request.get('/api/admin/dashboard/overview', { params })
}

export const getDashboardTrends = params => {
  return request.get('/api/admin/dashboard/trends', { params })
}