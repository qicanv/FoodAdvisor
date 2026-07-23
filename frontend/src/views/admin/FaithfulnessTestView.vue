<template>
  <AdminLayout
    title="评价摘要忠实性测试"
    subtitle="验证 AI 生成的商家口碑摘要是否忠实于原始评价，发现虚构与遗漏内容"
  >
    <div class="faithfulness-page">
      <div v-if="errorMessage" class="message error-message">
        {{ errorMessage }}
      </div>
      <div v-if="successMessage" class="message success-message">
        {{ successMessage }}
      </div>

      <!-- 顶部操作栏 -->
      <section class="toolbar-card">
        <div>
          <h2>摘要忠实性评测</h2>
          <p>选择商家，加载评价摘要，通过 LLM-as-Judge 验证摘要中的每个声明是否可追溯到原始评价。</p>
        </div>
        <div class="toolbar-actions">
          <button
            class="primary-button"
            :disabled="!selectedMerchantId || !hasSummary || testing"
            @click="handleRunTest"
          >
            {{ testing ? '评测执行中...' : '执行忠实性测试' }}
          </button>
        </div>
      </section>

      <!-- 主体工作区 -->
      <div class="workspace">
        <!-- 左侧：商家选择与历史 -->
        <aside class="side-panel">
          <!-- 商家搜索 -->
          <div class="panel-card">
            <div class="panel-header">
              <h3>选择商家</h3>
              <button class="text-button" @click="loadMerchants">刷新</button>
            </div>
            <div class="search-box">
              <input
                v-model="merchantSearchKeyword"
                placeholder="搜索商家名称..."
                @keyup.enter="searchMerchantsList"
              />
              <button class="small-primary-button" @click="searchMerchantsList">搜索</button>
            </div>
            <div v-if="loadingMerchants" class="state-text">加载中...</div>
            <div v-else-if="merchants.length === 0" class="state-text">暂无商家</div>
            <div v-else class="merchant-list">
              <button
                v-for="merchant in merchants"
                :key="merchant.id"
                :class="['merchant-item', { active: Number(selectedMerchantId) === Number(merchant.id) }]"
                @click="selectMerchant(merchant.id)"
              >
                <div class="merchant-name">{{ merchant.name }}</div>
                <div class="merchant-meta">
                  <span>{{ merchant.cuisine || '未知菜系' }}</span>
                  <span>⭐ {{ merchant.rating || '-' }}</span>
                </div>
              </button>
              <div v-if="merchantPage.total > merchants.length" class="load-more">
                <button class="text-button" @click="loadMoreMerchants">加载更多...</button>
              </div>
            </div>
          </div>

          <!-- 测试历史 -->
          <div class="panel-card" v-if="selectedMerchantId">
            <div class="panel-header">
              <h3>测试历史</h3>
              <button class="text-button" :disabled="loadingHistory" @click="loadTestHistory">刷新</button>
            </div>
            <div v-if="loadingHistory" class="state-text">加载中...</div>
            <div v-else-if="testHistory.length === 0" class="state-text">暂无测试记录</div>
            <div v-else class="history-list">
              <button
                v-for="record in testHistory"
                :key="record.id"
                :class="['history-item', { active: Number(selectedTestId) === Number(record.id) }]"
                @click="selectTestRecord(record)"
              >
                <div class="history-header">
                  <span :class="['status-badge', testStatusClass(record.testStatus)]">
                    {{ testStatusText(record.testStatus) }}
                  </span>
                  <strong>{{ formatPercent(record.overallScore) }}</strong>
                </div>
                <div class="history-detail">
                  <span>声明: {{ record.totalClaims }}</span>
                  <span>忠实: {{ record.faithfulCount }}</span>
                </div>
                <small>{{ formatDate(record.createdAt) }}</small>
              </button>
            </div>
          </div>
        </aside>

        <!-- 右侧：主要内容区 -->
        <main class="content-panel">
          <!-- 未选择商家 -->
          <div v-if="!selectedMerchantId" class="empty-content">
            <p>请从左侧选择一个商家，查看其评价摘要并执行忠实性测试。</p>
          </div>

          <template v-else>
            <!-- 评价摘要展示 -->
            <section class="section-card">
              <div class="section-header">
                <div>
                  <h3>商家评价摘要</h3>
                  <p v-if="merchantSummary">
                    版本 {{ merchantSummary.version }} · 基于 {{ merchantSummary.reviewCount }} 条评价
                    · 状态: {{ summaryStatusText(merchantSummary.status) }}
                  </p>
                </div>
                <button class="secondary-button" :disabled="loadingSummary" @click="loadMerchantSummary">
                  刷新摘要
                </button>
              </div>
              <div v-if="loadingSummary" class="state-panel">正在加载摘要...</div>
              <div v-else-if="!hasSummary" class="state-panel">
                <p>当前商家暂无有效评价摘要。</p>
                <p class="hint">摘要状态: {{ merchantSummary?.status || '无' }} — 可能因评论不足或尚未生成。</p>
              </div>
              <div v-else class="summary-display">
                <!-- 总体摘要 -->
                <div class="summary-text-block">
                  <div class="block-label">总体口碑</div>
                  <p>{{ merchantSummary.summaryText || '暂无总体评价' }}</p>
                </div>

                <!-- 优点 -->
                <div class="summary-points" v-if="merchantSummary.advantages?.length">
                  <div class="block-label">👍 优点</div>
                  <ul>
                    <li v-for="(point, idx) in merchantSummary.advantages" :key="'adv-' + idx">
                      <strong>{{ point.name }}</strong>
                      <span class="mention-count">提及 {{ point.mentionCount }} 次</span>
                      <span class="review-ref">评价ID: {{ (point.reviewIds || []).join(', ') }}</span>
                    </li>
                  </ul>
                </div>

                <!-- 不足 -->
                <div class="summary-points" v-if="merchantSummary.disadvantages?.length">
                  <div class="block-label">👎 不足</div>
                  <ul>
                    <li v-for="(point, idx) in merchantSummary.disadvantages" :key="'dis-' + idx">
                      <strong>{{ point.name }}</strong>
                      <span class="mention-count">提及 {{ point.mentionCount }} 次</span>
                      <span class="review-ref">评价ID: {{ (point.reviewIds || []).join(', ') }}</span>
                    </li>
                  </ul>
                </div>

                <!-- 推荐菜 -->
                <div class="summary-points" v-if="merchantSummary.recommendedDishes?.length">
                  <div class="block-label">🍽️ 推荐菜品</div>
                  <ul>
                    <li v-for="(point, idx) in merchantSummary.recommendedDishes" :key="'dish-' + idx">
                      <strong>{{ point.name }}</strong>
                      <span class="mention-count">提及 {{ point.mentionCount }} 次</span>
                    </li>
                  </ul>
                </div>

                <!-- 环境 / 服务 -->
                <div class="summary-meta-row" v-if="envText || svcText">
                  <div class="summary-meta-item" v-if="envText">
                    <div class="block-label">🏠 环境</div>
                    <p>{{ envText }}</p>
                  </div>
                  <div class="summary-meta-item" v-if="svcText">
                    <div class="block-label">💁 服务</div>
                    <p>{{ svcText }}</p>
                  </div>
                </div>

                <!-- 近期变化 -->
                <div class="summary-points" v-if="recentChanges.length">
                  <div class="block-label">📈 近期变化</div>
                  <ul>
                    <li v-for="(change, idx) in recentChanges" :key="'change-' + idx">
                      <strong>{{ change.text }}</strong>
                      <span :class="['trend-badge', trendClass(change.direction)]">
                        {{ trendText(change.direction) }}
                      </span>
                    </li>
                  </ul>
                </div>
              </div>
            </section>

            <!-- 评价原文列表 -->
            <section class="section-card">
              <div class="section-header">
                <div>
                  <h3>原始评价（证据池）</h3>
                  <p>共 {{ reviews.length }} 条评价，用于忠实性验证</p>
                </div>
                <div class="section-actions">
                  <label class="review-count-label">
                    参评数量
                    <select v-model.number="reviewLimit" @change="loadReviews">
                      <option :value="10">最近 10 条</option>
                      <option :value="20">最近 20 条</option>
                      <option :value="50">最近 50 条</option>
                      <option :value="100">最近 100 条</option>
                    </select>
                  </label>
                  <button class="secondary-button" :disabled="loadingReviews" @click="loadReviews">刷新</button>
                </div>
              </div>
              <div v-if="loadingReviews" class="state-panel">正在加载评价...</div>
              <div v-else-if="reviews.length === 0" class="state-panel">暂无评价数据</div>
              <div v-else class="review-grid">
                <article v-for="review in reviews" :key="review.reviewId || review.id" class="review-card">
                  <div class="review-card-header">
                    <code>评价 #{{ review.reviewId || review.id }}</code>
                    <span class="rating-stars">{{ '⭐'.repeat(review.rating || 0) }}</span>
                  </div>
                  <p class="review-content">{{ review.content }}</p>
                </article>
              </div>
            </section>

            <!-- 测试结果区 -->
            <section class="section-card" v-if="testResult">
              <div class="section-header">
                <div>
                  <h3>忠实性测试结果</h3>
                  <p>
                    模型: {{ testResult.modelName || '-' }}
                    · 追踪ID: {{ testResult.businessTraceId || '-' }}
                    <span v-if="testResult.modelVersion"> · 版本: {{ testResult.modelVersion }}</span>
                    <span v-if="testResult.promptVersion"> · 提示词: {{ testResult.promptVersion }}</span>
                  </p>
                </div>
                <span :class="['status-badge', 'large-badge', testStatusClass(testResult.testStatus)]">
                  {{ testStatusText(testResult.testStatus) }}
                </span>
              </div>

              <!-- 整体评分卡片 -->
              <div class="score-overview">
                <div class="score-main">
                  <div class="score-circle" :class="scoreLevelClass(testResult.overallScore)">
                    <span class="score-value">{{ formatPercent(testResult.overallScore) }}</span>
                    <span class="score-label">忠实性得分</span>
                  </div>
                </div>
                <div class="score-details">
                  <div class="score-item faithful">
                    <strong>{{ testResult.faithfulCount }}</strong>
                    <span>忠实声明</span>
                  </div>
                  <div class="score-item unfaithful">
                    <strong>{{ testResult.unfaithfulCount }}</strong>
                    <span>不忠实声明</span>
                  </div>
                  <div class="score-item uncertain">
                    <strong>{{ testResult.uncertainCount }}</strong>
                    <span>不确定声明</span>
                  </div>
                  <div class="score-item total">
                    <strong>{{ testResult.totalClaims }}</strong>
                    <span>声明总数</span>
                  </div>
                </div>
              </div>

              <!-- 声明级结果详情 -->
              <div class="claim-results">
                <div class="claim-results-header">
                  <h4>声明验证详情</h4>
                  <div class="claim-filter">
                    <button
                      v-for="filter in claimFilters"
                      :key="filter.value"
                      :class="['filter-chip', { active: claimFilter === filter.value }]"
                      @click="claimFilter = filter.value"
                    >
                      {{ filter.label }} ({{ claimFilterCount(filter.value) }})
                    </button>
                  </div>
                </div>

                <div v-if="filteredClaims.length === 0" class="state-panel">
                  没有符合筛选条件的声明
                </div>
                <div v-else class="claim-list">
                  <article
                    v-for="claim in filteredClaims"
                    :key="claim.claimIndex"
                    :class="['claim-card', verdictCardClass(claim.verdict)]"
                  >
                    <div class="claim-header">
                      <div class="claim-title">
                        <span :class="['verdict-badge', verdictClass(claim.verdict)]">
                          {{ verdictText(claim.verdict) }}
                        </span>
                        <span class="claim-type-tag">{{ claimTypeText(claim.claimType) }}</span>
                        <strong>{{ claim.claimText }}</strong>
                      </div>
                      <div class="claim-confidence">
                        置信度: {{ (claim.confidence * 100).toFixed(0) }}%
                      </div>
                    </div>
                    <div class="claim-body">
                      <div class="claim-reasoning">
                        <span class="reasoning-label">AI 判定理由：</span>
                        <p>{{ claim.reasoning }}</p>
                      </div>
                      <div class="claim-meta">
                        <span>引用评价ID: {{ (claim.citedReviewIds || []).join(', ') || '无' }}</span>
                        <span>实际匹配: {{ claim.actualMatchingCount }} 条</span>
                        <span v-if="claimAnnotations[claim.claimIndex]?.annotated">
                          · 人工标注: <strong :class="annotationVerdictClass(claimAnnotations[claim.claimIndex])">{{
                            annotationVerdictText(claimAnnotations[claim.claimIndex])
                          }}</strong>
                        </span>
                      </div>
                    </div>

                    <!-- 人工标注区（AC3：标记虚构/错误/遗漏） -->
                    <div class="annotation-section">
                      <div class="annotation-label">🔍 人工标注（测试人员复核）</div>
                      <div class="annotation-controls">
                        <div class="annotation-verdict-group">
                          <span class="annotation-prompt">标记为：</span>
                          <button
                            v-for="opt in annotationOptions"
                            :key="opt.value"
                            :class="[
                              'annotation-chip',
                              {
                                active:
                                  claimAnnotations[claim.claimIndex]?.issueType === opt.value
                              }
                            ]"
                            @click="setAnnotation(claim, opt.value)"
                          >
                            {{ opt.label }}
                          </button>
                        </div>
                        <input
                          v-model="claimAnnotations[claim.claimIndex].note"
                          class="annotation-note-input"
                          placeholder="补充说明（选填）..."
                          maxlength="500"
                          @blur="onAnnotationChanged(claim)"
                        />
                        <span
                          v-if="claimAnnotations[claim.claimIndex]?.annotated"
                          class="annotated-check"
                        >
                          ✅ 已标注
                        </span>
                      </div>
                    </div>

                    <div class="claim-actions">
                      <button
                        class="small-optimization-btn"
                        :class="{ active: isClaimInOptimization(claim) }"
                        @click="toggleOptimizationClaim(claim)"
                      >
                        {{ isClaimInOptimization(claim) ? '✓ 已加入优化清单' : '+ 加入优化清单' }}
                      </button>
                      <span
                        v-if="isClaimInOptimization(claim)"
                        class="optimization-confirm"
                      >点击上方按钮提交到后端</span>
                    </div>
                  </article>
                </div>

                <!-- 人工标注汇总 -->
                <div v-if="annotationSummary.total > 0" class="annotation-summary-bar">
                  <span>📋 人工标注汇总：</span>
                  <span class="annotation-count accurate">准确 {{ annotationSummary.accurate }}</span>
                  <span class="annotation-count fabricated">虚构 {{ annotationSummary.fabricated }}</span>
                  <span class="annotation-count incorrect">错误 {{ annotationSummary.incorrect }}</span>
                  <span class="annotation-count missing">遗漏 {{ annotationSummary.missing }}</span>
                  <span class="annotation-count other">其他 {{ annotationSummary.other }}</span>
                  <button
                    class="primary-button"
                    @click="submitOptimizationItems"
                    :disabled="optimizationClaimSet.size === 0"
                  >
                    提交优化清单 ({{ optimizationClaimSet.size }} 项)
                  </button>
                </div>
              </div>
            </section>

            <!-- 对比区域 -->
            <section class="section-card" v-if="testHistory.length >= 2">
              <div class="section-header">
                <div>
                  <h3>测试结果对比</h3>
                  <p>比较两次忠实性测试结果，追踪优化效果</p>
                </div>
              </div>
              <div class="compare-controls">
                <label>
                  基准测试
                  <select v-model.number="baselineTestId" @change="comparisonResult = null">
                    <option v-for="record in testHistory" :key="'bl-' + record.id" :value="record.id">
                      #{{ record.id }} · {{ formatPercent(record.overallScore) }} · {{ formatDate(record.createdAt) }}
                    </option>
                  </select>
                </label>
                <div class="compare-arrow">→</div>
                <label>
                  候选测试
                  <select v-model.number="candidateTestId" @change="comparisonResult = null">
                    <option v-for="record in testHistory" :key="'cl-' + record.id" :value="record.id">
                      #{{ record.id }} · {{ formatPercent(record.overallScore) }} · {{ formatDate(record.createdAt) }}
                    </option>
                  </select>
                </label>
                <button
                  class="primary-button"
                  :disabled="comparing || !baselineTestId || !candidateTestId || Number(baselineTestId) === Number(candidateTestId)"
                  @click="handleCompare"
                >
                  {{ comparing ? '对比中...' : '开始对比' }}
                </button>
              </div>
              <div
                v-if="baselineTestId && candidateTestId && Number(baselineTestId) === Number(candidateTestId)"
                class="compare-warning"
              >
                请选择两次不同的测试记录。
              </div>
              <template v-if="comparisonResult">
                <div class="comparison-summary-row">
                  <div class="comparison-item improved">
                    <strong>{{ comparisonResult.improvedCount || 0 }}</strong>
                    <span>已修复声明</span>
                  </div>
                  <div class="comparison-item regressed">
                    <strong>{{ comparisonResult.regressedCount || 0 }}</strong>
                    <span>新增不忠实</span>
                  </div>
                  <div class="comparison-item unchanged">
                    <strong>{{ comparisonResult.unchangedCount || 0 }}</strong>
                    <span>无变化</span>
                  </div>
                  <div class="comparison-item">
                    <strong>{{ formatPercent(comparisonResult.candidateScore) }}</strong>
                    <span>
                      候选得分
                      <small :class="scoreChangeClass(comparisonResult.scoreChange)">{{
                        formatPercentChange(comparisonResult.scoreChange)
                      }}</small>
                    </span>
                  </div>
                </div>
                <div v-if="comparisonResult.comparisonNote" class="comparison-note">
                  {{ comparisonResult.comparisonNote }}
                </div>
              </template>
            </section>

            <!-- 优化清单 -->
            <section class="section-card" v-if="optimizationItems.length > 0 || showOptimizationList">
              <div class="section-header">
                <div>
                  <h3>优化清单</h3>
                  <p>管理忠实性测试中发现的失败案例，跟踪处理进度</p>
                </div>
                <button class="secondary-button" @click="loadOptimizationList">刷新</button>
              </div>
              <div class="optimization-filters">
                <select v-model="optimizationStatusFilter" @change="loadOptimizationList">
                  <option value="">全部状态</option>
                  <option value="OPEN">待处理</option>
                  <option value="IN_PROGRESS">处理中</option>
                  <option value="RESOLVED">已解决</option>
                  <option value="DISMISSED">已驳回</option>
                </select>
              </div>
              <div class="table-wrapper" v-if="optimizationItems.length > 0">
                <table>
                  <thead>
                    <tr>
                      <th>ID</th>
                      <th>商家</th>
                      <th>声明内容</th>
                      <th>问题类型</th>
                      <th>判定</th>
                      <th>状态</th>
                      <th>创建时间</th>
                      <th>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="item in optimizationItems" :key="item.id">
                      <td><code>#{{ item.id }}</code></td>
                      <td>{{ item.merchantName || '-' }}</td>
                      <td class="claim-text-cell">{{ item.claimText }}</td>
                      <td>{{ issueTypeText(item.issueType) }}</td>
                      <td>
                        <span :class="['status-badge', verdictClass(item.verdict)]">
                          {{ verdictText(item.verdict) }}
                        </span>
                      </td>
                      <td>
                        <span :class="['status-badge', optimizationStatusClass(item.status)]">
                          {{ optimizationStatusText(item.status) }}
                        </span>
                      </td>
                      <td>{{ formatDate(item.createdAt) }}</td>
                      <td>
                        <select
                          :value="item.status"
                          @change="updateItemStatus(item, $event.target.value)"
                        >
                          <option value="OPEN">待处理</option>
                          <option value="IN_PROGRESS">处理中</option>
                          <option value="RESOLVED">已解决</option>
                          <option value="DISMISSED">已驳回</option>
                        </select>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <div v-else class="state-panel">暂无优化清单项</div>
            </section>
          </template>
        </main>
      </div>
    </div>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import AdminLayout from '../../components/AdminLayout.vue'
