<template>
  <MerchantLayout title="商户首页" subtitle="查看今日经营数据和快捷操作">
    <div class="dashboard-container">
      <div class="today-section">
        <div class="today-header">
          <h2 class="section-title">今日概览</h2>
          <span class="today-date">{{ todayDate }}</span>
        </div>
        
        <div class="today-cards">
          <div class="today-card">
            <div class="today-icon orders">
              <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#fff" stroke-width="2">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                <circle cx="12" cy="7" r="4"></circle>
              </svg>
            </div>
            <div class="today-info">
              <p class="today-label">今日订单</p>
              <p class="today-value">{{ todayStats.orders }}</p>
              <p :class="['today-change', todayStats.orderChange >= 0 ? 'positive' : 'negative']">
                {{ todayStats.orderChange >= 0 ? '↑' : '↓' }} {{ Math.abs(todayStats.orderChange) }}% 较昨日
              </p>
            </div>
          </div>

          <div class="today-card">
            <div class="today-icon revenue">
              <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#fff" stroke-width="2">
                <line x1="12" y1="1" x2="12" y2="23"></line>
                <path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"></path>
              </svg>
            </div>
            <div class="today-info">
              <p class="today-label">今日营收</p>
              <p class="today-value">￥{{ todayStats.revenue.toLocaleString() }}</p>
              <p :class="['today-change', todayStats.revenueChange >= 0 ? 'positive' : 'negative']">
                {{ todayStats.revenueChange >= 0 ? '↑' : '↓' }} {{ Math.abs(todayStats.revenueChange) }}% 较昨日
              </p>
            </div>
          </div>

          <div class="today-card">
            <div class="today-icon reviews">
              <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#fff" stroke-width="2">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                <polyline points="14 2 14 8 20 8"></polyline>
                <line x1="16" y1="13" x2="8" y2="13"></line>
                <line x1="16" y1="17" x2="8" y2="17"></line>
                <polyline points="10 9 9 9 8 9"></polyline>
              </svg>
            </div>
            <div class="today-info">
              <p class="today-label">今日评价</p>
              <p class="today-value">{{ todayStats.reviews }}</p>
              <p :class="['today-change', todayStats.reviewChange >= 0 ? 'positive' : 'negative']">
                {{ todayStats.reviewChange >= 0 ? '↑' : '↓' }} {{ Math.abs(todayStats.reviewChange) }}% 较昨日
              </p>
            </div>
          </div>

          <div class="today-card">
            <div class="today-icon visits">
              <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#fff" stroke-width="2">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                <circle cx="9" cy="7" r="4"></circle>
                <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
                <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
              </svg>
            </div>
            <div class="today-info">
              <p class="today-label">今日访问</p>
              <p class="today-value">{{ todayStats.visits }}</p>
              <p :class="['today-change', todayStats.visitChange >= 0 ? 'positive' : 'negative']">
                {{ todayStats.visitChange >= 0 ? '↑' : '↓' }} {{ Math.abs(todayStats.visitChange) }}% 较昨日
              </p>
            </div>
          </div>

          <div class="today-card">
            <div class="today-icon favorites">
              <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#fff" stroke-width="2">
                <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"></path>
              </svg>
            </div>
            <div class="today-info">
              <p class="today-label">今日收藏</p>
              <p class="today-value">{{ todayStats.favorites }}</p>
              <p :class="['today-change', todayStats.favoriteChange >= 0 ? 'positive' : 'negative']">
                {{ todayStats.favoriteChange >= 0 ? '↑' : '↓' }} {{ Math.abs(todayStats.favoriteChange) }}% 较昨日
              </p>
            </div>
          </div>

          <div class="today-card">
            <div class="today-icon avg-order">
              <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#fff" stroke-width="2">
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
                <polyline points="9 12 12 15 15 12"></polyline>
              </svg>
            </div>
            <div class="today-info">
              <p class="today-label">今日客单价</p>
              <p class="today-value">￥{{ todayStats.avgOrder.toFixed(2) }}</p>
              <p :class="['today-change', todayStats.avgOrderChange >= 0 ? 'positive' : 'negative']">
                {{ todayStats.avgOrderChange >= 0 ? '↑' : '↓' }} {{ Math.abs(todayStats.avgOrderChange) }}% 较昨日
              </p>
            </div>
          </div>
        </div>
      </div>

      <div class="shortcuts-section">
        <div class="section-header">
          <h2 class="section-title">快捷操作</h2>
          <button class="add-shortcut-btn" @click="showAddShortcut = true">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"></line>
              <line x1="5" y1="12" x2="19" y2="12"></line>
            </svg>
            添加快捷方式
          </button>
        </div>

        <div v-if="shortcuts.length > 0" class="shortcuts-grid">
          <div 
            v-for="shortcut in shortcuts" 
            :key="shortcut.id" 
            class="shortcut-card"
            :style="{ background: shortcut.color }"
            @click="navigateTo(shortcut.path)"
          >
            <div class="shortcut-icon">
              <svg :viewBox="getShortcutIcon(shortcut.path).viewBox" width="24" height="24" fill="none" stroke="#fff" stroke-width="2">
                <path :d="getShortcutIcon(shortcut.path).path" />
              </svg>
            </div>
            <span class="shortcut-label">{{ shortcut.label }}</span>
            <button class="delete-shortcut" @click.stop="removeShortcut(shortcut.id)">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="rgba(255,255,255,0.7)" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"></line>
                <line x1="6" y1="6" x2="18" y2="18"></line>
              </svg>
            </button>
          </div>
        </div>
        <div v-else class="empty-shortcuts">
          <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="#ccc" stroke-width="2">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
            <polyline points="9 12 12 15 15 12"></polyline>
          </svg>
          <p>暂无快捷方式</p>
          <button class="add-first-btn" @click="showAddShortcut = true">添加第一个快捷方式</button>
        </div>
      </div>

      <div class="bottom-section">
        <div class="card">
          <div class="card-header">
            <h3>最近评价</h3>
            <button class="view-all-btn" @click="navigateTo('/merchant/reviews')">查看全部</button>
          </div>
          <div class="card-content">
            <div v-if="recentReviews.length > 0" class="review-list">
              <div v-for="review in recentReviews" :key="review.id" class="review-item">
                <div class="review-rating">
                  <span v-for="i in 5" :key="i" class="star" :class="{ filled: i <= review.rating }">★</span>
                </div>
                <div class="review-content">
                  <p class="review-text">{{ review.content }}</p>
                  <p class="review-meta">{{ review.merchantName }} · {{ review.username }} · {{ review.time }}</p>
                </div>
              </div>
            </div>
            <div v-else class="empty-state">
              <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="#ccc" stroke-width="2">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                <polyline points="14 2 14 8 20 8"></polyline>
              </svg>
              <p>暂无评价</p>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-header">
            <h3>经营建议</h3>
          </div>
          <div class="card-content">
            <div v-if="businessAdvice.length > 0" class="advice-list">
              <div v-for="(advice, index) in businessAdvice" :key="index" class="advice-item">
                <div class="advice-icon" :class="advice.type">
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="#fff" stroke-width="2">
                    <path v-if="advice.type === 'warning'" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path>
                    <path v-else-if="advice.type === 'success'" d="M9 12l2 2 4-4m6 2a9 9 0 1 1-18 0 9 9 0 0 1 18 0z"></path>
                    <path v-else d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z"></path>
                  </svg>
                </div>
                <p class="advice-text">{{ advice.content }}</p>
              </div>
            </div>
            <div v-else class="empty-state">
              <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="#ccc" stroke-width="2">
                <path d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 1 1 7.072 0l-.548.547A3.374 3.374 0 0 0 14 18.469V19a2 2 0 1 1-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z"></path>
              </svg>
              <p>暂无建议</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="showAddShortcut" class="modal-overlay" @click="showAddShortcut = false">
      <div class="modal-content" @click.stop>
        <div class="modal-header">
          <h3>添加快捷方式</h3>
          <button class="modal-close" @click="showAddShortcut = false">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>选择功能</label>
            <select v-model="newShortcut.path" class="form-select">
              <option value="">请选择功能</option>
              <option v-for="item in availableFeatures" :key="item.path" :value="item.path">
                {{ item.label }}
              </option>
            </select>
          </div>
          <div class="form-group">
            <label>选择颜色</label>
            <div class="color-picker">
              <button 
                v-for="color in availableColors" 
                :key="color"
                :class="['color-option', { selected: newShortcut.color === color, disabled: isColorUsed(color) }]"
                :style="{ background: color }"
                :disabled="isColorUsed(color)"
                @click="newShortcut.color = color"
              ></button>
            </div>
            <p v-if="colorError" class="error-text">{{ colorError }}</p>
          </div>
        </div>
        <div class="modal-footer">
          <button class="cancel-btn" @click="showAddShortcut = false">取消</button>
          <button class="confirm-btn" @click="addShortcut" :disabled="!canAddShortcut">添加</button>
        </div>
      </div>
    </div>
  </MerchantLayout>
