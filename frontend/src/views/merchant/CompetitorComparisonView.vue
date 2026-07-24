<template>
  <MerchantLayout title="周边竞品对比" subtitle="与周边同类型商家进行多维度对比分析">
    <div class="comparison-container">
      <!-- ========== 店铺选择 ========== -->
      <div class="store-select-section">
        <div class="store-select-card">
          <div class="store-select-label">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9z"></path>
              <polyline points="9 22 9 12 15 12 15 22"></polyline>
            </svg>
            <span>选择要对比的店铺</span>
          </div>
          <div class="store-select-row">
            <select
              v-model="activeMerchantId"
              class="store-select"
              @change="onStoreChange"
              :disabled="storesLoading"
            >
              <option :value="null" disabled>请选择店铺</option>
              <option v-for="s in myStores" :key="s.id" :value="s.id">
                {{ s.name }}
              </option>
            </select>
            <span v-if="storesLoading" class="loading-text">加载店铺中...</span>
            <span v-else-if="myStores.length === 0" class="loading-text">暂无管理的店铺</span>
          </div>
        </div>
      </div>

      <!-- ========== 第一步：选择竞品 ========== -->
      <div class="select-section" v-if="activeMerchantId">
        <div class="section-card">
          <div class="card-title-row">
            <h3 class="card-title">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                <circle cx="8.5" cy="7" r="4"></circle>
                <path d="M20 8v6M23 11h-6"></path>
              </svg>
              选择竞品商家
            </h3>
            <span class="card-hint">选择周边 1~3 家同类型商家进行对比</span>
          </div>

          <!-- 加载候选列表 -->
          <div v-if="candidatesLoading" class="loading-row">
            <span class="loading-spinner"></span>
            <span>正在加载候选竞品...</span>
          </div>

          <!-- 候选列表为空 -->
          <div v-else-if="candidates.length === 0 && !candidatesError" class="empty-row">
            <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="#ccc" stroke-width="2">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
              <circle cx="8.5" cy="7" r="4"></circle>
              <line x1="20" y1="8" x2="20" y2="14"></line>
              <line x1="23" y1="11" x2="17" y2="11"></line>
            </svg>
            <p>周边暂无可对比的同类型商家</p>
            <p class="empty-sub">系统仅展示与您同区域、同品类且正常营业的商家</p>
          </div>

          <!-- 候选错误 -->
          <div v-else-if="candidatesError" class="error-row">
            <p>{{ candidatesError }}</p>
            <button class="retry-btn" @click="loadCandidates">重新加载</button>
          </div>

          <!-- 候选列表 -->
          <div v-else class="candidates-grid">
            <div
              v-for="c in candidates"
              :key="c.merchantId"
              :class="['candidate-card', { selected: selectedIds.includes(c.merchantId) }]"
              @click="toggleCandidate(c.merchantId)"
            >
              <div class="candidate-check">
                <div :class="['checkbox', { checked: selectedIds.includes(c.merchantId) }]">
                  <svg v-if="selectedIds.includes(c.merchantId)" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="#fff" stroke-width="3">
                    <polyline points="20 6 9 17 4 12"></polyline>
                  </svg>
                </div>
              </div>
              <div class="candidate-info">
                <h4 class="candidate-name">{{ c.merchantName }}</h4>
                <p class="candidate-category">{{ c.cuisine || c.category }}</p>
                <div class="candidate-stats">
                  <span class="stat-item">
                    <span class="stat-label">评分</span>
                    <span class="stat-value rating">{{ c.rating != null ? c.rating.toFixed(1) : '-' }}</span>
                  </span>
                  <span class="stat-item">
                    <span class="stat-label">人均</span>
                    <span class="stat-value price">¥{{ c.averagePrice != null ? c.averagePrice : '-' }}</span>
                  </span>
                  <span class="stat-item">
                    <span class="stat-label">评价</span>
                    <span class="stat-value">{{ c.reviewCount }}</span>
                  </span>
                </div>
              </div>
            </div>
          </div>

          <!-- 操作按钮 -->
          <div class="action-row">
            <span class="selection-count">已选 <strong>{{ selectedIds.length }}</strong> / 3 家竞品</span>
            <button
              class="compare-btn"
              :disabled="selectedIds.length === 0 || comparing"
              @click="startComparison"
            >
              <svg v-if="comparing" class="btn-spinner" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"></circle>
                <path d="M12 6v6l4 2"></path>
              </svg>
              <svg v-else viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M9 19v-6a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2zm0 0V9a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v10m-6 0a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2m0 0V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-2a2 2 0 0 1-2-2z"></path>
              </svg>
              {{ comparing ? '分析中...' : '开始对比分析' }}
            </button>
          </div>
        </div>
      </div>

      <!-- ========== 对比结果 ========== -->
      <div v-if="result && result.comparisonStatus === 'SUCCESS'" class="result-section">
        <!-- 核心指标对比柱状图 -->
        <div class="section-card">
          <h3 class="card-title">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 19v-6a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2zm0 0V9a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v10m-6 0a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2m0 0V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-2a2 2 0 0 1-2-2z"></path>
            </svg>
            核心指标对比
          </h3>
          <div ref="barChartRef" class="chart-container bar-chart"></div>
        </div>

        <!-- 多维度雷达图 -->
        <div class="charts-row">
          <div class="section-card half">
            <h3 class="card-title">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"></circle>
                <circle cx="12" cy="12" r="6"></circle>
                <circle cx="12" cy="12" r="2"></circle>
              </svg>
              多维度雷达对比
            </h3>
            <div ref="radarChartRef" class="chart-container radar-chart"></div>
          </div>

          <!-- AI 横向对比总结 + 改进建议 -->
          <div class="section-card half">
            <h3 class="card-title">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 1 1 7.072 0l-.548.547A3.374 3.374 0 0 0 14 18.469V19a2 2 0 1 1-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z"></path>
              </svg>
              AI 分析洞察
            </h3>
            <div class="ai-insight">
              <div class="insight-block" v-if="result.aiSummaryText">
                <h4>横向对比总结</h4>
                <p>{{ result.aiSummaryText }}</p>
              </div>
              <div class="insight-block" v-if="result.aiImprovementSuggestions && result.aiImprovementSuggestions.length > 0">
                <h4>改进建议</h4>
                <ul class="suggestion-list">
                  <li v-for="(s, i) in result.aiImprovementSuggestions" :key="i">
                    <span class="sugg-num">{{ i + 1 }}</span>
                    <span>{{ s }}</span>
                  </li>
                </ul>
              </div>
            </div>
          </div>
        </div>

        <!-- 各家商家 AI 分析卡片 -->
        <div class="section-card">
          <h3 class="card-title">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
              <polyline points="14 2 14 8 20 8"></polyline>
              <line x1="16" y1="13" x2="8" y2="13"></line>
              <line x1="16" y1="17" x2="8" y2="17"></line>
            </svg>
            商家对比分析
          </h3>
          <div class="analysis-cards">
            <div
              v-for="item in result.aiMerchantAnalyses"
              :key="item.merchantId"
              :class="['analysis-card', { self: isSelfMerchant(item.merchantId) }]"
            >
              <div class="analysis-header">
                <h4>
                  {{ item.merchantName }}
                  <span v-if="isSelfMerchant(item.merchantId)" class="self-tag">本店</span>
                </h4>
                <p class="assessment">{{ item.overallAssessment }}</p>
              </div>
              <div class="analysis-body">
                <div class="strengths-block" v-if="item.strengths && item.strengths.length > 0">
                  <h5>
                    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="#52c41a" stroke-width="2">
                      <polyline points="20 6 9 17 4 12"></polyline>
                    </svg>
                    优势
                  </h5>
                  <ul>
                    <li v-for="(s, i) in item.strengths" :key="i">{{ s }}</li>
                  </ul>
                </div>
                <div class="strengths-block empty" v-else>
                  <h5>
                    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="#999" stroke-width="2">
                      <polyline points="20 6 9 17 4 12"></polyline>
                    </svg>
                    优势
                  </h5>
                  <p class="no-data">无明显优势项</p>
                </div>
                <div class="weaknesses-block" v-if="item.weaknesses && item.weaknesses.length > 0">
                  <h5>
                    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="#ff4d4f" stroke-width="2">
                      <circle cx="12" cy="12" r="10"></circle>
                      <line x1="12" y1="8" x2="12" y2="12"></line>
                      <line x1="12" y1="16" x2="12.01" y2="16"></line>
                    </svg>
                    短板
                  </h5>
                  <ul>
                    <li v-for="(w, i) in item.weaknesses" :key="i">{{ w }}</li>
                  </ul>
                </div>
                <div class="weaknesses-block empty" v-else>
                  <h5>
                    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="#999" stroke-width="2">
                      <circle cx="12" cy="12" r="10"></circle>
                      <line x1="12" y1="8" x2="12" y2="12"></line>
                      <line x1="12" y1="16" x2="12.01" y2="16"></line>
                    </svg>
                    短板
                  </h5>
                  <p class="no-data">无明显短板</p>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 详细数据表格 -->
        <div class="section-card">
          <h3 class="card-title">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect>
              <line x1="3" y1="9" x2="21" y2="9"></line>
              <line x1="3" y1="15" x2="21" y2="15"></line>
              <line x1="9" y1="3" x2="9" y2="21"></line>
            </svg>
            详细数据对比
          </h3>
          <div class="data-table-wrapper">
            <table class="data-table">
              <thead>
                <tr>
                  <th>指标</th>
                  <th v-for="m in result.merchantData" :key="m.merchantId" :class="{ self: m.isSelf }">
                    {{ m.merchantName }}
                    <span v-if="m.isSelf" class="self-tag-small">本店</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>综合评分</td>
                  <td v-for="m in result.merchantData" :key="'r'+m.merchantId" :class="{ self: m.isSelf, highlight: isHighest(m, 'rating') }">
                    {{ m.rating != null ? m.rating.toFixed(1) : '-' }}
                  </td>
                </tr>
                <tr>
                  <td>人均消费</td>
                  <td v-for="m in result.merchantData" :key="'p'+m.merchantId" :class="{ self: m.isSelf }">
                    ¥{{ m.averagePrice != null ? m.averagePrice : '-' }}
                  </td>
                </tr>
                <tr>
                  <td>好评率</td>
                  <td v-for="m in result.merchantData" :key="'pr'+m.merchantId" :class="{ self: m.isSelf, highlight: isHighest(m, 'positiveRate') }">
                    {{ m.positiveRate != null ? (m.positiveRate * 100).toFixed(1) + '%' : '-' }}
                  </td>
                </tr>
                <tr>
                  <td>评价总数</td>
                  <td v-for="m in result.merchantData" :key="'rc'+m.merchantId" :class="{ self: m.isSelf, highlight: isHighest(m, 'reviewCount') }">
                    {{ m.reviewCount }}
                  </td>
                </tr>
                <tr>
                  <td>口味评分</td>
                  <td v-for="m in result.merchantData" :key="'t'+m.merchantId" :class="{ self: m.isSelf, highlight: isHighest(m, 'tasteRating') }">
                    {{ m.tasteRating != null ? m.tasteRating.toFixed(1) : '-' }}
                  </td>
                </tr>
                <tr>
                  <td>环境评分</td>
                  <td v-for="m in result.merchantData" :key="'e'+m.merchantId" :class="{ self: m.isSelf, highlight: isHighest(m, 'environmentRating') }">
                    {{ m.environmentRating != null ? m.environmentRating.toFixed(1) : '-' }}
                  </td>
                </tr>
                <tr>
                  <td>服务评分</td>
                  <td v-for="m in result.merchantData" :key="'s'+m.merchantId" :class="{ self: m.isSelf, highlight: isHighest(m, 'serviceRating') }">
                    {{ m.serviceRating != null ? m.serviceRating.toFixed(1) : '-' }}
                  </td>
                </tr>
                <tr>
                  <td>正面标签</td>
                  <td v-for="m in result.merchantData" :key="'pt'+m.merchantId" :class="{ self: m.isSelf }">
                    <span v-if="m.topPositiveTags && m.topPositiveTags.length > 0" class="tags-cell">
                      <span v-for="tag in m.topPositiveTags" :key="tag" class="tag positive">{{ tag }}</span>
                    </span>
                    <span v-else class="no-data">-</span>
                  </td>
                </tr>
                <tr>
                  <td>差评问题</td>
                  <td v-for="m in result.merchantData" :key="'ni'+m.merchantId" :class="{ self: m.isSelf }">
                    <span v-if="m.topNegativeIssues && m.topNegativeIssues.length > 0" class="tags-cell">
                      <span v-for="issue in m.topNegativeIssues" :key="issue" class="tag negative">{{ issue }}</span>
                    </span>
                    <span v-else class="no-data">-</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- AI 分析失败降级 -->
      <div v-else-if="result && result.comparisonStatus === 'FAILED'" class="result-section">
        <div class="section-card">
          <div class="failed-state">
            <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="#faad14" stroke-width="2">
              <circle cx="12" cy="12" r="10"></circle>
              <line x1="12" y1="8" x2="12" y2="12"></line>
              <line x1="12" y1="16" x2="12.01" y2="16"></line>
            </svg>
            <h3>统计数据已生成，但 AI 分析失败</h3>
            <p v-if="result.errorMessage">{{ result.errorMessage }}</p>
            <p v-else>AI 分析服务暂时不可用，请稍后重试。以下为统计数据对比。</p>
          </div>
        </div>
        <!-- 失败时仍然展示数据表格 -->
        <div v-if="result.merchantData" class="section-card">
          <h3 class="card-title">详细数据对比</h3>
          <div class="data-table-wrapper">
            <table class="data-table">
              <thead>
                <tr>
                  <th>指标</th>
                  <th v-for="m in result.merchantData" :key="m.merchantId" :class="{ self: m.isSelf }">
                    {{ m.merchantName }}
                    <span v-if="m.isSelf" class="self-tag-small">本店</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>综合评分</td>
                  <td v-for="m in result.merchantData" :key="'r'+m.merchantId" :class="{ self: m.isSelf }">
                    {{ m.rating != null ? m.rating.toFixed(1) : '-' }}
                  </td>
                </tr>
                <tr>
                  <td>人均消费</td>
                  <td v-for="m in result.merchantData" :key="'p'+m.merchantId" :class="{ self: m.isSelf }">
                    ¥{{ m.averagePrice != null ? m.averagePrice : '-' }}
                  </td>
                </tr>
                <tr>
                  <td>好评率</td>
                  <td v-for="m in result.merchantData" :key="'pr'+m.merchantId" :class="{ self: m.isSelf }">
                    {{ m.positiveRate != null ? (m.positiveRate * 100).toFixed(1) + '%' : '-' }}
                  </td>
                </tr>
                <tr>
                  <td>评价总数</td>
                  <td v-for="m in result.merchantData" :key="'rc'+m.merchantId" :class="{ self: m.isSelf }">
                    {{ m.reviewCount }}
                  </td>
                </tr>
                <tr>
                  <td>口味评分</td>
                  <td v-for="m in result.merchantData" :key="'t'+m.merchantId" :class="{ self: m.isSelf }">
                    {{ m.tasteRating != null ? m.tasteRating.toFixed(1) : '-' }}
                  </td>
                </tr>
                <tr>
                  <td>环境评分</td>
                  <td v-for="m in result.merchantData" :key="'e'+m.merchantId" :class="{ self: m.isSelf }">
                    {{ m.environmentRating != null ? m.environmentRating.toFixed(1) : '-' }}
                  </td>
                </tr>
                <tr>
                  <td>服务评分</td>
                  <td v-for="m in result.merchantData" :key="'s'+m.merchantId" :class="{ self: m.isSelf }">
                    {{ m.serviceRating != null ? m.serviceRating.toFixed(1) : '-' }}
                  </td>
                </tr>
                <tr>
                  <td>正面标签</td>
                  <td v-for="m in result.merchantData" :key="'pt'+m.merchantId" :class="{ self: m.isSelf }">
                    <span v-if="m.topPositiveTags && m.topPositiveTags.length > 0" class="tags-cell">
                      <span v-for="tag in m.topPositiveTags" :key="tag" class="tag positive">{{ tag }}</span>
                    </span>
                    <span v-else class="no-data">-</span>
                  </td>
                </tr>
                <tr>
                  <td>差评问题</td>
                  <td v-for="m in result.merchantData" :key="'ni'+m.merchantId" :class="{ self: m.isSelf }">
                    <span v-if="m.topNegativeIssues && m.topNegativeIssues.length > 0" class="tags-cell">
                      <span v-for="issue in m.topNegativeIssues" :key="issue" class="tag negative">{{ issue }}</span>
                    </span>
                    <span v-else class="no-data">-</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- 未开始对比的空状态 -->
      <div v-if="!result && !comparing && candidates.length > 0" class="empty-compare">
        <div class="section-card empty-hint">
          <svg viewBox="0 0 24 24" width="64" height="64" fill="none" stroke="#d9d9d9" stroke-width="2">
            <path d="M9 19v-6a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2zm0 0V9a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v10m-6 0a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2m0 0V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-2a2 2 0 0 1-2-2z"></path>
          </svg>
          <p>请在上方选择竞品商家后点击"开始对比分析"</p>
        </div>
      </div>

      <!-- 分析结果反馈 -->
      <AnalysisFeedbackPanel
        v-if="activeMerchantId && result"
        :merchantId="activeMerchantId"
        analysisType="COMPETITOR"
      />
    </div>
  </MerchantLayout>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import MerchantLayout from '../../components/MerchantLayout.vue'
