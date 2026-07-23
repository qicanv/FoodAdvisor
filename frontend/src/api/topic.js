import request from './request'

export const getTopics = (params) => {
  return request({
    url: '/api/admin/topics',
    method: 'get',
    params
  })
}

export const getTopic = (id) => {
  return request({
    url: `/api/admin/topics/${id}`,
    method: 'get'
  })
}

export const createTopic = (data) => {
  return request({
    url: '/api/admin/topics',
    method: 'post',
    data
  })
}

export const updateTopic = (id, data) => {
  return request({
    url: `/api/admin/topics/${id}`,
    method: 'put',
    data
  })
}

export const deleteTopic = (id) => {
  return request({
    url: `/api/admin/topics/${id}`,
    method: 'delete'
  })
}

export const getTopicMerchants = (id) => {
  return request({
    url: `/api/admin/topics/${id}/merchants`,
    method: 'get'
  })
}

export const addTopicMerchant = (id, merchantId) => {
  return request({
    url: `/api/admin/topics/${id}/merchants`,
    method: 'post',
    data: merchantId
  })
}

export const removeTopicMerchant = (id, merchantId) => {
  return request({
    url: `/api/admin/topics/${id}/merchants/${merchantId}`,
    method: 'delete'
  })
}

export const getTags = (params) => {
  return request({
    url: '/api/admin/topics/tags',
    method: 'get',
    params
  })
}

export const createTag = (name, category) => {
  return request({
    url: '/api/admin/topics/tags',
    method: 'post',
    params: { name, category }
  })
}

export const deleteTag = (id) => {
  return request({
    url: `/api/admin/topics/tags/${id}`,
    method: 'delete'
  })
}

export const getTagMerchants = (id) => {
  return request({
    url: `/api/admin/topics/tags/${id}/merchants`,
    method: 'get'
  })
}

export const searchMerchants = (keyword) => {
  return request({
    url: '/api/merchants',
    method: 'get',
    params: { keyword, pageSize: 50 }
  })
}