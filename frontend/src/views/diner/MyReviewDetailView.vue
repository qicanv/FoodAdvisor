<template>
  <div class="review-detail-view">
    <nav class="diner-nav">
      <div class="nav-container">
        <button
          type="button"
          class="brand-button"
          @click="goBack"
        >
          <span class="brand-logo-shell">
            <img
              src="../../assets/images/greedy-cat.png"
              alt="食尚参谋"
              class="logo-img"
            />
          </span>

          <span class="brand-copy">
            <strong>食尚参谋</strong>
            <span>评价详情</span>
          </span>
        </button>

        <div class="nav-links">
          <button
            type="button"
            class="back-btn"
            @click="goBack"
          >
            ← 返回我的评价
          </button>

          <button
            type="button"
            class="logout-btn"
            @click="handleLogout"
          >
            退出登录
          </button>
        </div>
      </div>
    </nav>

    <main class="detail-main">
      <div v-if="loading" class="page-state">
        <span class="loading-spinner"></span>
        <p>正在加载评价详情...</p>
      </div>

      <div v-else-if="review" class="container">
        <section class="detail-card">
          <header class="detail-header">
            <div class="merchant-section">
              <h1 class="merchant-name">
                {{ review.merchantName || '未知商家' }}
              </h1>

              <div
                v-if="review.merchantCategory || review.merchantCuisine"
                class="merchant-tags"
              >
                <span
                  v-if="review.merchantCategory"
                  class="tag"
                >
                  {{ review.merchantCategory }}
                </span>

                <span
                  v-if="review.merchantCuisine"
                  class="tag"
                >
                  {{ review.merchantCuisine }}
                </span>
              </div>
            </div>

            <span
              class="status-badge"
              :class="review.status"
            >
              {{ review.statusText || getReviewStatusText(review.status) }}
            </span>
          </header>

          <section class="rating-section">
            <div class="overall-rating">
              <span class="rating-value">
                {{ formatRating(review.rating) }}
              </span>

              <div class="rating-stars">
                <span
                  v-for="i in 5"
                  :key="i"
                  class="rating-star"
                  :class="{ filled: i <= Math.round(review.rating || 0) }"
                >
                  ★
                </span>
              </div>
            </div>

            <div
              v-if="hasSubRatings"
              class="sub-ratings"
            >
              <div class="sub-rating">
                <span class="sub-label">口味</span>
                <strong>{{ review.tasteRating || '-' }}</strong>
                <span class="sub-stars">
                  {{ getStars(review.tasteRating) }}
                </span>
              </div>

              <div class="sub-rating">
                <span class="sub-label">环境</span>
                <strong>{{ review.environmentRating || '-' }}</strong>
                <span class="sub-stars">
                  {{ getStars(review.environmentRating) }}
                </span>
              </div>

              <div class="sub-rating">
                <span class="sub-label">服务</span>
                <strong>{{ review.serviceRating || '-' }}</strong>
                <span class="sub-stars">
                  {{ getStars(review.serviceRating) }}
                </span>
              </div>
            </div>
          </section>

          <div class="info-grid">
            <div
              v-if="review.averageSpend !== null && review.averageSpend !== undefined"
              class="info-item"
            >
              <span class="info-label">人均消费</span>
              <strong>￥{{ review.averageSpend }}</strong>
            </div>

            <div
              v-if="review.consumptionDate"
              class="info-item"
            >
              <span class="info-label">消费日期</span>
              <strong>{{ formatDate(review.consumptionDate) }}</strong>
            </div>

            <div class="info-item">
              <span class="info-label">发布时间</span>
              <strong>{{ formatDateTime(review.createdAt) }}</strong>
            </div>

            <div
              v-if="review.editedAt"
              class="info-item"
            >
              <span class="info-label">最后编辑</span>
              <strong>{{ formatDateTime(review.editedAt) }}</strong>
            </div>
          </div>

          <section class="content-section">
            <h2 class="section-title">评价内容</h2>
            <p class="content-text">
              {{ review.content || '暂无评价内容' }}
            </p>
          </section>

          <section
            v-if="review.images && review.images.length > 0"
            class="images-section"
          >
            <h2 class="section-title">评价图片</h2>

            <div class="images-grid">
              <img
                v-for="img in review.images"
                :key="img.id || img.imageUrl"
                :src="img.imageUrl"
                class="review-image"
                :alt="`评价图片${img.id || ''}`"
              />
            </div>
          </section>

          <section
            v-if="review.merchantReply"
            class="reply-section"
          >
            <h2 class="section-title">商家回复</h2>

            <div class="reply-card">
              <div class="reply-header">
                <span class="reply-merchant">
                  {{ review.merchantReply.merchantName || review.merchantName }}
                </span>

                <span class="reply-time">
                  {{ formatDateTime(review.merchantReply.replyTime) }}
                </span>
              </div>

              <p class="reply-content">
                {{ review.merchantReply.replyContent }}
              </p>
            </div>
          </section>
        </section>

        <section class="follow-up-section">
          <div class="follow-up-heading">
            <div>
              <span class="follow-up-label">追加评价</span>
              <h2>再次到店后的真实体验</h2>
            </div>

            <button
              v-if="canAddFollowUp && !showFollowUpForm"
              type="button"
              class="add-follow-up-btn"
              @click="openCreateFollowUp"
            >
              ＋ 追加评价
            </button>
          </div>

          <div
            v-if="followUpLoading"
            class="follow-up-loading"
          >
            <span class="small-spinner"></span>
            <span>正在加载追评...</span>
          </div>

          <article
            v-else-if="followUp"
            class="follow-up-card"
          >
            <div class="follow-up-card-header">
              <div class="follow-up-title-row">
                <span class="follow-up-mark">追评</span>

                <span
                  class="follow-up-status"
                  :class="followUp.status"
                >
                  {{ getFollowUpStatusText(followUp.status) }}
                </span>
              </div>

              <div class="follow-up-actions">
                <button
                  type="button"
                  class="text-action"
                  @click="openEditFollowUp"
                >
                  编辑
                </button>

                <button
                  type="button"
                  class="text-action danger-text"
                  :disabled="followUpDeleting"
                  @click="deleteFollowUp"
                >
                  {{ followUpDeleting ? '删除中...' : '删除' }}
                </button>
              </div>
            </div>

            <div
              v-if="followUp.rating"
              class="follow-up-rating"
            >
              <span
                v-for="i in 5"
                :key="i"
                class="rating-star compact"
                :class="{ filled: i <= Math.round(followUp.rating || 0) }"
              >
                ★
              </span>

              <strong>{{ formatRating(followUp.rating) }}</strong>
            </div>

            <p class="follow-up-content">
              {{ followUp.content }}
            </p>

            <div class="follow-up-meta">
              <span>
                消费日期：{{ formatDate(followUp.consumptionDate) }}
              </span>

              <span
                v-if="followUp.averageSpend !== null &&
                  followUp.averageSpend !== undefined"
              >
                人均：￥{{ followUp.averageSpend }}
              </span>

              <span>
                发布于：{{ formatDateTime(
                  followUp.publishedAt || followUp.createdAt
                ) }}
              </span>

              <span
                v-if="followUp.currentVersion && followUp.currentVersion > 1"
              >
                已编辑 {{ followUp.currentVersion - 1 }} 次
              </span>
            </div>
          </article>

          <div
            v-else-if="!showFollowUpForm"
            class="follow-up-empty"
          >
            <template v-if="canAddFollowUp">
              <span class="empty-follow-up-icon">📝</span>
              <div>
                <strong>还没有追加评价</strong>
                <p>再次到店后，可以补充新的消费体验。</p>
              </div>
            </template>

            <template v-else>
              <strong>{{ followUpUnavailableText }}</strong>
            </template>
          </div>

          <form
            v-if="showFollowUpForm"
            class="follow-up-form"
            @submit.prevent="submitFollowUp"
          >
            <div class="form-header">
              <h3>
                {{ followUpFormMode === 'edit' ? '编辑追评' : '追加评价' }}
              </h3>

              <button
                type="button"
                class="close-form-btn"
                @click="closeFollowUpForm"
              >
                ×
              </button>
            </div>

            <div class="form-row two-columns">
              <label class="form-field">
                <span>消费日期 <em>*</em></span>
                <input
                  v-model="followUpForm.consumptionDate"
                  type="date"
                  :max="today"
                  required
                />
              </label>

              <label class="form-field">
                <span>本次评分</span>
                <select v-model="followUpForm.rating">
                  <option value="">不评分</option>
                  <option value="1">1 分</option>
                  <option value="2">2 分</option>
                  <option value="3">3 分</option>
                  <option value="4">4 分</option>
                  <option value="5">5 分</option>
                </select>
              </label>
            </div>

            <label class="form-field">
              <span>人均消费</span>

              <div class="money-input">
                <span>￥</span>
                <input
                  v-model="followUpForm.averageSpend"
                  type="number"
                  min="0"
                  step="0.01"
                  placeholder="选填"
                />
              </div>
            </label>

            <label class="form-field">
              <span>追评内容 <em>*</em></span>

              <textarea
                v-model="followUpForm.content"
                rows="6"
                maxlength="2000"
                placeholder="写下再次到店后的真实体验，至少 10 个字"
                required
              ></textarea>

              <span
                class="character-count"
                :class="{ invalid: followUpContentLength > 0 &&
                  followUpContentLength < 10 }"
              >
                {{ followUpContentLength }} / 2000
              </span>
            </label>

            <p
              v-if="followUpFormError"
              class="form-error"
            >
              {{ followUpFormError }}
            </p>

            <div class="form-actions">
              <button
                type="button"
                class="cancel-btn"
                :disabled="followUpSubmitting"
                @click="closeFollowUpForm"
              >
                取消
              </button>

              <button
                type="submit"
                class="submit-btn"
                :disabled="followUpSubmitting"
              >
                {{
                  followUpSubmitting
                    ? '提交中...'
                    : followUpFormMode === 'edit'
                      ? '保存修改'
                      : '提交追评'
                }}
              </button>
            </div>
          </form>
        </section>

        <section
          v-if="review.versionHistory && review.versionHistory.length > 0"
          class="history-section"
        >
          <h2 class="section-title">编辑历史</h2>

          <div class="history-list">
            <article
              v-for="ver in review.versionHistory"
              :key="ver.version"
              class="history-item"
            >
              <div class="history-header">
                <span class="version-label">
                  版本 {{ ver.version }}
                </span>

                <span
                  class="change-type"
                  :class="ver.changeType"
                >
                  {{ getChangeTypeText(ver.changeType) }}
                </span>

                <span class="history-time">
                  {{ formatDateTime(ver.createdAt) }}
                </span>
              </div>

              <div class="history-content">
                <div
                  v-if="ver.rating"
                  class="history-rating"
                >
                  评分：{{ ver.rating }} 分
                </div>

                <p class="history-text">
                  {{ ver.content }}
                </p>
              </div>
            </article>
          </div>
        </section>

        <section
          v-if="review.reviewType === 'ORIGINAL'"
          class="action-section"
        >
          <template v-if="review.status !== 'DELETED'">
            <button
              type="button"
              class="action-btn primary"
              @click="goToEdit"
            >
              ✏️ 编辑原评价
            </button>

            <button
              type="button"
              class="action-btn danger"
              :disabled="reviewDeleting"
              @click="handleDelete"
            >
              {{ reviewDeleting ? '删除中...' : '🗑️ 删除原评价' }}
            </button>
          </template>

          <div
            v-else
            class="deleted-hint"
          >
            已删除的评价仅可查看，无法编辑或追加评价
          </div>
        </section>
      </div>

      <div v-else class="page-state">
        <p>未找到评价详情</p>

        <button
          type="button"
          class="return-btn"
          @click="goBack"
        >
          返回我的评价
        </button>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '../../api/request'