import {
  runFaithfulnessTest,
  getTestHistory,
  getTestDetail,
  addToOptimizationList,
  getOptimizationList,
  updateOptimizationItem,
  compareTestResults,
  searchMerchants,
  getMerchantSummary,
  getMerchantReviews
} from '../../api/faithfulnessTest'

// --- 商家相关 ---
const merchants = ref([])
const merchantSearchKeyword = ref('')
const selectedMerchantId = ref(null)
const loadingMerchants = ref(false)
const merchantPage = ref({ total: 0, pageNum: 1, pageSize: 20 })

// --- 摘要相关 ---
const merchantSummary = ref(null)
const loadingSummary = ref(false)

// --- 评价相关 ---
const reviews = ref([])
const loadingReviews = ref(false)
const reviewLimit = ref(20)

// --- 测试执行 ---
const testing = ref(false)
const testResult = ref(null)
const selectedTestId = ref(null)
const errorMessage = ref('')
const successMessage = ref('')

// --- 测试历史 ---
const testHistory = ref([])
const loadingHistory = ref(false)

// --- 声明过滤 ---
const claimFilter = ref('ALL')
const claimFilters = [
  { label: '全部', value: 'ALL' },
  { label: '忠实', value: 'FAITHFUL' },
  { label: '不忠实', value: 'UNFAITHFUL' },
  { label: '不确定', value: 'UNCERTAIN' }
]

