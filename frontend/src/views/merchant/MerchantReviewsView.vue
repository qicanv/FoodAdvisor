<template>
  <MerchantLayout title="评价管理" subtitle="查看和管理所有店铺评价">
    <div class="reviews-container">
      <!-- Filters -->
      <div class="filter-bar">
        <div class="filter-left">
          <div class="filter-item">
            <label>店铺筛选</label>
            <select v-model="filterStoreId" class="filter-select" @change="onFilterChange">
              <option :value="null">全部店铺</option>
              <option v-for="store in myStores" :key="store.id" :value="store.id">
                {{ store.name }}
              </option>
            </select>
          </div>
          <div class="filter-item">
            <label>开始日期</label>
            <input type="date" v-model="filterStartDate" class="filter-date" @change="onFilterChange" />
          </div>
          <div class="filter-item">
            <label>结束日期</label>
            <input type="date" v-model="filterEndDate" class="filter-date" @change="onFilterChange" />
          </div>
        </div>
        <div class="filter-right">
          <button class="reset-btn" @click="resetFilters">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/><path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"/><path d="M16 21h5v-5"/>
            </svg>
            重置筛选
          </button>
        </div>
      </div>

      <!-- Reviews List -->
      <div class="reviews-content">
        <div v-if="loading" class="loading-state">
          <div class="spinner"></div>
          <p>加载中...</p>
        </div>

        <div v-else-if="filteredReviews.length > 0" class="review-list-full">
          <div v-for="review in paginatedReviews" :key="review.id" class="review-card">
            <div class="review-card-header">
              <div class="review-rating">
                <span v-for="i in 5" :key="i" class="star" :class="{ filled: i <= review.rating }">★</span>
              </div>
              <span class="review-time">{{ review.time }}</span>
            </div>
            <p class="review-card-content">{{ review.content }}</p>
            <div class="review-card-footer">
              <div class="review-meta-left">
                <span class="review-store">{{ review.merchantName }}</span>
                <span class="review-divider">·</span>
                <span class="review-user">{{ review.username }}</span>
              </div>
              <button class="btn-reply" @click="openReplyModal(review)">回复</button>
            </div>
          </div>

          <!-- Pagination -->
          <div v-if="totalPages > 1" class="pagination">
            <button
              class="page-btn"
              :disabled="currentPage <= 1"
              @click="currentPage--; scrollToTop()"
            >上一页</button>
            <span class="page-info">{{ currentPage }} / {{ totalPages }}</span>
            <button
              class="page-btn"
              :disabled="currentPage >= totalPages"
              @click="currentPage++; scrollToTop()"
            >下一页</button>
          </div>
        </div>

        <div v-else class="empty-state">
          <svg viewBox="0 0 24 24" width="64" height="64" fill="none" stroke="#ccc" stroke-width="2">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
            <polyline points="14 2 14 8 20 8"></polyline>
            <line x1="16" y1="13" x2="8" y2="13"></line>
            <line x1="16" y1="17" x2="8" y2="17"></line>
          </svg>
          <p>暂无评价数据</p>
          <span class="empty-hint">尝试调整筛选条件或添加新店铺</span>
        </div>
      </div>
    </div>

    <!-- Reply Modal -->
    <div v-if="replyModalVisible" class="modal-overlay" @click.self="closeReplyModal">
      <div class="modal-panel">
        <div class="modal-header">
          <h3>评价回复</h3>
          <button class="modal-close" @click="closeReplyModal">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="modal-body" v-if="currentReplyReview">
          <!-- Review Original -->
          <div class="detail-original">
            <div class="detail-label">📝 评价原文</div>
            <p>{{ currentReplyReview.content }}</p>
            <div class="detail-meta">
              <span>评分：{{ currentReplyReview.rating }}分</span>
              <span>时间：{{ currentReplyReview.time }}</span>
              <span>{{ currentReplyReview.merchantName }}</span>
            </div>
          </div>

          <!-- AI-Assisted Reply -->
          <div class="detail-reply-section">
            <div class="detail-label">💬 评价辅助回复</div>

            <!-- Error banner -->
            <div v-if="replyError" class="reply-error-banner">
              <span>{{ replyError }}</span>
              <button class="reply-error-close" @click="replyError = ''">✕</button>
            </div>

            <!-- Published -->
            <div v-if="replyDraft && replyDraft.status === 'PUBLISHED'" class="reply-card reply-published">
              <div class="reply-card-header">
                <span class="reply-status-badge published">✅ 已发布</span>
                <span class="reply-strategy-hint">{{ replyDraft.strategy === 'POSITIVE' ? '👍 好评策略' : '🔧 差评策略' }}</span>
              </div>
              <p class="reply-content-text">{{ getEffectiveContent(replyDraft) }}</p>
              <div class="reply-card-meta" v-if="replyDraft.publishedAt">
                发布时间：{{ new Date(replyDraft.publishedAt).toLocaleString('zh-CN') }}
              </div>
            </div>

            <!-- Editing mode -->
            <div v-else-if="replyEditing" class="reply-card reply-editing">
              <div class="reply-card-header">
                <span class="reply-edit-mode-label">编辑回复内容</span>
                <span class="reply-strategy-hint">{{ replyDraft?.strategy === 'POSITIVE' ? '👍 好评策略' : '🔧 差评策略' }}</span>
              </div>
              <textarea
                v-model="replyEditText"
                class="reply-edit-textarea"
                rows="6"
                maxlength="500"
                placeholder="编辑回复内容..."
              ></textarea>
              <div class="reply-edit-actions">
                <span class="reply-char-count">{{ replyEditText.length }}/500</span>
                <button class="btn-secondary" @click="handleCancelEdit" :disabled="replyLoading">取消</button>
                <button class="btn-primary-sm" @click="handleSaveEdit" :disabled="replyLoading">
                  {{ replyLoading ? '保存中...' : '保存修改' }}
                </button>
              </div>
            </div>

            <!-- Draft mode -->
            <div v-else-if="replyDraft && replyDraft.status === 'DRAFT'" class="reply-card reply-draft">
              <div class="reply-card-header">
                <span class="reply-status-badge draft">📝 草稿</span>
                <span class="reply-strategy-hint">{{ replyDraft.strategy === 'POSITIVE' ? '👍 好评策略' : '🔧 差评策略' }}</span>
              </div>
              <p class="reply-content-text">{{ getEffectiveContent(replyDraft) }}</p>
              <div class="reply-card-meta" v-if="replyDraft.generatedAt">
                生成时间：{{ new Date(replyDraft.generatedAt).toLocaleString('zh-CN') }}
              </div>
              <div class="reply-draft-actions">
                <button class="btn-danger-text" @click="handleDiscardDraft" :disabled="replyLoading">丢弃</button>
                <button class="btn-secondary" @click="handleStartEdit" :disabled="replyLoading">编辑</button>
                <button class="btn-publish" @click="handlePublishDraft" :disabled="replyPublishing">
                  {{ replyPublishing ? '发布中...' : '发布回复' }}
                </button>
              </div>
            </div>

            <!-- No draft: generate button -->
            <div v-else class="reply-card reply-generate">
              <p class="reply-generate-hint">根据评价内容自动生成回复建议。好评将表达感谢并回应具体优点，差评将道歉并提供改进承诺。</p>
              <button class="btn-generate-reply" @click="handleGenerateReply" :disabled="replyLoading">
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                  <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
                </svg>
                {{ replyLoading ? 'AI 正在生成回复建议...' : '生成回复建议' }}
              </button>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-secondary" @click="closeReplyModal">关闭</button>
        </div>
      </div>
    </div>
  </MerchantLayout>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import MerchantLayout from '../../components/MerchantLayout.vue'