const router = useRouter()
const route = useRoute()

const review = ref(null)
const followUp = ref(null)

const loading = ref(true)
const followUpLoading = ref(false)
const followUpSubmitting = ref(false)
const followUpDeleting = ref(false)
const reviewDeleting = ref(false)

const showFollowUpForm = ref(false)
const followUpFormMode = ref('create')
const followUpFormError = ref('')

const followUpForm = reactive({
  content: '',
  rating: '',
  consumptionDate: '',
  averageSpend: ''
})

const getLocalDateString = date => {
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0')
  ].join('-')
}

const today = getLocalDateString(new Date())

const hasSubRatings = computed(() => {
  return Boolean(
    review.value &&
    (
      review.value.tasteRating ||
      review.value.environmentRating ||
      review.value.serviceRating
    )
  )
})

const canAddFollowUp = computed(() => {
  return Boolean(
    review.value &&
    review.value.reviewType === 'ORIGINAL' &&
    review.value.status === 'PUBLISHED' &&
    !followUp.value
  )
})

const followUpContentLength = computed(() => {
  return followUpForm.content.length
})

const followUpUnavailableText = computed(() => {
  if (!review.value) return ''

  if (review.value.reviewType === 'FOLLOW_UP') {
    return '追评记录不能再次追加评价'
  }

  if (review.value.status === 'DELETED') {
    return '原评价已删除，无法追加评价'
  }

  if (review.value.status === 'PENDING') {
    return '原评价审核通过并发布后，才能追加评价'
  }

  if (review.value.status === 'HIDDEN') {
    return '当前评价已隐藏，暂时无法追加评价'
  }

  return '当前评价暂时无法追加评价'
})