import AnalysisFeedbackPanel from '../../components/AnalysisFeedbackPanel.vue'
import { getCompetitorCandidates, performCompetitorComparison } from '../../api/competitorComparison'
import { getMyMerchants } from '../../api/merchantConsole'

// ========== 店铺选择（独立加载，不依赖 MerchantLayout） ==========
const myStores = ref([])
const storesLoading = ref(false)
const activeMerchantId = ref(null)

const loadMyStores = async () => {
  storesLoading.value = true
  try {
    const res = await getMyMerchants()
    if (res.success && res.data) {
      myStores.value = res.data
      // 自动选中：优先从 localStorage 恢复，其次选第一家
      if (myStores.value.length > 0) {
        const savedId = localStorage.getItem('activeMerchantId')
        const savedIdNum = savedId ? Number(savedId) : null
        const exists = myStores.value.some(s => s.id === savedIdNum)
        if (exists) {
          activeMerchantId.value = savedIdNum
        } else {
          activeMerchantId.value = myStores.value[0].id
        }
        // 选中后自动加载候选
        loadCandidates()
      }
    }
  } catch (e) {
    console.error('加载店铺列表失败', e)
  } finally {
    storesLoading.value = false
  }
}

const onStoreChange = () => {
  if (activeMerchantId.value) {
    localStorage.setItem('activeMerchantId', String(activeMerchantId.value))
    selectedIds.value = []
    result.value = null
    loadCandidates()
  }
}
const candidates = ref([])
const candidatesLoading = ref(false)
const candidatesError = ref('')
const selectedIds = ref([])
const comparing = ref(false)
const result = ref(null)