// --- 优化清单 ---
const optimizationItems = ref([])
const showOptimizationList = ref(false)
const optimizationStatusFilter = ref('')
const optimizationClaimSet = ref(new Set())

// --- 人工标注（AC3：标记虚构/错误/遗漏内容） ---
// 存储每个声明的标注：{ claimIndex: { issueType, note, annotated } }
const claimAnnotations = ref({})

const annotationOptions = [
  { label: '准确（确认AI）', value: 'ACCURATE' },
  { label: '虚构内容', value: 'FABRICATED' },
  { label: '事实错误', value: 'INCORRECT' },
  { label: '遗漏信息', value: 'MISSING' },
  { label: '其他问题', value: 'OTHER' }
]

const setAnnotation = (claim, issueType) => {
  const existing = claimAnnotations.value[claim.claimIndex] || { issueType: '', note: '', annotated: false }
  if (existing.issueType === issueType) {
    claimAnnotations.value[claim.claimIndex] = { ...existing, issueType: '', annotated: false }
  } else {
    claimAnnotations.value[claim.claimIndex] = { ...existing, issueType, annotated: true }
  }
  claimAnnotations.value = { ...claimAnnotations.value }
}

const onAnnotationChanged = () => {
  claimAnnotations.value = { ...claimAnnotations.value }
}