</template>

<script setup>
import { ref, computed, onMounted, inject } from 'vue'
import { useRouter } from 'vue-router'
import MerchantLayout from '../../components/MerchantLayout.vue'
import { getMerchantReviews } from '../../api/reviewAnalysis'
import { getMyMerchants } from '../../api/merchantConsole'

const router = useRouter()
const activeStoreId = inject('activeMerchantId', ref(null))

const todayDate = computed(() => {
  const now = new Date()
  return `${now.getMonth() + 1}月${now.getDate()}日 周${['日', '一', '二', '三', '四', '五', '六'][now.getDay()]}`
})

const todayStats = ref({
  orders: 28,
  revenue: 5680,
  reviews: 5,
  visits: 128,
  favorites: 8,
  avgOrder: 202.86,
  orderChange: 12,
  revenueChange: 8.5,
  reviewChange: 25,
  visitChange: -5,
  favoriteChange: 15,
  avgOrderChange: 3.2
})

const shortcuts = ref([
  { id: 1, path: '/merchant/dishes', label: '菜品管理', color: 'linear-gradient(135deg, #52c41a 0%, #73d13d 100%)' },
  { id: 2, path: '/merchant/reviews', label: '评价管理', color: 'linear-gradient(135deg, #1890ff 0%, #40a9ff 100%)' },
])

