<template>
  <div class="ai-dining-page">
    <aside
      v-show="historySidebarOpen"
      class="history-sidebar"
      aria-label="历史对话"
    >
      <div class="history-sidebar-header">
        <div class="history-sidebar-title">
          <span class="history-sidebar-logo">✨</span>
          <strong>历史对话</strong>
        </div>

        <button
          type="button"
          class="history-close-button"
          aria-label="收起历史对话侧栏"
          @click="closeHistorySidebar"
        >
          ‹
        </button>
      </div>

      <button
        type="button"
        class="sidebar-new-session-button"
        :disabled="initializing || sending || adjustingSuggestionKey !== ''"
        @click="startNewConversation"
      >
        <span>＋</span>
        新建对话
      </button>

      <div class="history-sidebar-content">
        <div
          v-if="historyLoading"
          class="history-state"
        >
          正在加载...
        </div>

        <div
          v-else-if="historyError"
          class="history-state history-error"
        >
          {{ historyError }}

          <button
            type="button"
            class="history-retry-button"
            @click="loadHistorySessions"
          >
            重新加载
          </button>
        </div>

        <div
          v-else-if="historySessions.length === 0"
          class="history-state"
        >
          暂无历史对话
        </div>

        <div v-else class="history-session-list">
          <div
            v-for="session in historySessions"
            :key="session.sessionId"
            class="history-session-row"
          >
            <button
              type="button"
              class="history-session-item"
              :class="{ active: session.sessionId === sessionId }"
              :disabled="
                initializing ||
                sending ||
                adjustingSuggestionKey !== '' ||
                deletingSessionId === session.sessionId
              "
              @click="switchHistorySession(session)"
            >
              <span class="history-session-title">
                {{ session.title }}
              </span>

              <span
                v-if="session.updatedAt"
                class="history-session-time"
              >
                {{ formatSessionTime(session.updatedAt) }}
              </span>
            </button>

            <button
              type="button"
              class="history-session-delete"
              title="删除对话"
              :disabled="
                initializing ||
                sending ||
                deletingSessionId !== null
              "
              @click.stop="deleteHistorySession(session)"
            >
              {{
                deletingSessionId === session.sessionId
                  ? '删除中'
                  : '删除'
              }}
            </button>
          </div>
        </div>
      </div>
    </aside>

    <div class="ai-dining-main">
    <header class="page-header">
      <div class="header-container">
        <div class="header-leading">
          <button
            v-if="!historySidebarOpen"
            type="button"
            class="history-button"
            :disabled="initializing || sending || adjustingSuggestionKey !== ''"
            @click="toggleHistorySidebar"
          >
            <span class="history-button-icon">☰</span>
            历史对话
          </button>
        </div>

        <div class="page-title">
          <div class="title-line">
            <h1>AI 探店</h1>
            <span class="online-badge">
              <span class="online-dot"></span>
              在线
            </span>
          </div>
        </div>

        <div class="header-actions">
          <button
            class="back-button"
            type="button"
            @click="router.push('/diner/home')"
          >
            <span class="back-icon">⌂</span>
            <span>返回首页</span>
          </button>
        </div>
      </div>
    </header>

    <main class="dialogue-shell">
      <div class="assistant-toolbar">
        <div class="assistant-profile">
          <span class="assistant-avatar">✨</span>

          <span class="assistant-copy">
            <strong>食尚参谋 AI 助手</strong>
          </span>
        </div>

        <div class="location-toolbar">
          <button
            type="button"
            class="location-button"
            :disabled="locationStatus === 'LOCATING'"
            @click="requestCurrentLocation"
          >
            <span class="location-icon">⌖</span>
            {{ locationButtonText() }}
          </button>

          <span class="location-status">
            <span
              class="location-status-dot"
              :class="{ ready: locationStatus === 'READY' }"
            ></span>
            {{ locationStatusText() }}
          </span>
        </div>
      </div>

      <div
        v-if="Object.keys(currentConstraints).length"
        class="constraint-bar"
      >
        <span class="constraint-label">当前条件</span>
        <span class="constraint-content">
          {{ constraintSummary(currentConstraints) }}
        </span>
      </div>

      <section
        ref="messageListRef"
        class="message-list"
        aria-live="polite"
      >
        <div v-if="initializing" class="state-panel">
          <span class="state-spinner"></span>
          <strong>正在加载会话</strong>
        </div>

        <div v-else-if="messages.length === 0" class="empty-panel">
          <div class="empty-icon-shell">
            <span class="empty-icon">✨</span>
          </div>

          <h2>想吃什么，直接告诉我</h2>
        </div>

        <article
          v-for="message in messages"
          :key="message.key"
          class="message-row"
          :class="message.role === 'USER' ? 'user-row' : 'assistant-row'"
        >
          <div class="message-avatar">
            {{ message.role === 'USER' ? '我' : '✨' }}
          </div>

          <div class="message-column">
            <div class="message-role">
              {{ message.role === 'USER' ? '我' : 'AI 探店助手' }}
            </div>

            <div class="message-bubble">
              <p>{{ message.content }}</p>

              <div v-if="message.notice" class="notice-banner">
                <span>ℹ</span>
                {{ message.notice }}
              </div>

              <div
                v-if="message.recommendations.length"
                class="merchant-grid"
              >
                <div
                  v-for="merchant in message.recommendations"
                  :key="merchant.merchantId"
                  class="merchant-card"
                  role="button"
                  tabindex="0"
                  @click="openMerchant(merchant.merchantId)"
                  @keydown.enter="openMerchant(merchant.merchantId)"
                >
                  <div class="merchant-card-header">
                    <span class="rank-badge">
                      TOP {{ merchant.rankNo || '-' }}
                    </span>

                    <span class="operation-status">
                      <span class="operation-dot"></span>
                      {{ operationStatusText(merchant.operationStatus) }}
                    </span>
                  </div>

                  <h3>{{ textOr(merchant.merchantName, '商家名称暂无') }}</h3>

                  <div class="merchant-meta">
                    <span>
                      {{ textOr(merchant.category || merchant.cuisine, '暂无商家类别') }}
                    </span>
                    <span>{{ ratingText(merchant.merchantRating) }}</span>
                    <span>{{ priceText(merchant.averagePrice) }}</span>
                    <span>{{ distanceText(merchant.distanceKm) }}</span>
                  </div>

                  <p class="reason">
                    {{ textOr(merchant.reason, '暂无推荐理由') }}
                  </p>

                  <ul
                    v-if="merchant.riskNotes?.length"
                    class="risk-list"
                  >
                    <li
                      v-for="risk in merchant.riskNotes"
                      :key="risk"
                    >
                      {{ risk }}
                    </li>
                  </ul>

                  <div
                    v-if="Array.isArray(merchant.matchedDishes) && merchant.matchedDishes.length"
                    class="matched-dishes"
                  >
                    <span class="matched-dishes-title">匹配菜品</span>

                    <span
                      v-for="dish in merchant.matchedDishes.slice(0, 3)"
                      :key="dish.dishId"
                      class="matched-dish"
                    >
                      {{ textOr(dish.dishName, '菜品名称暂无') }}
                      {{ dish.dishPrice == null ? '价格暂无' : `￥${dish.dishPrice}` }}
                    </span>
                  </div>

                  <div class="merchant-card-footer">
                    <span class="merchant-detail-hint">点击查看商家详情</span>

                    <button
                      type="button"
                      class="evidence-button"
                      @click.stop="openEvidence(message, merchant)"
                    >
                      查看依据
                      <span>→</span>
                    </button>
                  </div>
                </div>
              </div>

              <div
                v-if="message.suggestions.length"
                class="suggestion-panel"
              >
                <div class="panel-heading">
                  <span class="panel-icon">↻</span>
                  <strong>可以尝试调整</strong>
                </div>

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
                      {{ isAdjusting(message, suggestion) ? '重新推荐中...' : '调整并重新推荐' }}
                    </small>
                  </button>
                </div>
              </div>

              <div
                v-if="message.limitingConditions.length"
                class="limiting-panel"
              >
                <div class="panel-heading">
                  <span class="panel-icon">!</span>
                  <strong>当前限制条件</strong>
                </div>

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
          </div>
        </article>

        <div v-if="sending" class="typing-indicator">
          <span class="typing-avatar">✨</span>
          <span class="typing-bubble">
            <span class="typing-dots">
              <i></i>
              <i></i>
              <i></i>
            </span>
            AI 正在理解需求并筛选商家
          </span>
        </div>
      </section>

      <div
        v-if="errorMessage"
        class="error-banner"
        role="alert"
      >
        <span class="error-icon">!</span>
        <span class="error-text">{{ errorMessage }}</span>

        <button
          v-if="pendingRequest"
          type="button"
          class="retry-button"
          @click="submitMessage"
        >
          使用同一请求重试
        </button>
      </div>

      <form class="composer" @submit.prevent="submitMessage">
        <div class="composer-input-shell">
          <textarea
            v-model="draft"
            maxlength="1000"
            rows="2"
            :disabled="initializing || sending"
            placeholder="输入你的用餐需求..."
            @keydown.enter.exact.prevent="submitMessage"
          />

          <div class="composer-footer">
            <div class="composer-actions">
              <span class="word-count">{{ draft.length }}/1000</span>

              <button
                type="submit"
                :disabled="initializing || sending || !sessionId"
              >
                <span>{{ sending ? '发送中...' : '发送需求' }}</span>
                <span class="send-icon">↑</span>
              </button>
            </div>
          </div>
        </div>
      </form>
    </main>

    </div>

    <div
      v-if="historySidebarOpen"
      class="history-mobile-mask"
      @click="closeHistorySidebar"
    ></div>

    <div
      v-if="evidenceDialogOpen"
      class="dialog-mask"
      @click.self="closeEvidence"
    >
      <section
        class="evidence-dialog"
        role="dialog"
        aria-modal="true"
      >
        <header>
          <div>
            <span class="dialog-eyebrow">推荐可解释性</span>
            <h2>推荐依据</h2>
          </div>

          <button
            type="button"
            class="dialog-close-button"
            @click="closeEvidence"
          >
            ×
          </button>
        </header>

        <div
          v-if="evidenceLoading"
          class="dialog-state-panel"
        >
          正在加载推荐依据...
        </div>

        <div
          v-else-if="evidenceError"
          class="dialog-error-banner"
        >
          {{ evidenceError }}
        </div>

        <div
          v-else-if="!evidences.length"
          class="dialog-state-panel"
        >
          暂无可查看依据
        </div>

        <div v-else class="evidence-list">
          <article
            v-for="(evidence, index) in evidences"
            :key="index"
            class="evidence-item"
          >
            <div class="evidence-item-header">
              <strong>{{ evidenceTypeText(evidence.sourceType) }}</strong>
              <span>{{ evidence.merchantName }}</span>
            </div>

            <span
              v-if="evidence.conditionKey"
              class="evidence-condition"
            >
              对应条件：{{ evidence.conditionKey }}
            </span>

            <p v-if="evidence.available">
              {{ evidence.excerpt || '暂无详细内容' }}
            </p>

            <p v-else class="unavailable-text">
              该评价已删除或当前无权查看
            </p>

            <small
              v-if="evidence.available && evidence.reviewTime"
            >
              {{ evidence.reviewTime }}
            </small>
          </article>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  adjustDiningRecommendation,
  createDiningSession,
  deleteDiningSession,
  getDiningMessages,
  getDiningSessions,
  getRecommendationEvidences,
  sendDiningMessage
} from '../../api/aiDining'
import { logMerchantClick, logSearch } from '../../api/behavior'