const getStars = rating => {
  if (!rating) return ''

  const stars = Math.max(0, Math.min(5, Math.round(Number(rating))))
  return '★'.repeat(stars) + '☆'.repeat(5 - stars)
}

const formatRating = rating => {
  const value = Number(rating)

  if (Number.isNaN(value)) {
    return '-'
  }

  return value.toFixed(1)
}

const getReviewStatusText = status => {
  const statusMap = {
    PUBLISHED: '正常',
    DELETED: '已删除',
    PENDING: '待审核',
    HIDDEN: '已隐藏'
  }

  return statusMap[status] || '未知状态'
}

const getFollowUpStatusText = status => {
  const statusMap = {
    PUBLISHED: '已发布',
    PENDING: '待处理',
    HIDDEN: '已隐藏',
    DELETED: '已删除'
  }

  return statusMap[status] || '未知状态'
}

const getChangeTypeText = type => {
  const typeMap = {
    CREATE: '创建',
    EDIT: '编辑',
    DELETE: '删除'
  }

  return typeMap[type] || '修改'
}

const getResponseError = (response, fallback) => {
  return response?.message || fallback
}

const handleAuthOrPermissionError = response => {
  if (response?.code === 401) {
    router.push('/diner')
    return true
  }

  return false
}

const loadReview = async () => {
  const reviewId = route.params.id

  if (!reviewId) {
    loading.value = false
    return
  }

  loading.value = true

  try {
    const response = await request.get(
      `/api/reviews/my-reviews/${reviewId}`
    )

    if (response.success && response.data) {
      review.value = response.data
      followUp.value = response.data.followUp || null
      return
    }

    if (handleAuthOrPermissionError(response)) {
      return
    }

    if (response.code === 404) {
      alert('评价不存在')
      goBack()
      return
    }

    if (response.code === 403) {
      alert('无权查看此评价')
      goBack()
      return
    }

    alert(getResponseError(response, '获取评价详情失败'))
    goBack()
  } catch (error) {
    console.error('获取评价详情失败:', error)
    alert(error.message || '获取评价详情失败')
  } finally {
    loading.value = false
  }
}

