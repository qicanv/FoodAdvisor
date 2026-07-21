import request from './request'

const BASE_URL = '/api/admin/recommendation-evaluations'

// 测试集
export const getEvaluationDatasets = (params) => {
  return request.get(`${BASE_URL}/datasets`, { params })
}

export const getEvaluationDataset = (datasetId) => {
  return request.get(`${BASE_URL}/datasets/${datasetId}`)
}

export const createEvaluationDataset = (data) => {
  return request.post(`${BASE_URL}/datasets`, data)
}

export const updateEvaluationDataset = (datasetId, data) => {
  return request.put(`${BASE_URL}/datasets/${datasetId}`, data)
}

// 测试案例
export const getEvaluationCases = (datasetId, params) => {
  return request.get(`${BASE_URL}/datasets/${datasetId}/cases`, { params })
}

export const getEvaluationCase = (datasetId, caseId) => {
  return request.get(
    `${BASE_URL}/datasets/${datasetId}/cases/${caseId}`
  )
}

export const createEvaluationCase = (datasetId, data) => {
  return request.post(
    `${BASE_URL}/datasets/${datasetId}/cases`,
    data
  )
}

export const updateEvaluationCase = (
  datasetId,
  caseId,
  data
) => {
  return request.put(
    `${BASE_URL}/datasets/${datasetId}/cases/${caseId}`,
    data
  )
}

export const deleteEvaluationCase = (
  datasetId,
  caseId
) => {
  return request.delete(
    `${BASE_URL}/datasets/${datasetId}/cases/${caseId}`
  )
}

// 评测运行
export const getEvaluationRuns = (datasetId, params) => {
  return request.get(
    `${BASE_URL}/datasets/${datasetId}/runs`,
    { params }
  )
}

export const executeEvaluationRun = (
  datasetId,
  data = {}
) => {
  return request.post(
    `${BASE_URL}/datasets/${datasetId}/runs`,
    data
  )
}

export const getEvaluationRun = (runId) => {
  return request.get(`${BASE_URL}/runs/${runId}`)
}

export const getEvaluationRunResults = (runId) => {
  return request.get(`${BASE_URL}/runs/${runId}/results`)
}

// 人工标注
export const annotateEvaluationResult = (
  runId,
  resultId,
  data
) => {
  return request.put(
    `${BASE_URL}/runs/${runId}/results/${resultId}/annotation`,
    data
  )
}

// 运行对比
export const compareEvaluationRuns = (
  baselineRunId,
  candidateRunId
) => {
  return request.get(`${BASE_URL}/runs/compare`, {
    params: {
      baselineRunId,
      candidateRunId,
    },
  })
}