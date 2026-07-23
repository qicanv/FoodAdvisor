<template>
  <AdminLayout title="系统首页" subtitle="欢迎回来，管理员">
    <section class="stats-section">
      <h2 class="section-title">数据概览</h2>

      <div class="stats-grid">
        <div
          v-for="stat in stats"
          :key="stat.label"
          class="stat-card"
        >
          <div :class="['stat-icon', stat.iconClass]">
            <svg
              :viewBox="stat.iconViewBox"
              width="28"
              height="28"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path :d="stat.iconPath" />
            </svg>
          </div>

          <div class="stat-info">
            <span class="stat-value">{{ stat.value }}</span>
            <span class="stat-label">{{ stat.label }}</span>
          </div>
        </div>
      </div>
    </section>

    <section class="quick-actions">
      <div class="section-header">
        <h2 class="section-title">快捷操作</h2>
        <button class="edit-btn" @click="showEditModal = true">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
          </svg>
          <span>编辑</span>
        </button>
      </div>

      <div class="action-cards">
        <div
          v-for="action in quickActions"
          :key="action.label"
          :class="['action-card', action.bgClass]"
          @click="navigateTo(action.path)"
        >
          <span class="action-emoji">{{ action.emoji }}</span>

          <div class="action-info">
            <h3>{{ action.label }}</h3>
            <p>{{ action.description }}</p>
          </div>

          <svg
            viewBox="0 0 24 24"
            width="18"
            height="18"
            fill="none"
            stroke="#fff"
            stroke-width="2"
            class="action-arrow"
          >
            <path d="M5 12h14M12 5l7 7-7 7"></path>
          </svg>
        </div>
      </div>

      <div v-if="quickActions.length === 0" class="empty-actions">
        <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="#ccc" stroke-width="1.5">
          <path d="M12 20v-6M12 4v6M4 12h6M14 12h6"></path>
        </svg>
        <p>暂无快捷操作</p>
        <button class="add-action-btn" @click="showEditModal = true">添加快捷操作</button>
      </div>
    </section>

    <section class="recent-section">
      <div class="section-header">
        <h2 class="section-title">最近更新的商家</h2>

        <router-link
          to="/admin/restaurants"
          class="view-all"
        >
          查看全部
        </router-link>
      </div>

      <div
        v-if="merchants.length > 0"
        class="recent-list"
      >
        <div
          v-for="item in merchants"
          :key="item.id"
          class="recent-item"
        >
          <div class="recent-info">
            <h4>{{ item.name }}</h4>
            <p>{{ item.category }} · {{ item.address }}</p>
          </div>

          <div class="recent-meta">
            <span
              :class="[
                'status-tag',
                getOperationStatusClass(item.operationStatus),
              ]"
            >
              {{ getOperationStatusText(item.operationStatus) }}
            </span>

            <span class="update-time">
              {{ formatTime(item.updatedAt) }}
            </span>
          </div>
        </div>
      </div>

      <div
        v-else
        class="empty-state"
      >
        <svg
          viewBox="0 0 24 24"
          width="48"
          height="48"
          fill="none"
          stroke="#ccc"
          stroke-width="1.5"
        >
          <path
            d="M8 21l1-17a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4l1 17"
          ></path>
        </svg>

        <p>暂无商家数据</p>
      </div>
    </section>

    <div v-if="showEditModal" class="modal-overlay" @click.self="showEditModal = false">
      <div class="modal-content">
        <div class="modal-header">
          <h3>编辑快捷操作</h3>
          <button class="modal-close" @click="showEditModal = false">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M18 6L6 18M6 6l12 12"></path>
            </svg>
          </button>
        </div>

        <div class="modal-body">
          <div class="current-actions">
            <h4>当前快捷操作</h4>
            <div v-if="quickActions.length === 0" class="empty-tip">
              暂无快捷操作，点击下方添加
            </div>
            <div v-else class="action-list">
              <div v-for="action in quickActions" :key="action.label" class="action-item">
                <span class="action-emoji-small">{{ action.emoji }}</span>
                <span class="action-label">{{ action.label }}</span>
                <button class="remove-btn" @click="removeAction(action.label)">
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M18 6L6 18M6 6l12 12"></path>
                  </svg>
                </button>
              </div>
            </div>
          </div>

          <div class="available-actions">
            <h4>可添加的操作</h4>
            <div v-if="availableActions.length === 0" class="empty-tip">
              所有操作已添加
            </div>
            <div v-else class="action-list">
              <div v-for="action in availableActions" :key="action.label" class="action-item">
                <span class="action-emoji-small">{{ action.emoji }}</span>
                <span class="action-label">{{ action.label }}</span>
                <button class="add-btn" @click="addAction(action)">
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M12 5v14M5 12h14"></path>
                  </svg>
                </button>
              </div>
            </div>
          </div>
        </div>

        <div class="modal-footer">
          <button class="btn btn-secondary" @click="showEditModal = false">关闭</button>
        </div>
      </div>
    </div>
  </AdminLayout>
