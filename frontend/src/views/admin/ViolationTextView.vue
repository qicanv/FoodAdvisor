<template>
  <AdminLayout title="违规文本检测" subtitle="违规文本识别统计概览">
    <!-- 统计总览 -->
    <div class="stats-cards">
      <div class="stat-card stat-card-total">
        <div class="stat-icon">📊</div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.totalDetections || 0 }}</div>
          <div class="stat-label">近30天检测总数</div>
        </div>
      </div>
      <div class="stat-card stat-card-ai">
        <div class="stat-icon">🤖</div>
        <div class="stat-info">
          <div class="stat-value">{{ aiSuccessCount }}</div>
          <div class="stat-label">AI 检测成功</div>
        </div>
      </div>
      <div class="stat-card stat-card-fallback">
        <div class="stat-icon">⚠️</div>
        <div class="stat-info">
          <div class="stat-value">{{ fallbackCount }}</div>
          <div class="stat-label">降级关键词检测</div>
        </div>
      </div>
      <div class="stat-card stat-card-high">
        <div class="stat-icon">🔴</div>
        <div class="stat-info">
          <div class="stat-value">{{ highRiskCount }}</div>
          <div class="stat-label">高风险检出</div>
        </div>
      </div>
    </div>

    <!-- 主内容区 -->
    <div class="main-grid">
      <div class="panel">
        <div class="panel-header"><h3>📌 风险类型分布（近30天）</h3></div>
        <div class="panel-body">
          <div v-if="stats.byRiskType && stats.byRiskType.length > 0" class="type-list">
            <div v-for="item in stats.byRiskType" :key="item.riskType" class="type-item">
              <div class="type-header">
                <span :class="['type-badge', typeBadgeClass(item.riskType)]">{{ item.name }}</span>
                <span class="type-count">{{ item.count || 0 }} 次</span>
              </div>
              <div class="type-bar-wrapper">
                <div :class="['type-bar', typeBarClass(item.riskType)]" :style="{ width: barWidth(item.count) + '%' }"></div>
              </div>
            </div>
          </div>
          <div v-else class="empty-state">暂无数据</div>
        </div>
      </div>
      <div class="panel">
        <div class="panel-header"><h3>📊 风险等级分布</h3></div>
        <div class="panel-body">
          <div v-if="stats.byRiskLevel && stats.byRiskLevel.length > 0" class="level-cards">
            <div v-for="item in stats.byRiskLevel" :key="item.riskLevel" :class="['level-card', 'level-' + item.riskLevel.toLowerCase()]">
              <span class="level-icon">{{ levelIcon(item.riskLevel) }}</span>
              <span class="level-name">{{ levelName(item.riskLevel) }}</span>
              <span class="level-count">{{ item.count || 0 }}</span>
            </div>
          </div>
          <div v-else class="empty-state">暂无数据</div>
        </div>
      </div>
    </div>

    <!-- 快捷入口 -->
    <div class="panel" style="margin-top: 16px">
      <div class="panel-header"><h3>🔗 处理待审核内容</h3></div>
      <div class="panel-body">
        <router-link to="/admin/moderation" class="cta-link">
          <span class="cta-icon">🔍</span>
          <div>
            <strong>进入内容审核工作台</strong>
            <p>查看待审核评价，执行通过、驳回、删除等审核操作</p>
          </div>
          <span class="cta-arrow">→</span>
        </router-link>
      </div>
    </div>
  </AdminLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import AdminLayout from '@/components/AdminLayout.vue'
import { getViolationStats } from '@/api/violationText'

const stats = ref({})

const aiSuccessCount = computed(() => {
  if (!stats.value.byDetectionStatus) return 0
  const item = stats.value.byDetectionStatus.find(s => s.status === 'SUCCESS')
  return item ? item.count : 0
})
const fallbackCount = computed(() => {
  if (!stats.value.byDetectionStatus) return 0
  const item = stats.value.byDetectionStatus.find(s => s.status === 'FALLBACK')
  return item ? item.count : 0
})
const highRiskCount = computed(() => {
  if (!stats.value.byRiskLevel) return 0
  const item = stats.value.byRiskLevel.find(s => s.riskLevel === 'HIGH')
  return item ? item.count : 0
})
const maxTypeCount = computed(() => {
  if (!stats.value.byRiskType || stats.value.byRiskType.length === 0) return 1
  return Math.max(...stats.value.byRiskType.map(t => t.count || 0), 1)
})
const barWidth = (count) => Math.round((count || 0) / maxTypeCount.value * 100)
const typeBadgeClass = (t) => ({ AD_SPAM: 'type-ad', ABUSE: 'type-abuse', FALSE_AD: 'type-false', SPAM: 'type-spam', OTHER: 'type-other' }[t] || '')
const typeBarClass = (t) => ({ AD_SPAM: 'bar-ad', ABUSE: 'bar-abuse', FALSE_AD: 'bar-false', SPAM: 'bar-spam', OTHER: 'bar-other' }[t] || '')
const levelIcon = (l) => ({ HIGH: '🔴', MEDIUM: '🟡', LOW: '🟢' }[l] || '⚪')
const levelName = (l) => ({ HIGH: '高风险', MEDIUM: '中风险', LOW: '低风险' }[l] || l)

