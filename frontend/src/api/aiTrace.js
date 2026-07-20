import request from './request'

/**
 * @typedef {Object} AiRequestTrace
 * @property {string} traceId
 * @property {string=} requestId
 * @property {number=} sessionId
 * @property {number=} userId
 * @property {string=} scene
 * @property {string=} intent
 * @property {string|Object=} structuredConditions
 * @property {string=} provider
 * @property {string=} modelName
 * @property {string=} modelVersion
 * @property {string=} promptVersion
 * @property {string=} status
 * @property {string|Object=} finalOutputSummary
 * @property {string=} errorCode
 * @property {string=} errorMessage
 * @property {string=} startedAt
 * @property {string=} completedAt
 * @property {number=} totalDurationMs
 */

/**
 * @typedef {Object} AiRequestTraceStage
 * @property {number=} sequenceNo
 * @property {string=} stageName
 * @property {number=} attemptNo
 * @property {string=} status
 * @property {string|Object=} inputSummary
 * @property {string|Object=} outputSummary
 * @property {string=} provider
 * @property {string=} modelName
 * @property {string=} modelVersion
 * @property {string=} promptVersion
 * @property {number=} durationMs
 * @property {string=} errorCode
 * @property {string=} errorMessage
 * @property {string=} startedAt
 * @property {string=} completedAt
 */

/**
 * @typedef {Object} AiTraceRetrievalSource
 * @property {number=} rankNo
 * @property {string=} sourceType
 * @property {string=} sourceId
 * @property {string=} documentId
 * @property {string=} chunkId
 * @property {number=} merchantId
 * @property {string=} merchantName
 * @property {string=} summary
 * @property {number=} relevanceScore
 */

/**
 * @typedef {Object} AiTraceDetail
 * @property {AiRequestTrace} trace
 * @property {AiRequestTraceStage[]} stages
 * @property {AiTraceRetrievalSource[]} retrievalSources
 */

/**
 * @typedef {Object} AiTraceQuery
 * @property {string=} traceId
 * @property {string=} requestId
 * @property {number=} sessionId
 * @property {number=} userId
 * @property {string=} scene
 * @property {string=} status
 * @property {string=} modelName
 * @property {boolean=} fallback
 * @property {string=} startTime ISO-8601 offset datetime
 * @property {string=} endTime ISO-8601 offset datetime
 * @property {number=} pageNum
 * @property {number=} pageSize
 */

/** @param {AiTraceQuery} params */
export const getAiTraces = params => request.get('/api/admin/ai-traces', { params })

/** @param {string} traceId */
export const getAiTraceDetail = traceId =>
  request.get(`/api/admin/ai-traces/${encodeURIComponent(traceId)}`)