const showAddShortcut = ref(false)
const newShortcut = ref({ path: '', color: '' })
const colorError = ref('')

const availableFeatures = [
  { path: '/merchant/home', label: '店铺首页', iconViewBox: '0 0 24 24', iconPath: 'M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9z M9 22l3-3 3 3' },
  { path: '/merchant/dishes', label: '菜品管理', iconViewBox: '0 0 24 24', iconPath: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2 M12 7a4 4 0 1 1 0 8 4 4 0 0 1 0-8z' },
  { path: '/merchant/reviews', label: '评价管理', iconViewBox: '0 0 24 24', iconPath: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z M14 2v6h6 M16 13H8 M16 17H8 M10 9H8' },
]

const availableColors = [
  'linear-gradient(135deg, #52c41a 0%, #73d13d 100%)',
  'linear-gradient(135deg, #1890ff 0%, #40a9ff 100%)',
  'linear-gradient(135deg, #faad14 0%, #ffc53d 100%)',
  'linear-gradient(135deg, #722ed1 0%, #9254de 100%)',
  'linear-gradient(135deg, #13c2c2 0%, #36cfc9 100%)',
  'linear-gradient(135deg, #eb2f96 0%, #ff69c1 100%)',
  'linear-gradient(135deg, #fa8c16 0%, #ffa940 100%)',
  'linear-gradient(135deg, #531dab 0%, #722ed1 100%)',
]

const recentReviews = ref([])
const merchantMap = ref({})

const formatReviewTime = (dateStr) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const now = new Date()
  const diffMs = now - d
  const diffHours = Math.floor(diffMs / (1000 * 60 * 60))
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24))

  if (diffHours < 1) {
    const diffMins = Math.floor(diffMs / (1000 * 60))
    return diffMins <= 0 ? '刚刚' : `${diffMins}分钟前`
  } else if (diffHours < 24) {
    return `${diffHours}小时前`
  } else if (diffDays < 7) {
    return `${diffDays}天前`
  } else {
    return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  }
}

