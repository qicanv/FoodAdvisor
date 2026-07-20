const EMPTY_TEXT = '-'

export const traceStatusTagType = status => ({
  SUCCESS: 'success',
  FAILED: 'danger',
  FALLBACK: 'warning',
  RUNNING: 'primary',
}[status] || 'info')

export const displayTraceValue = value =>
  value === null || value === undefined || value === '' ? EMPTY_TEXT : String(value)

export const formatTraceDuration = value => {
  const duration = Number(value)
  if (!Number.isFinite(duration) || duration < 0) return EMPTY_TEXT
  return duration >= 1000 ? `${(duration / 1000).toFixed(2)} s` : `${Math.round(duration)} ms`
}

export const formatTracePercentage = value => {
  const score = Number(value)
  if (!Number.isFinite(score)) return EMPTY_TEXT
  const ratio = Math.min(1, Math.max(0, score))
  return `${(ratio * 100).toFixed(2)}%`
}

export const formatTraceTime = value => {
  if (!value) return EMPTY_TEXT
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return EMPTY_TEXT
  return date.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  })
}

export const formatTraceJson = value => {
  if (value === null || value === undefined || value === '') return '暂无数据'
  if (typeof value === 'string') {
    try {
      return JSON.stringify(JSON.parse(value), null, 2)
    } catch {
      return value
    }
  }
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

export const sortTraceStages = stages => [...(Array.isArray(stages) ? stages : [])]
  .sort((left, right) => {
    const sequence = Number(left?.sequenceNo ?? 0) - Number(right?.sequenceNo ?? 0)
    return sequence || Number(left?.attemptNo ?? 0) - Number(right?.attemptNo ?? 0)
  })