const route = useRoute()
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
const evidenceDialogOpen = ref(false)
const evidenceLoading = ref(false)
const evidenceError = ref('')
const evidences = ref([])
const pendingRequest = ref(null)
const historySidebarOpen = ref(
  typeof window !== 'undefined' && window.innerWidth > 980
)
const historyLoading = ref(false)
const historyError = ref('')
const historySessions = ref([])
const deletingSessionId = ref(null)

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

const sessionIdOf = session => Number(
  session?.sessionId ?? session?.id
)

const normalizeSessionSummary = session => ({
  sessionId: sessionIdOf(session),
  title:
    session?.title ||
    session?.sessionTitle ||
    '未命名对话',
  updatedAt:
    session?.updatedAt ||
    session?.lastMessageAt ||
    session?.createdAt ||
    ''
})

const extractSessionList = response => {
  const data = response?.data

  if (Array.isArray(data)) return data
  if (Array.isArray(data?.sessions)) return data.sessions
  if (Array.isArray(data?.records)) return data.records
  if (Array.isArray(data?.items)) return data.items

  return []
}

const formatSessionTime = value => {
  if (!value) return ''

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''

  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  })
}

const createRequestId = () => {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `web-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

const textOr = (value, fallback) => value === null || value === undefined || value === '' ? fallback : value
const hiddenConstraintKeys = new Set([
  'constraintStrengths'
])

const constraintSummary = value => Object.entries(value || {})
  .filter(([key, item]) => {
    if (hiddenConstraintKeys.has(key)) return false
    if (item === null || item === undefined || item === '') return false
    if (Array.isArray(item)) return item.length > 0

    // 其他内部对象也不直接渲染，避免再次出现 [object Object]
    if (typeof item === 'object') return false

    return true
  })
  .map(([key, item]) => {
    let displayValue = item

    if (Array.isArray(item)) {
      displayValue = item.join('、')
    } else if (typeof item === 'boolean') {
      displayValue = item ? '是' : '否'
    }

    return `${key}: ${displayValue}`
  })
  .join('；')
const ratingText = value => value === null || value === undefined ? '暂无评分' : `评分 ${value}`
const priceText = value => value === null || value === undefined ? '人均消费暂无' : `人均 ￥${value}`
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
  limitingConditions: Array.isArray(item.limitingConditions) ? item.limitingConditions : [],
  notice: degradationNotice(item)
})

const degradationNotice = data => {
  if (data?.responseType === 'DATA_ERROR') return '数据服务异常，请稍后重试'
  if (data?.recommendation?.semanticStatus === 'UNAVAILABLE') return '语义检索暂不可用，本次已使用确定性规则排序'
  if (data?.degraded && data?.extractor === 'RULE_FALLBACK') return 'AI 理解暂不可用，本次已使用规则安全降级'
  if (data?.degraded) return '部分 AI 能力暂不可用，核心推荐仍可使用'
  return ''
}

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

const loadHistorySessions = async () => {
  historyLoading.value = true
  historyError.value = ''

  try {
    const response = await getDiningSessions(currentUserId())

    if (!response.success) {
      throw new Error(response.message || '历史对话加载失败')
    }

    historySessions.value = extractSessionList(response)
      .map(normalizeSessionSummary)
      .filter(item => Number.isSafeInteger(item.sessionId) && item.sessionId > 0)
      .sort((left, right) => {
        const leftTime = new Date(left.updatedAt || 0).getTime()
        const rightTime = new Date(right.updatedAt || 0).getTime()
        return rightTime - leftTime
      })
  } catch (error) {
    historySessions.value = []
    historyError.value =
      error.message || '历史对话加载失败，请稍后重试'
  } finally {
    historyLoading.value = false
  }
}

const isMobileViewport = () =>
  typeof window !== 'undefined' && window.innerWidth <= 980

const toggleHistorySidebar = async () => {
  historySidebarOpen.value = !historySidebarOpen.value

  if (historySidebarOpen.value) {
    await loadHistorySessions()
  }
}

const closeHistorySidebar = () => {
  historySidebarOpen.value = false
}

const switchHistorySession = async session => {
  const targetSessionId = sessionIdOf(session)

  if (
    !Number.isSafeInteger(targetSessionId) ||
    targetSessionId <= 0
  ) {
    historyError.value = '该历史会话信息无效'
    return
  }

  if (targetSessionId === sessionId.value) {
    if (isMobileViewport()) {
      closeHistorySidebar()
    }
    return
  }

  initializing.value = true
  errorMessage.value = ''

  try {
    await loadHistory(targetSessionId)
    sessionId.value = targetSessionId
    localStorage.setItem(
      sessionStorageKey(),
      String(targetSessionId)
    )
    currentConstraints.value = {}
    pendingRequest.value = null
    if (isMobileViewport()) {
      closeHistorySidebar()
    }
  } catch (error) {
    historyError.value =
      error.message || '历史消息加载失败，请稍后重试'
  } finally {
    initializing.value = false
    scrollToBottom()
  }
}

const deleteHistorySession = async session => {
  const targetSessionId = sessionIdOf(session)

  if (
    !Number.isSafeInteger(targetSessionId) ||
    targetSessionId <= 0
  ) {
    historyError.value = '该历史会话信息无效'
    return
  }

  const confirmed = window.confirm(
    `确定删除“${session.title || '该对话'}”吗？\n\n` +
    '删除后将不再显示，但系统仍会保留相关记录。'
  )

  if (!confirmed) return

  deletingSessionId.value = targetSessionId
  historyError.value = ''

  try {
    const response = await deleteDiningSession(targetSessionId)

    if (!response.success) {
      throw new Error(response.message || '删除对话失败')
    }

    historySessions.value = historySessions.value.filter(
      item => item.sessionId !== targetSessionId
    )

    if (targetSessionId !== sessionId.value) {
      return
    }

    const nextSession = historySessions.value[0]

    if (nextSession) {
      await switchHistorySession(nextSession)
      return
    }

    await createSession()
    resetConversationView()
    await loadHistorySessions()
  } catch (error) {
    historyError.value =
      error.message || '删除对话失败，请稍后重试'
  } finally {
    deletingSessionId.value = null
  }
}

const createSession = async () => {
  const response = await createDiningSession('AI探店对话')
  if (!response.success || !response.data?.sessionId) {
    throw new Error(response.message || '会话创建失败')
  }
  sessionId.value = response.data.sessionId
  localStorage.setItem(sessionStorageKey(), String(sessionId.value))
}

const resetConversationView = () => {
  messages.value = []
  draft.value = ''
  currentConstraints.value = {}
  adjustingSuggestionKey.value = ''
  errorMessage.value = ''
  pendingRequest.value = null

  evidenceDialogOpen.value = false
  evidenceLoading.value = false
  evidenceError.value = ''
  evidences.value = []
}

const startNewConversation = async () => {
  if (
    initializing.value ||
    sending.value ||
    adjustingSuggestionKey.value
  ) {
    return
  }

  initializing.value = true
  errorMessage.value = ''

  try {
    // 先成功创建并保存新的 sessionId，
    // 再清空页面，避免创建失败时丢掉当前聊天界面。
    await createSession()
    resetConversationView()

    if (historySidebarOpen.value) {
      await loadHistorySessions()
    }
  } catch (error) {
    errorMessage.value =
      error.message || '新建对话失败，请稍后重试'
  } finally {
    initializing.value = false
    scrollToBottom()
  }
}

const clearDialogueReturnQuery = () => {
  const nextQuery = { ...route.query }
  const hadSessionId = Object.prototype.hasOwnProperty.call(
    nextQuery,
    'sessionId'
  )
  const hadFrom = Object.prototype.hasOwnProperty.call(
    nextQuery,
    'from'
  )

  if (!hadSessionId && !hadFrom) return

  delete nextQuery.sessionId
  delete nextQuery.from

  router.replace({
    path: route.path,
    query: nextQuery
  }).catch(() => {})
}

const initialize = async () => {
  initializing.value = true
  errorMessage.value = ''

  const rawRouteSessionId = Array.isArray(route.query.sessionId)
    ? route.query.sessionId[0]
    : route.query.sessionId

  const routeSessionId = Number(rawRouteSessionId)
  const savedSessionId = Number(
    localStorage.getItem(sessionStorageKey())
  )

  const preferredSessionId =
    Number.isSafeInteger(routeSessionId) && routeSessionId > 0
      ? routeSessionId
      : savedSessionId

  try {
    if (
      Number.isSafeInteger(preferredSessionId) &&
      preferredSessionId > 0
    ) {
      sessionId.value = preferredSessionId

      // URL 中的 sessionId 优先，并同步为当前本地会话。
      localStorage.setItem(
        sessionStorageKey(),
        String(preferredSessionId)
      )

      await loadHistory(preferredSessionId)

      if (
        Number.isSafeInteger(routeSessionId) &&
        routeSessionId > 0
      ) {
        clearDialogueReturnQuery()
      }
    } else {
      await createSession()
    }
  } catch (error) {
    localStorage.removeItem(sessionStorageKey())
    messages.value = []

    try {
      await createSession()
    } catch (createError) {
      errorMessage.value =
        createError.message || 'AI 探店暂时不可用，请稍后重试'
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

  const userId = currentUserId()
  logSearch({ userId: userId !== 'anonymous' ? userId : undefined, keyword: content }).catch(() => {})

  const requestId =
    pendingRequest.value?.content === content
      ? pendingRequest.value.requestId
      : createRequestId()
  pendingRequest.value = { content, requestId }
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
      limitingConditions: data.recommendation?.limitingConditions || [],
      notice: degradationNotice(data)
    })
    currentConstraints.value =
      data.recommendation?.currentConstraints ||
      data.currentConstraints ||
      currentConstraints.value
    draft.value = ''
    pendingRequest.value = null
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
  if (!merchantId) return

  const userId = currentUserId()

  logMerchantClick({
    userId: userId !== 'anonymous' ? userId : undefined,
    merchantId
  }).catch(() => {})

  router.push({
    path: `/diner/merchant/${merchantId}`,
    query: {
      from: 'ai-dining',
      sessionId: String(sessionId.value)
    }
  })
}

const evidenceTypeText = type => ({
  REVIEW: '用户评价',
  MERCHANT: '商家资料',
  DISH: '菜单菜品'
}[type] || '推荐依据')

const openEvidence = async (message, merchant) => {
  evidenceDialogOpen.value = true
  evidenceLoading.value = true
  evidenceError.value = ''
  evidences.value = []
  if (!message.recommendationId) {
    evidenceLoading.value = false
    return
  }
  try {
    const response = await getRecommendationEvidences(
      message.recommendationId,
      merchant.merchantId
    )
    if (!response.success) throw new Error(response.message || '推荐依据加载失败')
    evidences.value = response.data || []
  } catch (error) {
    evidenceError.value = error.message || '推荐依据加载失败，请稍后重试'
  } finally {
    evidenceLoading.value = false
  }
}

const closeEvidence = () => {
  evidenceDialogOpen.value = false
}

onMounted(async () => {
  await initialize()

  if (historySidebarOpen.value) {
    await loadHistorySessions()
  }
})
</script>

<style scoped>
.ai-dining-page,
.ai-dining-page *,
.ai-dining-page *::before,
.ai-dining-page *::after {
  box-sizing: border-box;
}

.ai-dining-page {
  display: flex;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  height: 100vh;
  height: 100dvh;
  min-height: 0;
  flex-direction: row;
  overflow: hidden;
  color: #29231e;
  font-family:
    "Microsoft YaHei",
    "PingFang SC",
    "Noto Sans SC",
    Arial,
    sans-serif;
  background:
    radial-gradient(
      circle at 12% 4%,
      rgba(255, 226, 194, 0.52),
      transparent 24%
    ),
    radial-gradient(
      circle at 88% 7%,
      rgba(221, 214, 254, 0.48),
      transparent 26%
    ),
    #f8f6f2;
}

.ai-dining-main {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex: 1 1 auto;
  flex-direction: column;
  overflow: hidden;
}

.ai-dining-page button,
.ai-dining-page textarea {
  font-family: inherit;
}

.page-header {
  width: 100%;
  flex: 0 0 auto;
  padding: 8px 0;
  border-bottom: 1px solid rgba(229, 222, 212, 0.84);
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(18px);
}

.header-container {
  display: grid;
  width: calc(100% - 48px);
  max-width: 1180px;
  min-width: 0;
  margin: 0 auto;
  grid-template-columns: minmax(150px, 0.65fr) minmax(0, 1.5fr) minmax(150px, 0.65fr);
  align-items: center;
  gap: 24px;
}

.header-leading {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: flex-start;
}

.back-button {
  display: inline-flex;
  width: fit-content;
  max-width: 100%;
  align-items: center;
  gap: 8px;
  justify-self: end;
  padding: 10px 13px;
  border: 1px solid #e7e0d8;
  border-radius: 12px;
  color: #655c54;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  background: rgba(255, 255, 255, 0.78);
  cursor: pointer;
  transition:
    border-color 0.2s,
    color 0.2s,
    background 0.2s,
    transform 0.2s;
}

.back-button:hover {
  transform: translateX(2px);
  border-color: #fdba74;
  color: #c2410c;
  background: #fff8f1;
}

.back-icon {
  font-size: 18px;
  line-height: 1;
}

.page-title {
  min-width: 0;
  text-align: center;
}

.title-eyebrow {
  display: inline-flex;
  margin-bottom: 2px;
  color: #7c3aed;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.title-line {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.page-title h1 {
  margin: 0;
  color: #29231e;
  font-size: 30px;
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: -1px;
}

.online-badge {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 6px;
  padding: 4px 9px;
  border: 1px solid #d1fae5;
  border-radius: 999px;
  color: #15803d;
  font-size: 12px;
  font-weight: 700;
  background: #f0fdf4;
}

.online-dot,
.session-dot,
.location-status-dot,
.operation-dot {
  display: inline-block;
  flex: 0 0 auto;
  border-radius: 50%;
}

.online-dot {
  width: 7px;
  height: 7px;
  background: #22c55e;
  box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.14);
}


.header-actions {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.session-state {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 7px;
  color: #80766e;
  font-size: 13px;
  white-space: nowrap;
}

.session-dot {
  width: 7px;
  height: 7px;
  background: #a78bfa;
}

.history-button {
  display: inline-flex;
  flex: 0 0 auto;
  min-height: 42px;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 13px;
  border: 1px solid transparent;
  border-radius: 12px;
  color: #655c54;
  font-size: 14px;
  font-weight: 700;
  white-space: nowrap;
  background: transparent;
  cursor: pointer;
  transition:
    border-color 0.2s,
    color 0.2s,
    background 0.2s;
}

.history-button:hover:not(:disabled) {
  border-color: #e7e0d8;
  color: #6d28d9;
  background: #faf8ff;
}

.history-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.history-button-icon {
  font-size: 15px;
  line-height: 1;
}

.history-session-row {
  position: relative;
  min-width: 0;
}

.history-session-row .history-session-item {
  padding-right: 58px;
}

.history-session-delete {
  position: absolute;
  top: 8px;
  right: 7px;
  min-width: 44px;
  height: 28px;
  padding: 0 7px;
  border: 0;
  border-radius: 7px;
  color: #8a8178;
  font-size: 12px;
  font-weight: 600;
  background: transparent;
  opacity: 0;
  cursor: pointer;
  transition:
    opacity 0.2s,
    color 0.2s,
    background 0.2s;
}

.history-session-row:hover .history-session-delete,
.history-session-row:focus-within .history-session-delete {
  opacity: 1;
}

.history-session-delete:hover:not(:disabled) {
  color: #dc2626;
  background: #fee2e2;
}

.history-session-delete:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

@media (hover: none) {
  .history-session-delete {
    opacity: 1;
  }
}

.new-session-button {
  display: inline-flex;
  flex: 0 0 auto;
  min-height: 42px;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 0 15px;
  border: 1px solid #c4b5fd;
  border-radius: 12px;
  color: #6d28d9;
  font-size: 14px;
  font-weight: 700;
  white-space: nowrap;
  background: #fff;
  cursor: pointer;
  transition:
    border-color 0.2s,
    background 0.2s,
    box-shadow 0.2s,
    transform 0.2s;
}

.new-session-button:hover:not(:disabled) {
  transform: translateY(-1px);
  border-color: #8b5cf6;
  background: #faf8ff;
  box-shadow: 0 8px 18px rgba(109, 40, 217, 0.1);
}

.new-session-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.dialogue-shell {
  display: flex;
  width: calc(100% - 24px);
  max-width: 1380px;
  min-width: 0;
  min-height: 0;
  flex: 1 1 auto;
  flex-direction: column;
  margin: 8px auto 12px;
  overflow: hidden;
  border: 1px solid rgba(229, 222, 212, 0.84);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 20px 54px rgba(92, 67, 43, 0.1);
}

.assistant-toolbar {
  display: flex;
  width: 100%;
  min-width: 0;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 11px 18px;
  border-bottom: 1px solid #eee8e1;
  background:
    linear-gradient(
      135deg,
      rgba(255, 250, 245, 0.98),
      rgba(250, 248, 255, 0.98)
    );
}

.assistant-profile {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 11px;
}

.assistant-avatar,
.message-avatar,
.typing-avatar {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
}

.assistant-avatar {
  width: 38px;
  height: 38px;
  border: 1px solid #ddd6fe;
  border-radius: 12px;
  font-size: 18px;
  background: #f5f3ff;
  box-shadow: 0 6px 14px rgba(109, 40, 217, 0.08);
}

.assistant-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.assistant-copy strong {
  color: #312a25;
  font-size: 15px;
  line-height: 1.4;
}


.location-toolbar {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.location-button {
  display: inline-flex;
  min-height: 40px;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 0 13px;
  border: 1px solid #fed7aa;
  border-radius: 11px;
  color: #c2410c;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  background: #fffaf5;
  cursor: pointer;
  transition:
    border-color 0.2s,
    background 0.2s;
}

.location-button:hover:not(:disabled) {
  border-color: #fb923c;
  background: #fff7ed;
}

.location-button:disabled {
  opacity: 0.6;
  cursor: wait;
}

.location-icon {
  font-size: 18px;
  line-height: 1;
}

.location-status {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 7px;
  color: #8a8178;
  font-size: 13px;
  white-space: nowrap;
}

.location-status-dot {
  width: 7px;
  height: 7px;
  background: #cbd5e1;
}

.location-status-dot.ready {
  background: #22c55e;
  box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.12);
}

.constraint-bar {
  display: flex;
  width: 100%;
  min-width: 0;
  flex: 0 0 auto;
  align-items: flex-start;
  gap: 10px;
  padding: 11px 22px;
  border-bottom: 1px solid #eee8e1;
  background: #fffaf5;
}

.constraint-label {
  flex: 0 0 auto;
  padding: 3px 8px;
  border-radius: 7px;
  color: #9a3412;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.5;
  background: #ffedd5;
}

.constraint-content {
  min-width: 0;
  color: #6f665e;
  font-size: 14px;
  line-height: 1.65;
  overflow-wrap: anywhere;
}

.message-list {
  width: 100%;
  min-width: 0;
  min-height: 0;
  flex: 1 1 auto;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 24px 32px;
  scroll-behavior: smooth;
  scrollbar-color: #d6cec5 transparent;
  scrollbar-width: thin;
}

.message-list::-webkit-scrollbar {
  width: 8px;
}

.message-list::-webkit-scrollbar-track {
  background: transparent;
}

.message-list::-webkit-scrollbar-thumb {
  border: 2px solid transparent;
  border-radius: 999px;
  background: #d6cec5;
  background-clip: padding-box;
}

.empty-panel,
.state-panel {
  display: flex;
  width: 100%;
  min-height: 100%;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  text-align: center;
}

.empty-panel {
  max-width: 630px;
  margin: 0 auto;
  color: #80766e;
}

.empty-icon-shell {
  display: grid;
  width: 76px;
  height: 76px;
  margin-bottom: 16px;
  place-items: center;
  border: 1px solid #ddd6fe;
  border-radius: 24px;
  background:
    radial-gradient(
      circle at 35% 30%,
      rgba(255, 255, 255, 0.9),
      transparent 36%
    ),
    linear-gradient(135deg, #f5f3ff, #ede9fe);
  box-shadow: 0 14px 30px rgba(109, 40, 217, 0.1);
}

.empty-icon {
  font-size: 34px;
}


.empty-panel h2 {
  margin: 0;
  color: #29231e;
  font-size: 29px;
  line-height: 1.4;
}





.state-panel {
  gap: 8px;
  color: #80766e;
}

.state-panel strong {
  color: #4d453e;
  font-size: 17px;
}


.state-spinner {
  width: 34px;
  height: 34px;
  margin-bottom: 6px;
  border: 3px solid #eee8e1;
  border-top-color: #8b5cf6;
  border-radius: 50%;
  animation: spin 0.9s linear infinite;
}

.message-row {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: flex-start;
  gap: 11px;
  margin-bottom: 24px;
}

.user-row {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 38px;
  height: 38px;
  margin-top: 21px;
  border: 1px solid #e4ddd6;
  border-radius: 13px;
  color: #6d28d9;
  font-size: 15px;
  font-weight: 700;
  background: #f5f3ff;
}

.user-row .message-avatar {
  border-color: #fed7aa;
  color: #c2410c;
  background: #fff3e8;
}

.message-column {
  display: flex;
  width: auto;
  max-width: calc(100% - 49px);
  min-width: 0;
  flex-direction: column;
}

.user-row .message-column {
  align-items: flex-end;
}

.message-role {
  margin: 0 4px 6px;
  color: #8a8178;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.4;
}

.message-bubble {
  width: fit-content;
  max-width: 100%;
  min-width: 0;
  padding: 17px 18px;
  border: 1px solid #e7e0d8;
  border-radius: 18px 18px 18px 6px;
  color: #3f3832;
  background: #fbfaf8;
  box-shadow: 0 5px 16px rgba(80, 61, 43, 0.05);
}

.assistant-row .message-column {
  width: min(94%, 1120px);
}

.assistant-row .message-bubble {
  width: 100%;
}

.user-row .message-column {
  max-width: min(72%, 650px);
}

.user-row .message-bubble {
  border-color: transparent;
  border-radius: 18px 18px 6px 18px;
  color: #fff;
  background: linear-gradient(135deg, #6d5bd0 0%, #8b5cf6 100%);
  box-shadow: 0 10px 24px rgba(109, 40, 217, 0.18);
}

.message-bubble > p {
  margin: 0;
  font-size: 16px;
  line-height: 1.75;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.notice-banner {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: 8px;
  margin-top: 12px;
  padding: 10px 12px;
  border: 1px solid #ddd6fe;
  border-radius: 10px;
  color: #5b4e78;
  font-size: 14px;
  line-height: 1.55;
  background: #f5f3ff;
}

.notice-banner > span {
  flex: 0 0 auto;
  font-weight: 800;
}

.merchant-grid {
  display: grid;
  width: 100%;
  min-width: 0;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 17px;
}

.merchant-card {
  display: flex;
  width: 100%;
  min-width: 0;
  min-height: 250px;
  flex-direction: column;
  padding: 17px;
  overflow: hidden;
  border: 1px solid #e8e1da;
  border-radius: 16px;
  text-align: left;
  background:
    linear-gradient(
      145deg,
      rgba(255, 255, 255, 0.98),
      rgba(255, 250, 245, 0.94)
    );
  cursor: pointer;
  transition:
    transform 0.22s,
    border-color 0.22s,
    box-shadow 0.22s;
}

.merchant-card:hover {
  transform: translateY(-3px);
  border-color: #c4b5fd;
  box-shadow: 0 14px 28px rgba(91, 60, 30, 0.1);
}

.merchant-card:focus-visible {
  outline: 3px solid rgba(139, 92, 246, 0.18);
  outline-offset: 2px;
}

.merchant-card-header {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.rank-badge {
  padding: 4px 8px;
  border-radius: 7px;
  color: #6d28d9;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.04em;
  background: #ede9fe;
}

.operation-status {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
  color: #7d746c;
  font-size: 12px;
  white-space: nowrap;
}

.operation-dot {
  width: 6px;
  height: 6px;
  background: #22c55e;
}

.merchant-card h3 {
  margin: 12px 0 9px;
  color: #29231e;
  font-size: 19px;
  font-weight: 750;
  line-height: 1.4;
  overflow-wrap: anywhere;
}

.merchant-meta {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: 6px;
}

.merchant-meta span {
  max-width: 100%;
  padding: 4px 8px;
  border: 1px solid #eee8e1;
  border-radius: 7px;
  color: #655c54;
  font-size: 13px;
  line-height: 1.3;
  overflow-wrap: anywhere;
  background: #f8f6f2;
}

.reason {
  margin: 13px 0 0 !important;
  color: #5d554e;
  font-size: 15px !important;
  line-height: 1.65 !important;
  overflow-wrap: anywhere;
}

.risk-list {
  margin: 10px 0 0;
  padding-left: 20px;
  color: #b45309;
  font-size: 13px;
  line-height: 1.6;
}

.matched-dishes {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px;
  margin-top: 11px;
}

.matched-dishes-title {
  color: #7d746c;
  font-size: 13px;
  font-weight: 700;
}

.matched-dish {
  max-width: 100%;
  padding: 5px 9px;
  border: 1px solid #fed7aa;
  border-radius: 999px;
  color: #9a3412;
  font-size: 13px;
  line-height: 1.35;
  overflow-wrap: anywhere;
  background: #fff7ed;
}

.merchant-card-footer {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: auto;
  padding-top: 15px;
}

.merchant-detail-hint {
  min-width: 0;
  color: #9a9087;
  font-size: 12px;
  line-height: 1.4;
  overflow-wrap: anywhere;
}

.evidence-button {
  display: inline-flex;
  flex: 0 0 auto;
  min-height: 38px;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  border: 1px solid #c4b5fd;
  border-radius: 9px;
  color: #6d28d9;
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
  background: #fff;
  cursor: pointer;
  transition:
    border-color 0.2s,
    background 0.2s;
}

.evidence-button:hover {
  border-color: #8b5cf6;
  background: #faf8ff;
}

.suggestion-panel,
.limiting-panel {
  min-width: 0;
  margin-top: 14px;
  padding: 14px;
  border-radius: 12px;
}

.suggestion-panel {
  border: 1px solid #fed7aa;
  color: #9a3412;
  background: #fff7ed;
}

.limiting-panel {
  border: 1px solid #e5e7eb;
  color: #5f574f;
  background: #f8fafc;
}

.panel-heading {
  display: flex;
  align-items: center;
  gap: 8px;
}

.panel-icon {
  display: grid;
  width: 24px;
  height: 24px;
  flex: 0 0 24px;
  place-items: center;
  border-radius: 7px;
  font-size: 13px;
  font-weight: 800;
  background: rgba(255, 255, 255, 0.7);
}

.suggestion-list {
  display: grid;
  min-width: 0;
  gap: 8px;
  margin-top: 10px;
}

.suggestion-button {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 11px 12px;
  border: 1px solid #fdba74;
  border-radius: 10px;
  color: #9a3412;
  font-size: 14px;
  text-align: left;
  background: #fff;
  cursor: pointer;
}

.suggestion-button > span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.suggestion-button:hover:not(:disabled) {
  border-color: #f97316;
  box-shadow: 0 3px 12px rgba(249, 115, 22, 0.14);
}

.suggestion-button:disabled {
  opacity: 0.6;
  cursor: wait;
}

.suggestion-button small {
  flex: 0 0 auto;
  color: #c2410c;
  font-size: 12px;
  white-space: nowrap;
}

.limiting-panel ul {
  margin: 9px 0 0;
  padding-left: 21px;
  font-size: 14px;
  line-height: 1.65;
}

.typing-indicator {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: 11px;
  color: #6d28d9;
  font-size: 14px;
}

.typing-avatar {
  width: 38px;
  height: 38px;
  border: 1px solid #ddd6fe;
  border-radius: 13px;
  background: #f5f3ff;
}

.typing-bubble {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
  padding: 11px 14px;
  border: 1px solid #e7e0d8;
  border-radius: 14px 14px 14px 5px;
  color: #6f665e;
  background: #fbfaf8;
}

.typing-dots {
  display: inline-flex;
  gap: 4px;
}

.typing-dots i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #8b5cf6;
  animation: typing 1.2s infinite ease-in-out;
}

.typing-dots i:nth-child(2) {
  animation-delay: 0.15s;
}

.typing-dots i:nth-child(3) {
  animation-delay: 0.3s;
}

.error-banner {
  display: flex;
  min-width: 0;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
  margin: 0 22px 12px;
  padding: 12px 14px;
  border: 1px solid #fecaca;
  border-radius: 11px;
  color: #b42318;
  font-size: 14px;
  line-height: 1.55;
  background: #fef3f2;
}

.error-icon {
  display: grid;
  width: 24px;
  height: 24px;
  flex: 0 0 24px;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  font-weight: 800;
  background: #dc2626;
}

.error-text {
  min-width: 0;
  flex: 1 1 auto;
  overflow-wrap: anywhere;
}

.retry-button {
  flex: 0 0 auto;
  min-height: 34px;
  padding: 0 10px;
  border: 1px solid #b42318;
  border-radius: 8px;
  color: #b42318;
  font-size: 13px;
  white-space: nowrap;
  background: #fff;
  cursor: pointer;
}

.composer {
  width: 100%;
  min-width: 0;
  flex: 0 0 auto;
  padding: 12px 20px 14px;
  border-top: 1px solid #eee8e1;
  background: rgba(255, 255, 255, 0.98);
}

.composer-input-shell {
  width: 100%;
  min-width: 0;
  padding: 5px;
  border: 1px solid #d8d1c9;
  border-radius: 16px;
  background: #fff;
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
}

.composer-input-shell:focus-within {
  border-color: #a78bfa;
  box-shadow: 0 0 0 4px rgba(139, 92, 246, 0.1);
}

.composer textarea {
  display: block;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  min-height: 48px;
  padding: 7px 11px 4px;
  resize: none;
  border: 0;
  outline: 0;
  color: #39332e;
  font-size: 16px;
  line-height: 1.65;
  background: transparent;
}

.composer textarea::placeholder {
  color: #a09890;
}

.composer textarea:disabled {
  cursor: not-allowed;
}

.composer-footer {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
  padding: 4px 4px 1px 10px;
}


.composer-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
}

.word-count {
  color: #a09890;
  font-size: 12px;
  white-space: nowrap;
}

.composer button[type="submit"] {
  display: inline-flex;
  min-width: 118px;
  min-height: 42px;
  align-items: center;
  justify-content: center;
  gap: 9px;
  padding: 0 14px;
  border: 0;
  border-radius: 11px;
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  white-space: nowrap;
  background: linear-gradient(135deg, #6d5bd0, #8b5cf6);
  box-shadow: 0 9px 20px rgba(109, 40, 217, 0.19);
  cursor: pointer;
  transition:
    transform 0.2s,
    box-shadow 0.2s;
}

.composer button[type="submit"]:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 12px 24px rgba(109, 40, 217, 0.25);
}

.composer button[type="submit"]:disabled {
  opacity: 0.55;
  box-shadow: none;
  cursor: not-allowed;
}

.send-icon {
  display: grid;
  width: 22px;
  height: 22px;
  place-items: center;
  border-radius: 7px;
  color: #6d28d9;
  background: rgba(255, 255, 255, 0.9);
}


.history-sidebar {
  display: flex;
  width: 286px;
  min-width: 286px;
  height: 100%;
  flex: 0 0 286px;
  flex-direction: column;
  overflow: hidden;
  border-right: 1px solid #e7e0d8;
  background: rgba(248, 247, 244, 0.98);
}

.history-sidebar-header {
  display: flex;
  min-width: 0;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 14px 10px;
}

.history-sidebar-title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 9px;
  color: #312a25;
  font-size: 15px;
}

.history-sidebar-logo {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  place-items: center;
  border: 1px solid #ddd6fe;
  border-radius: 9px;
  background: #f5f3ff;
}

.history-close-button {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  place-items: center;
  border: 0;
  border-radius: 9px;
  color: #7d746c;
  font-size: 26px;
  line-height: 1;
  background: transparent;
  cursor: pointer;
}

.history-close-button:hover {
  color: #6d28d9;
  background: #ede9fe;
}

.sidebar-new-session-button {
  display: flex;
  min-height: 44px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: flex-start;
  gap: 9px;
  margin: 2px 12px 10px;
  padding: 0 13px;
  border: 1px solid #ded8d1;
  border-radius: 11px;
  color: #3f3832;
  font-size: 14px;
  font-weight: 700;
  background: #fff;
  cursor: pointer;
  transition:
    border-color 0.2s,
    background 0.2s,
    box-shadow 0.2s;
}

.sidebar-new-session-button:hover:not(:disabled) {
  border-color: #c4b5fd;
  background: #faf8ff;
  box-shadow: 0 5px 14px rgba(109, 40, 217, 0.08);
}

.sidebar-new-session-button:disabled {
  opacity: 0.58;
  cursor: not-allowed;
}

.history-sidebar-content {
  min-width: 0;
  min-height: 0;
  flex: 1 1 auto;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 4px 10px 14px;
  scrollbar-color: #d6cec5 transparent;
  scrollbar-width: thin;
}

.history-state {
  display: flex;
  min-height: 180px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 12px;
  padding: 24px 12px;
  color: #8a8178;
  font-size: 14px;
  text-align: center;
}

.history-error {
  color: #b42318;
}

.history-retry-button {
  min-height: 36px;
  padding: 0 12px;
  border: 1px solid #c4b5fd;
  border-radius: 9px;
  color: #6d28d9;
  font-weight: 700;
  background: #fff;
  cursor: pointer;
}

.history-session-list {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.history-session-item {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: flex-start;
  flex-direction: column;
  gap: 4px;
  padding: 10px 11px;
  border: 0;
  border-radius: 9px;
  color: #4d453e;
  text-align: left;
  background: transparent;
  cursor: pointer;
  transition:
    color 0.2s,
    background 0.2s;
}

.history-session-item:hover:not(:disabled) {
  background: #efede9;
}

.history-session-item.active {
  color: #5b21b6;
  background: #ede9fe;
}

.history-session-item:disabled {
  opacity: 0.58;
  cursor: not-allowed;
}

.history-session-title {
  width: 100%;
  min-width: 0;
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-session-time {
  color: #9a9087;
  font-size: 11px;
  line-height: 1.35;
}

.history-mobile-mask {
  display: none;
}

.dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 300;
  display: grid;
  width: 100%;
  max-width: 100%;
  place-items: center;
  padding: 20px;
  overflow: hidden;
  background: rgba(41, 35, 30, 0.52);
  backdrop-filter: blur(6px);
}

.evidence-dialog {
  width: min(700px, calc(100vw - 40px));
  max-width: 100%;
  max-height: min(82vh, 760px);
  overflow-x: hidden;
  overflow-y: auto;
  padding: 22px;
  border: 1px solid #e7e0d8;
  border-radius: 20px;
  background: #fff;
  box-shadow: 0 28px 70px rgba(41, 35, 30, 0.2);
}

.evidence-dialog > header {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid #eee8e1;
}

.dialog-eyebrow {
  display: block;
  margin-bottom: 4px;
  color: #7c3aed;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.05em;
}

.evidence-dialog h2 {
  margin: 0;
  color: #29231e;
  font-size: 25px;
  line-height: 1.35;
}

.dialog-close-button {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  place-items: center;
  border: 1px solid #e7e0d8;
  border-radius: 11px;
  color: #7d746c;
  font-size: 22px;
  line-height: 1;
  background: #fff;
  cursor: pointer;
}

.dialog-close-button:hover {
  border-color: #fdba74;
  color: #c2410c;
  background: #fff8f1;
}

.dialog-state-panel,
.dialog-error-banner {
  margin-top: 16px;
  padding: 24px;
  border-radius: 12px;
  text-align: center;
  font-size: 14px;
}

.dialog-state-panel {
  color: #80766e;
  background: #faf8f5;
}

.dialog-error-banner {
  color: #b42318;
  background: #fef3f2;
}

.evidence-list {
  display: grid;
  gap: 12px;
  margin-top: 16px;
}

.evidence-item {
  min-width: 0;
  padding: 15px;
  border: 1px solid #e7e0d8;
  border-radius: 13px;
  background: #fcfbf9;
}

.evidence-item-header {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.evidence-item-header strong {
  flex: 0 0 auto;
  padding: 4px 8px;
  border-radius: 7px;
  color: #6d28d9;
  font-size: 13px;
  background: #ede9fe;
}

.evidence-item-header span {
  min-width: 0;
  color: #655c54;
  font-size: 14px;
  font-weight: 600;
  text-align: right;
  overflow-wrap: anywhere;
}

.evidence-condition {
  display: block;
  margin-top: 8px;
  color: #9a3412;
  font-size: 13px;
}

.evidence-item p {
  margin: 11px 0 0;
  color: #514943;
  font-size: 15px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.evidence-item small {
  display: block;
  margin-top: 8px;
  color: #9a9087;
  font-size: 12px;
}

.unavailable-text {
  color: #9a3412 !important;
}


@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes typing {
  0%,
  60%,
  100% {
    transform: translateY(0);
    opacity: 0.45;
  }

  30% {
    transform: translateY(-4px);
    opacity: 1;
  }
}

@media (max-width: 980px) {
  .history-sidebar {
    position: fixed;
    inset: 0 auto 0 0;
    z-index: 290;
    width: min(320px, calc(100vw - 52px));
    min-width: 0;
    height: 100dvh;
    flex-basis: auto;
    box-shadow: 18px 0 48px rgba(41, 35, 30, 0.18);
  }

  .history-mobile-mask {
    position: fixed;
    inset: 0;
    z-index: 280;
    display: block;
    background: rgba(41, 35, 30, 0.3);
    backdrop-filter: blur(3px);
  }

  .header-container {
    grid-template-columns: auto minmax(0, 1fr) auto;
  }

  .session-state {
    display: none;
  }

  .assistant-row .message-column {
    width: calc(100% - 49px);
  }

  .merchant-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 760px) {
  .page-header {
    padding: 14px 0;
  }

  .header-container {
    width: calc(100% - 32px);
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 12px;
  }

  .page-title {
    grid-column: 1 / -1;
    grid-row: 1;
    padding-bottom: 4px;
  }

  .page-title h1 {
    font-size: 30px;
  }

  .header-leading {
    grid-column: 1;
    grid-row: 2;
  }

  .header-actions {
    grid-column: 2;
    grid-row: 2;
  }

  .dialogue-shell {
    width: calc(100% - 16px);
    margin: 6px auto 8px;
    border-radius: 18px;
  }

  .assistant-toolbar {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }

  .location-toolbar {
    width: 100%;
    justify-content: space-between;
  }

  .message-list {
    min-height: 0;
    padding: 20px 16px;
  }

  .message-column,
  .assistant-row .message-column {
    max-width: calc(100% - 49px);
    width: calc(100% - 49px);
  }

  .user-row .message-column {
    max-width: calc(100% - 49px);
  }

  .message-bubble {
    padding: 15px;
  }

  .merchant-card {
    min-height: 0;
  }

  .composer {
    padding: 14px;
  }

  .error-banner {
    margin-right: 14px;
    margin-left: 14px;
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .retry-button {
    margin-left: 34px;
  }
}

@media (max-width: 520px) {
  .title-eyebrow,
  .online-badge {
    display: none;
  }

  .page-title h1 {
    font-size: 28px;
  }

  .back-button,
  .history-button,
  .new-session-button {
    min-height: 40px;
    padding-right: 11px;
    padding-left: 11px;
    font-size: 13px;
  }

  .location-status {
    display: none;
  }

  .location-toolbar {
    justify-content: flex-start;
  }

  .location-button {
    width: 100%;
  }

  .constraint-bar {
    align-items: flex-start;
    flex-direction: column;
  }

  .message-list {
    padding-right: 12px;
    padding-left: 12px;
  }

  .message-avatar,
  .typing-avatar {
    width: 34px;
    height: 34px;
  }

  .message-column,
  .assistant-row .message-column,
  .user-row .message-column {
    width: calc(100% - 45px);
    max-width: calc(100% - 45px);
  }

  .message-avatar {
    margin-top: 20px;
  }

  .merchant-card-footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .evidence-button {
    width: 100%;
    justify-content: center;
  }

  .suggestion-button {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
  }

  .composer-footer {
    align-items: flex-end;
  }

  .composer button[type="submit"] {
    min-width: 94px;
  }

  .evidence-dialog {
    width: calc(100vw - 24px);
    padding: 17px;
  }

  .evidence-item-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }

  .evidence-item-header span {
    text-align: left;
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
  overflow-x: clip;
}
</style>