const loadFollowUp = async () => {
  const reviewId = route.params.id

  if (!reviewId) return

  followUpLoading.value = true

  try {
    const response = await request.get(
      `/api/reviews/${reviewId}/follow-up`
    )

    if (response.success) {
      followUp.value = response.data || null
      return
    }

    if (!handleAuthOrPermissionError(response)) {
      console.error(
        '获取追评失败:',
        getResponseError(response, '获取追评失败')
      )
    }

    followUp.value = null
  } catch (error) {
    console.error('获取追评失败:', error)
    followUp.value = null
  } finally {
    followUpLoading.value = false
  }
}

const resetFollowUpForm = () => {
  followUpForm.content = ''
  followUpForm.rating = ''
  followUpForm.consumptionDate = ''
  followUpForm.averageSpend = ''
  followUpFormError.value = ''
}

const openCreateFollowUp = () => {
  resetFollowUpForm()
  followUpFormMode.value = 'create'
  followUpForm.consumptionDate = today
  showFollowUpForm.value = true
}

const openEditFollowUp = () => {
  if (!followUp.value) return

  followUpFormMode.value = 'edit'
  followUpForm.content = followUp.value.content || ''
  followUpForm.rating = followUp.value.rating
    ? String(Math.round(Number(followUp.value.rating)))
    : ''
  followUpForm.consumptionDate =
    followUp.value.consumptionDate || ''
  followUpForm.averageSpend =
    followUp.value.averageSpend !== null &&
    followUp.value.averageSpend !== undefined
      ? String(followUp.value.averageSpend)
      : ''
  followUpFormError.value = ''
  showFollowUpForm.value = true
}

const closeFollowUpForm = () => {
  if (followUpSubmitting.value) return

  showFollowUpForm.value = false
  resetFollowUpForm()
}

const validateFollowUpForm = () => {
  const content = followUpForm.content.trim()

  if (content.length < 10) {
    return '追评内容不能少于 10 个字符'
  }

  if (content.length > 2000) {
    return '追评内容不能超过 2000 个字符'
  }

  if (!followUpForm.consumptionDate) {
    return '请选择本次消费日期'
  }

  if (followUpForm.consumptionDate > today) {
    return '消费日期不能晚于今天'
  }

  if (followUpForm.rating !== '') {
    const rating = Number(followUpForm.rating)

    if (
      !Number.isInteger(rating) ||
      rating < 1 ||
      rating > 5
    ) {
      return '评分必须是 1 到 5 之间的整数'
    }
  }

  if (followUpForm.averageSpend !== '') {
    const averageSpend = Number(followUpForm.averageSpend)

    if (
      Number.isNaN(averageSpend) ||
      averageSpend < 0
    ) {
      return '人均消费不能小于 0'
    }
  }

  return ''
}

const buildFollowUpPayload = () => {
  return {
    content: followUpForm.content.trim(),
    rating:
      followUpForm.rating === ''
        ? null
        : Number(followUpForm.rating),
    consumptionDate: followUpForm.consumptionDate,
    averageSpend:
      followUpForm.averageSpend === ''
        ? null
        : Number(followUpForm.averageSpend)
  }
}