const loadRecentReviews = async () => {
  try {
    // Load all stores for name mapping
    const storeResp = await getMyMerchants()
    if (storeResp.success && storeResp.data) {
      storeResp.data.forEach(s => {
        merchantMap.value[s.id] = s.name
      })
    }

    // Get reviews for active store
    const storeId = activeStoreId.value
    if (!storeId) {
      // If no active store, try to get reviews for the first store
      const stores = storeResp?.data || []
      if (stores.length === 0) return
      const firstId = stores[0].id
      const resp = await getMerchantReviews(firstId, { pageNum: 1, pageSize: 3 })
      if (resp.success && resp.data) {
        recentReviews.value = (resp.data.records || resp.data || []).slice(0, 3).map(r => ({
          id: r.id,
          rating: Number(r.rating) || 0,
          content: r.content || '',
          username: r.nickname || r.username || '匿名用户',
          merchantName: merchantMap.value[r.merchantId] || '未知店铺',
          time: formatReviewTime(r.publishedAt || r.createdAt)
        }))
      }
      return
    }

    const resp = await getMerchantReviews(storeId, { pageNum: 1, pageSize: 3 })
    if (resp.success && resp.data) {
      recentReviews.value = (resp.data.records || resp.data || []).slice(0, 3).map(r => ({
        id: r.id,
        rating: Number(r.rating) || 0,
        content: r.content || '',
        username: r.nickname || r.username || '匿名用户',
        merchantName: merchantMap.value[r.merchantId] || '未知店铺',
        time: formatReviewTime(r.publishedAt || r.createdAt)
      }))
    }
  } catch (error) {
    console.error('加载最近评价失败:', error)
  }
}

const businessAdvice = ref([
  { type: 'success', content: '今日午市客流量较高，建议增加人手准备' },
  { type: 'warning', content: '有顾客反馈上菜速度较慢，建议优化后厨流程' },
  { type: 'info', content: '招牌菜好评率达95%，建议加大推广' },
])

const canAddShortcut = computed(() => {
  return newShortcut.value.path && newShortcut.value.color && !isColorUsed(newShortcut.value.color)
})

const getShortcutIcon = (path) => {
  const feature = availableFeatures.find(f => f.path === path)
  return feature ? { viewBox: feature.iconViewBox, path: feature.iconPath } : { viewBox: '0 0 24 24', path: 'M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9z' }
}

const isColorUsed = (color) => {
  return shortcuts.value.some(s => s.color === color)
}

const navigateTo = (path) => {
  router.push(path)
}

const addShortcut = () => {
  if (!canAddShortcut.value) return
  
  const feature = availableFeatures.find(f => f.path === newShortcut.value.path)
  shortcuts.value.push({
    id: Date.now(),
    path: newShortcut.value.path,
    label: feature ? feature.label : newShortcut.value.path,
    color: newShortcut.value.color
  })
  
  newShortcut.value = { path: '', color: '' }
  showAddShortcut.value = false
}

const removeShortcut = (id) => {
  shortcuts.value = shortcuts.value.filter(s => s.id !== id)
}

onMounted(() => {
  loadRecentReviews()
})
</script>

<style scoped>
.dashboard-container {
  width: 100%;
}

.today-section {
  margin-bottom: 24px;
}

.today-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.section-title {
  font-size: 20px;
  font-weight: 600;
  color: #1f2d3d;
  margin: 0;
}

.today-date {
  font-size: 14px;
  color: #667085;
}

.today-cards {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 16px;
}

.today-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  transition: transform 0.2s, box-shadow 0.2s;
}

.today-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.today-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.today-icon.orders {
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
}

.today-icon.revenue {
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
}

