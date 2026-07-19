<template>
  <div class="ai-dining-page">
    <header class="page-header">
      <button class="back-button" type="button" @click="router.push('/diner/home')">← 返回首页</button>
      <div>
        <h1>AI 探店</h1>
        <p>直接描述人数、预算、菜系、距离和用餐场景</p>
      </div>
      <span class="session-state">{{ sessionId ? '会话已保存' : '正在准备会话' }}</span>
    </header>

    <main class="dialogue-shell">
      <div class="location-toolbar">
        <button
          type="button"
          class="location-button"
          :disabled="locationStatus === 'LOCATING'"
          @click="requestCurrentLocation"
        >
          {{ locationButtonText() }}
        </button>
        <span class="location-status">{{ locationStatusText() }}</span>
      </div>
      <section ref="messageListRef" class="message-list" aria-live="polite">
        <div v-if="initializing" class="state-panel">正在加载会话...</div>
        <div v-else-if="messages.length === 0" class="empty-panel">
          <div class="empty-icon">✨</div>
          <h2>想吃什么，直接告诉我</h2>
          <p>例如：四个人，人均八十，想吃川菜，三公里内，适合朋友聚餐。</p>
        </div>

        <article
          v-for="message in messages"
          :key="message.key"
          class="message-row"
          :class="message.role === 'USER' ? 'user-row' : 'assistant-row'"
        >
          <div class="message-bubble">
            <div class="message-role">{{ message.role === 'USER' ? '我' : 'AI 探店助手' }}</div>
            <p>{{ message.content }}</p>

            <div v-if="message.recommendations.length" class="merchant-grid">
              <button
                v-for="merchant in message.recommendations"
                :key="merchant.merchantId"
                type="button"
                class="merchant-card"
                @click="openMerchant(merchant.merchantId)"
              >
                <div class="merchant-card-header">
                  <span class="rank-badge">#{{ merchant.rankNo || '-' }}</span>
                  <span class="operation-status">{{ operationStatusText(merchant.operationStatus) }}</span>
                </div>
                <h3>{{ textOr(merchant.merchantName, '商家名称暂无') }}</h3>
                <div class="merchant-meta">
                  <span>{{ textOr(merchant.category || merchant.cuisine, '暂无商家类别') }}</span>
                  <span>{{ ratingText(merchant.merchantRating) }}</span>
                  <span>{{ priceText(merchant.averagePrice) }}</span>
                  <span>{{ distanceText(merchant.distanceKm) }}</span>
                </div>
                <p class="reason">{{ textOr(merchant.reason, '暂无推荐理由') }}</p>
                <div
                  v-if="Array.isArray(merchant.matchedDishes) && merchant.matchedDishes.length"
                  class="matched-dishes"
                >
                  <span class="matched-dishes-title">匹配菜品：</span>
                  <span
                    v-for="dish in merchant.matchedDishes.slice(0, 3)"
                    :key="dish.dishId"
                    class="matched-dish"
                  >
                    {{ textOr(dish.dishName, '菜品名称暂无') }}
                    {{ dish.dishPrice == null ? '价格暂无' : `¥${dish.dishPrice}` }}
                  </span>
                </div>
              </button>
            </div>

            <div v-if="message.suggestions.length" class="suggestion-panel">
              <strong>可以尝试调整：</strong>
              <div class="suggestion-list">
                <button
                  v-for="suggestion in message.suggestions"
                  :key="suggestion.id || suggestion.displayText"
                  type="button"
                  class="suggestion-button"
                  :disabled="adjustingSuggestionKey !== ''"
                  @click="applySuggestion(message, suggestion)"
                >
                  <span>{{ suggestion.displayText || suggestion.reason }}</span>
                  <small>
                    {{ isAdjusting(message, suggestion) ? '重新推荐中...' : '点击调整并重新推荐' }}
                  </small>
                </button>
              </div>
            </div>

            <div v-if="message.limitingConditions.length" class="limiting-panel">
              <strong>当前限制条件</strong>
              <ul>
                <li
                  v-for="condition in message.limitingConditions"
                  :key="condition.field || condition.type || JSON.stringify(condition)"
                >
                  {{ condition.description || condition.field }}
                </li>
              </ul>
            </div>
          </div>
        </article>

        <div v-if="sending" class="typing-indicator">AI 正在理解需求并筛选商家...</div>
      </section>

      <div v-if="errorMessage" class="error-banner" role="alert">
        {{ errorMessage }}
      </div>

      <form class="composer" @submit.prevent="submitMessage">
        <textarea
          v-model="draft"
          maxlength="1000"
          rows="3"
          :disabled="initializing || sending"
          placeholder="说说你的用餐需求，例如：两个人，人均 100 元，想吃火锅..."
          @keydown.enter.exact.prevent="submitMessage"
        />
        <div class="composer-footer">
          <span>{{ draft.length }}/1000</span>
          <button type="submit" :disabled="initializing || sending || !sessionId">
            {{ sending ? '发送中...' : '发送' }}
          </button>
        </div>
      </form>
    </main>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  adjustDiningRecommendation,
  createDiningSession,
  getDiningMessages,
  sendDiningMessage
} from '../../api/aiDining'