const submitFollowUp = async () => {
  const validationError = validateFollowUpForm()

  if (validationError) {
    followUpFormError.value = validationError
    return
  }

  followUpSubmitting.value = true
  followUpFormError.value = ''

  try {
    const payload = buildFollowUpPayload()
    let response

    if (followUpFormMode.value === 'edit') {
      if (!followUp.value?.id) {
        followUpFormError.value = '追评信息缺失，请刷新后重试'
        return
      }

      response = await request.put(
        `/api/reviews/${followUp.value.id}/follow-up`,
        payload
      )
    } else {
      response = await request.post(
        `/api/reviews/${route.params.id}/follow-up`,
        payload
      )
    }

    if (!response.success) {
      if (handleAuthOrPermissionError(response)) {
        return
      }

      followUpFormError.value = getResponseError(
        response,
        followUpFormMode.value === 'edit'
          ? '保存追评失败'
          : '提交追评失败'
      )
      return
    }

    alert(
      followUpFormMode.value === 'edit'
        ? '追评已更新'
        : '追评提交成功'
    )

    showFollowUpForm.value = false
    resetFollowUpForm()
    await loadFollowUp()
  } catch (error) {
    console.error('保存追评失败:', error)
    followUpFormError.value =
      error.message || '保存追评失败，请稍后重试'
  } finally {
    followUpSubmitting.value = false
  }
}

const deleteFollowUp = async () => {
  if (!followUp.value?.id || followUpDeleting.value) return

  if (!confirm('确定要删除这条追评吗？原评价不会受到影响。')) {
    return
  }

  followUpDeleting.value = true

  try {
    const response = await request.delete(
      `/api/reviews/${followUp.value.id}/follow-up`
    )

    if (!response.success) {
      if (handleAuthOrPermissionError(response)) {
        return
      }

      alert(getResponseError(response, '删除追评失败'))
      return
    }

    alert('追评已删除')
    followUp.value = null
    showFollowUpForm.value = false
    resetFollowUpForm()
  } catch (error) {
    console.error('删除追评失败:', error)
    alert(error.message || '删除追评失败，请稍后重试')
  } finally {
    followUpDeleting.value = false
  }
}

const goBack = () => {
  router.push('/diner/my-reviews')
}

const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('accessToken')
  localStorage.removeItem('user')
  localStorage.removeItem('userInfo')
  localStorage.removeItem('userRole')
  localStorage.removeItem('userId')
  router.push('/diner')
}

const goToEdit = () => {
  router.push(`/diner/review/edit/${route.params.id}`)
}

const handleDelete = async () => {
  if (reviewDeleting.value) return

  if (!confirm('确定要删除这条原评价吗？删除后将无法恢复。')) {
    return
  }

  reviewDeleting.value = true

  try {
    const response = await request.delete(
      `/api/reviews/${route.params.id}`
    )

    if (!response.success) {
      if (handleAuthOrPermissionError(response)) {
        return
      }

      alert(getResponseError(response, '删除评价失败'))
      return
    }

    alert('评价已删除')
    goBack()
  } catch (error) {
    console.error('删除评价失败:', error)
    alert(error.message || '删除失败，请重试')
  } finally {
    reviewDeleting.value = false
  }
}

const formatDate = dateStr => {
  if (!dateStr) return '-'

  const rawValue = String(dateStr)

  if (/^\d{4}-\d{2}-\d{2}$/.test(rawValue)) {
    return rawValue
  }

  const date = new Date(dateStr)

  if (Number.isNaN(date.getTime())) {
    return rawValue
  }

  return getLocalDateString(date)
}

const formatDateTime = dateStr => {
  if (!dateStr) return '-'

  const date = new Date(dateStr)

  if (Number.isNaN(date.getTime())) {
    return String(dateStr)
  }

  return [
    formatDate(dateStr),
    [
      String(date.getHours()).padStart(2, '0'),
      String(date.getMinutes()).padStart(2, '0')
    ].join(':')
  ].join(' ')
}

onMounted(() => {
  loadReview()
})
</script>

<style scoped>
.review-detail-view,
.review-detail-view *,
.review-detail-view *::before,
.review-detail-view *::after {
  box-sizing: border-box;
}

.review-detail-view {
  width: 100%;
  min-height: 100vh;
  color: #302a25;
  background:
    radial-gradient(
      circle at 8% 3%,
      rgba(255, 228, 204, 0.5),
      transparent 25%
    ),
    radial-gradient(
      circle at 92% 5%,
      rgba(254, 240, 214, 0.58),
      transparent 24%
    ),
    #f8f6f2;
}

.review-detail-view button,
.review-detail-view input,
.review-detail-view select,
.review-detail-view textarea {
  font-family: inherit;
}

.diner-nav {
  position: sticky;
  top: 0;
  z-index: 100;
  border-bottom: 1px solid rgba(229, 222, 212, 0.86);
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(16px);
}

.nav-container,
.container {
  width: calc(100% - 48px);
  max-width: 920px;
  min-width: 0;
  margin: 0 auto;
}