const annotationVerdictText = (annotation) => {
  const map = { ACCURATE: '准确', FABRICATED: '虚构', INCORRECT: '错误', MISSING: '遗漏', OTHER: '其他' }
  return map[annotation?.issueType] || '已标注'
}

const annotationVerdictClass = (annotation) => {
  const map = { ACCURATE: 'annotation-accurate', FABRICATED: 'annotation-fabricated', INCORRECT: 'annotation-incorrect', MISSING: 'annotation-missing', OTHER: 'annotation-other' }
  return map[annotation?.issueType] || ''
}

const annotationSummary = computed(() => {
  const summary = { total: 0, accurate: 0, fabricated: 0, incorrect: 0, missing: 0, other: 0 }
  Object.values(claimAnnotations.value).forEach(a => {
    if (a?.annotated && a?.issueType) {
      summary.total++
      switch (a.issueType) {
        case 'ACCURATE': summary.accurate++; break
        case 'FABRICATED': summary.fabricated++; break
        case 'INCORRECT': summary.incorrect++; break
        case 'MISSING': summary.missing++; break
        default: summary.other++; break
      }
    }
  })
  return summary
})

// --- 对比 ---
const baselineTestId = ref(null)
const candidateTestId = ref(null)
const comparing = ref(false)
const comparisonResult = ref(null)

// --- 计算属性 ---
const hasSummary = computed(() => {
  return merchantSummary.value?.status === 'SUCCESS' && merchantSummary.value?.summaryText
})

const envText = computed(() => {
  const env = merchantSummary.value?.environmentSummary
  if (!env || typeof env !== 'object') return null
  return env.text || null
})

const svcText = computed(() => {
  const svc = merchantSummary.value?.serviceSummary
  if (!svc || typeof svc !== 'object') return null
  return svc.text || null
})

const recentChanges = computed(() => {
  const changes = merchantSummary.value?.recentChanges
  if (!Array.isArray(changes)) return []
  return changes
})

const hasUnfaithfulClaims = computed(() => {
  return (testResult.value?.claimResults || []).some(c => c.verdict === 'UNFAITHFUL')
})

const filteredClaims = computed(() => {
  const claims = testResult.value?.claimResults || []
  if (claimFilter.value === 'ALL') {
    return claims.map((c, i) => ({ ...c, claimIndex: i }))
  }
  return claims
    .map((c, i) => ({ ...c, claimIndex: i }))
    .filter(c => c.verdict === claimFilter.value)
})

// --- 方法 ---
const showError = (msg) => {
  errorMessage.value = msg || '操作失败'
  successMessage.value = ''
  setTimeout(() => { errorMessage.value = '' }, 8000)
}

const showSuccess = (msg) => {
  successMessage.value = msg
  errorMessage.value = ''
  setTimeout(() => { successMessage.value = '' }, 5000)
}

// 商家列表
const searchMerchantsList = async () => {
  loadingMerchants.value = true
  merchantPage.value.pageNum = 1
  const result = await searchMerchants({
    pageNum: 1,
    pageSize: merchantPage.value.pageSize,
    keyword: merchantSearchKeyword.value || undefined
  })
  loadingMerchants.value = false
  if (!result.success) {
    showError(result.message)
    return
  }
  merchants.value = result.data?.records || []
  merchantPage.value.total = result.data?.total || 0
}

const loadMoreMerchants = async () => {
  const nextPage = merchantPage.value.pageNum + 1
  loadingMerchants.value = true
  const result = await searchMerchants({
    pageNum: nextPage,
    pageSize: merchantPage.value.pageSize,
    keyword: merchantSearchKeyword.value || undefined
  })
  loadingMerchants.value = false
  if (!result.success) {
    showError(result.message)
    return
  }
  merchants.value.push(...(result.data?.records || []))
  merchantPage.value.pageNum = nextPage
  merchantPage.value.total = result.data?.total || 0
}