// ========== 图表引用 ==========
const barChartRef = ref(null)
const radarChartRef = ref(null)
let barChartInstance = null
let radarChartInstance = null

// ========== 颜色配置 ==========
const COLORS = ['#52c41a', '#1890ff', '#faad14', '#722ed1']

// ========== 计算属性辅助 ==========
const isSelfMerchant = (merchantId) => {
  return merchantId === activeMerchantId.value
}

const isHighest = (merchant, field) => {
  if (!result.value || !result.value.merchantData) return false
  const values = result.value.merchantData.map(m => m[field]).filter(v => v != null)
  if (values.length === 0) return false
  const max = Math.max(...values.map(v => Number(v)))
  return Number(merchant[field]) === max
}

// ========== 加载候选竞品 ==========
const loadCandidates = async () => {
  if (!activeMerchantId.value) return
  candidatesLoading.value = true
  candidatesError.value = ''
  try {
    const res = await getCompetitorCandidates(activeMerchantId.value)
    if (res.success) {
      candidates.value = res.data || []
    } else {
      candidatesError.value = `加载失败 (${res.code || 'ERROR'}): ${res.message || '未知错误'}`
    }
  } catch (e) {
    console.error('加载候选竞品异常:', e)
    candidatesError.value = '网络请求失败，请确认后端服务是否正常运行'
  } finally {
    candidatesLoading.value = false
  }
}