</template>

<script setup>
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import AdminLayout from '../../components/AdminLayout.vue'
import { getAdminMerchants } from '../../api/adminMerchant'

const router = useRouter()

const merchants = ref([])

const stats = ref([
  {
    value: '0',
    label: '注册用户',
    iconViewBox: '0 0 24 24',
    iconPath:
      'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2 M9 7a4 4 0 1 1 8 0',
    iconClass: 'stat-icon-blue',
  },
  {
    value: '0',
    label: '合作商家',
    iconViewBox: '0 0 24 24',
    iconPath:
      'M8 21l1-17a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4l1 17',
    iconClass: 'stat-icon-green',
  },
  {
    value: '0',
    label: '评价总数',
    iconViewBox: '0 0 24 24',
    iconPath:
      'M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z',
    iconClass: 'stat-icon-orange',
  },
  {
    value: '0',
    label: '平均评分',
    iconViewBox: '0 0 24 24',
    iconPath:
      'M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z',
    iconClass: 'stat-icon-purple',
  },
])

const QUICK_ACTIONS_VERSION = '14'

const defaultQuickActions = [
  {
    label: '模型配置',
    description: '管理 AI 模型服务配置',
    path: '/admin/model-configs',
    emoji: '🤖',
    bgClass: 'action-card-blue',
  },
  {
    label: '运营数据',
    description: '查看平台核心运营数据',
    path: '/admin/dashboard',
    emoji: '📊',
    bgClass: 'action-card-orange',
  },
  {
    label: '数据分析',
    description: '分析用户搜索、点击和反馈行为',
    path: '/admin/analytics',
    emoji: '📈',
    bgClass: 'action-card-purple',
  },
  {
    label: '区域热点',
    description: '分析各区域消费热点和变化趋势',
    path: '/admin/regional-hotspots',
    emoji: '📍',
    bgClass: 'action-card-olive',
  },
  {
    label: '商家统计',
    description: '查看商家状态和运营统计',
    path: '/admin/restaurants?tab=status',
    emoji: '📉',
    bgClass: 'action-card-cyan',
  },
  {
    label: '专题管理',
    description: '管理分类标签和探店专题',
    path: '/admin/topics',
    emoji: '📚',
    bgClass: 'action-card-pink',
  },
  {
    label: '内容审核',
    description: '审核系统标记的可疑评价和内容',
    path: '/admin/moderation',
    emoji: '🔍',
    bgClass: 'action-card-rose',
  },
  {
    label: '推荐测评',
    description: '管理AI推荐算法测评任务',
    path: '/admin/evaluation',
    emoji: '🎯',
    bgClass: 'action-card-amber',
  },
  {
    label: 'AI请求追踪',
    description: '追踪AI服务的请求和响应详情',
    path: '/admin/ai-traces',
    emoji: '🔬',
    bgClass: 'action-card-lime',
  },
  {
    label: 'AI监控',
    description: '监控AI功能运行状态和性能指标',
    path: '/admin/ai-monitor',
    emoji: '📡',
    bgClass: 'action-card-indigo',
  },
  {
    label: '举报审核',
    description: '审核用户举报的违规内容',
    path: '/admin/reports',
    emoji: '🚨',
    bgClass: 'action-card-pink',
  },
  {
    label: '行为分析',
    description: '分析用户搜索、点击和反馈行为',
    path: '/admin/behavior-analysis',
    emoji: '📊',
    bgClass: 'action-card-teal',
  },
]

const getInitialQuickActions = () => {
  const savedVersion = localStorage.getItem('adminQuickActionsVersion')
  const saved = localStorage.getItem('adminQuickActions')
  
  if (saved && savedVersion === QUICK_ACTIONS_VERSION) {
    try {
      return JSON.parse(saved)
    } catch {
      return [...defaultQuickActions]
    }
  } else {
    const defaultActions = [...defaultQuickActions]
    localStorage.setItem('adminQuickActions', JSON.stringify(defaultActions))
    localStorage.setItem('adminQuickActionsVersion', QUICK_ACTIONS_VERSION)
    return defaultActions
  }
}