import { getMerchantReviews } from '../../api/reviewAnalysis'
import { getMyMerchants } from '../../api/merchantConsole'
import {
  generateReplyDraft,
  editReplyDraft,
  publishReplyDraft,
  getReplyDraft,
  discardReplyDraft,
} from '../../api/reviewReply'

const myStores = ref([])
const allReviews = ref([])
const loading = ref(false)

// Filters
const filterStoreId = ref(null)
const filterStartDate = ref('')
const filterEndDate = ref('')

// Pagination
const currentPage = ref(1)
const pageSize = 10

const formatReviewTime = (dateStr) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

const loadAllReviews = async () => {
  loading.value = true
  try {
    // Load stores first
    const storeResp = await getMyMerchants()
    if (storeResp.success && storeResp.data) {
      myStores.value = storeResp.data
    }

    if (myStores.value.length === 0) {
      allReviews.value = []
      loading.value = false
      return
    }

    // Load reviews for all stores
    const reviewPromises = myStores.value.map(store =>
      getMerchantReviews(store.id, { pageNum: 1, pageSize: 200 })
    )
    const results = await Promise.all(reviewPromises)

    // Create store name map
    const storeNameMap = {}
    myStores.value.forEach(s => {
      storeNameMap[s.id] = s.name
    })

    // Merge and format reviews from all stores
    const merged = []
    results.forEach(resp => {
      if (resp.success && resp.data) {
        const records = resp.data.records || resp.data || []
        records.forEach(r => {
          merged.push({
            id: r.id,
            rating: Number(r.rating) || 0,
            content: r.content || '',
            username: r.nickname || r.username || '匿名用户',
            merchantId: r.merchantId,
            merchantName: storeNameMap[r.merchantId] || '未知店铺',
            time: formatReviewTime(r.publishedAt || r.createdAt),
            rawDate: r.publishedAt || r.createdAt
          })
        })
      }
    })

    // Sort by date descending
    merged.sort((a, b) => new Date(b.rawDate) - new Date(a.rawDate))
    allReviews.value = merged
  } catch (error) {
    console.error('加载评价失败:', error)
    allReviews.value = []
  } finally {
    loading.value = false
  }
}