// ========== 选择/取消竞品 ==========
const toggleCandidate = (id) => {
  const idx = selectedIds.value.indexOf(id)
  if (idx >= 0) {
    selectedIds.value.splice(idx, 1)
  } else {
    if (selectedIds.value.length >= 3) {
      return // 最多3家
    }
    selectedIds.value.push(id)
  }
}

// ========== 执行对比 ==========
const startComparison = async () => {
  if (selectedIds.value.length === 0 || comparing.value) return
  comparing.value = true
  result.value = null

  try {
    const res = await performCompetitorComparison(activeMerchantId.value, [...selectedIds.value])
    if (res.success) {
      result.value = res.data
    } else {
      result.value = {
        comparisonStatus: 'FAILED',
        merchantId: activeMerchantId.value,
        errorMessage: `[${res.code || 'ERROR'}] ${res.message || '对比分析失败'}`
      }
    }
  } catch (e) {
    console.error('竞品对比异常:', e)
    result.value = {
      comparisonStatus: 'FAILED',
      merchantId: activeMerchantId.value,
      errorMessage: '网络异常，请确认后端服务与 AI 服务是否正常运行'
    }
  } finally {
    comparing.value = false
    await nextTick()
    renderCharts()
  }
}

// ========== 渲染图表 ==========
const renderCharts = () => {
  renderBarChart()
  renderRadarChart()
}

