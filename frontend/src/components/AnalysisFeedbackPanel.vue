<template>
  <div class="analysis-feedback-panel">
    <div class="feedback-header">
      <span class="feedback-title">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <polyline points="14 2 14 8 20 8"/>
          <line x1="16" y1="13" x2="8" y2="13"/>
          <line x1="16" y1="17" x2="8" y2="17"/>
        </svg>
        分析结果反馈
      </span>
      <span v-if="currentFeedback" class="feedback-status" :class="currentFeedback.feedbackType === 'ACCURATE' ? 'accurate' : 'inaccurate'">
        {{ currentFeedback.feedbackType === 'ACCURATE' ? '已标记为准确' : '已标记为不准确' }}
      </span>
    </div>

    <div class="feedback-body">
      <!-- 准确/不准确 按钮 -->
      <div class="feedback-type-row">
        <button
          :class="['feedback-btn', 'btn-accurate', { active: selectedType === 'ACCURATE' }]"
          @click="selectType('ACCURATE')"
          :disabled="submitting"
        >
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
            <polyline points="22 4 12 14.01 9 11.01"/>
          </svg>
          准确
        </button>
        <button
          :class="['feedback-btn', 'btn-inaccurate', { active: selectedType === 'INACCURATE' }]"
          @click="selectType('INACCURATE')"
          :disabled="submitting"
        >
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="15" y1="9" x2="9" y2="15"/>
            <line x1="9" y1="9" x2="15" y2="15"/>
          </svg>
          不准确
        </button>
      </div>

      <!-- 说明文本 -->
      <div class="feedback-content-row" v-if="selectedType">
        <textarea
          v-model="contentText"
          class="feedback-textarea"
          :placeholder="selectedType === 'ACCURATE' ? '可选：说明哪些分析结果准确...' : '请说明哪些地方不准确（选填）...'"
          rows="2"
          maxlength="2000"
          :disabled="submitting"
        ></textarea>
        <span class="char-count">{{ contentText.length }}/2000</span>
      </div>

      <!-- 操作按钮 -->
      <div class="feedback-actions" v-if="selectedType">
        <button
          class="submit-btn"
          @click="submitFeedback"
          :disabled="submitting || !selectedType"
        >
          <svg v-if="submitting" class="spinning" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"></circle>
            <path d="M12 6v6l4 2"></path>
          </svg>
          {{ submitting ? '提交中...' : (currentFeedback ? '更新反馈' : '提交反馈') }}
        </button>
        <button
          v-if="currentFeedback"
          class="cancel-btn"
          @click="resetForm"
          :disabled="submitting"
        >
          取消
        </button>
      </div>

      <!-- 成功消息 -->
      <div v-if="successMsg" class="feedback-success">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
          <polyline points="22 4 12 14.01 9 11.01"/>
        </svg>
        {{ successMsg }}
      </div>

      <!-- 错误消息 -->
      <div v-if="errorMsg" class="feedback-error">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/>
        </svg>
        {{ errorMsg }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { submitAnalysisFeedback, getMerchantAnalysisFeedback } from '../api/analysisFeedback'

const props = defineProps({
  merchantId: { type: Number, required: true },
  analysisType: { type: String, required: true },
  analysisId: { type: Number, default: null }
})

const selectedType = ref(null)
const contentText = ref('')
const submitting = ref(false)
const successMsg = ref('')
const errorMsg = ref('')
const currentFeedback = ref(null)

// 加载已有反馈
async function loadExistingFeedback() {
  try {
    const res = await getMerchantAnalysisFeedback(props.merchantId, {
      analysisType: props.analysisType,
      pageNum: 1,
      pageSize: 10
    })
    if (res.success && res.data?.records?.length > 0) {
      // 查找匹配 analysisId 的反馈
      const match = props.analysisId
        ? res.data.records.find(r => r.analysisId === props.analysisId)
        : res.data.records[0] // 对于无 analysisId 的类型整体反馈，取第一条

      if (match) {
        currentFeedback.value = match
        selectedType.value = match.feedbackType
        contentText.value = match.content || ''
      }
    }
  } catch (_) {
    // 静默失败，不影响展示
  }
}

function selectType(type) {
  if (submitting.value) return
  selectedType.value = type
  successMsg.value = ''
  errorMsg.value = ''
}

function resetForm() {
  if (currentFeedback.value) {
    selectedType.value = currentFeedback.value.feedbackType
    contentText.value = currentFeedback.value.content || ''
  } else {
    selectedType.value = null
    contentText.value = ''
  }
  successMsg.value = ''
  errorMsg.value = ''
}

async function submitFeedback() {
  if (!selectedType.value) return

  submitting.value = true
  successMsg.value = ''
  errorMsg.value = ''

  try {
    const res = await submitAnalysisFeedback(props.merchantId, {
      analysisType: props.analysisType,
      analysisId: props.analysisId,
      feedbackType: selectedType.value,
      content: contentText.value.trim() || null
    })

    if (res.success) {
      currentFeedback.value = res.data
      successMsg.value = currentFeedback.value
        ? '反馈已更新'
        : '反馈已提交'
      setTimeout(() => { successMsg.value = '' }, 3000)
    } else {
      errorMsg.value = res.message || '提交失败，请重试'
    }
  } catch (e) {
    errorMsg.value = e?.message || '网络错误，请重试'
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadExistingFeedback()
})

// 当 merchantId 或 analysisId 变化时重新加载
watch(() => [props.merchantId, props.analysisType, props.analysisId], () => {
  currentFeedback.value = null
  selectedType.value = null
  contentText.value = ''
  successMsg.value = ''
  errorMsg.value = ''
  loadExistingFeedback()
})
</script>

<style scoped>
.analysis-feedback-panel {
  margin-top: 20px;
  padding: 16px 20px;
  background: #fafbfc;
  border: 1px solid #e8ecf0;
  border-radius: 12px;
}

.feedback-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.feedback-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.feedback-status {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 12px;
  font-weight: 500;
}

.feedback-status.accurate {
  color: #52c41a;
  background: #f6ffed;
  border: 1px solid #b7eb8f;
}

.feedback-status.inaccurate {
  color: #ff4d4f;
  background: #fff2f0;
  border: 1px solid #ffccc7;
}

.feedback-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.feedback-type-row {
  display: flex;
  gap: 10px;
}

.feedback-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  border-radius: 8px;
  border: 1px solid #dcdfe6;
  background: #fff;
  font-size: 13px;
  font-weight: 500;
  color: #606266;
  cursor: pointer;
  transition: all 0.2s;
}

