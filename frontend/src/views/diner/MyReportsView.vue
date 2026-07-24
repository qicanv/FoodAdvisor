<template>
  <div class="my-reports-view">
    <nav class="diner-nav">
      <div class="nav-container">
        <div class="logo-section">
          <img src="../../assets/images/greedy-cat.png" alt="食尚参谋" class="logo-img" />
          <span class="brand-name">食尚参谋 - 我的举报</span>
        </div>
        <div class="nav-links">
          <button class="back-btn" @click="goBack">← 返回个人中心</button>
          <UserAccountMenu
            role="diner"
            profile-path="/diner/profile"
          />
        </div>
      </div>
    </nav>

    <main class="reports-main">
      <div class="container">
        <div class="filter-section">
          <div class="filter-row">
            <div class="filter-group">
              <span class="filter-label">状态筛选</span>
              <div class="filter-options">
                <button
                  v-for="opt in statusOptions"
                  :key="opt.value"
                  class="filter-btn"
                  :class="{ active: selectedStatus === opt.value }"
                  @click="selectedStatus = opt.value"
                >
                  {{ opt.label }}
                </button>
              </div>
            </div>
          </div>
        </div>

        <div class="stats-bar">
          <span class="stats-text">共 {{ total }} 条举报记录</span>
        </div>

        <div class="reports-list">
          <div
            v-for="report in reports"
            :key="report.id"
            class="report-item"
          >
            <div class="report-header">
              <div class="merchant-info">
                <h3 class="merchant-name">{{ report.merchantName }}</h3>
                <span class="reason-tag">{{ report.reasonText }}</span>
              </div>
              <span class="status-tag" :class="report.status">
                {{ report.statusText }}
              </span>
            </div>
            <p class="report-review">{{ report.reviewSummary }}</p>
            <div v-if="report.description" class="report-description">
              <span class="desc-label">我的说明：</span>
              <span class="desc-text">{{ report.description }}</span>
            </div>
            <div v-if="report.resolution" class="report-resolution">
              <span class="desc-label">处理结果：</span>
              <span class="desc-text">{{ report.resolution }}</span>
            </div>
            <div class="report-footer">
              <span class="time-text">举报时间：{{ formatDate(report.createdAt) }}</span>
              <span v-if="report.handledAt" class="time-text">
                处理时间：{{ formatDate(report.handledAt) }}
              </span>
            </div>
          </div>

          <div v-if="reports.length === 0" class="empty-state">
            <div class="empty-icon-wrapper">
              <span class="empty-icon">🛡️</span>
            </div>
            <h3 class="empty-title">暂无举报记录</h3>
            <p class="empty-desc">
              {{ selectedStatus ? '当前筛选条件下没有举报记录' : '你还没有提交过任何举报' }}
            </p>
            <button v-if="!selectedStatus" class="empty-btn" @click="goBack">返回个人中心</button>
          </div>
        </div>

        <div v-if="total > pageSize" class="pagination">
          <button
            class="page-btn"
            :disabled="pageNum <= 1"
            @click="pageNum--"
          >
            ← 上一页
          </button>
          <span class="page-info">第 {{ pageNum }} / {{ totalPages }} 页</span>
          <button
            class="page-btn"
            :disabled="pageNum >= totalPages"
            @click="pageNum++"
          >
            下一页 →
          </button>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../../api/request'
import UserAccountMenu from '../../components/UserAccountMenu.vue'

const router = useRouter()
const reports = ref([])
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)
const selectedStatus = ref('')

const statusOptions = [
  { label: '全部', value: '' },
  { label: '待处理', value: 'PENDING' },
  { label: '已处理', value: 'RESOLVED' },
  { label: '已驳回', value: 'REJECTED' }
]

const totalPages = computed(() => Math.ceil(total.value / pageSize.value))

const loadReports = async () => {
  try {
    const response = await request.get('/api/reports/my', {
      params: {
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        status: selectedStatus.value || undefined
      }
    })

    if (response.success && response.data) {
      reports.value = response.data.records || []
      total.value = response.data.total || 0
    } else {
      reports.value = []
      total.value = 0
    }
  } catch (error) {
    console.error('获取举报列表失败:', error)
    reports.value = []
    total.value = 0
  }
}