const quickActions = ref(getInitialQuickActions())
const showEditModal = ref(false)

const loadQuickActions = () => {
  quickActions.value = getInitialQuickActions()
}

const saveQuickActions = () => {
  localStorage.setItem('adminQuickActions', JSON.stringify(quickActions.value))
  localStorage.setItem('adminQuickActionsVersion', QUICK_ACTIONS_VERSION)
}

const removeAction = (label) => {
  quickActions.value = quickActions.value.filter(a => a.label !== label)
  saveQuickActions()
}

const addAction = (action) => {
  quickActions.value.push(action)
  saveQuickActions()
}

const availableActions = computed(() => {
  const usedLabels = quickActions.value.map(a => a.label)
  return [
    { label: '商家管理', description: '管理商家信息', path: '/admin/restaurants', emoji: '🏪', bgClass: 'action-card-green' },
    { label: '模型配置', description: '管理 AI 模型服务配置', path: '/admin/model-configs', emoji: '🤖', bgClass: 'action-card-blue' },
    { label: '运营数据', description: '查看平台核心运营数据', path: '/admin/dashboard', emoji: '📊', bgClass: 'action-card-orange' },
    { label: '数据分析', description: '分析用户搜索、点击和反馈行为', path: '/admin/analytics', emoji: '📈', bgClass: 'action-card-purple' },
    { label: '区域热点', description: '分析各区域消费热点和变化趋势', path: '/admin/regional-hotspots', emoji: '📍', bgClass: 'action-card-olive' },
    { label: '商家统计', description: '查看商家状态和运营统计', path: '/admin/restaurants?tab=status', emoji: '📉', bgClass: 'action-card-cyan' },
    { label: '专题管理', description: '管理分类标签和探店专题', path: '/admin/topics', emoji: '📚', bgClass: 'action-card-pink' },
    { label: '食客管理', description: '管理平台注册用户和食客信息', path: '/admin/diners', emoji: '👥', bgClass: 'action-card-red' },
    { label: '审计日志', description: '查询系统和重要操作的审计日志', path: '/admin/logs', emoji: '📋', bgClass: 'action-card-teal' },
    { label: '违规文本', description: '查看AI识别违规文本的统计和详情', path: '/admin/violation-text', emoji: '🛡️', bgClass: 'action-card-red' },
  { label: '内容审核', description: '审核系统标记的可疑评价和内容', path: '/admin/moderation', emoji: '🔍', bgClass: 'action-card-rose' },
    { label: '推荐测评', description: '管理AI推荐算法测评任务', path: '/admin/evaluation', emoji: '🎯', bgClass: 'action-card-amber' },
    { label: 'AI请求追踪', description: '追踪AI服务的请求和响应详情', path: '/admin/ai-traces', emoji: '🔬', bgClass: 'action-card-lime' },
    { label: 'AI监控', description: '监控AI功能运行状态和性能指标', path: '/admin/ai-monitor', emoji: '📡', bgClass: 'action-card-indigo' },
    { label: '举报审核', description: '审核用户举报的违规内容', path: '/admin/reports', emoji: '🚨', bgClass: 'action-card-pink' },
    { label: '行为分析', description: '分析用户搜索、点击和反馈行为', path: '/admin/behavior-analysis', emoji: '📊', bgClass: 'action-card-teal' },
  ].filter(a => !usedLabels.includes(a.label))
})

const loadData = async () => {
  try {
    const response = await getAdminMerchants({
      pageNum: 1,
      pageSize: 5,
    })

    if (response.success) {
      merchants.value =
        response.data?.value ||
        response.data?.records ||
        []

      stats.value[1].value =
        response.data?.total ||
        merchants.value.length
    }
  } catch (error) {
    console.error('加载管理端首页数据失败：', error)
  }
}

const navigateTo = path => {
  router.push(path)
}

const getOperationStatusClass = status => {
  switch (status) {
    case 'OPERATING':
      return 'status-operating'
    case 'SUSPENDED':
      return 'status-suspended'
    case 'CLOSED_PERMANENTLY':
      return 'status-closed'
    default:
      return ''
  }
}

const getOperationStatusText = status => {
  switch (status) {
    case 'OPERATING':
      return '营业中'
    case 'SUSPENDED':
      return '停业中'
    case 'CLOSED_PERMANENTLY':
      return '永久关闭'
    default:
      return status || '-'
  }
}