.nav-container {
  display: flex;
  min-height: 70px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.brand-button {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
  padding: 0;
  border: 0;
  color: inherit;
  text-align: left;
  background: transparent;
  cursor: pointer;
}

.brand-logo-shell {
  display: grid;
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  place-items: center;
  overflow: hidden;
  border: 1px solid #f0ddce;
  border-radius: 13px;
  background: #fff8f1;
}

.logo-img {
  width: 37px;
  height: 37px;
  object-fit: cover;
}

.brand-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 1px;
}

.brand-copy strong {
  color: #2e2925;
  font-size: 17px;
  font-weight: 750;
}

.brand-copy span {
  color: #978c82;
  font-size: 12px;
}

.nav-links {
  display: flex;
  gap: 10px;
}

.back-btn,
.logout-btn {
  min-height: 39px;
  padding: 0 13px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.back-btn {
  border: 1px solid #fed7aa;
  color: #c2410c;
  background: #fff8f1;
}

.back-btn:hover {
  border-color: #fb923c;
  background: #fff1e6;
}

.logout-btn {
  border: 1px solid #fecaca;
  color: #dc2626;
  background: #fff;
}

.logout-btn:hover {
  background: #fef2f2;
}

.detail-main {
  padding: 24px 0 48px;
}

.detail-card,
.follow-up-section,
.history-section,
.action-section {
  border: 1px solid #e9e2db;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 8px 24px rgba(80, 61, 43, 0.05);
}

.detail-card {
  padding: 26px;
}

.detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding-bottom: 20px;
  border-bottom: 1px solid #eee8e1;
}

.merchant-section {
  min-width: 0;
}

.merchant-name {
  margin: 0;
  overflow: hidden;
  color: #2f2924;
  font-size: 28px;
  font-weight: 800;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.merchant-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 10px;
}

.tag {
  padding: 4px 9px;
  border: 1px solid #eadfd6;
  border-radius: 999px;
  color: #776d65;
  font-size: 12px;
  background: #faf8f5;
}

