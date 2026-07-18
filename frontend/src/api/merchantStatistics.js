import request from './request'

export const getMerchantStatisticsOverview = params => {
  return request.get('/api/merchant-statistics/overview', { params })
}

export const getMerchantStatisticsTrends = params => {
  return request.get('/api/merchant-statistics/trends', { params })
}