const loadMerchants = () => {
  merchantSearchKeyword.value = ''
  searchMerchantsList()
}

const selectMerchant = async (merchantId) => {
  selectedMerchantId.value = merchantId
  testResult.value = null
  selectedTestId.value = null
  comparisonResult.value = null
  claimAnnotations.value = {}
  optimizationClaimSet.value = new Set()
  errorMessage.value = ''
  successMessage.value = ''
  await Promise.all([loadMerchantSummary(), loadReviews(), loadTestHistory()])
}

// 摘要
const loadMerchantSummary = async () => {
  if (!selectedMerchantId.value) return
  loadingSummary.value = true
  const result = await getMerchantSummary(selectedMerchantId.value)
  loadingSummary.value = false
  if (!result.success) {
    merchantSummary.value = null
    showError(result.message)
    return
  }
  merchantSummary.value = result.data
}

// 评价
const loadReviews = async () => {
  if (!selectedMerchantId.value) return
  loadingReviews.value = true
  const result = await getMerchantReviews(selectedMerchantId.value, {
    pageSize: reviewLimit.value
  })
  loadingReviews.value = false
  if (!result.success) {
    reviews.value = []
    showError(result.message)
    return
  }
  reviews.value = (result.data?.records || result.data || [])
}

// 测试执行
const handleRunTest = async () => {
  if (!selectedMerchantId.value || !hasSummary.value) {
    showError('请先选择有有效摘要的商家')
    return
  }
  if (reviews.value.length === 0) {
    showError('当前商家没有可用于测试的评价')
    return
  }

  testing.value = true
  errorMessage.value = ''
  successMessage.value = ''

  // 构建请求：将前端摘要VO转为AI服务需要的格式
  const summary = buildSummaryPayload()
  const reviewItems = reviews.value.map(r => ({
    reviewId: r.reviewId || r.id,
    content: r.content,
    rating: r.rating || null
  }))

  const result = await runFaithfulnessTest({
    merchantId: Number(selectedMerchantId.value),
    summary,
    reviews: reviewItems
  })

  testing.value = false

  if (!result.success) {
    showError(result.message || '忠实性测试执行失败')
    return
  }

  testResult.value = result.data
  initClaimAnnotations(result.data)
  optimizationClaimSet.value = new Set()
  showSuccess(`忠实性测试完成，整体得分: ${formatPercent(result.data.overallScore)}`)
  await loadTestHistory()
}

const buildSummaryPayload = () => {
  const s = merchantSummary.value
  if (!s) return null
  return {
    merchantId: Number(s.merchantId),
    version: s.version || 1,
    summaryStatus: s.status || 'SUCCESS',
    summaryText: s.summaryText || null,
    advantages: (s.advantages || []).map(p => ({
      name: p.name, mentionCount: p.mentionCount, reviewIds: p.reviewIds || []
    })),
    disadvantages: (s.disadvantages || []).map(p => ({
      name: p.name, mentionCount: p.mentionCount, reviewIds: p.reviewIds || []
    })),
    recommendedDishes: (s.recommendedDishes || []).map(p => ({
      name: p.name, mentionCount: p.mentionCount, reviewIds: p.reviewIds || []
    })),
    environmentSummary: s.environmentSummary || {},
    serviceSummary: s.serviceSummary || {},
    recentChanges: Array.isArray(s.recentChanges) ? s.recentChanges : [],
    reviewCount: s.reviewCount || reviews.value.length,
    minimumReviewCount: s.minimumReviewCount || 3,
    evidences: []
  }
}

// 测试历史
const loadTestHistory = async () => {
  if (!selectedMerchantId.value) return
  loadingHistory.value = true
  const result = await getTestHistory(selectedMerchantId.value)
  loadingHistory.value = false
  if (!result.success) {
    testHistory.value = []
    return
  }
  testHistory.value = Array.isArray(result.data?.records || result.data)
    ? (result.data?.records || result.data || [])
    : []

  // 设置默认对比选项
  if (testHistory.value.length >= 2) {
    if (!baselineTestId.value || !testHistory.value.find(t => Number(t.id) === Number(baselineTestId.value))) {
      candidateTestId.value = testHistory.value[0].id
      baselineTestId.value = testHistory.value[testHistory.value.length - 1].id
    }
  }
}

const selectTestRecord = async (record) => {
  selectedTestId.value = record.id
  const result = await getTestDetail(record.id)
  if (result.success) {
    testResult.value = result.data
    initClaimAnnotations(result.data)
  }
}

// 初始化每个声明的标注对象，避免 v-model 访问 undefined
const initClaimAnnotations = (testData) => {
  const annotations = {}
  const claims = testData?.claimResults || []
  claims.forEach((_, i) => {
    annotations[i] = { issueType: '', note: '', annotated: false }
  })
  claimAnnotations.value = annotations
}

// 优化清单
const isClaimInOptimization = (claim) => {
  return optimizationClaimSet.value.has(claim.claimIndex)
}

const toggleOptimizationClaim = (claim) => {
  const key = claim.claimIndex
  if (optimizationClaimSet.value.has(key)) {
    optimizationClaimSet.value.delete(key)
  } else {
    optimizationClaimSet.value.add(key)
  }
  optimizationClaimSet.value = new Set(optimizationClaimSet.value)
}

const addAllUnfaithfulToOptimization = () => {
  const claims = testResult.value?.claimResults || []
  claims.forEach((c, i) => {
    if (c.verdict === 'UNFAITHFUL') {
      optimizationClaimSet.value.add(i)
    }
  })
  optimizationClaimSet.value = new Set(optimizationClaimSet.value)
  showOptimizationList.value = true

  // 提交到后端
  submitOptimizationItems()
}

const submitOptimizationItems = async () => {
  const claimResults = testResult.value?.claimResults || []
  const indices = Array.from(optimizationClaimSet.value)
  const items = indices.map(i => {
    const annotation = claimAnnotations.value[i] || {}
    return {
      claimIndex: i,
      claimType: claimResults[i]?.claimType || '',
      claimText: claimResults[i]?.claimText || '',
      verdict: claimResults[i]?.verdict || 'UNFAITHFUL',
      issueType: annotation.issueType || 'OTHER',
      note: annotation.note || '',
      reasoning: claimResults[i]?.reasoning || ''
    }
  })

  if (items.length === 0) return

  const result = await addToOptimizationList(selectedTestId.value || testResult.value?.id || 0, {
    merchantId: Number(selectedMerchantId.value),
    items
  })

  if (result.success) {
    showSuccess(`${items.length} 个声明已加入优化清单`)
    await loadOptimizationList()
  } else {
    showError(result.message || '加入优化清单失败')
  }
}