const formatTime = dateString => {
  if (!dateString) {
    return '-'
  }

  const date = new Date(dateString)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const hours = Math.floor(diff / (1000 * 60 * 60))
  const days = Math.floor(hours / 24)

  if (hours < 1) {
    return '刚刚'
  }

  if (hours < 24) {
    return `${hours}小时前`
  }

  if (days < 7) {
    return `${days}天前`
  }

  return date.toLocaleDateString('zh-CN')
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.section-title {
  margin-bottom: 20px;
  color: #1f2d3d;
  font-size: 20px;
  font-weight: 600;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 32px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  border: 1px solid rgba(24, 144, 255, 0.15);
  box-shadow: 
    0 4px 20px rgba(24, 144, 255, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.8);
  position: relative;
  overflow: hidden;
}

.stat-card::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -20%;
  width: 120px;
  height: 120px;
  background: radial-gradient(circle, rgba(24, 144, 255, 0.08) 0%, transparent 70%);
  border-radius: 50%;
  pointer-events: none;
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 14px;
  position: relative;
  z-index: 1;
}

.stat-icon-blue {
  color: #1890ff;
  background: linear-gradient(135deg, rgba(24, 144, 255, 0.2) 0%, rgba(64, 169, 255, 0.1) 100%);
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.2);
}

.stat-icon-green {
  color: #52c41a;
  background: linear-gradient(135deg, rgba(82, 196, 26, 0.2) 0%, rgba(115, 209, 61, 0.1) 100%);
  box-shadow: 0 4px 12px rgba(82, 196, 26, 0.2);
}

.stat-icon-orange {
  color: #ff6700;
  background: linear-gradient(135deg, rgba(255, 103, 0, 0.2) 0%, rgba(255, 149, 64, 0.1) 100%);
  box-shadow: 0 4px 12px rgba(255, 103, 0, 0.2);
}

.stat-icon-purple {
  color: #722ed1;
  background: linear-gradient(135deg, rgba(114, 46, 209, 0.2) 0%, rgba(146, 84, 222, 0.1) 100%);
  box-shadow: 0 4px 12px rgba(114, 46, 209, 0.2);
}

.stat-info {
  display: flex;
  flex: 1;
  flex-direction: column;
  position: relative;
  z-index: 1;
}

.stat-value {
  color: #1f2d3d;
  font-size: 28px;
  font-weight: 700;
}

.stat-label {
  color: #667085;
  font-size: 13px;
}

.quick-actions {
  margin-bottom: 32px;
}

.action-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.action-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px;
  color: #ffffff;
  cursor: pointer;
  border-radius: 16px;
  transition: all 0.3s;
  position: relative;
  overflow: hidden;
}

.action-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.1) 0%, transparent 50%);
  pointer-events: none;
}

.action-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.2);
}

.action-card-green {
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
  border: 1px solid rgba(82, 196, 26, 0.3);
}

.action-card-blue {
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
  border: 1px solid rgba(24, 144, 255, 0.3);
}