.today-icon.reviews {
  background: linear-gradient(135deg, #faad14 0%, #ffc53d 100%);
}

.today-icon.visits {
  background: linear-gradient(135deg, #722ed1 0%, #9254de 100%);
}

.today-icon.favorites {
  background: linear-gradient(135deg, #eb2f96 0%, #ff69c1 100%);
}

.today-icon.avg-order {
  background: linear-gradient(135deg, #13c2c2 0%, #36cfc9 100%);
}

.today-info {
  text-align: center;
}

.today-label {
  font-size: 13px;
  color: #667085;
  margin: 0;
}

.today-value {
  font-size: 24px;
  font-weight: 700;
  color: #1f2d3d;
  margin: 4px 0 0;
}

.today-change {
  font-size: 12px;
  margin: 4px 0 0;
}

.today-change.positive {
  color: #52c41a;
}

.today-change.negative {
  color: #ff4d4f;
}

.shortcuts-section {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.add-shortcut-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  font-size: 13px;
  color: #52c41a;
  background: #f6ffed;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.add-shortcut-btn:hover {
  background: #d9f7be;
}

.shortcuts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 16px;
}

.shortcut-card {
  position: relative;
  border-radius: 12px;
  padding: 24px 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  transition: transform 0.2s;
}

.shortcut-card:hover {
  transform: translateY(-4px) scale(1.02);
}

.shortcut-icon {
  width: 40px;
  height: 40px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.shortcut-label {
  font-size: 14px;
  font-weight: 500;
  color: #fff;
}

.delete-shortcut {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 24px;
  height: 24px;
  background: rgba(0, 0, 0, 0.2);
  border: none;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s;
}

.shortcut-card:hover .delete-shortcut {
  opacity: 1;
}

.delete-shortcut:hover {
  background: rgba(0, 0, 0, 0.3);
}

.empty-shortcuts {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.empty-shortcuts svg {
  margin-bottom: 12px;
}

.empty-shortcuts p {
  font-size: 14px;
  color: #999;
  margin: 0 0 16px;
}

.add-first-btn {
  padding: 10px 24px;
  font-size: 14px;
  color: #52c41a;
  background: #f6ffed;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.add-first-btn:hover {
  background: #d9f7be;
}

.bottom-section {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 24px;
}

.card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.card-header {
  padding: 20px 24px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: #1f2d3d;
  margin: 0;
}

.view-all-btn {
  font-size: 13px;
  color: #52c41a;
  background: none;
  border: none;
  cursor: pointer;
  transition: color 0.2s;
}

.view-all-btn:hover {
  color: #389e0d;
}

.card-content {
  padding: 20px 24px;
}

.review-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.review-item {
  display: flex;
  gap: 16px;
}

.review-rating {
  flex-shrink: 0;
}

.star {
  font-size: 14px;
  color: #e0e0e0;
}

.star.filled {
  color: #faad14;
}

.review-content {
  flex: 1;
}

.review-text {
  font-size: 14px;
  color: #1f2d3d;
  margin: 0 0 8px;
  line-height: 1.5;
}

.review-meta {
  font-size: 12px;
  color: #999;
  margin: 0;
}

.advice-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.advice-item {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.advice-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.advice-icon.warning {
  background: #fff7e6;
}

.advice-icon.warning svg {
  stroke: #faad14;
}

.advice-icon.success {
  background: #f6ffed;
}

.advice-icon.success svg {
  stroke: #52c41a;
}

.advice-icon.info {
  background: #e6f7ff;
}

.advice-icon.info svg {
  stroke: #1890ff;
}

.advice-text {
  font-size: 14px;
  color: #1f2d3d;
  margin: 0;
  line-height: 1.5;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.empty-state svg {
  margin-bottom: 12px;
}

.empty-state p {
  font-size: 14px;
  color: #999;
  margin: 0;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: #fff;
  border-radius: 12px;
  width: 400px;
  max-width: 90%;
}

.modal-header {
  padding: 20px 24px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.modal-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: #1f2d3d;
  margin: 0;
}

.modal-close {
  background: none;
  border: none;
  color: #999;
  cursor: pointer;
  padding: 4px;
  transition: color 0.2s;
}

.modal-close:hover {
  color: #666;
}

.modal-body {
  padding: 24px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group:last-child {
  margin-bottom: 0;
}

.form-group label {
  display: block;
  font-size: 14px;
  color: #1f2d3d;
  margin-bottom: 8px;
}

.form-select {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  font-size: 14px;
}

.color-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.color-option {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  border: 2px solid transparent;
  cursor: pointer;
  transition: all 0.2s;
}

.color-option:hover {
  transform: scale(1.1);
}

.color-option.selected {
  border-color: #1f2d3d;
  transform: scale(1.15);
}

.color-option.disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.color-option.disabled:hover {
  transform: none;
}

.error-text {
  font-size: 12px;
  color: #ff4d4f;
  margin: 8px 0 0;
}

.modal-footer {
  padding: 16px 24px;
  border-top: 1px solid #f0f0f0;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.cancel-btn {
  padding: 8px 20px;
  font-size: 14px;
  color: #667085;
  background: #f5f7fa;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.cancel-btn:hover {
  background: #eef2f7;
}

.confirm-btn {
  padding: 8px 20px;
  font-size: 14px;
  color: #fff;
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.confirm-btn:hover:not(:disabled) {
  opacity: 0.9;
}

.confirm-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

@media (max-width: 1200px) {
  .today-cards {
    grid-template-columns: repeat(3, 1fr);
  }
  
  .bottom-section {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .today-cards {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 480px) {
  .today-cards {
    grid-template-columns: 1fr;
  }
}
</style>