const loadOptimizationList = async () => {
  const result = await getOptimizationList({
    merchantId: selectedMerchantId.value || undefined,
    status: optimizationStatusFilter.value || undefined
  })
  if (result.success) {
    optimizationItems.value = result.data?.records || result.data || []
    if (optimizationItems.value.length > 0) {
      showOptimizationList.value = true
    }
  }
}

const updateItemStatus = async (item, newStatus) => {
  const result = await updateOptimizationItem(item.id, { status: newStatus })
  if (result.success) {
    await loadOptimizationList()
  } else {
    showError(result.message || '状态更新失败')
  }
}

// 对比
const handleCompare = async () => {
  if (!baselineTestId.value || !candidateTestId.value) return
  comparing.value = true
  comparisonResult.value = null
  const result = await compareTestResults(baselineTestId.value, candidateTestId.value)
  comparing.value = false
  if (!result.success) {
    showError(result.message)
    return
  }
  comparisonResult.value = result.data
}

// 辅助计算
const claimFilterCount = (verdict) => {
  const claims = testResult.value?.claimResults || []
  if (verdict === 'ALL') return claims.length
  return claims.filter(c => c.verdict === verdict).length
}

// --- 格式化辅助函数 ---
const formatPercent = (value) => {
  const num = Number(value)
  if (!Number.isFinite(num)) return '0.00%'
  return `${(num * 100).toFixed(2)}%`
}

const formatPercentChange = (value) => {
  const num = Number(value)
  if (!Number.isFinite(num)) return '0.00%'
  const prefix = num > 0 ? '+' : ''
  return `${prefix}${(num * 100).toFixed(2)}%`
}

const formatDate = (value) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

const testStatusText = (status) => {
  const map = { SUCCESS: '全部忠实', PARTIAL: '部分不忠实', FAILED: '测试失败' }
  return map[status] || status || '-'
}

const testStatusClass = (status) => {
  const map = { SUCCESS: 'success', PARTIAL: 'warning', FAILED: 'danger' }
  return map[status] || 'muted'
}

const summaryStatusText = (status) => {
  const map = { SUCCESS: '已生成', INSUFFICIENT_DATA: '评论不足', NONE: '未生成' }
  return map[status] || status || '-'
}

const verdictText = (verdict) => {
  const map = { FAITHFUL: '忠实', UNFAITHFUL: '不忠实', UNCERTAIN: '不确定' }
  return map[verdict] || verdict || '-'
}

const verdictClass = (verdict) => {
  const map = { FAITHFUL: 'success', UNFAITHFUL: 'danger', UNCERTAIN: 'warning' }
  return map[verdict] || 'muted'
}

const verdictCardClass = (verdict) => {
  const map = { FAITHFUL: 'faithful-card', UNFAITHFUL: 'unfaithful-card', UNCERTAIN: 'uncertain-card' }
  return map[verdict] || ''
}

const claimTypeText = (type) => {
  const map = {
    advantage: '优点', disadvantage: '不足', recommendedDish: '推荐菜',
    environmentSummary: '环境', serviceSummary: '服务',
    recentChange: '近期变化', summaryText: '总体摘要'
  }
  return map[type] || type || '-'
}

const scoreLevelClass = (score) => {
  if (score >= 0.9) return 'score-excellent'
  if (score >= 0.7) return 'score-good'
  if (score >= 0.5) return 'score-fair'
  return 'score-poor'
}

const scoreChangeClass = (change) => {
  const num = Number(change)
  if (num > 0) return 'change-positive'
  if (num < 0) return 'change-negative'
  return 'change-neutral'
}

const issueTypeText = (type) => {
  const map = {
    FABRICATED: '虚构内容', MISLEADING: '歪曲/误导',
    MISSING: '遗漏信息', INCORRECT: '事实错误', OTHER: '其他'
  }
  return map[type] || type || '-'
}

const optimizationStatusText = (status) => {
  const map = { OPEN: '待处理', IN_PROGRESS: '处理中', RESOLVED: '已解决', DISMISSED: '已驳回' }
  return map[status] || status || '-'
}

const optimizationStatusClass = (status) => {
  const map = { OPEN: 'warning', IN_PROGRESS: 'warning', RESOLVED: 'success', DISMISSED: 'muted' }
  return map[status] || 'muted'
}

const trendText = (direction) => {
  const map = { IMPROVING: '改善中', DECLINING: '下降中', STABLE: '稳定' }
  return map[direction] || direction || '-'
}

const trendClass = (direction) => {
  const map = { IMPROVING: 'success', DECLINING: 'danger', STABLE: 'muted' }
  return map[direction] || 'muted'
}

// 生命周期
onMounted(() => {
  loadMerchants()
})
</script>

<style scoped>
.faithfulness-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.message {
  padding: 12px 16px;
  border-radius: 10px;
  font-size: 14px;
}

.error-message {
  color: #b42318;
  background: #fef3f2;
  border: 1px solid #fecdca;
}

.success-message {
  color: #027a48;
  background: #ecfdf3;
  border: 1px solid #abefc6;
}

/* 顶部工具栏 */
.toolbar-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 22px 24px;
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 14px;
}

.toolbar-card h2 {
  margin: 0;
  color: #1d2939;
}

.toolbar-card p {
  margin: 6px 0 0;
  color: #667085;
}

/* 工作区 */
.workspace {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 20px;
}

/* 左侧面板 */
.side-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  position: sticky;
  top: 112px;
  align-self: start;
}

.panel-card {
  padding: 18px;
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 14px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.panel-header h3 {
  margin: 0;
  color: #1d2939;
  font-size: 15px;
}

.search-box {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}

.search-box input {
  flex: 1;
  min-height: 36px;
  padding: 7px 10px;
  box-sizing: border-box;
  border: 1px solid #d0d5dd;
  border-radius: 8px;
  font: inherit;
  font-size: 13px;
}

.small-primary-button {
  padding: 6px 10px;
  color: #fff;
  background: #1684f8;
  border: none;
  border-radius: 7px;
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  white-space: nowrap;
}

.merchant-list,
.history-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 400px;
  overflow-y: auto;
}

.merchant-item,
.history-item {
  padding: 12px;
  text-align: left;
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 9px;
  cursor: pointer;
  transition: all 0.2s;
}

.merchant-item.active,
.history-item.active {
  border-color: #1684f8;
  background: #eff8ff;
  box-shadow: 0 0 0 2px rgba(22, 132, 248, 0.08);
}