.feedback-btn:hover:not(:disabled) {
  border-color: #c0c4cc;
  background: #f5f7fa;
}

.feedback-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-accurate.active {
  border-color: #52c41a;
  background: #f6ffed;
  color: #52c41a;
  font-weight: 600;
}

.btn-inaccurate.active {
  border-color: #ff4d4f;
  background: #fff2f0;
  color: #ff4d4f;
  font-weight: 600;
}

.feedback-content-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.feedback-textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 10px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.6;
  resize: vertical;
  color: #303133;
  background: #fff;
  transition: border-color 0.2s;
}

.feedback-textarea:focus {
  outline: none;
  border-color: #409eff;
}

.feedback-textarea:disabled {
  background: #f5f7fa;
  color: #c0c4cc;
}

.char-count {
  align-self: flex-end;
  font-size: 11px;
  color: #c0c4cc;
}

.feedback-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.submit-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 18px;
  background: linear-gradient(135deg, #409eff 0%, #337ecc 100%);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.cancel-btn {
  padding: 7px 14px;
  background: #fff;
  color: #909399;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.cancel-btn:hover:not(:disabled) {
  color: #606266;
  border-color: #c0c4cc;
}

.cancel-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.feedback-success {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: #f0f9eb;
  color: #67c23a;
  border-radius: 6px;
  font-size: 13px;
}

.feedback-error {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: #fef0f0;
  color: #f56c6c;
  border-radius: 6px;
  font-size: 13px;
}

.spinning {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