// Filtered reviews
const filteredReviews = computed(() => {
  let result = allReviews.value

  // Filter by store
  if (filterStoreId.value) {
    result = result.filter(r => r.merchantId === filterStoreId.value)
  }

  // Filter by start date
  if (filterStartDate.value) {
    const startDate = new Date(filterStartDate.value)
    startDate.setHours(0, 0, 0, 0)
    result = result.filter(r => new Date(r.rawDate) >= startDate)
  }

  // Filter by end date
  if (filterEndDate.value) {
    const endDate = new Date(filterEndDate.value)
    endDate.setHours(23, 59, 59, 999)
    result = result.filter(r => new Date(r.rawDate) <= endDate)
  }

  return result
})

// Paginated reviews
const totalPages = computed(() => Math.max(1, Math.ceil(filteredReviews.value.length / pageSize)))

const paginatedReviews = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredReviews.value.slice(start, start + pageSize)
})

const onFilterChange = () => {
  currentPage.value = 1
}

const resetFilters = () => {
  filterStoreId.value = null
  filterStartDate.value = ''
  filterEndDate.value = ''
  currentPage.value = 1
}

const scrollToTop = () => {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

// ==================== Reply Modal ====================
const replyModalVisible = ref(false)
const currentReplyReview = ref(null)

// Reply state
const replyDraft = ref(null)
const replyLoading = ref(false)
const replyEditing = ref(false)
const replyEditText = ref('')
const replyPublishing = ref(false)
const replyError = ref('')

function getEffectiveContent(draft) {
  if (!draft) return ''
  return draft.editedContent || draft.generatedContent || ''
}

function openReplyModal(review) {
  currentReplyReview.value = review
  replyModalVisible.value = true
  fetchReplyDraft(review.id)
}

function closeReplyModal() {
  replyModalVisible.value = false
}

// Reset reply state when modal closes
watch(replyModalVisible, (val) => {
  if (!val) {
    replyDraft.value = null
    replyLoading.value = false
    replyEditing.value = false
    replyEditText.value = ''
    replyPublishing.value = false
    replyError.value = ''
  }
})

async function fetchReplyDraft(reviewId) {
  if (!reviewId) return
  replyDraft.value = null
  replyError.value = ''
  try {
    const res = await getReplyDraft(reviewId)
    if (res.success && res.data) {
      replyDraft.value = res.data
    }
  } catch (e) { /* no draft is normal */ }
}

async function handleGenerateReply() {
  const reviewId = currentReplyReview.value?.id
  if (!reviewId) return
  replyLoading.value = true
  replyError.value = ''
  try {
    const res = await generateReplyDraft(reviewId)
    if (res.success && res.data) {
      if (res.data.status === 'FAILED') {
        replyError.value = res.data.errorMessage || 'AI 回复生成失败，请稍后重试'
        replyDraft.value = null
      } else {
        replyDraft.value = res.data
      }
    } else {
      replyError.value = res.message || '生成回复失败'
    }
  } catch (e) {
    replyError.value = '网络请求失败，请检查服务状态后重试'
  } finally {
    replyLoading.value = false
  }
}

function handleStartEdit() {
  replyEditText.value = getEffectiveContent(replyDraft.value)
  replyEditing.value = true
  replyError.value = ''
}

function handleCancelEdit() {
  replyEditing.value = false
  replyEditText.value = ''
}

async function handleSaveEdit() {
  const reviewId = currentReplyReview.value?.id
  if (!reviewId || !replyEditText.value.trim()) return
  replyLoading.value = true
  try {
    const res = await editReplyDraft(reviewId, replyEditText.value.trim())
    if (res.success && res.data) {
      replyDraft.value = res.data
      replyEditing.value = false
      replyEditText.value = ''
    }
  } catch (e) {
    replyError.value = '保存失败，请重试'
  } finally {
    replyLoading.value = false
  }
}

async function handlePublishDraft() {
  const reviewId = currentReplyReview.value?.id
  if (!reviewId) return
  replyPublishing.value = true
  replyError.value = ''
  try {
    const res = await publishReplyDraft(reviewId)
    if (res.success && res.data) {
      replyDraft.value = { ...replyDraft.value, status: 'PUBLISHED' }
    } else {
      replyError.value = res.message || '发布失败'
    }
  } catch (e) {
    replyError.value = '发布请求失败'
  } finally {
    replyPublishing.value = false
  }
}

async function handleDiscardDraft() {
  const reviewId = currentReplyReview.value?.id
  if (!reviewId) return
  if (!confirm('确定要丢弃此回复草稿吗？丢弃后可重新生成。')) return
  replyLoading.value = true
  try {
    const res = await discardReplyDraft(reviewId)
    if (res.success) {
      replyDraft.value = null
      replyEditing.value = false
      replyEditText.value = ''
      replyError.value = ''
    }
  } catch (e) {
    replyError.value = '丢弃失败，请重试'
  } finally {
    replyLoading.value = false
  }
}

onMounted(() => {
  loadAllReviews()
})
</script>

<style scoped>
.reviews-container {
  width: 100%;
}

/* Filter Bar */
.filter-bar {
  background: #fff;
  border-radius: 12px;
  padding: 20px 24px;
  margin-bottom: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 16px;
}

.filter-left {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
  align-items: flex-end;
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.filter-item label {
  font-size: 13px;
  color: #667085;
  font-weight: 500;
}

.filter-select,
.filter-date {
  padding: 8px 12px;
  font-size: 14px;
  color: #1f2d3d;
  background: #f5f7fa;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  min-width: 160px;
}

.filter-select:focus,
.filter-date:focus {
  outline: none;
  border-color: #52c41a;
  background: #fff;
}

.filter-right {
  display: flex;
  align-items: flex-end;
}

.reset-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  font-size: 13px;
  color: #667085;
  background: #f5f7fa;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.reset-btn:hover {
  background: #eef2f7;
  border-color: #52c41a;
  color: #52c41a;
}

/* Reviews List */
.reviews-content {
  min-height: 400px;
}

.review-list-full {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.review-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  transition: transform 0.2s, box-shadow 0.2s;
}

.review-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.review-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.review-rating {
  display: flex;
  gap: 2px;
}

.star {
  font-size: 16px;
  color: #e0e0e0;
}

.star.filled {
  color: #faad14;
}

.review-time {
  font-size: 13px;
  color: #999;
}

.review-card-content {
  font-size: 15px;
  color: #1f2d3d;
  line-height: 1.6;
  margin: 0 0 12px;
}

.review-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
  color: #667085;
}