.merchant-item:hover,
.history-item:hover {
  border-color: #b2ddff;
}

.merchant-name {
  color: #1d2939;
  font-weight: 700;
  margin-bottom: 5px;
}

.merchant-meta {
  display: flex;
  gap: 10px;
  color: #667085;
  font-size: 12px;
}

.history-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.history-detail {
  display: flex;
  gap: 12px;
  color: #667085;
  font-size: 12px;
  margin-bottom: 4px;
}

.history-item small {
  color: #98a2b3;
  font-size: 11px;
}

.load-more {
  padding: 8px;
  text-align: center;
}

.state-text {
  padding: 16px;
  color: #98a2b3;
  text-align: center;
  font-size: 13px;
}

/* 右侧内容 */
.content-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
}

.empty-content {
  padding: 60px 35px;
  color: #98a2b3;
  text-align: center;
  font-size: 15px;
}

.section-card {
  padding: 22px;
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 14px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 18px;
}

.section-header h3 {
  margin: 0;
  color: #1d2939;
}

.section-header p {
  margin: 6px 0 0;
  color: #667085;
  font-size: 13px;
}

.section-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.review-count-label {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #475467;
  font-size: 13px;
}

.review-count-label select {
  min-height: 36px;
  padding: 6px 8px;
  border: 1px solid #d0d5dd;
  border-radius: 7px;
  background: #fff;
}

.state-panel {
  padding: 35px;
  color: #98a2b3;
  text-align: center;
}

.state-panel .hint {
  font-size: 12px;
  color: #b0b7c3;
  margin-top: 6px;
}