const renderBarChart = () => {
  if (!barChartRef.value || !result.value || !result.value.merchantData) return
  if (barChartInstance) barChartInstance.dispose()

  barChartInstance = echarts.init(barChartRef.value)
  const data = result.value.merchantData

  const metrics = [
    { key: 'rating', label: '综合评分', max: 5, unit: '' },
    { key: 'averagePrice', label: '人均消费(元)', max: null, unit: '¥' },
    { key: 'positiveRate', label: '好评率(%)', max: 1, unit: '', transform: v => (v * 100).toFixed(1) },
    { key: 'reviewCount', label: '评价总数', max: null, unit: '' },
    { key: 'tasteRating', label: '口味评分', max: 5, unit: '' },
    { key: 'environmentRating', label: '环境评分', max: 5, unit: '' },
    { key: 'serviceRating', label: '服务评分', max: 5, unit: '' },
  ]

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
    },
    legend: {
      data: data.map((m, i) => ({ name: m.merchantName, icon: 'roundRect' })),
      top: 0,
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      data: metrics.map(m => m.label),
      axisLabel: { fontSize: 11 },
    },
    yAxis: {
      type: 'value',
    },
    series: data.map((merchant, idx) => ({
      name: merchant.merchantName,
      type: 'bar',
      data: metrics.map(m => {
        const val = merchant[m.key]
        if (val == null) return null
        if (m.transform) return m.transform(val)
        return val
      }),
      itemStyle: {
        color: COLORS[idx % COLORS.length],
        borderRadius: [4, 4, 0, 0],
      },
      barMaxWidth: 40,
    })),
  }

  barChartInstance.setOption(option)
}

