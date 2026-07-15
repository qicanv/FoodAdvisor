import request from './request'

const adminHeaders = {
  'X-User-Role': 'ADMIN',
}

export function getModelConfigs() {
  return request.get('/api/admin/model-configs', {
    headers: adminHeaders,
  })
}

export function createModelConfig(data) {
  return request.post('/api/admin/model-configs', data, {
    headers: adminHeaders,
  })
}

export function updateModelConfig(id, data) {
  return request.put(`/api/admin/model-configs/${id}`, data, {
    headers: adminHeaders,
  })
}

export function testModelConfig(id) {
  return request.post(`/api/admin/model-configs/${id}/test`, null, {
    headers: adminHeaders,
  })
}

export function getSceneBindings() {
  return request.get('/api/admin/model-configs/scene-bindings', {
    headers: adminHeaders,
  })
}

export function bindScene(data) {
  return request.put('/api/admin/model-configs/scene-bindings', data, {
    headers: adminHeaders,
  })
}
