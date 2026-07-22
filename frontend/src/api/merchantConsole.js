import request from './request'

// ========== 店铺管理 ==========

/** 获取当前商户管理的店铺列表 */
export const getMyMerchants = () => {
  return request.get('/api/merchant-console/merchants')
}

/** 获取单个店铺详情 */
export const getMyMerchantDetail = (merchantId) => {
  return request.get(`/api/merchant-console/merchants/${merchantId}`)
}

/** 新建店铺 */
export const createMyMerchant = (data) => {
  return request.post('/api/merchant-console/merchants', data)
}

/** 修改店铺基本信息 */
export const updateMyMerchant = (merchantId, data) => {
  return request.put(`/api/merchant-console/merchants/${merchantId}`, data)
}

/** 修改经营状态 */
export const updateMyMerchantOperationStatus = (merchantId, data) => {
  return request.put(`/api/merchant-console/merchants/${merchantId}/operation-status`, data)
}

/** 获取营业时间 */
export const getMyMerchantBusinessHours = (merchantId) => {
  return request.get(`/api/merchant-console/merchants/${merchantId}/business-hours`)
}

/** 修改营业时间 */
export const updateMyMerchantBusinessHours = (merchantId, data) => {
  return request.put(`/api/merchant-console/merchants/${merchantId}/business-hours`, data)
}

// ========== 菜品管理 ==========

/** 获取菜品列表 */
export const getMyDishes = (params) => {
  return request.get('/api/merchant-console/dishes', { params })
}

/** 新增菜品 */
export const createMyDish = (data) => {
  return request.post('/api/merchant-console/dishes', data)
}

/** 修改菜品 */
export const updateMyDish = (dishId, data) => {
  return request.put(`/api/merchant-console/dishes/${dishId}`, data)
}

/** 修改菜品状态 */
export const updateMyDishStatus = (dishId, data) => {
  return request.put(`/api/merchant-console/dishes/${dishId}/status`, data)
}

/** 下架菜品 */
export const deleteMyDish = (dishId) => {
  return request.delete(`/api/merchant-console/dishes/${dishId}`)
}

// ========== 亮点挖掘 ==========

/** 获取商家亮点列表 */
export const getMerchantHighlights = (merchantId) => {
  return request.get(`/api/merchants/${merchantId}/highlights`)
}

/** 查看亮点依据 */
export const getMerchantHighlightEvidences = (merchantId, highlightId) => {
  return request.get(`/api/merchants/${merchantId}/highlights/evidences`, {
    params: highlightId ? { highlightId } : {},
  })
}

/** 生成/刷新亮点 */
export const generateMerchantHighlights = (merchantId, force) => {
  return request.post(
    `/api/merchant-console/merchants/${merchantId}/highlights/generate?force=${!!force}`,
    null,
    { timeout: 60000 }
  )
}
