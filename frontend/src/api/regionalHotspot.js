import request from './request'

// ==================== 区域消费热点洞察（EPIC-05 故事5）====================

export const getAllRegions = () => {
  return request.get('/api/admin/regional-hotspots/regions')
}

export const getRegionalHotspots = (params) => {
  return request.get('/api/admin/regional-hotspots', { params })
}

// ==================== 区域热词（EPIC-03 故事1）====================

/**
 * 分页查询热词列表
 * @param {Object} params - { regionCode, category, periodType, pageNum, pageSize }
 */
export const getHotWords = (params) => {
  return request.get('/api/hot-words', { params })
}

/**
 * 获取有热词数据的区域列表
 */
export const getHotWordRegions = () => {
  return request.get('/api/hot-words/regions')
}

/**
 * 获取热词关联的商家列表（第一层下钻）
 * @param {number} id - 热词ID
 * @param {number} limit - 最多返回商家数
 */
export const getHotWordMerchants = (id, limit = 20) => {
  return request.get(`/api/hot-words/${id}/merchants`, { params: { limit } })
}

/**
 * 获取某热词在某商家下的评价列表（第二层下钻）
 * @param {number} id - 热词ID
 * @param {number} merchantId - 商家ID
 * @param {number} limit - 最多返回评价数
 */
export const getHotWordMerchantReviews = (id, merchantId, limit = 30) => {
  return request.get(`/api/hot-words/${id}/merchants/${merchantId}/reviews`, { params: { limit } })
}

/**
 * 管理员手动触发全量热词生成
 * @param {Object} params - { periodType, daysBack }
 */
export const regenerateAllHotWords = (params = {}) => {
  return request.post('/api/hot-words/regenerate', params)
}

/**
 * 管理员手动触发单个区域的热词生成
 * @param {string} regionCode - 区域编码
 * @param {Object} params - { periodType, daysBack }
 */
export const regenerateRegionHotWords = (regionCode, params = {}) => {
  return request.post(`/api/hot-words/regenerate/${regionCode}`, params)
}