const goBack = () => {
  router.push('/diner/profile')
}

const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

watch([selectedStatus], () => {
  pageNum.value = 1
  loadReports()
})

watch(pageNum, () => {
  loadReports()
})

onMounted(() => {
  loadReports()
})
</script>

<style scoped>
.my-reports-view {
  min-height: 100vh;
  background: #f5f7fa;
}

.container {
  max-width: 1000px;
  margin: 0 auto;
  padding: 0 24px;
}

.diner-nav {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
  padding: 16px 0;
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
}

.nav-container {
  max-width: 1000px;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.logo-section {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-img {
  width: 40px;
  height: 40px;
  border-radius: 8px;
}

.brand-name {
  font-size: 20px;
  font-weight: 700;
  color: #ff6700;
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 16px;
}

.back-btn {
  padding: 8px 16px;
  background: #fff5f0;
  color: #ff6700;
  border: 1px solid #ffccb3;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
}

.reports-main {
  padding-top: 80px;
  padding-bottom: 40px;
}

.filter-section {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
}

.filter-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.filter-label {
  font-size: 14px;
  color: #666;
  font-weight: 500;
}

.filter-options {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.filter-btn {
  padding: 8px 16px;
  background: #f5f5f5;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.filter-btn:hover {
  border-color: #ff6700;
  color: #ff6700;
}

.filter-btn.active {
  background: #ff6700;
  border-color: #ff6700;
  color: #fff;
}

.stats-bar {
  margin-bottom: 16px;
}

.stats-text {
  font-size: 15px;
  color: #666;
}

.reports-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.report-item {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.report-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.merchant-info {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.merchant-name {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.reason-tag {
  padding: 4px 12px;
  background: #fff5f0;
  color: #ff6700;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 500;
}

.status-tag {
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
}

.status-tag.PENDING {
  background: #fffbe6;
  color: #faad14;
}

.status-tag.RESOLVED {
  background: #f6ffed;
  color: #52c41a;
}

.status-tag.REJECTED {
  background: #fff2f0;
  color: #ff4d4f;
}

.report-review {
  font-size: 15px;
  color: #666;
  line-height: 1.6;
  margin: 0 0 10px 0;
}

.report-description,
.report-resolution {
  margin-bottom: 8px;
  padding: 10px 14px;
  border-radius: 6px;
  font-size: 14px;
  line-height: 1.5;
}

.report-description {
  background: #f9fafb;
}

.report-resolution {
  background: #f0f9eb;
}

.desc-label {
  color: #999;
  font-weight: 500;
}

.desc-text {
  color: #333;
}

.report-footer {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
}

.time-text {
  font-size: 13px;
  color: #999;
}

.empty-state {
  text-align: center;
  padding: 80px 20px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.empty-icon-wrapper {
  width: 100px;
  height: 100px;
  margin: 0 auto 24px;
  background: linear-gradient(135deg, #fff5f0 0%, #fff0e6 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-icon {
  font-size: 48px;
}

.empty-title {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin: 0 0 12px 0;
}

.empty-desc {
  font-size: 15px;
  color: #999;
  margin: 0 0 24px 0;
  line-height: 1.6;
}

.empty-btn {
  padding: 12px 32px;
  background: #ff6700;
  color: #fff;
  border: none;
  border-radius: 25px;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.empty-btn:hover {
  background: #e55a00;
  transform: translateY(-2px);
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  margin-top: 30px;
}

.page-btn {
  padding: 10px 20px;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  font-size: 15px;
  cursor: pointer;
}

.page-btn:hover:not(:disabled) {
  border-color: #ff6700;
  color: #ff6700;
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-info {
  font-size: 15px;
  color: #666;
}

@media (max-width: 768px) {
  .report-header {
    flex-direction: column;
    gap: 8px;
  }

  .status-tag {
    align-self: flex-start;
  }
}
</style>
