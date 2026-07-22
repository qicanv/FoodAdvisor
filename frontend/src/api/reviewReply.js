import request from './request'

// ========== 评价辅助回复（EPIC-02 故事7） ==========

/** 生成 AI 回复建议草稿 */
export const generateReplyDraft = (reviewId) => {
  return request.post(`/api/reviews/${reviewId}/reply-draft/generate`, null, {
    timeout: 60000,
  })
}

/** 编辑 AI 生成的草稿内容 */
export const editReplyDraft = (reviewId, editedContent) => {
  return request.put(`/api/reviews/${reviewId}/reply-draft`, { editedContent })
}

/** 发布草稿为正式回复 */
export const publishReplyDraft = (reviewId) => {
  return request.post(`/api/reviews/${reviewId}/reply-draft/publish`)
}

/** 获取当前草稿 */
export const getReplyDraft = (reviewId) => {
  return request.get(`/api/reviews/${reviewId}/reply-draft`)
}

/** 丢弃草稿 */
export const discardReplyDraft = (reviewId) => {
  return request.delete(`/api/reviews/${reviewId}/reply-draft`)
}