const renderRadarChart = () => {
  if (!radarChartRef.value || !result.value || !result.value.merchantData) return
  if (radarChartInstance) radarChartInstance.dispose()

  radarChartInstance = echarts.init(radarChartRef.value)
  const data = result.value.merchantData

  const maxValues = {
    rating: 5,
    positiveRate: 1,
    tasteRating: 5,
    environmentRating: 5,
    serviceRating: 5,
  }

  const option = {
    tooltip: {
      trigger: 'item',
    },
    legend: {
      data: data.map(m => m.merchantName),
      bottom: 0,
    },
    radar: {
      center: ['50%', '50%'],
      radius: '65%',
      indicator: [
        { name: '综合评分', max: 5 },
        { name: '好评率', max: 1 },
        { name: '口味', max: 5 },
        { name: '环境', max: 5 },
        { name: '服务', max: 5 },
      ],
      axisName: { fontSize: 11 },
    },
    series: [
      {
        type: 'radar',
        data: data.map((merchant, idx) => ({
          name: merchant.merchantName,
          value: [
            merchant.rating != null ? Number(merchant.rating) : 0,
            merchant.positiveRate != null ? Number(merchant.positiveRate) : 0,
            merchant.tasteRating != null ? Number(merchant.tasteRating) : 0,
            merchant.environmentRating != null ? Number(merchant.environmentRating) : 0,
            merchant.serviceRating != null ? Number(merchant.serviceRating) : 0,
          ],
          lineStyle: { color: COLORS[idx % COLORS.length] },
          areaStyle: { color: COLORS[idx % COLORS.length], opacity: 0.15 },
          itemStyle: { color: COLORS[idx % COLORS.length] },
        })),
      },
    ],
  }

  radarChartInstance.setOption(option)
}