.status-badge,
.follow-up-status {
  flex: 0 0 auto;
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.status-badge.PUBLISHED,
.follow-up-status.PUBLISHED {
  color: #15803d;
  background: #f0fdf4;
}

.status-badge.DELETED,
.follow-up-status.DELETED {
  color: #dc2626;
  background: #fef2f2;
}

.status-badge.PENDING,
.follow-up-status.PENDING {
  color: #b45309;
  background: #fffbeb;
}

.status-badge.HIDDEN,
.follow-up-status.HIDDEN {
  color: #6b7280;
  background: #f3f4f6;
}

.rating-section {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 34px;
  margin-top: 20px;
  padding: 20px;
  border: 1px solid #f0e6dd;
  border-radius: 16px;
  background: #fffaf5;
}

.overall-rating {
  display: flex;
  min-width: 112px;
  flex: 0 0 auto;
  align-items: center;
  flex-direction: column;
}

.rating-value {
  color: #ea580c;
  font-size: 42px;
  font-weight: 800;
  line-height: 1.15;
}

.rating-stars {
  display: flex;
  gap: 2px;
  margin-top: 5px;
}

.rating-star {
  color: #ddd6cf;
  font-size: 20px;
  line-height: 1;
}

.rating-star.filled {
  color: #f59e0b;
}

.rating-star.compact {
  font-size: 16px;
}

.sub-ratings {
  display: grid;
  min-width: 0;
  flex: 1 1 auto;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.sub-rating {
  display: flex;
  min-width: 0;
  align-items: center;
  flex-direction: column;
  gap: 4px;
  padding: 12px 8px;
  border-radius: 12px;
  background: #fff;
}

.sub-label {
  color: #958a81;
  font-size: 12px;
}

.sub-rating strong {
  color: #423a34;
  font-size: 18px;
}

.sub-stars {
  color: #f59e0b;
  font-size: 11px;
  letter-spacing: -1px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-top: 18px;
}

.info-item {
  display: flex;
  min-width: 0;
  min-height: 66px;
  justify-content: center;
  flex-direction: column;
  gap: 5px;
  padding: 11px 12px;
  border: 1px solid #eee8e1;
  border-radius: 11px;
  background: #faf8f5;
}

.info-label {
  color: #988d84;
  font-size: 12px;
}

.info-item strong {
  color: #3d3630;
  font-size: 13px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.content-section,
.images-section,
.reply-section {
  margin-top: 24px;
}

.section-title {
  margin: 0 0 12px;
  color: #342e29;
  font-size: 18px;
  line-height: 1.4;
}

.content-text,
.reply-content,
.follow-up-content,
.history-text {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.content-text {
  margin: 0;
  color: #514942;
  font-size: 15px;
  line-height: 1.8;
}

.images-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
  gap: 11px;
}

.review-image {
  width: 100%;
  height: 130px;
  object-fit: cover;
  border-radius: 12px;
}

.reply-card {
  padding: 18px;
  border-left: 4px solid #60a5fa;
  border-radius: 13px;
  background: #eff6ff;
}

.reply-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 9px;
}

.reply-merchant {
  color: #1d4ed8;
  font-size: 14px;
  font-weight: 700;
}

.reply-time {
  color: #8591a4;
  font-size: 12px;
}

.reply-content {
  margin: 0;
  color: #445064;
  font-size: 14px;
  line-height: 1.7;
}

.follow-up-section,
.history-section,
.action-section {
  margin-top: 18px;
  padding: 22px;
}

.follow-up-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid #eee8e1;
}

.follow-up-label {
  color: #ea580c;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.follow-up-heading h2 {
  margin: 4px 0 0;
  color: #342e29;
  font-size: 20px;
  line-height: 1.4;
}

.add-follow-up-btn {
  min-height: 39px;
  padding: 0 14px;
  border: 0;
  border-radius: 10px;
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  background: #ea580c;
  cursor: pointer;
  box-shadow: 0 8px 18px rgba(234, 88, 12, 0.18);
}

.add-follow-up-btn:hover {
  background: #c2410c;
}

.follow-up-loading,
.follow-up-empty {
  display: flex;
  min-height: 106px;
  align-items: center;
  justify-content: center;
  gap: 13px;
  margin-top: 15px;
  padding: 18px;
  border: 1px dashed #e5d9ce;
  border-radius: 14px;
  color: #847970;
  background: #fcfaf7;
}

.follow-up-empty {
  justify-content: flex-start;
}

.empty-follow-up-icon {
  font-size: 27px;
}

.follow-up-empty strong {
  color: #4b433c;
  font-size: 14px;
}

.follow-up-empty p {
  margin: 4px 0 0;
  color: #958a81;
  font-size: 13px;
}

.small-spinner,
.loading-spinner {
  border: 4px solid #f1e7de;
  border-top-color: #ea580c;
  border-radius: 50%;
  animation: spin 0.85s linear infinite;
}

.small-spinner {
  width: 25px;
  height: 25px;
  border-width: 3px;
}

.loading-spinner {
  width: 40px;
  height: 40px;
}

.follow-up-card {
  margin-top: 15px;
  padding: 18px;
  border: 1px solid #f2d9c3;
  border-radius: 15px;
  background: linear-gradient(135deg, #fffaf4, #fff5eb);
}

.follow-up-card-header,
.follow-up-title-row,
.follow-up-actions,
.follow-up-rating {
  display: flex;
  align-items: center;
}

.follow-up-card-header {
  justify-content: space-between;
  gap: 15px;
}

.follow-up-title-row {
  gap: 8px;
}

.follow-up-mark {
  padding: 5px 10px;
  border-radius: 999px;
  color: #fff;
  font-size: 12px;
  font-weight: 800;
  background: #ea580c;
}

.follow-up-actions {
  gap: 5px;
}

.text-action {
  padding: 5px 8px;
  border: 0;
  color: #c2410c;
  font-size: 13px;
  font-weight: 700;
  background: transparent;
  cursor: pointer;
}

.text-action:hover {
  text-decoration: underline;
}

.danger-text {
  color: #dc2626;
}

.text-action:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.follow-up-rating {
  gap: 7px;
  margin-top: 14px;
}

.follow-up-rating strong {
  color: #7c7168;
  font-size: 13px;
}

.follow-up-content {
  margin: 13px 0 0;
  color: #514942;
  font-size: 14px;
  line-height: 1.75;
}

.follow-up-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 7px 15px;
  margin-top: 14px;
  color: #91867d;
  font-size: 12px;
}

.follow-up-form {
  margin-top: 15px;
  padding: 18px;
  border: 1px solid #f0d8c3;
  border-radius: 15px;
  background: #fffaf5;
}

.form-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 15px;
  margin-bottom: 16px;
}

.form-header h3 {
  margin: 0;
  color: #3a332d;
  font-size: 18px;
}

.close-form-btn {
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 9px;
  color: #7e736a;
  font-size: 22px;
  background: #f3ede7;
  cursor: pointer;
}

.form-row.two-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 13px;
}

.form-field {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 7px;
  margin-top: 13px;
}

.form-field > span:first-child {
  color: #5f564f;
  font-size: 13px;
  font-weight: 700;
}

.form-field em {
  color: #dc2626;
  font-style: normal;
}

.form-field input,
.form-field select,
.form-field textarea {
  width: 100%;
  border: 1px solid #ddd5ce;
  border-radius: 10px;
  color: #3c3530;
  font-size: 14px;
  background: #fff;
  outline: none;
}

.form-field input,
.form-field select {
  height: 41px;
  padding: 0 11px;
}

.form-field textarea {
  min-height: 125px;
  padding: 11px 12px;
  line-height: 1.65;
  resize: vertical;
}

.form-field input:focus,
.form-field select:focus,
.form-field textarea:focus {
  border-color: #fb923c;
  box-shadow: 0 0 0 3px rgba(251, 146, 60, 0.12);
}

