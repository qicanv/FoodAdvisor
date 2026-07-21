import request from './request'

const adminHeaders = {
  'X-User-Role': 'ADMIN',
}

const scenePath = sceneCode =>
  `/api/admin/prompts/${encodeURIComponent(sceneCode)}`

/**
 * 查询全部提示词场景及当前启用版本。
 */
export function getPromptDefinitions() {
  return request.get('/api/admin/prompts', {
    headers: adminHeaders,
  })
}

/**
 * 查询指定提示词场景。
 */
export function getPromptDefinition(sceneCode) {
  return request.get(scenePath(sceneCode), {
    headers: adminHeaders,
  })
}

/**
 * 查询指定场景的全部历史版本。
 */
export function getPromptVersions(sceneCode) {
  return request.get(`${scenePath(sceneCode)}/versions`, {
    headers: adminHeaders,
  })
}

/**
 * 创建新的不可变提示词版本。
 *
 * data:
 * {
 *   content: string,
 *   changeNote: string,
 *   activate: boolean
 * }
 */
export function createPromptVersion(sceneCode, data) {
  return request.post(`${scenePath(sceneCode)}/versions`, data, {
    headers: adminHeaders,
  })
}

/**
 * 启用指定版本。
 *
 * data:
 * {
 *   operationNote: string
 * }
 */
export function activatePromptVersion(sceneCode, versionId, data = {}) {
  return request.post(
    `${scenePath(sceneCode)}/versions/${versionId}/activate`,
    data,
    {
      headers: adminHeaders,
    }
  )
}

/**
 * 回滚到指定历史版本。
 *
 * data:
 * {
 *   operationNote: string
 * }
 */
export function rollbackPromptVersion(sceneCode, versionId, data = {}) {
  return request.post(
    `${scenePath(sceneCode)}/versions/${versionId}/rollback`,
    data,
    {
      headers: adminHeaders,
    }
  )
}

/**
 * 查询指定场景的启用和回滚日志。
 */
export function getPromptActivationLogs(sceneCode) {
  return request.get(`${scenePath(sceneCode)}/activation-logs`, {
    headers: adminHeaders,
  })
}