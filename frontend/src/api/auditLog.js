import request from './request'

/**
 * 分页查询审计日志。
 *
 * 支持的查询参数：
 * startTime、endTime、operatorUserId、operatorUsername、
 * module、level、operationType、result、objectType、objectId、
 * pageNum、pageSize。
 */
export function getAuditLogs(params = {}) {
  return request.get('/api/admin/logs', {
    params,
  })
}