onMounted(async () => {
  try { const res = await getViolationStats(); if (res.data) stats.value = res.data } catch (e) { console.error(e) }
})
</script>

<style scoped>
.stats-cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 16px; }
.stat-card { display: flex; align-items: center; gap: 12px; padding: 20px; border-radius: 10px; background: #fff; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.stat-card .stat-icon { font-size: 32px; }
.stat-card .stat-value { font-size: 28px; font-weight: 700; color: #1a1a2e; }
.stat-card .stat-label { font-size: 13px; color: #666; }
.stat-card-total { border-left: 4px solid #667eea; }
.stat-card-ai { border-left: 4px solid #52c41a; }
.stat-card-fallback { border-left: 4px solid #fa8c16; }
.stat-card-high { border-left: 4px solid #f5222d; }
.main-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.panel { background: #fff; border-radius: 10px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); overflow: hidden; }
.panel-header { padding: 14px 20px; border-bottom: 1px solid #f0f0f0; }
.panel-header h3 { margin: 0; font-size: 15px; color: #1a1a2e; }
.panel-body { padding: 16px 20px; }
.type-item { margin-bottom: 14px; }
.type-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.type-badge { font-size: 13px; font-weight: 600; padding: 2px 10px; border-radius: 12px; }
.type-ad { background: #fff1f0; color: #cf1322; }
.type-abuse { background: #fff7e6; color: #d46b08; }
.type-false { background: #fff0f6; color: #c41d7f; }
.type-spam { background: #f6ffed; color: #389e0d; }
.type-other { background: #f5f5f5; color: #595959; }
.type-count { font-size: 13px; color: #666; font-weight: 500; }
.type-bar-wrapper { height: 8px; background: #f0f0f0; border-radius: 4px; overflow: hidden; }
.type-bar { height: 100%; border-radius: 4px; transition: width 0.6s; }
.bar-ad { background: linear-gradient(90deg, #ff4d4f, #ff7875); }
.bar-abuse { background: linear-gradient(90deg, #fa8c16, #ffc069); }
.bar-false { background: linear-gradient(90deg, #eb2f96, #ff85c0); }
.bar-spam { background: linear-gradient(90deg, #52c41a, #95de64); }
.bar-other { background: linear-gradient(90deg, #8c8c8c, #bfbfbf); }
.level-cards { display: flex; gap: 12px; }
.level-card { flex: 1; text-align: center; padding: 20px 12px; border-radius: 10px; border: 2px solid transparent; }
.level-high { background: #fff1f0; border-color: #ffa39e; }
.level-medium { background: #fff7e6; border-color: #ffd591; }
.level-low { background: #f6ffed; border-color: #b7eb8f; }
.level-icon { font-size: 28px; display: block; margin-bottom: 6px; }
.level-name { font-size: 14px; color: #333; font-weight: 500; }
.level-count { font-size: 32px; font-weight: 700; color: #1a1a2e; }
.cta-link { display: flex; align-items: center; gap: 16px; padding: 20px; border-radius: 8px; border: 2px solid #667eea; text-decoration: none; color: inherit; background: linear-gradient(135deg, #f0f5ff, #fff); transition: all 0.2s; }
.cta-link:hover { box-shadow: 0 4px 16px rgba(102,126,234,0.2); transform: translateY(-1px); }
.cta-icon { font-size: 32px; }
.cta-link strong { display: block; font-size: 15px; color: #1a1a2e; margin-bottom: 4px; }
.cta-link p { margin: 0; font-size: 13px; color: #888; }
.cta-arrow { font-size: 24px; color: #667eea; margin-left: auto; }
.empty-state { text-align: center; padding: 40px; color: #bbb; font-size: 14px; }
@media (max-width: 768px) { .stats-cards { grid-template-columns: repeat(2, 1fr); } .main-grid { grid-template-columns: 1fr; } .level-cards { flex-direction: column; } }
</style>