// ========== 窗口大小响应 ==========
const handleResize = () => {
  if (barChartInstance) barChartInstance.resize()
  if (radarChartInstance) radarChartInstance.resize()
}

onMounted(() => {
  loadMyStores()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (barChartInstance) barChartInstance.dispose()
  if (radarChartInstance) radarChartInstance.dispose()
})
</script>

<style scoped>
.comparison-container {
  width: 100%;
}

/* ========== 店铺选择 ========== */
.store-select-section {
  margin-bottom: 24px;
}

.store-select-card {
  background: linear-gradient(135deg, #f6ffed 0%, #e6ffe6 100%);
  border: 1px solid #b7eb8f;
  border-radius: 12px;
  padding: 16px 24px;
  display: flex;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
}

.store-select-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2d3d;
  white-space: nowrap;
  flex-shrink: 0;
}

.store-select-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.store-select {
  flex: 1;
  max-width: 320px;
  padding: 10px 14px;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  font-size: 14px;
  background: #fff;
  color: #1f2d3d;
  cursor: pointer;
  transition: border-color 0.2s;
  appearance: auto;
}

.store-select:focus {
  outline: none;
  border-color: #52c41a;
  box-shadow: 0 0 0 2px rgba(82, 196, 26, 0.15);
}

.loading-text {
  font-size: 13px;
  color: #999;
  white-space: nowrap;
}

/* ========== 通用卡片 ========== */
.section-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.card-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2d3d;
  margin: 0 0 20px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-hint {
  font-size: 13px;
  color: #999;
}

/* ========== 选择区域 ========== */
.loading-row, .empty-row, .error-row {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px;
  gap: 12px;
  color: #999;
  font-size: 14px;
}

.loading-spinner {
  width: 24px;
  height: 24px;
  border: 2px solid #e0e0e0;
  border-top-color: #52c41a;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.empty-sub {
  font-size: 12px;
  color: #bbb;
  margin: 0;
}

.error-row {
  color: #ff4d4f;
}