/* 摘要展示 */
.summary-display {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.summary-text-block {
  padding: 16px;
  background: #f8fafc;
  border: 1px solid #e4e7ec;
  border-radius: 10px;
}

.summary-text-block p {
  margin: 8px 0 0;
  color: #344054;
  line-height: 1.7;
}

.block-label {
  color: #1684f8;
  font-size: 12px;
  font-weight: 700;
}

.summary-points ul {
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.summary-points li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: #f8fafc;
  border-radius: 7px;
  font-size: 13px;
}

.summary-points li strong {
  color: #1d2939;
}

.mention-count {
  color: #98a2b3;
  font-size: 11px;
}

.review-ref {
  color: #b0b7c3;
  font-size: 11px;
  margin-left: auto;
}

.summary-meta-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.summary-meta-item {
  padding: 14px;
  background: #f8fafc;
  border: 1px solid #e4e7ec;
  border-radius: 10px;
}

.summary-meta-item p {
  margin: 6px 0 0;
  color: #344054;
  font-size: 13px;
  line-height: 1.6;
}

.trend-badge {
  display: inline-flex;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
}

.trend-badge.success { color: #027a48; background: #ecfdf3; }
.trend-badge.danger { color: #b42318; background: #fef3f2; }
.trend-badge.muted { color: #475467; background: #f2f4f7; }

/* 评价卡片网格 */
.review-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  max-height: 500px;
  overflow-y: auto;
}

.review-card {
  padding: 14px;
  background: #f8fafc;
  border: 1px solid #eaecf0;
  border-radius: 9px;
}

.review-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.review-card-header code {
  color: #1684f8;
  font-size: 12px;
}

.rating-stars {
  font-size: 12px;
}

.review-content {
  margin: 0;
  color: #344054;
  font-size: 13px;
  line-height: 1.65;
}

/* 测试结果 - 评分概览 */
.score-overview {
  display: flex;
  align-items: center;
  gap: 30px;
  padding: 20px;
  background: #f8fafc;
  border-radius: 12px;
  margin-bottom: 22px;
}

.score-circle {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 4px solid;
}

.score-excellent { border-color: #027a48; background: #ecfdf3; }
.score-good { border-color: #1684f8; background: #eff8ff; }
.score-fair { border-color: #b54708; background: #fffaeb; }
.score-poor { border-color: #b42318; background: #fef3f2; }

.score-value {
  font-size: 24px;
  font-weight: 800;
  color: #1d2939;
}

.score-label {
  font-size: 11px;
  color: #667085;
  margin-top: 2px;
}

.score-details {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  flex: 1;
}

.score-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 14px;
  border-radius: 10px;
}

.score-item strong {
  font-size: 28px;
}

.score-item span {
  font-size: 12px;
}

.score-item.faithful { background: #ecfdf3; color: #027a48; }
.score-item.unfaithful { background: #fef3f2; color: #b42318; }
.score-item.uncertain { background: #fffaeb; color: #b54708; }
.score-item.total { background: #f2f4f7; color: #475467; }

/* 声明结果 */
.claim-results-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.claim-results-header h4 {
  margin: 0;
  color: #344054;
}

.claim-filter {
  display: flex;
  gap: 6px;
}

.filter-chip {
  padding: 5px 12px;
  border: 1px solid #d0d5dd;
  border-radius: 999px;
  background: #fff;
  color: #667085;
  cursor: pointer;
  font-size: 12px;
  transition: all 0.2s;
}

.filter-chip.active {
  border-color: #1684f8;
  background: #eff8ff;
  color: #1684f8;
}

.claim-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.claim-card {
  padding: 16px;
  border: 1px solid #e4e7ec;
  border-radius: 10px;
  transition: border-color 0.2s;
}

.claim-card.faithful-card { border-left: 4px solid #027a48; }
.claim-card.unfaithful-card { border-left: 4px solid #b42318; }
.claim-card.uncertain-card { border-left: 4px solid #b54708; }

.claim-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 10px;
}

.claim-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.claim-title strong {
  color: #1d2939;
  font-size: 14px;
}

.claim-type-tag {
  padding: 2px 8px;
  background: #f2f4f7;
  color: #667085;
  border-radius: 4px;
  font-size: 11px;
}

.claim-confidence {
  color: #98a2b3;
  font-size: 12px;
  white-space: nowrap;
}

.claim-reasoning {
  padding: 10px 12px;
  background: #f8fafc;
  border-radius: 7px;
  margin-bottom: 8px;
}

.reasoning-label {
  color: #667085;
  font-size: 12px;
  font-weight: 600;
}

.claim-reasoning p {
  margin: 5px 0 0;
  color: #475467;
  font-size: 13px;
  line-height: 1.6;
}

.claim-meta {
  display: flex;
  gap: 16px;
  color: #98a2b3;
  font-size: 12px;
}

.claim-actions {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #eaecf0;
}

/* 人工标注区（AC3） */
.annotation-section {
  margin-top: 12px;
  padding: 14px;
  background: #fafbfc;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
}

.annotation-label {
  color: #475467;
  font-size: 12px;
  font-weight: 700;
  margin-bottom: 10px;
}

.annotation-controls {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.annotation-verdict-group {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.annotation-prompt {
  color: #667085;
  font-size: 12px;
  margin-right: 2px;
}

.annotation-chip {
  padding: 4px 10px;
  border: 1px solid #d0d5dd;
  border-radius: 999px;
  background: #fff;
  color: #667085;
  cursor: pointer;
  font-size: 11px;
  transition: all 0.2s;
}

.annotation-chip:hover {
  border-color: #1684f8;
  color: #1684f8;
}

.annotation-chip.active {
  border-color: #1684f8;
  background: #eff8ff;
  color: #1684f8;
  font-weight: 600;
}

.annotation-note-input {
  flex: 1;
  min-width: 180px;
  min-height: 30px;
  padding: 4px 8px;
  box-sizing: border-box;
  border: 1px solid #d0d5dd;
  border-radius: 6px;
  font: inherit;
  font-size: 12px;
  background: #fff;
}

.annotated-check {
  color: #027a48;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.annotation-accurate { color: #027a48; }
.annotation-fabricated { color: #b42318; }
.annotation-incorrect { color: #b54708; }
.annotation-missing { color: #b54708; }
.annotation-other { color: #667085; }

/* 标注汇总栏 */
.annotation-summary-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
  padding: 14px 16px;
  background: #f8fafc;
  border: 1px solid #e4e7ec;
  border-radius: 10px;
  font-size: 13px;
  color: #475467;
}

.annotation-count {
  display: inline-flex;
  gap: 3px;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.annotation-count.accurate {
  color: #027a48;
  background: #ecfdf3;
}

.annotation-count.fabricated {
  color: #b42318;
  background: #fef3f2;
}

.annotation-count.incorrect,
.annotation-count.missing {
  color: #b54708;
  background: #fffaeb;
}

.annotation-count.other {
  color: #475467;
  background: #f2f4f7;
}

.small-optimization-btn {
  padding: 5px 12px;
  border: 1px solid #d0d5dd;
  border-radius: 6px;
  background: #fff;
  color: #667085;
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  transition: all 0.2s;
}

.small-optimization-btn:hover {
  border-color: #1684f8;
  color: #1684f8;
}

.small-optimization-btn.active {
  border-color: #027a48;
  background: #ecfdf3;
  color: #027a48;
}

.optimization-confirm {
  color: #98a2b3;
  font-size: 11px;
}

/* 对比控制 */
.compare-controls {
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 14px;
}

.compare-controls label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: #475467;
  font-size: 13px;
}

.compare-controls select {
  min-width: 260px;
  min-height: 38px;
  padding: 7px 10px;
  border: 1px solid #d0d5dd;
  border-radius: 8px;
  background: #fff;
  font: inherit;
  font-size: 13px;
}

.compare-arrow {
  padding-bottom: 10px;
  color: #98a2b3;
  font-size: 20px;
}

.compare-warning {
  margin-top: 14px;
  padding: 11px 14px;
  color: #b54708;
  background: #fffaeb;
  border: 1px solid #fedf89;
  border-radius: 8px;
}

.comparison-summary-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.comparison-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 18px;
  border-radius: 10px;
}

.comparison-item strong {
  font-size: 26px;
}

.comparison-item span {
  font-size: 12px;
  color: #667085;
}

.comparison-item span small {
  margin-left: 4px;
  font-weight: 700;
}

.comparison-item.improved { background: #ecfdf3; color: #027a48; }
.comparison-item.regressed { background: #fef3f2; color: #b42318; }
.comparison-item.unchanged { background: #f2f4f7; color: #475467; }

.comparison-note {
  margin-top: 14px;
  padding: 12px 14px;
  background: #f8fafc;
  border-radius: 8px;
  color: #475467;
  font-size: 13px;
  line-height: 1.6;
}

/* 优化清单 */
.optimization-filters {
  margin-bottom: 14px;
}

.optimization-filters select {
  min-height: 36px;
  padding: 6px 10px;
  border: 1px solid #d0d5dd;
  border-radius: 7px;
  background: #fff;
  font: inherit;
  font-size: 13px;
}

.table-wrapper {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th, td {
  padding: 10px 12px;
  border-bottom: 1px solid #eaecf0;
  text-align: left;
  vertical-align: top;
  font-size: 13px;
}

th {
  color: #475467;
  background: #f9fafb;
  font-size: 12px;
  white-space: nowrap;
}

td {
  color: #344054;
}

.claim-text-cell {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

td select {
  min-height: 30px;
  padding: 3px 6px;
  border: 1px solid #d0d5dd;
  border-radius: 6px;
  background: #fff;
  font-size: 12px;
}

/* 通用组件 */
.primary-button,
.secondary-button,
.text-button {
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font: inherit;
}

.primary-button {
  padding: 10px 17px;
  color: #fff;
  background: #1684f8;
}

.secondary-button {
  padding: 9px 14px;
  color: #344054;
  background: #f2f4f7;
}

.text-button {
  color: #1684f8;
  background: transparent;
  font-size: 13px;
  padding: 4px 8px;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.status-badge {
  display: inline-flex;
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 12px;
}

.status-badge.large-badge {
  padding: 7px 14px;
  font-size: 14px;
  font-weight: 600;
}

.status-badge.success { color: #027a48; background: #ecfdf3; }
.status-badge.warning { color: #b54708; background: #fffaeb; }
.status-badge.danger { color: #b42318; background: #fef3f2; }
.status-badge.muted { color: #475467; background: #f2f4f7; }

.verdict-badge {
  display: inline-flex;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}

.verdict-badge.success { color: #027a48; background: #ecfdf3; }
.verdict-badge.danger { color: #b42318; background: #fef3f2; }
.verdict-badge.warning { color: #b54708; background: #fffaeb; }

.change-positive { color: #027a48; }
.change-negative { color: #b42318; }
.change-neutral { color: #667085; }

.toolbar-actions {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}

/* 响应式 */
@media (max-width: 1200px) {
  .workspace {
    grid-template-columns: 1fr;
  }
  .side-panel {
    position: static;
  }
  .review-grid {
    grid-template-columns: 1fr;
  }
  .score-details {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .comparison-summary-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .toolbar-card {
    flex-direction: column;
    gap: 14px;
  }
  .score-overview {
    flex-direction: column;
    text-align: center;
  }
  .score-details {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    width: 100%;
  }
  .compare-controls {
    flex-direction: column;
    align-items: stretch;
  }
  .compare-controls select {
    width: 100%;
    min-width: 0;
  }
  .compare-arrow {
    display: none;
  }
  .claim-results-header {
    flex-direction: column;
  }
  .summary-meta-row {
    grid-template-columns: 1fr;
  }
}
</style>
