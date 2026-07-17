<template>
  <AdminLayout title="系统首页" subtitle="欢迎回来，管理员">
    <section class="stats-section">
      <h2 class="section-title">数据概览</h2>
      <div class="stats-grid">
        <div class="stat-card" v-for="stat in stats" :key="stat.label">
          <div :class="['stat-icon', stat.iconClass]">
            <svg :viewBox="stat.iconViewBox" width="28" height="28" fill="none" stroke="currentColor" stroke-width="2">
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
      <h2 class="section-title">快捷操作</h2>
      <div class="action-cards">
        <div 
          v-for="action in quickActions" 
          :key="action.label"
          :class="['action-card', action.bgClass]"
          @click="navigateTo(action.path)"
        >
          <svg :viewBox="action.iconViewBox" width="40" height="40" fill="none" stroke="#fff" stroke-width="2">
            <path :d="action.iconPath" />
          </svg>
          <div class="action-info">
            <h3>{{ action.label }}</h3>
            <p>{{ action.description }}</p>
          </div>
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="#fff" stroke-width="2" class="action-arrow">
            <path d="M5 12h14M12 5l7 7-7 7"></path>
          </svg>
        </div>
      </div>
    </section>

    <section class="recent-section">
      <div class="section-header">
        <h2 class="section-title">最近更新的商家</h2>
        <router-link to="/admin/restaurants" class="view-all">查看全部</router-link>
      </div>
      <div class="recent-list" v-if="merchants.length > 0">
        <div class="recent-item" v-for="item in merchants" :key="item.id">
          <div class="recent-info">
            <h4>{{ item.name }}</h4>
            <p>{{ item.category }} · {{ item.address }}</p>
          </div>
          <div class="recent-meta">
            <span :class="['status-tag', getOperationStatusClass(item.operationStatus)]">{{ getOperationStatusText(item.operationStatus) }}</span>
            <span class="update-time">{{ formatTime(item.updatedAt) }}</span>
          </div>
        </div>
      </div>
      <div class="empty-state" v-else>
        <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="#ccc" stroke-width="1.5">
          <path d="M8 21l1-17a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4l1 17"></path>
        </svg>
        <p>暂无商家数据</p>
      </div>
    </section>
  </AdminLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
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
    iconPath: 'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2 M9 7a4 4 0 1 1 8 0', 
    iconClass: 'stat-icon-blue',
  },
  { 
    value: '0', 
    label: '合作商家', 
    iconViewBox: '0 0 24 24', 
    iconPath: 'M8 21l1-17a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4l1 17', 
    iconClass: 'stat-icon-green',
  },
  { 
    value: '0', 
    label: '评价总数', 
    iconViewBox: '0 0 24 24', 
    iconPath: 'M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z', 
    iconClass: 'stat-icon-orange',
  },
  { 
    value: '0', 
    label: '平均评分', 
    iconViewBox: '0 0 24 24', 
    iconPath: 'M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z', 
    iconClass: 'stat-icon-purple',
  },
])

const quickActions = [
  { 
    label: '新增商家', 
    description: '添加新的合作商家信息', 
    path: '/admin/restaurants',
    iconViewBox: '0 0 24 24',
    iconPath: 'M12 20v-6M12 4v6M4 12h6M14 12h6',
    bgClass: 'action-card-green'
  },
  { 
    label: '模型配置', 
    description: '管理AI模型服务配置', 
    path: '/admin/model-configs',
    iconViewBox: '0 0 24 24',
    iconPath: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z M9 12l2 2 4-4',
    bgClass: 'action-card-blue'
  },
]

const loadData = async () => {
  try {
    const response = await getAdminMerchants({ pageNum: 1, pageSize: 5 })
    if (response.success) {
      merchants.value = response.data.value || response.data.records || []
      stats.value[1].value = response.data.total || merchants.value.length
    }
  } catch (error) {
    console.error('加载数据失败:', error)
  }
}

const navigateTo = (path) => {
  router.push(path)
}

const getOperationStatusClass = (status) => {
  switch (status) {
    case 'OPERATING': return 'status-operating'
    case 'SUSPENDED': return 'status-suspended'
    case 'CLOSED_PERMANENTLY': return 'status-closed'
    default: return ''
  }
}

const getOperationStatusText = (status) => {
  switch (status) {
    case 'OPERATING': return '营业中'
    case 'SUSPENDED': return '停业中'
    case 'CLOSED_PERMANENTLY': return '永久关闭'
    default: return status || '-'
  }
}

const formatTime = (dateString) => {
  if (!dateString) return '-'
  const date = new Date(dateString)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const hours = Math.floor(diff / (1000 * 60 * 60))
  const days = Math.floor(hours / 24)
  
  if (hours < 1) return '刚刚'
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
  return date.toLocaleDateString('zh-CN')
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.section-title {
  font-size: 20px;
  font-weight: 600;
  color: #1f2d3d;
  margin-bottom: 20px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 32px;
}

.stat-card {
  background: #fff;
  border-radius: 16px;
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-icon-blue {
  background: rgba(24, 144, 255, 0.1);
  color: #1890ff;
}

.stat-icon-green {
  background: rgba(82, 196, 26, 0.1);
  color: #52c41a;
}

.stat-icon-orange {
  background: rgba(255, 103, 0, 0.1);
  color: #ff6700;
}

.stat-icon-purple {
  background: rgba(114, 46, 209, 0.1);
  color: #722ed1;
}

.stat-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #1f2d3d;
}

.stat-label {
  font-size: 13px;
  color: #667085;
}

.quick-actions {
  margin-bottom: 32px;
}

.action-cards {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.action-card {
  padding: 24px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  gap: 16px;
  cursor: pointer;
  transition: all 0.3s;
  color: #fff;
}

.action-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

.action-card-green {
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
}

.action-card-blue {
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
}

.action-info {
  flex: 1;
}

.action-info h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 4px;
}

.action-info p {
  font-size: 13px;
  opacity: 0.9;
  margin: 0;
}

.action-arrow {
  opacity: 0.7;
}

.recent-section {
  background: #fff;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.view-all {
  font-size: 14px;
  color: #1890ff;
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
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background: #fafafa;
  border-radius: 12px;
  transition: background 0.2s;
}

.recent-item:hover {
  background: #f5f5f5;
}

.recent-info h4 {
  font-size: 15px;
  font-weight: 600;
  color: #1f2d3d;
  margin: 0 0 4px;
}

.recent-info p {
  font-size: 13px;
  color: #667085;
  margin: 0;
}

.recent-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.status-tag {
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
}

.status-operating {
  background: rgba(82, 196, 26, 0.1);
  color: #52c41a;
}

.status-suspended {
  background: rgba(255, 177, 0, 0.1);
  color: #ffb100;
}

.status-closed {
  background: rgba(255, 77, 79, 0.1);
  color: #ff4d4f;
}

.update-time {
  font-size: 12px;
  color: #999;
}

.empty-state {
  text-align: center;
  padding: 40px;
  color: #999;
}

.empty-state p {
  margin-top: 12px;
}

@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .action-cards {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