.retry-btn {
  padding: 8px 20px;
  font-size: 13px;
  color: #fff;
  background: #52c41a;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}

/* ========== 候选卡片网格 ========== */
.candidates-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.candidate-card {
  border: 2px solid #f0f0f0;
  border-radius: 10px;
  padding: 16px;
  display: flex;
  gap: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.candidate-card:hover {
  border-color: #b7eb8f;
  background: #f6ffed;
}

.candidate-card.selected {
  border-color: #52c41a;
  background: #f6ffed;
  box-shadow: 0 2px 8px rgba(82, 196, 26, 0.15);
}

.candidate-check {
  flex-shrink: 0;
  padding-top: 2px;
}

.checkbox {
  width: 22px;
  height: 22px;
  border: 2px solid #d9d9d9;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.checkbox.checked {
  background: #52c41a;
  border-color: #52c41a;
}

.candidate-info {
  flex: 1;
  min-width: 0;
}

.candidate-name {
  font-size: 15px;
  font-weight: 600;
  color: #1f2d3d;
  margin: 0 0 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.candidate-category {
  font-size: 12px;
  color: #999;
  margin: 0 0 10px;
}

.candidate-stats {
  display: flex;
  gap: 16px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-label {
  font-size: 11px;
  color: #999;
}

.stat-value {
  font-size: 14px;
  font-weight: 600;
  color: #1f2d3d;
}

.stat-value.rating {
  color: #faad14;
}

.stat-value.price {
  color: #ff4d4f;
}

/* ========== 操作行 ========== */
.action-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.selection-count {
  font-size: 13px;
  color: #667085;
}

.selection-count strong {
  color: #52c41a;
}

.compare-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 28px;
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.compare-btn:hover:not(:disabled) {
  opacity: 0.9;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(82, 196, 26, 0.3);
}

.compare-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-spinner {
  animation: spin 1s linear infinite;
}

/* ========== 结果区域 ========== */
.result-section {
  margin-top: 8px;
}

/* ========== 图表 ========== */
.chart-container {
  width: 100%;
}

.bar-chart {
  height: 380px;
}

.radar-chart {
  height: 360px;
}

.charts-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  margin-bottom: 24px;
}

.charts-row .section-card {
  margin-bottom: 0;
}

@media (max-width: 900px) {
  .charts-row {
    grid-template-columns: 1fr;
  }
}

/* ========== AI 分析洞察 ========== */
.ai-insight {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.insight-block h4 {
  font-size: 14px;
  font-weight: 600;
  color: #1f2d3d;
  margin: 0 0 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
}

.insight-block p {
  font-size: 14px;
  color: #555;
  line-height: 1.7;
  margin: 0;
}

.suggestion-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.suggestion-list li {
  display: flex;
  gap: 10px;
  font-size: 13px;
  color: #555;
  line-height: 1.6;
  align-items: flex-start;
}

.sugg-num {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  background: #f6ffed;
  color: #52c41a;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
}

/* ========== 分析卡片 ========== */
.analysis-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
}

.analysis-card {
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  padding: 20px;
  transition: all 0.2s;
}

.analysis-card.self {
  border-color: #b7eb8f;
  background: #fcfff5;
}

.analysis-header {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f5f5f5;
}

.analysis-header h4 {
  font-size: 15px;
  font-weight: 600;
  color: #1f2d3d;
  margin: 0 0 6px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.self-tag {
  font-size: 11px;
  background: #52c41a;
  color: #fff;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
}

.assessment {
  font-size: 13px;
  color: #888;
  margin: 0;
  line-height: 1.5;
}

.analysis-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.strengths-block h5,
.weaknesses-block h5 {
  font-size: 13px;
  font-weight: 600;
  margin: 0 0 6px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.strengths-block h5 {
  color: #52c41a;
}

.weaknesses-block h5 {
  color: #ff4d4f;
}

.strengths-block ul,
.weaknesses-block ul {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.strengths-block li,
.weaknesses-block li {
  font-size: 12px;
  color: #666;
  line-height: 1.5;
  padding-left: 16px;
  position: relative;
}

.strengths-block li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 7px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #b7eb8f;
}

.weaknesses-block li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 7px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ffccc7;
}

.strengths-block.empty h5,
.weaknesses-block.empty h5 {
  color: #999;
}

.no-data {
  font-size: 12px;
  color: #bbb;
  margin: 0;
  padding-left: 18px;
}

/* ========== 数据表格 ========== */
.data-table-wrapper {
  overflow-x: auto;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.data-table th,
.data-table td {
  padding: 12px 16px;
  text-align: center;
  border-bottom: 1px solid #f5f5f5;
}

.data-table th {
  background: #fafafa;
  font-weight: 600;
  color: #1f2d3d;
  white-space: nowrap;
}

.data-table th.self {
  background: #fcfff5;
  color: #52c41a;
}

.data-table td {
  color: #555;
  white-space: nowrap;
}

.data-table td.self {
  background: #fcfff5;
  font-weight: 500;
  color: #1f2d3d;
}

.data-table td.highlight {
  color: #52c41a;
  font-weight: 700;
}

.data-table tr:last-child td {
  border-bottom: none;
}

.data-table tbody tr:hover td {
  background: #fafafa;
}

.data-table tbody tr:hover td.self {
  background: #f0fbe6;
}

.self-tag-small {
  font-size: 10px;
  background: #52c41a;
  color: #fff;
  padding: 1px 6px;
  border-radius: 8px;
  margin-left: 4px;
}

.tags-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: center;
}

.tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  white-space: nowrap;
}

.tag.positive {
  background: #f6ffed;
  color: #52c41a;
}

.tag.negative {
  background: #fff2f0;
  color: #ff4d4f;
}

/* ========== 失败状态 ========== */
.failed-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px;
  gap: 12px;
  text-align: center;
}

.failed-state h3 {
  font-size: 16px;
  color: #faad14;
  margin: 0;
}

.failed-state p {
  font-size: 14px;
  color: #999;
  margin: 0;
}

/* ========== 空状态 ========== */
.empty-compare {
  margin-top: 8px;
}

.empty-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px;
  gap: 16px;
}

.empty-hint p {
  font-size: 15px;
  color: #bbb;
  margin: 0;
}

/* ========== 响应式 ========== */
@media (max-width: 768px) {
  .candidates-grid {
    grid-template-columns: 1fr;
  }

  .analysis-cards {
    grid-template-columns: 1fr;
  }

  .action-row {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }

  .compare-btn {
    justify-content: center;
  }

  .bar-chart {
    height: 280px;
  }

  .radar-chart {
    height: 280px;
  }
}
</style>