const router = useRouter()
const sessionId = ref(null)
const messages = ref([])
const draft = ref('')
const initializing = ref(true)
const sending = ref(false)
const adjustingSuggestionKey = ref('')
const errorMessage = ref('')
const messageListRef = ref(null)
const currentConstraints = ref({})
const locationStatus = ref('NOT_REQUESTED')
const currentLocation = ref(null)

const currentUserId = () => {
  const raw = localStorage.getItem('user') || localStorage.getItem('userInfo')
  if (!raw) return 'anonymous'
  try {
    return JSON.parse(raw)?.id || 'anonymous'
  } catch {
    return 'anonymous'
  }
}

const sessionStorageKey = () => `foodadvisor.aiDining.session.${currentUserId()}`

const createRequestId = () => {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `web-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

const textOr = (value, fallback) => value === null || value === undefined || value === '' ? fallback : value
const ratingText = value => value === null || value === undefined ? '暂无评分' : `评分 ${value}`
const priceText = value => value === null || value === undefined ? '人均消费暂无' : `人均 ¥${value}`
const distanceText = value => value === null || value === undefined ? '距离未知' : `距离 ${value} km`
const operationStatusText = value => {
  const labels = { OPERATING: '营业中', SUSPENDED: '暂停营业', CLOSED_PERMANENTLY: '已停业' }
  return labels[value] || '营业状态未知'
}

const locationButtonText = () =>
  locationStatus.value === 'LOCATING'
    ? '正在获取位置...'
    : locationStatus.value === 'READY'
      ? '重新获取当前位置'
      : '使用当前位置'

const locationStatusText = () => {
  const labels = {
    NOT_REQUESTED: '未获取位置',
    LOCATING: '正在获取',
    READY: '已获取当前位置',
    DENIED: '位置权限已拒绝',
    UNSUPPORTED: '当前浏览器不支持定位'
  }
  return labels[locationStatus.value] || '未获取位置'
}

const requestCurrentLocation = () => {
  if (!navigator.geolocation) {
    locationStatus.value = 'UNSUPPORTED'
    currentLocation.value = null
    return
  }

  locationStatus.value = 'LOCATING'
  errorMessage.value = ''
  navigator.geolocation.getCurrentPosition(
    position => {
      currentLocation.value = {
        userLatitude: position.coords.latitude,
        userLongitude: position.coords.longitude
      }
      locationStatus.value = 'READY'
    },
    error => {
      currentLocation.value = null
      locationStatus.value =
        error.code === error.PERMISSION_DENIED
          ? 'DENIED'
          : 'NOT_REQUESTED'
      errorMessage.value =
        error.code === error.PERMISSION_DENIED
          ? '您已拒绝位置权限；普通推荐仍可使用，距离推荐需要授权当前位置'
          : '当前位置获取失败，请稍后重试'
    },
    {
      enableHighAccuracy: false,
      timeout: 10000,
      maximumAge: 300000
    }
  )
}

const normalizeHistoryMessage = item => ({
  key: `history-${item.id}`,
  id: item.id,
  role: item.role,
  content: item.content || '',
  requestId: item.requestId,
  responseType: item.responseType,
  recommendationId: item.recommendationId,
  recommendations: Array.isArray(item.recommendations) ? item.recommendations : [],
  suggestions: Array.isArray(item.adjustmentSuggestions) ? item.adjustmentSuggestions : [],
  limitingConditions: Array.isArray(item.limitingConditions) ? item.limitingConditions : []
})

const scrollToBottom = async () => {
  await nextTick()
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  }
}

const loadHistory = async id => {
  const response = await getDiningMessages(id)
  if (!response.success) throw new Error(response.message || '历史消息加载失败')
  messages.value = (response.data?.messages || []).map(normalizeHistoryMessage)
}

const createSession = async () => {
  const response = await createDiningSession('AI探店对话')
  if (!response.success || !response.data?.sessionId) {
    throw new Error(response.message || '会话创建失败')
  }
  sessionId.value = response.data.sessionId
  localStorage.setItem(sessionStorageKey(), String(sessionId.value))
}

const initialize = async () => {
  initializing.value = true
  errorMessage.value = ''
  const savedId = Number(localStorage.getItem(sessionStorageKey()))
  try {
    if (Number.isSafeInteger(savedId) && savedId > 0) {
      sessionId.value = savedId
      await loadHistory(savedId)
    } else {
      await createSession()
    }
  } catch (error) {
    localStorage.removeItem(sessionStorageKey())
    messages.value = []
    try {
      await createSession()
    } catch (createError) {
      errorMessage.value = createError.message || 'AI 探店暂时不可用，请稍后重试'
    }
  } finally {
    initializing.value = false
    scrollToBottom()
  }
}

const submitMessage = async () => {
  if (sending.value || initializing.value) return
  const content = draft.value.trim()
  if (!content) {
    errorMessage.value = '请输入有效的用餐需求，不能只输入空格或换行'
    return
  }

  const requestId = createRequestId()
  sending.value = true
  errorMessage.value = ''
  try {
    const response = await sendDiningMessage(
      sessionId.value,
      content,
      requestId,
      currentLocation.value
    )
    if (!response.success || !response.data) {
      if (response.message?.includes('缺少当前位置')) {
        throw new Error('该需求包含距离条件，请先点击“使用当前位置”并授权定位')
      }
      throw new Error(response.message || '消息发送失败')
    }

    const data = response.data
    messages.value.push({
      key: `user-${data.userMessageId || requestId}`,
      role: 'USER',
      content,
      requestId,
      recommendations: [],
      suggestions: [],
      limitingConditions: []
    })
    messages.value.push({
      key: `assistant-${data.assistantMessageId || requestId}`,
      id: data.assistantMessageId,
      role: 'ASSISTANT',
      content: data.assistantText || data.recommendation?.message || '请求已处理',
      requestId,
      responseType: data.responseType,
      recommendationId: data.recommendation?.recommendationId,
      recommendations: data.recommendation?.results || [],
      suggestions: data.recommendation?.adjustmentSuggestions || [],
      limitingConditions: data.recommendation?.limitingConditions || []
    })
    currentConstraints.value =
      data.recommendation?.currentConstraints ||
      data.currentConstraints ||
      currentConstraints.value
    draft.value = ''
  } catch (error) {
    errorMessage.value = error.message || '网络或 AI 服务异常，请稍后重新发送'
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

const suggestionKey = (message, suggestion) =>
  `${message.key}:${suggestion.id || suggestion.field || suggestion.displayText}`

const isAdjusting = (message, suggestion) =>
  adjustingSuggestionKey.value === suggestionKey(message, suggestion)

const applySuggestion = async (message, suggestion) => {
  if (adjustingSuggestionKey.value || !sessionId.value) return
  if (!suggestion?.field || suggestion.suggestedValue === undefined) {
    errorMessage.value = '该调整建议缺少有效参数，请刷新后重试'
    return
  }

  adjustingSuggestionKey.value = suggestionKey(message, suggestion)
  errorMessage.value = ''
  try {
    const response = await adjustDiningRecommendation(
      sessionId.value,
      message.id,
      suggestion.field,
      suggestion.suggestedValue,
      currentLocation.value
    )
    if (!response.success || !response.data) {
      throw new Error(response.message || '调整条件后重新推荐失败')
    }

    const recommendation = response.data
    currentConstraints.value =
      recommendation.currentConstraints ||
      recommendation.constraints ||
      {}
    message.responseType = recommendation.status
    message.recommendationId = recommendation.recommendationId
    message.content =
      recommendation.message ||
      (recommendation.status === 'SUCCESS'
        ? `已为你找到 ${recommendation.resultCount || 0} 家符合条件的商家`
        : '当前仍没有完全匹配的结果')
    message.recommendations =
      recommendation.status === 'SUCCESS' && Array.isArray(recommendation.results)
        ? recommendation.results
        : []
    message.suggestions =
      recommendation.status === 'NO_MATCH' &&
      Array.isArray(recommendation.adjustmentSuggestions)
        ? recommendation.adjustmentSuggestions
        : []
    message.limitingConditions =
      recommendation.status === 'NO_MATCH' &&
      Array.isArray(recommendation.limitingConditions)
        ? recommendation.limitingConditions
        : []
  } catch (error) {
    errorMessage.value =
      error.message || '调整条件后重新推荐失败，请稍后重试'
  } finally {
    adjustingSuggestionKey.value = ''
    scrollToBottom()
  }
}

const openMerchant = merchantId => {
  if (merchantId) router.push(`/diner/merchant/${merchantId}`)
}

onMounted(initialize)
</script>

<style scoped>
.ai-dining-page { min-height: 100vh; padding: 24px; background: #f4f5fb; color: #1f2937; }
.page-header { max-width: 1120px; margin: 0 auto 18px; display: grid; grid-template-columns: 160px 1fr 160px; align-items: center; }
.page-header h1 { margin: 0; text-align: center; font-size: 30px; }
.page-header p { margin: 6px 0 0; text-align: center; color: #667085; }
.back-button { justify-self: start; border: 0; background: transparent; color: #5b5bd6; cursor: pointer; font-size: 15px; }
.session-state { justify-self: end; color: #667085; font-size: 13px; }
.dialogue-shell { max-width: 1120px; margin: 0 auto; overflow: hidden; border-radius: 20px; background: #fff; box-shadow: 0 12px 36px rgba(31, 41, 55, .1); }
.location-toolbar { display: flex; align-items: center; gap: 12px; padding: 14px 22px; border-bottom: 1px solid #eaecf0; background: #fafafa; }
.location-button { min-width: 132px; padding: 8px 13px; border: 1px solid #8b5cf6; border-radius: 9px; color: #6d28d9; background: #fff; cursor: pointer; }
.location-button:disabled { opacity: .6; cursor: wait; }
.location-status { color: #667085; font-size: 13px; }
.message-list { height: calc(100vh - 280px); min-height: 430px; overflow-y: auto; padding: 28px; }
.empty-panel, .state-panel { height: 100%; display: grid; place-content: center; text-align: center; color: #667085; }
.empty-panel h2 { color: #1f2937; margin: 12px 0 4px; }
.empty-icon { font-size: 44px; }
.message-row { display: flex; margin-bottom: 22px; }
.user-row { justify-content: flex-end; }
.message-bubble { max-width: 86%; padding: 15px 18px; border-radius: 16px; background: #f2f3f8; }
.user-row .message-bubble { max-width: 72%; color: #fff; background: linear-gradient(135deg, #5b5bd6, #8b5cf6); }
.message-role { margin-bottom: 7px; font-size: 12px; font-weight: 700; opacity: .72; }
.message-bubble > p { margin: 0; line-height: 1.65; white-space: pre-wrap; }
.merchant-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin-top: 16px; }
.merchant-card { padding: 16px; text-align: left; border: 1px solid #e5e7eb; border-radius: 14px; background: #fff; cursor: pointer; }
.merchant-card:hover { border-color: #8b5cf6; box-shadow: 0 5px 18px rgba(91, 91, 214, .12); }
.merchant-card-header { display: flex; justify-content: space-between; font-size: 12px; color: #667085; }
.rank-badge { color: #7c3aed; font-weight: 700; }
.merchant-card h3 { margin: 9px 0; color: #111827; }
.merchant-meta { display: flex; flex-wrap: wrap; gap: 6px; }
.merchant-meta span { padding: 3px 7px; border-radius: 6px; background: #f3f4f6; color: #4b5563; font-size: 12px; }
.reason { margin-top: 12px !important; color: #374151; font-size: 14px; }
.matched-dishes { margin-top: 10px; display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.matched-dishes-title { color: #6b7280; font-size: 13px; }
.matched-dish { padding: 4px 8px; border-radius: 999px; background: #fff7ed; color: #9a3412; font-size: 13px; }
.suggestion-panel { margin-top: 14px; padding: 12px; border-radius: 10px; background: #fff7ed; color: #9a3412; }
.suggestion-list { display: grid; gap: 8px; margin-top: 9px; }
.suggestion-button { display: flex; justify-content: space-between; gap: 14px; align-items: center; width: 100%; padding: 10px 12px; text-align: left; border: 1px solid #fdba74; border-radius: 9px; color: #9a3412; background: #fff; cursor: pointer; }
.suggestion-button:hover:not(:disabled) { border-color: #f97316; box-shadow: 0 3px 12px rgba(249, 115, 22, .14); }
.suggestion-button:disabled { opacity: .6; cursor: wait; }
.suggestion-button small { flex: none; color: #c2410c; }
.limiting-panel { margin-top: 12px; padding: 12px; border-radius: 10px; color: #475467; background: #f8fafc; }
.limiting-panel ul { margin: 7px 0 0; padding-left: 20px; }
.typing-indicator { color: #7c3aed; font-size: 14px; }
.error-banner { margin: 0 28px 12px; padding: 11px 14px; border-radius: 9px; color: #b42318; background: #fef3f2; }
.composer { padding: 18px 22px; border-top: 1px solid #eaecf0; background: #fff; }
.composer textarea { width: 100%; resize: none; box-sizing: border-box; padding: 13px; border: 1px solid #d0d5dd; border-radius: 12px; font: inherit; }
.composer textarea:focus { outline: 0; border-color: #7c3aed; box-shadow: 0 0 0 3px rgba(124, 58, 237, .1); }
.composer-footer { display: flex; justify-content: space-between; align-items: center; margin-top: 9px; color: #98a2b3; font-size: 12px; }
.composer button { min-width: 100px; padding: 10px 18px; border: 0; border-radius: 9px; color: #fff; background: #6d5bd0; cursor: pointer; }
.composer button:disabled { opacity: .55; cursor: not-allowed; }
@media (max-width: 760px) {
  .ai-dining-page { padding: 12px; }
  .page-header { grid-template-columns: 1fr auto; }
  .page-header > div { grid-column: 1 / -1; grid-row: 1; margin-bottom: 16px; }
  .back-button { grid-row: 2; }
  .session-state { grid-row: 2; }
  .message-list { height: calc(100vh - 300px); padding: 16px; }
  .message-bubble { max-width: 94%; }
  .merchant-grid { grid-template-columns: 1fr; }
}
</style>