.money-input {
  display: flex;
  align-items: center;
  overflow: hidden;
  border: 1px solid #ddd5ce;
  border-radius: 10px;
  background: #fff;
}

.money-input:focus-within {
  border-color: #fb923c;
  box-shadow: 0 0 0 3px rgba(251, 146, 60, 0.12);
}

.money-input > span {
  padding-left: 11px;
  color: #8a7f75;
  font-size: 14px;
}

.money-input input {
  border: 0;
  box-shadow: none;
}

.money-input input:focus {
  border: 0;
  box-shadow: none;
}

.character-count {
  align-self: flex-end;
  color: #9b9087;
  font-size: 12px;
}

.character-count.invalid {
  color: #dc2626;
}

.form-error {
  margin: 12px 0 0;
  padding: 9px 11px;
  border-radius: 9px;
  color: #b91c1c;
  font-size: 13px;
  background: #fef2f2;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 9px;
  margin-top: 17px;
}

.cancel-btn,
.submit-btn {
  min-height: 40px;
  padding: 0 16px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}

.cancel-btn {
  border: 1px solid #ddd5ce;
  color: #6f665e;
  background: #fff;
}

.submit-btn {
  border: 0;
  color: #fff;
  background: #ea580c;
}

.submit-btn:disabled,
.cancel-btn:disabled {
  opacity: 0.58;
  cursor: not-allowed;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 11px;
}

.history-item {
  padding: 15px;
  border: 1px solid #eee8e1;
  border-left: 4px solid #fb923c;
  border-radius: 11px;
  background: #faf8f5;
}

.history-header {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 8px;
}

.version-label {
  color: #3d3630;
  font-size: 13px;
  font-weight: 700;
}

.change-type {
  padding: 3px 7px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
}

.change-type.CREATE {
  color: #15803d;
  background: #dcfce7;
}

.change-type.EDIT {
  color: #1d4ed8;
  background: #dbeafe;
}

.change-type.DELETE {
  color: #dc2626;
  background: #fee2e2;
}

.history-time {
  margin-left: auto;
  color: #968b82;
  font-size: 12px;
}

.history-rating {
  color: #c2410c;
  font-size: 13px;
  font-weight: 700;
}

.history-text {
  margin: 5px 0 0;
  color: #6f665e;
  font-size: 13px;
  line-height: 1.65;
}

.action-section {
  display: flex;
  gap: 12px;
}

.action-btn {
  min-height: 45px;
  flex: 1 1 0;
  border-radius: 11px;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}

.action-btn.primary {
  border: 0;
  color: #fff;
  background: #ea580c;
}

.action-btn.primary:hover {
  background: #c2410c;
}

.action-btn.danger {
  border: 1px solid #fecaca;
  color: #dc2626;
  background: #fff;
}

.action-btn.danger:hover {
  background: #fef2f2;
}

.action-btn:disabled {
  opacity: 0.58;
  cursor: not-allowed;
}

.deleted-hint {
  flex: 1 1 auto;
  padding: 12px;
  border-radius: 10px;
  color: #8c8178;
  font-size: 14px;
  text-align: center;
  background: #f7f4f1;
}

.page-state {
  display: flex;
  min-height: 330px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 12px;
  color: #8c8178;
  text-align: center;
}

.page-state p {
  margin: 0;
  font-size: 14px;
}

.return-btn {
  min-height: 40px;
  padding: 0 15px;
  border: 1px solid #fed7aa;
  border-radius: 10px;
  color: #c2410c;
  font-size: 14px;
  font-weight: 700;
  background: #fff8f1;
  cursor: pointer;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 760px) {
  .nav-container,
  .container {
    width: calc(100% - 32px);
  }

  .logout-btn {
    display: none;
  }

  .rating-section {
    align-items: stretch;
    flex-direction: column;
  }

  .overall-rating {
    align-items: flex-start;
  }

  .info-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .brand-copy span {
    display: none;
  }

  .back-btn {
    padding: 0 10px;
    font-size: 13px;
  }

  .detail-main {
    padding-top: 16px;
  }

  .detail-card,
  .follow-up-section,
  .history-section,
  .action-section {
    padding: 18px;
  }

  .detail-header,
  .follow-up-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .merchant-name {
    font-size: 24px;
    white-space: normal;
  }

  .sub-ratings {
    grid-template-columns: 1fr;
  }

  .info-grid,
  .form-row.two-columns {
    grid-template-columns: 1fr;
  }

  .follow-up-card-header {
    align-items: flex-start;
  }

  .follow-up-meta {
    flex-direction: column;
  }

  .history-header {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .history-time {
    width: 100%;
    margin-left: 0;
  }

  .action-section {
    flex-direction: column;
  }
}
</style>

<style>
html,
body,
#app {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  margin: 0;
  overflow-x: hidden;
}
</style>
