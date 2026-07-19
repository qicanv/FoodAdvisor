import request from './request'

export const getAllRegions = () => {
  return request.get('/api/admin/regional-hotspots/regions')
}

export const getRegionalHotspots = (params) => {
  return request.get('/api/admin/regional-hotspots', { params })
}