.action-card-purple {
  background: linear-gradient(135deg, #5c3377 0%, #7b5ca5 100%);
  border: 1px solid rgba(92, 51, 119, 0.3);
}

.action-card-indigo {
  background: linear-gradient(135deg, #1e3a8a 0%, #3b82f6 100%);
  border: 1px solid rgba(30, 58, 138, 0.3);
}

.action-card-lime {
  background: linear-gradient(135deg, #84cc16 0%, #a3e635 100%);
  border: 1px solid rgba(132, 204, 22, 0.3);
}

.action-info {
  flex: 1;
}

.action-info h3 {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 600;
}

.action-info p {
  margin: 0;
  font-size: 13px;
  opacity: 0.9;
}

.action-arrow {
  opacity: 0.7;
}

.recent-section {
  padding: 24px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  border: 1px solid rgba(24, 144, 255, 0.15);
  box-shadow: 
    0 4px 20px rgba(24, 144, 255, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.section-header .section-title {
  margin-bottom: 0;
}

.view-all {
  color: #1890ff;
  font-size: 14px;
  text-decoration: none;
}

.view-all:hover {
  text-decoration: underline;
}

.recent-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.recent-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  background: rgba(24, 144, 255, 0.03);
  border-radius: 12px;
  border: 1px solid rgba(24, 144, 255, 0.08);
  transition: all 0.2s;
}

.recent-item:hover {
  background: rgba(24, 144, 255, 0.06);
  border-color: rgba(24, 144, 255, 0.15);
  transform: translateX(4px);
}

.recent-info h4 {
  margin: 0 0 4px;
  color: #1f2d3d;
  font-size: 15px;
  font-weight: 600;
}

.recent-info p {
  margin: 0;
  color: #667085;
  font-size: 13px;
}

.recent-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.status-tag {
  padding: 4px 12px;
  font-size: 12px;
  font-weight: 500;
  border-radius: 20px;
}

.status-operating {
  color: #52c41a;
  background: rgba(82, 196, 26, 0.1);
}

.status-suspended {
  color: #ffb100;
  background: rgba(255, 177, 0, 0.1);
}

.status-closed {
  color: #ff4d4f;
  background: rgba(255, 77, 79, 0.1);
}

.update-time {
  color: #999999;
  font-size: 12px;
}

.empty-state {
  padding: 40px;
  color: #999999;
  text-align: center;
  background: rgba(24, 144, 255, 0.03);
  border-radius: 12px;
  border: 1px dashed rgba(24, 144, 255, 0.2);
}

.empty-state p {
  margin-top: 12px;
}

@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .action-cards {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .stats-grid,
  .action-cards {
    grid-template-columns: 1fr;
  }

  .recent-item {
    align-items: flex-start;
    gap: 12px;
    flex-direction: column;
  }
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.edit-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: #f5f5f5;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  color: #666;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.edit-btn:hover {
  background: #e8e8e8;
  border-color: #bfbfbf;
}

.action-emoji {
  font-size: 32px;
}

.action-card-orange {
  background: linear-gradient(135deg, #fa8c16 0%, #ffa940 100%);
  border: 1px solid rgba(250, 140, 22, 0.3);
}

.action-card-cyan {
  background: linear-gradient(135deg, #13c2c2 0%, #36cfc9 100%);
  border: 1px solid rgba(19, 194, 194, 0.3);
}

.action-card-pink {
  background: linear-gradient(135deg, #c41d7f 0%, #e672a8 100%);
  border: 1px solid rgba(196, 29, 127, 0.3);
}

.action-card-red {
  background: linear-gradient(135deg, #e15863 0%, #e85d6d 100%);
  border: 1px solid rgba(207, 19, 34, 0.3);
}

.action-card-teal {
  background: linear-gradient(135deg, #3f51b5 0%, #5c6bc0 100%);
  border: 1px solid rgba(63, 81, 181, 0.3);
}

.action-card-olive {
  background: linear-gradient(135deg, #558b2f 0%, #689f38 100%);
  border: 1px solid rgba(85, 139, 47, 0.3);
}

.action-card-rose {
  background: linear-gradient(135deg, #e11d48 0%, #f43f5e 100%);
  border: 1px solid rgba(225, 29, 72, 0.3);
}

.action-card-amber {
  background: linear-gradient(135deg, #d97706 0%, #f59e0b 100%);
  border: 1px solid rgba(217, 119, 6, 0.3);
}

.empty-actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  background: rgba(24, 144, 255, 0.03);
  border-radius: 16px;
  border: 1px dashed rgba(24, 144, 255, 0.2);
}

.empty-actions p {
  margin: 16px 0 20px;
  color: #999;
}

.add-action-btn {
  padding: 10px 24px;
  background: #1890ff;
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.add-action-btn:hover {
  background: #40a9ff;
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
  width: 500px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
  overflow: hidden;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #f0f0f0;
}

.modal-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.modal-close {
  background: none;
  border: none;
  color: #999;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s;
}

.modal-close:hover {
  color: #666;
  background: #f5f5f5;
}

.modal-body {
  padding: 20px;
  max-height: 400px;
  overflow-y: auto;
}

.modal-body h4 {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.current-actions {
  margin-bottom: 24px;
}

.action-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.action-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: #fafafa;
  border-radius: 8px;
}

.action-emoji-small {
  font-size: 20px;
}

.action-label {
  flex: 1;
  font-size: 14px;
  color: #333;
}

.remove-btn, .add-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s;
}

.remove-btn {
  color: #ff4d4f;
}

.remove-btn:hover {
  background: #fff2f0;
}

.add-btn {
  color: #52c41a;
}

.add-btn:hover {
  background: #f6ffed;
}

.empty-tip {
  padding: 16px;
  background: #f5f5f5;
  border-radius: 8px;
  color: #999;
  font-size: 13px;
  text-align: center;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  padding: 16px 20px;
  border-top: 1px solid #f0f0f0;
  gap: 10px;
}

.btn {
  padding: 8px 20px;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
  border: none;
}

.btn-secondary {
  background: #f5f5f5;
  color: #666;
  border: 1px solid #d9d9d9;
}

.btn-secondary:hover {
  background: #e8e8e8;
}
</style>