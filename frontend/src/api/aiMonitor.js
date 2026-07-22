import request from './request'

export const getAiMonitorStats = (params) => {
  const mockData = {
    success: true,
    data: {
      totalStats: {
        totalCalls: 12568,
        successCalls: 12345,
        failureCalls: 223,
        successRate: 98.2,
        avgResponseTime: 1256
      },
      functionStats: [
        {
          functionType: 'RECOMMENDATION',
          totalCalls: 4523,
          successCalls: 4489,
          failureCalls: 34,
          successRate: 99.2,
          avgResponseTime: 1520
        },
        {
          functionType: 'SEARCH',
          totalCalls: 3891,
          successCalls: 3821,
          failureCalls: 70,
          successRate: 98.2,
          avgResponseTime: 980
        },
        {
          functionType: 'SENTIMENT',
          totalCalls: 2156,
          successCalls: 2134,
          failureCalls: 22,
          successRate: 99.0,
          avgResponseTime: 850
        },
        {
          functionType: 'SUMMARY',
          totalCalls: 1245,
          successCalls: 1208,
          failureCalls: 37,
          successRate: 97.0,
          avgResponseTime: 1850
        },
        {
          functionType: 'REPLY',
          totalCalls: 753,
          successCalls: 693,
          failureCalls: 60,
          successRate: 92.0,
          avgResponseTime: 2100
        }
      ]
    }
  }

  const { functionType, model, timeRange } = params
  if (functionType) {
    mockData.data.functionStats = mockData.data.functionStats.filter(
      s => s.functionType === functionType
    )
    const filtered = mockData.data.functionStats[0]
    if (filtered) {
      mockData.data.totalStats = {
        totalCalls: filtered.totalCalls,
        successCalls: filtered.successCalls,
        failureCalls: filtered.failureCalls,
        successRate: filtered.successRate,
        avgResponseTime: filtered.avgResponseTime
      }
    }
  }

  return Promise.resolve(mockData)
}

export const getAiFailureLogs = (params) => {
  const now = new Date()
  const mockLogs = [
    {
      traceId: 'trace-001',
      functionType: 'REPLY',
      model: 'gpt-4o',
      errorType: 'Timeout',
      errorMessage: '请求超时，模型响应时间超过30秒',
      responseTime: 30000,
      createdAt: new Date(now.getTime() - 1000 * 60 * 5).toISOString()
    },
    {
      traceId: 'trace-002',
      functionType: 'SEARCH',
      model: 'gpt-3.5',
      errorType: 'RateLimitError',
      errorMessage: 'API请求频率超限，请稍后重试',
      responseTime: 0,
      createdAt: new Date(now.getTime() - 1000 * 60 * 15).toISOString()
    },
    {
      traceId: 'trace-003',
      functionType: 'SUMMARY',
      model: 'qwen-plus',
      errorType: 'QuotaExceeded',
      errorMessage: '本月API调用额度已用完',
      responseTime: 0,
      createdAt: new Date(now.getTime() - 1000 * 60 * 30).toISOString()
    },
    {
      traceId: 'trace-004',
      functionType: 'RECOMMENDATION',
      model: 'gpt-4o',
      errorType: 'ServiceUnavailable',
      errorMessage: '服务暂时不可用，请稍后重试',
      responseTime: 5000,
      createdAt: new Date(now.getTime() - 1000 * 60 * 45).toISOString()
    },
    {
      traceId: 'trace-005',
      functionType: 'REPLY',
      model: 'gpt-4',
      errorType: 'Timeout',
      errorMessage: '请求超时，模型响应时间超过30秒',
      responseTime: 30000,
      createdAt: new Date(now.getTime() - 1000 * 60 * 60).toISOString()
    },
    {
      traceId: 'trace-006',
      functionType: 'SENTIMENT',
      model: 'gpt-3.5',
      errorType: 'ValidationError',
      errorMessage: '输入文本长度超过限制（最大5000字符）',
      responseTime: 0,
      createdAt: new Date(now.getTime() - 1000 * 60 * 90).toISOString()
    },
    {
      traceId: 'trace-007',
      functionType: 'SEARCH',
      model: 'gpt-4o',
      errorType: 'NetworkError',
      errorMessage: '网络连接异常，请检查网络设置',
      responseTime: 10000,
      createdAt: new Date(now.getTime() - 1000 * 60 * 120).toISOString()
    },
    {
      traceId: 'trace-008',
      functionType: 'RECOMMENDATION',
      model: 'qwen-plus',
      errorType: 'InternalServerError',
      errorMessage: '服务器内部错误，请联系管理员',
      responseTime: 8000,
      createdAt: new Date(now.getTime() - 1000 * 60 * 180).toISOString()
    }
  ]

  const { functionType, model, timeRange } = params
  let filteredLogs = [...mockLogs]

  if (functionType) {
    filteredLogs = filteredLogs.filter(l => l.functionType === functionType)
  }

  if (model) {
    filteredLogs = filteredLogs.filter(l => l.model === model)
  }

  return Promise.resolve({
    success: true,
    data: filteredLogs
  })
}