.review-meta-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-reply {
  padding: 6px 16px;
  font-size: 13px;
  color: #52c41a;
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.btn-reply:hover {
  background: #d9f7be;
  color: #389e0d;
}

.review-store {
  color: #52c41a;
  font-weight: 500;
}

.review-divider {
  color: #d9d9d9;
}

.review-user {
  color: #999;
}

/* Pagination */
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  padding: 24px 0;
}

.page-btn {
  padding: 8px 20px;
  font-size: 14px;
  color: #52c41a;
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.page-btn:hover:not(:disabled) {
  background: #d9f7be;
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-info {
  font-size: 14px;
  color: #667085;
  font-weight: 500;
}

/* Loading State */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 4px solid #f0f0f0;
  border-top-color: #52c41a;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.loading-state p {
  margin-top: 16px;
  font-size: 14px;
  color: #999;
}

/* Empty State */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
}

.empty-state svg {
  margin-bottom: 16px;
}

.empty-state p {
  font-size: 16px;
  color: #999;
  margin: 0 0 8px;
}

.empty-hint {
  font-size: 13px;
  color: #ccc;
}

/* ===== Reply Modal ===== */
.modal-overlay {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.5); display: flex; align-items: center;
  justify-content: center; z-index: 1000; padding: 32px;
}
.modal-panel {
  background: #fff; border-radius: 16px; width: 100%; max-width: 700px;
  max-height: 85vh; overflow-y: auto; box-shadow: 0 16px 48px rgba(0,0,0,0.15);
}
.modal-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 20px 28px; border-bottom: 1px solid #f0f0f0;
}
.modal-header h3 { font-size: 17px; font-weight: 600; color: #1f2d3d; margin: 0; }
.modal-close {
  width: 36px; height: 36px; display: flex; align-items: center; justify-content: center;
  border: none; background: #f5f5f5; border-radius: 50%; cursor: pointer;
  color: #999; transition: all 0.2s;
}
.modal-close:hover { background: #eee; color: #333; }
.modal-body { padding: 24px 28px; }
.modal-footer { padding: 16px 28px; border-top: 1px solid #f0f0f0; display: flex; justify-content: flex-end; }

.detail-original { margin-bottom: 20px; }
.detail-original p {
  font-size: 14px; color: #1f2d3d; line-height: 1.8; margin: 8px 0;
  padding: 12px 16px; background: #fafafa; border-radius: 8px; border-left: 3px solid #1890ff;
}
.detail-meta { display: flex; gap: 20px; font-size: 13px; color: #999; flex-wrap: wrap; }
.detail-label { font-size: 14px; font-weight: 600; color: #1f2d3d; margin-bottom: 8px; }

/* ===== Reply Section ===== */
.detail-reply-section {
  margin-bottom: 20px;
  padding: 16px;
  background: #fafafa;
  border-radius: 10px;
  border: 1px solid #f0f0f0;
}
.reply-error-banner {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 14px; background: #fff2f0; border: 1px solid #ffccc7;
  border-radius: 6px; margin-bottom: 12px; font-size: 13px; color: #cf1322;
}
.reply-error-close {
  padding: 2px 8px; background: none; border: none; cursor: pointer;
  font-size: 14px; color: #cf1322; opacity: 0.6;
}
.reply-error-close:hover { opacity: 1; }

.reply-card {
  padding: 14px 16px; border-radius: 8px; margin-top: 8px;
}
.reply-card.reply-published { background: #f6ffed; border: 1px solid #b7eb8f; border-left: 4px solid #52c41a; }
.reply-card.reply-draft { background: #fffbe6; border: 1px solid #ffe58f; }
.reply-card.reply-editing { background: #fff; border: 1px solid #d9d9d9; }
.reply-card.reply-generate { text-align: center; padding: 20px 16px; background: #fff; border: 1px dashed #d9d9d9; }

.reply-card-header { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.reply-status-badge {
  display: inline-block; padding: 2px 10px; border-radius: 4px;
  font-size: 12px; font-weight: 600;
}
.reply-status-badge.published { color: #52c41a; background: #f6ffed; }
.reply-status-badge.draft { color: #d48806; background: #fffbe6; }
.reply-strategy-hint { font-size: 12px; color: #999; }
.reply-edit-mode-label { font-weight: 600; font-size: 13px; color: #1f2d3d; }

.reply-content-text {
  font-size: 14px; color: #333; line-height: 1.7;
  white-space: pre-wrap; margin: 0 0 10px;
}
.reply-card-meta { font-size: 12px; color: #999; margin-top: 4px; }

.reply-draft-actions {
  display: flex; justify-content: flex-end; gap: 8px;
  margin-top: 14px; padding-top: 12px; border-top: 1px solid #f0f0f0;
}
.reply-edit-actions {
  display: flex; justify-content: flex-end; align-items: center; gap: 8px;
  margin-top: 12px;
}
.reply-char-count { font-size: 12px; color: #999; margin-right: auto; }

.reply-edit-textarea {
  width: 100%; padding: 10px 14px; border: 1px solid #d9d9d9;
  border-radius: 6px; font-size: 14px; line-height: 1.6;
  color: #1f2d3d; resize: vertical; font-family: inherit;
  box-sizing: border-box;
}
.reply-edit-textarea:focus { outline: none; border-color: #52c41a; box-shadow: 0 0 0 2px rgba(82,196,26,0.1); }

.reply-generate-hint {
  font-size: 13px; color: #999; margin: 0 0 16px; line-height: 1.6;
}

/* ===== Reply Buttons ===== */
.btn-primary-sm {
  padding: 8px 18px; font-size: 13px; font-weight: 500; color: #fff;
  background: linear-gradient(135deg, #52c41a, #73d13d); border: none;
  border-radius: 6px; cursor: pointer; transition: all 0.2s;
}
.btn-primary-sm:hover:not(:disabled) { opacity: 0.9; }
.btn-primary-sm:disabled { opacity: 0.6; cursor: not-allowed; }

.btn-danger-text {
  padding: 8px 16px; font-size: 13px; color: #ff4d4f; background: none;
  border: 1px solid #ffccc7; border-radius: 6px; cursor: pointer; transition: all 0.2s;
}
.btn-danger-text:hover:not(:disabled) { background: #fff2f0; }
.btn-danger-text:disabled { opacity: 0.5; cursor: not-allowed; }

.btn-publish {
  padding: 8px 18px; font-size: 13px; font-weight: 500; color: #fff;
  background: linear-gradient(135deg, #52c41a, #73d13d); border: none;
  border-radius: 6px; cursor: pointer; transition: all 0.2s;
}
.btn-publish:hover:not(:disabled) { opacity: 0.9; transform: translateY(-1px); }
.btn-publish:disabled { opacity: 0.6; cursor: not-allowed; }

.btn-generate-reply {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 10px 24px; font-size: 14px; font-weight: 500; color: #fff;
  background: linear-gradient(135deg, #1890ff, #40a9ff); border: none;
  border-radius: 8px; cursor: pointer; transition: all 0.2s;
}
.btn-generate-reply:hover:not(:disabled) { opacity: 0.9; transform: translateY(-1px); }
.btn-generate-reply:disabled { opacity: 0.6; cursor: not-allowed; }

.btn-secondary {
  padding: 8px 18px; font-size: 13px; color: #667085;
  background: #f5f7fa; border: 1px solid #d9d9d9; border-radius: 6px;
  cursor: pointer; transition: all 0.2s;
}
.btn-secondary:hover { background: #eef2f7; }

@media (max-width: 768px) {
  .filter-bar {
    flex-direction: column;
  }

  .filter-left {
    flex-direction: column;
    width: 100%;
  }

  .filter-select,
  .filter-date {
    width: 100%;
  }
}
</style>
