<template>
  <MerchantLayout title="评论情感分析" subtitle="AI 驱动的评论情感洞察，快速提取海量用户反馈的关键优缺点">
    <div class="sentiment-container">
      <!-- ========== 顶部操作栏 ========== -->
      <div class="toolbar">
        <div class="toolbar-left">
          <div class="store-selector">
            <label class="toolbar-label">店铺</label>
            <select v-model="selectedStoreId" class="toolbar-select">
              <option v-for="s in stores" :key="s.id" :value="s.id">{{ s.name }}</option>
            </select>
          </div>
          <div class="time-filter">
            <label class="toolbar-label">时间</label>
            <select v-model="timeRange" class="toolbar-select">
              <option value="7d">近 7 天</option>
              <option value="30d">近 30 天</option>
              <option value="90d">近 90 天</option>
              <option value="all">全部</option>
            </select>
          </div>
          <div class="mode-selector">
            <label class="toolbar-label">分析模式</label>
            <select v-model="analysisMode" class="toolbar-select">
              <option value="hybrid">⚡ 深度分析（推荐）</option>
              <option value="local">🚀 快速分析（本地AI）</option>
              <option value="llm">☁️ 全面分析（云端AI）</option>
            </select>
          </div>
          <button class="btn-analyze" @click="triggerAnalysis" :disabled="analyzing">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
              <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
            </svg>
            <span>{{ analyzing ? '分析中...' : '一键分析' }}</span>
          </button>
        </div>
        <div class="toolbar-right">
          <span class="last-update" v-if="lastUpdateTime">
            最近更新：{{ lastUpdateTime }}
          </span>
          <button class="btn-refresh" @click="loadAll">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/><path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"/><path d="M16 21h5v-5"/>
            </svg>
          </button>
        </div>
      </div>

      <!-- 消息提示 -->
      <div v-if="messageText" :class="['message-banner', messageType]">
        <span>{{ messageText }}</span>
        <button class="message-close" @click="messageText = ''">✕</button>
      </div>

      <!-- ========== 概览卡片 ========== -->
      <div class="summary-cards">
        <div class="summary-card">
          <div class="summary-icon total">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#fff" stroke-width="2">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/>
              <line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/>
            </svg>
          </div>
          <div class="summary-info">
            <p class="summary-label">已分析评价</p>
            <p class="summary-value">{{ summary.totalAnalyzed }}</p>
            <p class="summary-sub">共 {{ summary.totalReviews }} 条评价</p>
          </div>
        </div>

        <div class="summary-card">
          <div class="summary-icon positive">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#fff" stroke-width="2">
              <circle cx="12" cy="12" r="10"/><path d="M8 14s1.5 2 4 2 4-2 4-2"/><line x1="9" y1="9" x2="9.01" y2="9"/><line x1="15" y1="9" x2="15.01" y2="9"/>
            </svg>
          </div>
          <div class="summary-info">
            <p class="summary-label">好评率</p>
            <p class="summary-value">{{ summary.positiveRate }}%</p>
            <p :class="['summary-sub', summary.positiveTrend >= 0 ? 'trend-up' : 'trend-down']">
              {{ summary.positiveTrend >= 0 ? '↑' : '↓' }} {{ Math.abs(summary.positiveTrend) }}%
            </p>
          </div>
        </div>

        <div class="summary-card">
          <div class="summary-icon negative">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#fff" stroke-width="2">
              <circle cx="12" cy="12" r="10"/><path d="M8 15s1.5-2 4-2 4 2 4 2"/><line x1="9" y1="9" x2="9.01" y2="9"/><line x1="15" y1="9" x2="15.01" y2="9"/>
            </svg>
          </div>
          <div class="summary-info">
            <p class="summary-label">差评率</p>
            <p class="summary-value">{{ summary.negativeRate }}%</p>
            <p :class="['summary-sub', summary.negativeTrend <= 0 ? 'trend-up' : 'trend-down']">
              {{ summary.negativeTrend <= 0 ? '↓' : '↑' }} {{ Math.abs(summary.negativeTrend) }}%
            </p>
          </div>
        </div>

        <div class="summary-card">
          <div class="summary-icon complaint">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#fff" stroke-width="2">
              <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
              <line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>
            </svg>
          </div>
          <div class="summary-info">
            <p class="summary-label">主要差评维度</p>
            <p class="summary-value" style="font-size:18px">{{ summary.topComplaintDimension }}</p>
            <p class="summary-sub">{{ summary.topComplaintCount }} 条提及</p>
          </div>
        </div>
      </div>

      <!-- ========== 图表区域 ========== -->
      <div class="charts-row">
        <!-- 整体情感分布 -->
        <div class="chart-card">
          <div class="chart-header">
            <h3>整体情感分布</h3>
          </div>
          <div class="chart-body">
            <div class="donut-chart-wrapper">
              <svg viewBox="0 0 220 220" class="donut-chart">
                <circle cx="110" cy="110" r="80" fill="none" stroke="#f0f0f0" stroke-width="26"/>
                <circle
                  v-for="(seg, i) in sentimentDonutSegments"
                  :key="i"
                  cx="110" cy="110" r="80" fill="none"
                  :stroke="seg.color" stroke-width="26"
                  :stroke-dasharray="seg.dashArray"
                  :stroke-dashoffset="seg.dashOffset"
                  transform="rotate(-90 110 110)"
                  class="donut-segment"
                />
                <text x="110" y="110" text-anchor="middle" class="donut-center-value">{{ summary.totalAnalyzed }}</text>
                <text x="110" y="128" text-anchor="middle" class="donut-center-label">总分析数</text>
              </svg>
              <div class="donut-legend">
                <div v-for="item in sentimentDist" :key="item.label" class="legend-row">
                  <span class="legend-dot" :style="{background: item.color}"></span>
                  <span class="legend-label">{{ item.label }}</span>
                  <span class="legend-value">{{ item.count }}</span>
                  <span class="legend-pct">{{ item.percentage }}%</span>
                </div>
              </div>
            </div>
            <!-- 口碑趋势小图 -->
            <div class="trend-mini" style="margin-top:16px; padding-top:12px; border-top:1px solid #f0f0f0">
              <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:8px">
                <span style="font-size:13px;font-weight:600;color:#1f2d3d">📈 口碑趋势</span>
                <div class="trend-tabs">
                  <button :class="['dim-tab', { active: trendPeriod === 'week' }]" @click="trendPeriod='week'">周</button>
                  <button :class="['dim-tab', { active: trendPeriod === 'month' }]" @click="trendPeriod='month'">月</button>
                </div>
              </div>
              <div v-if="trendLabels.length === 0" class="empty-hint">暂无趋势数据</div>
              <svg v-else viewBox="0 0 440 150" class="trend-svg" style="width:100%;height:auto">
                <line v-for="i in 5" :key="'gy'+i" :x1="35" :y1="8 + i * 24" :x2="432" :y2="8 + i * 24" stroke="#f5f5f5" stroke-width="0.8"/>
                <text v-for="i in 5" :key="'yl'+i" x="30" :y="12 + i * 24" fill="#bbb" font-size="8" text-anchor="end">{{ 5 - i }}</text>
                <text v-for="(l, i) in trendLabels" :key="'xl'+i" :x="35 + i * xStep" y="145" fill="#bbb" font-size="7" text-anchor="middle" :transform="'rotate(-30 ' + (35 + i * xStep) + ' 145)'">{{ l }}</text>
                <polyline :points="ratingLine" fill="none" stroke="#1890ff" stroke-width="1.6"/>
                <circle v-for="(p, i) in ratingPoints" :key="'rd'+i" :cx="p.x" :cy="p.y" r="2.5" fill="#fff" stroke="#1890ff" stroke-width="1.2"/>
                <polyline :points="posRateLine" fill="none" stroke="#52c41a" stroke-width="1" stroke-dasharray="4 2"/>
                <polyline :points="negRateLine" fill="none" stroke="#ff4d4f" stroke-width="1" stroke-dasharray="4 2"/>
                <rect x="300" y="1" width="7" height="7" fill="#1890ff" rx="1.5"/>
                <text x="309" y="8" fill="#aaa" font-size="8">均分</text>
                <rect x="335" y="1" width="7" height="7" fill="#52c41a" rx="1.5"/>
                <text x="344" y="8" fill="#aaa" font-size="8">好评率</text>
                <rect x="378" y="1" width="7" height="7" fill="#ff4d4f" rx="1.5"/>
                <text x="387" y="8" fill="#aaa" font-size="8">差评率</text>
              </svg>
            </div>
          </div>
        </div>

        <!-- 维度情感雷达 -->
        <div class="chart-card">
          <div class="chart-header">
            <h3>各维度情感分析</h3>
            <div class="chart-tabs">
              <button
                v-for="d in dimensionTabs"
                :key="d.key"
                :class="['dim-tab', { active: activeDimension === d.key }]"
                @click="activeDimension = d.key"
              >{{ d.label }}</button>
            </div>
          </div>
          <div class="chart-body">
            <div class="dimension-bars">
              <div v-for="dim in dimensionData" :key="dim.key" class="dimension-item">
                <div class="dim-header">
                  <span class="dim-name">{{ dim.label }}</span>
                  <span class="dim-score">{{ dim.coverage }}% 提及率</span>
                </div>
                <div class="dim-bar-track">
                  <div class="dim-bar-positive" :style="{width: dim.positivePct + '%'}">
                    <span v-if="dim.positivePct > 15">{{ dim.positivePct }}%</span>
                  </div>
                  <div class="dim-bar-neutral" :style="{width: dim.neutralPct + '%', left: dim.positivePct + '%'}">
                    <span v-if="dim.neutralPct > 15">{{ dim.neutralPct }}%</span>
                  </div>
                  <div class="dim-bar-negative" :style="{width: dim.negativePct + '%', left: (dim.positivePct + dim.neutralPct) + '%'}">
                    <span v-if="dim.negativePct > 15">{{ dim.negativePct }}%</span>
                  </div>
                </div>
                <div class="dim-legend-row">
                  <span class="dim-legend-pos">👍 {{ dim.positiveCount }}</span>
                  <span class="dim-legend-neg">👎 {{ dim.negativeCount }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ========== 关键洞察 ========== -->
      <div class="charts-row">
        <!-- 好评关键词云 -->
        <div class="chart-card">
          <div class="chart-header">
            <h3>🏆 好评关键词 TOP10</h3>
          </div>
          <div class="chart-body">
            <div class="keyword-cloud">
              <span
                v-for="kw in positiveKeywords"
                :key="kw.word"
                class="keyword-tag positive"
                :style="{fontSize: (14 + kw.count * 2.5) + 'px', opacity: 0.5 + kw.count / (maxPositiveKwCount * 2)}"
                @click="filterByKeyword(kw.word)"
                :title="'点击筛选包含「' + kw.word + '」的评价'"
              >{{ kw.word }}<small>{{ kw.count }}</small></span>
            </div>
          </div>
        </div>

        <!-- 差评问题归类 -->
        <div class="chart-card">
          <div class="chart-header">
            <h3>⚠️ 差评问题归类</h3>
          </div>
          <div class="chart-body">
            <div class="issue-list">
              <div v-for="(issue, idx) in complaintIssues" :key="issue.category" class="issue-row" @click="openIssueDetail(issue)">
                <span class="issue-rank" :style="{background: rankColor(idx)}">{{ idx + 1 }}</span>
                <div class="issue-info">
                  <span class="issue-name">{{ issue.categoryName }}</span>
                  <div class="issue-bar-track">
                    <div class="issue-bar-fill" :style="{width: issue.percentage + '%', background: rankColor(idx)}"></div>
                  </div>
                </div>
                <span class="issue-count">{{ issue.count }}条</span>
                <span class="issue-pct">{{ issue.percentage }}%</span>
              </div>
              <div v-if="complaintIssues.length === 0" class="empty-hint">暂无差评数据</div>
            </div>
          </div>
        </div>
      </div>

      <!-- ========== 商家亮点挖掘 ========== -->
      <div class="highlights-card">
        <div class="chart-header">
          <h3>🌟 商家亮点</h3>
          <button class="btn-generate-highlights" @click="generateHighlights" :disabled="highlightsLoading">
            {{ highlightsLoading ? '生成中...' : (highlights.length > 0 ? '刷新亮点' : '生成亮点') }}
          </button>
        </div>
        <div class="chart-body">
          <div v-if="highlightsLoading" class="empty-hint">正在生成亮点...</div>
          <div v-else-if="highlightsStatus === 'INSUFFICIENT_DATA'" class="highlight-status-warn">
            ⚠️ 正面评论数量不足（当前 {{ highlightsAvailCount || 0 }} 条，需要 {{ highlightsMinCount || 5 }} 条），暂无法生成亮点
          </div>
          <div v-else-if="highlightsStatus === 'NONE'" class="empty-hint">点击"生成亮点"按钮，AI 将从好评中挖掘您的店铺优势</div>
          <div v-else-if="highlights.length === 0" class="empty-hint">暂无亮点数据</div>
          <div v-else class="highlights-grid">
            <div v-for="hl in highlights" :key="hl.highlightId" :class="['highlight-item', getHighlightClass(hl.highlightType)]" @click="showHighlightEvidence(hl)">
              <div class="hl-type-icon">{{ getHighlightIcon(hl.highlightType) }}</div>
              <div class="hl-content">
                <h4>{{ hl.title }}</h4>
                <p>{{ hl.description }}</p>
                <div class="hl-meta">
                  <span class="hl-count">🔥 {{ hl.mentionCount }} 次提及</span>
                  <span class="hl-ratio">👍 {{ (hl.positiveRatio * 100).toFixed(0) }}% 好评</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ========== AI 口碑摘要 ========== -->
      <div class="ai-summary-card" v-if="aiSummary">
        <div class="ai-summary-header">
          <h3>
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
              <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
            </svg>
            AI 口碑总结
          </h3>
          <span class="ai-badge">AI 生成</span>
        </div>
        <div class="ai-summary-body">
          <div class="ai-summary-section">
            <h4>✅ 主要优点</h4>
            <ul>
              <li v-for="(adv, i) in aiSummary.advantages" :key="i">
                <strong>{{ adv.name }}</strong>（{{ adv.mentionCount }} 条评价提及）
              </li>
            </ul>
          </div>
          <div class="ai-summary-section">
            <h4>❌ 主要不足</h4>
            <ul>
              <li v-for="(dis, i) in aiSummary.disadvantages" :key="i">
                <strong>{{ dis.name }}</strong>（{{ dis.mentionCount }} 条评价提及）
              </li>
            </ul>
          </div>
          <div class="ai-summary-section" v-if="aiSummary.recommendedDishes && aiSummary.recommendedDishes.length">
            <h4>🍽️ 推荐菜品</h4>
            <div class="recommend-tags">
              <span v-for="(d, i) in aiSummary.recommendedDishes" :key="i" class="recommend-tag">
                {{ d.name }} <small>({{ d.mentionCount }})</small>
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- ========== 评论明细列表 ========== -->
      <div class="reviews-card">
        <div class="chart-header">
          <h3>评论分析明细</h3>
          <div class="list-filters">
            <select v-model="filterSentiment" class="filter-select" @change="loadReviews">
              <option value="">全部情感</option>
              <option value="POSITIVE">正面</option>
              <option value="NEGATIVE">负面</option>
              <option value="NEUTRAL">中性</option>
              <option value="MIXED">混合</option>
            </select>
            <select v-model="filterDimension" class="filter-select" @change="loadReviews">
              <option value="">全部维度</option>
              <option value="SERVICE">服务</option>
              <option value="TASTE">口味</option>
              <option value="PRICE">价格</option>
              <option value="ENVIRONMENT">环境</option>
            </select>
            <div class="search-box">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="#999" stroke-width="2">
                <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
              </svg>
              <input v-model="searchKeyword" placeholder="搜索关键词..." class="filter-search" @input="onSearchInput" />
              <button v-if="searchKeyword" class="clear-search-btn" @click="clearKeywordFilter" title="清除搜索">✕</button>
            </div>
          </div>
        </div>
        <div class="reviews-table-wrapper">
          <table class="reviews-table">
            <thead>
              <tr>
                <th style="width:8%">评分</th>
                <th style="width:32%">评价内容</th>
                <th style="width:10%">整体情感</th>
                <th style="width:10%">置信度</th>
                <th style="width:22%">维度分析</th>
                <th style="width:10%">关键词</th>
                <th style="width:8%">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="rv in reviewList" :key="rv.reviewId" @click="openDetail(rv)">
                <td>
                  <span :class="['rating-badge', 'r' + rv.rating]">{{ rv.rating }}分</span>
                </td>
                <td class="content-cell" :title="rv.content">{{ truncate(rv.content, 60) }}</td>
                <td>
                  <span :class="['sentiment-tag', rv.sentiment.toLowerCase()]">{{ sentimentLabel(rv.sentiment) }}</span>
                </td>
                <td>
                  <div class="confidence-bar" :title="'置信度: ' + (rv.confidence * 100).toFixed(0) + '%'">
                    <div class="confidence-fill" :style="{width: rv.confidence * 100 + '%', background: confidenceColor(rv.confidence)}"></div>
                  </div>
                </td>
                <td>
                  <div class="aspect-mini-tags">
                    <span
                      v-for="asp in rv.aspects"
                      :key="asp.category"
                      :class="['aspect-mini-tag', asp.sentiment.toLowerCase()]"
                      :title="asp.text"
                    >{{ aspectLabel(asp.category) }}</span>
                  </div>
                </td>
                <td>
                  <div class="keyword-mini">
                    <span v-for="kw in rv.keywords.slice(0, 3)" :key="kw" class="kw-tag">{{ kw }}</span>
                  </div>
                </td>
                <td>
                  <button class="btn-detail" @click.stop="openDetail(rv)">详情</button>
                </td>
              </tr>
              <tr v-if="reviewList.length === 0">
                <td colspan="7" class="empty-cell">暂无评价数据</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="pagination" v-if="totalPages > 1">
          <button :disabled="page <= 1" @click="page--; loadReviews()">上一页</button>
          <span v-for="p in visiblePages" :key="p"
                :class="['page-btn', { active: p === page }]"
                @click="page = p; loadReviews()">{{ p }}</span>
          <button :disabled="page >= totalPages" @click="page++; loadReviews()">下一页</button>
        </div>
      </div>

      <!-- ========== 评价详情弹窗 ========== -->
      <div v-if="detailVisible" class="modal-overlay" @click.self="detailVisible = false">
        <div class="modal-panel">
          <div class="modal-header">
            <h3>评价情感分析详情</h3>
            <button class="modal-close" @click="detailVisible = false">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <div class="modal-body" v-if="currentDetail">
            <div class="detail-original">
              <div class="detail-label">📝 评价原文</div>
              <p>{{ currentDetail.content }}</p>
              <div class="detail-meta">
                <span>评分：{{ currentDetail.rating }}分</span>
                <span>时间：{{ currentDetail.reviewTime || '-' }}</span>
              </div>
            </div>

            <!-- 评价辅助回复 -->
            <div class="detail-reply-section">
              <div class="detail-label">💬 评价辅助回复</div>

              <!-- 错误提示 -->
              <div v-if="replyError" class="reply-error-banner">
                <span>{{ replyError }}</span>
                <button class="reply-error-close" @click="replyError = ''">✕</button>
              </div>

              <!-- 已发布状态 -->
              <div v-if="replyDraft && replyDraft.status === 'PUBLISHED'" class="reply-card reply-published">
                <div class="reply-card-header">
                  <span class="reply-status-badge published">✅ 已发布</span>
                  <span class="reply-strategy-hint">{{ replyDraft.strategy === 'POSITIVE' ? '👍 好评策略' : '🔧 差评策略' }}</span>
                </div>
                <p class="reply-content-text">{{ getEffectiveContent(replyDraft) }}</p>
                <div class="reply-card-meta" v-if="replyDraft.publishedAt">
                  发布时间：{{ new Date(replyDraft.publishedAt).toLocaleString('zh-CN') }}
                </div>
              </div>

              <!-- 编辑模式 -->
              <div v-else-if="replyEditing" class="reply-card reply-editing">
                <div class="reply-card-header">
                  <span class="reply-edit-mode-label">编辑回复内容</span>
                  <span class="reply-strategy-hint">{{ replyDraft?.strategy === 'POSITIVE' ? '👍 好评策略' : '🔧 差评策略' }}</span>
                </div>
                <textarea
                  v-model="replyEditText"
                  class="reply-edit-textarea"
                  rows="6"
                  maxlength="500"
                  placeholder="编辑回复内容..."
                ></textarea>
                <div class="reply-edit-actions">
                  <span class="reply-char-count">{{ replyEditText.length }}/500</span>
                  <button class="btn-secondary" @click="handleCancelEdit" :disabled="replyLoading">取消</button>
                  <button class="btn-primary-sm" @click="handleSaveEdit" :disabled="replyLoading">
                    {{ replyLoading ? '保存中...' : '保存修改' }}
                  </button>
                </div>
              </div>

              <!-- 草稿展示模式 -->
              <div v-else-if="replyDraft && replyDraft.status === 'DRAFT'" class="reply-card reply-draft">
                <div class="reply-card-header">
                  <span class="reply-status-badge draft">📝 草稿</span>
                  <span class="reply-strategy-hint">{{ replyDraft.strategy === 'POSITIVE' ? '👍 好评策略' : '🔧 差评策略' }}</span>
                </div>
                <p class="reply-content-text">{{ getEffectiveContent(replyDraft) }}</p>
                <div class="reply-card-meta" v-if="replyDraft.generatedAt">
                  生成时间：{{ new Date(replyDraft.generatedAt).toLocaleString('zh-CN') }}
                </div>
                <div class="reply-draft-actions">
                  <button class="btn-danger-text" @click="handleDiscardDraft" :disabled="replyLoading">丢弃</button>
                  <button class="btn-secondary" @click="handleStartEdit" :disabled="replyLoading">编辑</button>
                  <button class="btn-publish" @click="handlePublishDraft" :disabled="replyPublishing">
                    {{ replyPublishing ? '发布中...' : '发布回复' }}
                  </button>
                </div>
              </div>

              <!-- 无草稿：生成按钮 -->
              <div v-else class="reply-card reply-generate">
                <p class="reply-generate-hint">根据评价内容自动生成回复建议。好评将表达感谢并回应具体优点，差评将道歉并提供改进承诺。</p>
                <button class="btn-generate-reply" @click="handleGenerateReply" :disabled="replyLoading">
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                    <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
                  </svg>
                  {{ replyLoading ? 'AI 正在生成回复建议...' : '生成回复建议' }}
                </button>
              </div>
            </div>

            <div class="detail-analysis">
              <div class="detail-label">🔍 AI 分析结果</div>
              <div class="detail-sentiment-row">
                <span>整体情感：</span>
                <span :class="['sentiment-tag', currentDetail.sentiment.toLowerCase()]">{{ sentimentLabel(currentDetail.sentiment) }}</span>
                <span>置信度：{{ (currentDetail.confidence * 100).toFixed(0) }}%</span>
              </div>
              <div class="detail-model-row" v-if="currentDetail.modelName">
                <span class="detail-label-sm">分析模型：</span>
                <span class="model-name-tag">{{ modelNameLabel(currentDetail.modelName) }}</span>
              </div>
              <div class="detail-aspects">
                <span class="detail-label-sm">维度详情：</span>
                <div class="aspect-cards">
                  <div v-for="asp in currentDetail.aspects" :key="asp.category" :class="['aspect-card', asp.sentiment.toLowerCase()]">
                    <span class="aspect-card-label">{{ aspectLabel(asp.category) }}</span>
                    <span :class="['aspect-card-sentiment', asp.sentiment.toLowerCase()]">{{ sentimentLabel(asp.sentiment) }}</span>
                    <span class="aspect-card-text">{{ asp.text }}</span>
                  </div>
                </div>
              </div>
              <div class="detail-keywords" v-if="currentDetail.keywords && currentDetail.keywords.length">
                <span class="detail-label-sm">关键词：</span>
                <span v-for="kw in currentDetail.keywords" :key="kw" class="kw-tag">{{ kw }}</span>
              </div>
              <div class="detail-issues" v-if="currentDetail.issueCategories && currentDetail.issueCategories.length">
                <span class="detail-label-sm">⚠️ 问题归因：</span>
                <div v-for="iss in currentDetail.issueCategories" :key="iss.category" class="issue-item">
                  <span class="issue-tag">{{ iss.categoryName }}</span>
                  <span class="issue-conf">置信度 {{ (iss.confidence * 100).toFixed(0) }}%</span>
                  <span class="issue-evidence" v-if="iss.evidenceText">"{{ iss.evidenceText }}"</span>
                </div>
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn-secondary" @click="detailVisible = false">关闭</button>
          </div>
        </div>
      </div>

      <!-- ========== 亮点证据弹窗 ========== -->
      <div v-if="hlEvidenceVisible" class="modal-overlay" @click.self="hlEvidenceVisible = false">
        <div class="modal-panel">
          <div class="modal-header">
            <h3>🌟 亮点依据：{{ currentHlTitle }}</h3>
            <button class="modal-close" @click="hlEvidenceVisible = false">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <div v-if="hlEvidenceLoading" class="empty-hint">加载中...</div>
            <div v-else-if="hlEvidences.length === 0" class="empty-hint">暂无评价依据</div>
            <div v-else class="issue-review-list">
              <div v-for="ev in hlEvidences" :key="ev.evidenceId || ev.reviewId" class="issue-review-item" style="border-left-color:#52c41a">
                <div class="ir-header">
                  <span v-if="ev.rating" class="rating-badge r4">{{ ev.rating }}分</span>
                  <span class="ir-confidence">提及于 {{ ev.reviewTime || '-' }}</span>
                </div>
                <p class="ir-content">{{ ev.reviewContent || ev.evidenceExcerpt }}</p>
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn-secondary" @click="hlEvidenceVisible = false">关闭</button>
          </div>
        </div>
      </div>

      <!-- ========== 差评归因钻取弹窗 ========== -->
      <div v-if="issueDetailVisible" class="modal-overlay" @click.self="issueDetailVisible = false">
        <div class="modal-panel">
          <div class="modal-header">
            <h3>⚠️ 差评归因：{{ currentIssue?.categoryName }}</h3>
            <button class="modal-close" @click="issueDetailVisible = false">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <div v-if="issueReviewsLoading" class="empty-hint">加载中...</div>
            <div v-else-if="issueReviews.length === 0" class="empty-hint">暂无相关评价</div>
            <div v-else class="issue-review-list">
              <div v-for="ir in issueReviews" :key="ir.reviewId" class="issue-review-item">
                <div class="ir-header">
                  <span :class="['rating-badge', 'r' + ir.rating]">{{ ir.rating }}分</span>
                  <span class="ir-confidence">置信度 {{ (ir.confidence * 100).toFixed(0) }}%</span>
                </div>
                <p class="ir-content">{{ ir.content }}</p>
                <p class="ir-evidence" v-if="ir.evidenceText">
                  <span class="ir-evidence-label">问题依据：</span>"{{ ir.evidenceText }}"
                </p>
              </div>
            </div>
            <!-- 分页 -->
            <div class="pagination" v-if="issueTotalPages > 1">
              <button :disabled="issuePage <= 1" @click="issuePage--; loadIssueReviews()">上一页</button>
              <span v-for="p in issueVisiblePages" :key="p"
                    :class="['page-btn', { active: p === issuePage }]"
                    @click="issuePage = p; loadIssueReviews()">{{ p }}</span>
              <button :disabled="issuePage >= issueTotalPages" @click="issuePage++; loadIssueReviews()">下一页</button>
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn-secondary" @click="issueDetailVisible = false">关闭</button>
          </div>
        </div>
      </div>
    </div>
  </MerchantLayout>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import MerchantLayout from '../../components/MerchantLayout.vue'
import { getSentimentSummary, getSentimentReviews, triggerBatchAnalysis } from '../../api/sentiment'
import { getMyMerchants, getMerchantHighlights, generateMerchantHighlights, getMerchantHighlightEvidences } from '../../api/merchantConsole'
import { getIssueCategoryReviews } from '../../api/reviewAnalysis'
import {
  generateReplyDraft,
  editReplyDraft,
  publishReplyDraft,
  getReplyDraft,
  discardReplyDraft,
} from '../../api/reviewReply'

// ========== 状态 ==========
const stores = ref([{ id: 0, name: '全部店铺' }])
const selectedStoreId = ref(0)
const timeRange = ref('all')
const analysisMode = ref('hybrid')  // 'local' | 'llm' | 'hybrid'
const analyzing = ref(false)
const lastUpdateTime = ref('')

// 汇总
const summary = ref({
  totalReviews: 0, totalAnalyzed: 0,
  positiveRate: 0, negativeRate: 0,
  positiveTrend: 0, negativeTrend: 0,
  topComplaintDimension: '-', topComplaintCount: 0,
})

// 情感分布
const sentimentDist = ref([
  { label: '正面', key: 'POSITIVE', count: 0, percentage: 0, color: '#52c41a' },
  { label: '负面', key: 'NEGATIVE', count: 0, percentage: 0, color: '#ff4d4f' },
  { label: '中性', key: 'NEUTRAL', count: 0, percentage: 0, color: '#1890ff' },
  { label: '混合', key: 'MIXED', count: 0, percentage: 0, color: '#faad14' },
])

// 维度
const dimensionTabs = [
  { key: 'all', label: '全部' },
  { key: 'SERVICE', label: '服务' },
  { key: 'TASTE', label: '口味' },
  { key: 'PRICE', label: '价格' },
  { key: 'ENVIRONMENT', label: '环境' },
]
const activeDimension = ref('all')
const dimensionData = ref([
  { key: 'SERVICE', label: '服务', positivePct: 0, neutralPct: 0, negativePct: 0, positiveCount: 0, negativeCount: 0, coverage: 0 },
  { key: 'TASTE', label: '口味', positivePct: 0, neutralPct: 0, negativePct: 0, positiveCount: 0, negativeCount: 0, coverage: 0 },
  { key: 'PRICE', label: '价格', positivePct: 0, neutralPct: 0, negativePct: 0, positiveCount: 0, negativeCount: 0, coverage: 0 },
  { key: 'ENVIRONMENT', label: '环境', positivePct: 0, neutralPct: 0, negativePct: 0, positiveCount: 0, negativeCount: 0, coverage: 0 },
])

// 关键词
const positiveKeywords = ref([])
const maxPositiveKwCount = ref(1)
const complaintIssues = ref([])

// AI 摘要
const aiSummary = ref(null)

// 亮点挖掘
const highlights = ref([])
const highlightsLoading = ref(false)
const highlightsStatus = ref('')
const highlightsAvailCount = ref(0)
const highlightsMinCount = ref(5)

async function loadHighlights() {
  const ids = getEffectiveIds()
  if (ids.length === 0) return
  try {
    // 如果是"全部店铺"，聚合所有店铺的亮点
    if (ids.length > 1) {
      const allHls = []
      let totalAvail = 0, allNone = true
      for (const id of ids) {
        const res = await getMerchantHighlights(id)
        if (res.success && res.data) {
          const data = res.data
          if (data.length === 1 && (data[0].status === 'INSUFFICIENT_DATA' || data[0].status === 'NONE')) {
            totalAvail += data[0].availablePositiveCount || 0
          } else {
            allNone = false
            allHls.push(...data.filter(h => h.status === 'ACTIVE'))
          }
        }
      }
      if (allNone && allHls.length === 0) {
        highlightsStatus.value = 'NONE'
        highlightsAvailCount.value = totalAvail
        highlightsMinCount.value = 5
        highlights.value = []
      } else {
        highlightsStatus.value = 'ACTIVE'
        highlights.value = allHls
      }
    } else {
      // 单个店铺
      const res = await getMerchantHighlights(ids[0])
      if (res.success && res.data) {
        const data = res.data
        if (data.length === 1 && (data[0].status === 'INSUFFICIENT_DATA' || data[0].status === 'NONE')) {
          highlightsStatus.value = data[0].status
          highlightsAvailCount.value = data[0].availablePositiveCount || 0
          highlightsMinCount.value = data[0].minimumReviewCount || 3
          highlights.value = []
          if (data[0].status === 'NONE') {
            showMessage('该店铺尚未生成亮点，点击"生成亮点"按钮开始', 'info')
          }
        } else {
          highlightsStatus.value = 'ACTIVE'
          highlights.value = data.filter(h => h.status === 'ACTIVE')
        }
      }
    }
  } catch (e) { console.error('加载亮点失败', e) }
}

async function generateHighlights() {
  const ids = getEffectiveIds()
  if (ids.length === 0) return
  highlightsLoading.value = true
  try {
    let okCount = 0, failCount = 0
    for (const id of ids) {
      try {
        const res = await generateMerchantHighlights(id, false)
        if (res.success) {
          const data = res.data
          if (data && data.length === 1 && (data[0].status === 'INSUFFICIENT_DATA' || data[0].status === 'NONE')) {
            failCount++
          } else {
            okCount++
          }
        } else {
          failCount++
        }
      } catch (e) { failCount++ }
    }
    const msg = okCount > 0
      ? `${okCount} 店亮点已更新` + (failCount > 0 ? `，${failCount} 店数据不足` : '')
      : `${failCount} 店暂无足够正面评价`
    showMessage(msg, okCount > 0 ? 'success' : 'info')
    await loadHighlights()
  } catch (e) { showMessage('亮点生成失败', 'error') }
  finally { highlightsLoading.value = false }
}

// 亮点证据弹窗
const hlEvidenceVisible = ref(false)
const hlEvidenceLoading = ref(false)
const hlEvidences = ref([])
const currentHlTitle = ref('')

async function showHighlightEvidence(hl) {
  currentHlTitle.value = hl.title
  hlEvidenceVisible.value = true
  hlEvidenceLoading.value = true
  try {
    // 用亮点自身的 merchantId，而不是 selectedStoreId（全部店铺时 selectedStoreId=0）
    const mId = hl.merchantId || selectedStoreId.value
    const res = await getMerchantHighlightEvidences(mId, hl.highlightId)
    if (res.success && res.data) {
      hlEvidences.value = res.data
    }
  } catch (e) { console.error('加载亮点依据失败', e) }
  finally { hlEvidenceLoading.value = false }
}

function getHighlightIcon(type) {
  return { SIGNATURE_DISH: '🍽️', ENVIRONMENT: '🏠', SERVICE: '🤝', PRICE: '💰', BRAND_FEATURE: '⭐' }[type] || '✨'
}
function getHighlightClass(type) {
  return { SIGNATURE_DISH: 'hl-dish', ENVIRONMENT: 'hl-env', SERVICE: 'hl-service', PRICE: 'hl-price', BRAND_FEATURE: 'hl-brand' }[type] || ''
}

// 口碑趋势（纯 SVG）
const trendPeriod = ref('month')
const CHART_W = 440, CHART_H = 130, CHART_LEFT = 35, CHART_RIGHT = 8, CHART_TOP = 8

const trendGroups = computed(() => {
  const groups = {}
  reviewList.value.forEach(r => {
    if (!r.reviewTime) return
    const d = new Date(r.reviewTime)
    if (isNaN(d.getTime())) return
    const m = d.getMonth() + 1, day = d.getDate()
    // sortKey 用于排序，label 用于显示
    const sortKey = trendPeriod.value === 'week'
      ? `${d.getFullYear()}-${String(m).padStart(2,'0')}-W${String(Math.ceil(day / 7)).padStart(2,'0')}`
      : `${d.getFullYear()}-${String(m).padStart(2,'0')}`
    const label = trendPeriod.value === 'week'
      ? `${m}月第${Math.ceil(day / 7)}周`
      : `${m}月`
    if (!groups[sortKey]) groups[sortKey] = { sum: 0, count: 0, pos: 0, neg: 0, total: 0, ratings: [], label }
    groups[sortKey].sum += (r.rating || 0)
    groups[sortKey].count++
    groups[sortKey].total++
    groups[sortKey].ratings.push(r.rating || 0)
    const s = String(r.sentiment || '').toUpperCase()
    if (s === 'POSITIVE') groups[sortKey].pos++
    else if (s === 'NEGATIVE') groups[sortKey].neg++
  })
  return Object.entries(groups).sort((a, b) => a[0].localeCompare(b[0]))
})

const trendLabels = computed(() => trendGroups.value.map(e => e[1].label))
const xStep = computed(() => trendLabels.value.length > 1 ? (CHART_W - CHART_LEFT - CHART_RIGHT) / (trendLabels.value.length - 1) : (CHART_W - CHART_LEFT - CHART_RIGHT))

function yPos(v, max) { return CHART_TOP + CHART_H - (v / max * CHART_H) }

const ratingPoints = computed(() => {
  const max = 5
  return trendGroups.value.map(([, g], i) => ({
    x: CHART_LEFT + i * xStep.value,
    y: yPos(g.count > 0 ? g.sum / g.count : 0, max),
  }))
})

const posRatePoints = computed(() => {
  return trendGroups.value.map(([, g], i) => ({
    x: CHART_LEFT + i * xStep.value,
    y: yPos(g.total > 0 ? g.pos / g.total * 100 : 0, 100),
  }))
})

const negRatePoints = computed(() => {
  return trendGroups.value.map(([, g], i) => ({
    x: CHART_LEFT + i * xStep.value,
    y: yPos(g.total > 0 ? g.neg / g.total * 100 : 0, 100),
  }))
})

const ratingLine = computed(() => ratingPoints.value.map(p => `${p.x},${p.y}`).join(' '))
const posRateLine = computed(() => posRatePoints.value.map(p => `${p.x},${p.y}`).join(' '))
const negRateLine = computed(() => negRatePoints.value.map(p => `${p.x},${p.y}`).join(' '))

// 评价列表
const reviewList = ref([])
const filterSentiment = ref('')
const filterDimension = ref('')
const searchKeyword = ref('')
const page = ref(1)
const pageSize = ref(15)
const totalPages = ref(1)
const totalCount = ref(0)

// 详情弹窗
const detailVisible = ref(false)
const currentDetail = ref(null)

// ==================== 辅助回复 ====================
const replyDraft = ref(null)
const replyLoading = ref(false)
const replyEditing = ref(false)
const replyEditText = ref('')
const replyPublishing = ref(false)
const replyError = ref('')

// 差评归因钻取
const issueDetailVisible = ref(false)
const currentIssue = ref(null)
const issueReviews = ref([])
const issueReviewsLoading = ref(false)
const issuePage = ref(1)
const issueTotalPages = ref(1)
const issuePageSize = 10
const issueVisiblePages = computed(() => {
  const pages = []
  for (let p = Math.max(1, issuePage.value - 2); p <= Math.min(issueTotalPages.value, issuePage.value + 2); p++) pages.push(p)
  return pages
})

async function openIssueDetail(issue) {
  if (selectedStoreId.value === 0) {
    showMessage('请先选择具体店铺再查看差评归因详情', 'info')
    return
  }
  currentIssue.value = issue
  issuePage.value = 1
  issueDetailVisible.value = true
  await loadIssueReviews()
}

async function loadIssueReviews() {
  if (!currentIssue.value || selectedStoreId.value === 0) return
  issueReviewsLoading.value = true
  try {
    const res = await getIssueCategoryReviews(selectedStoreId.value, currentIssue.value.category, {
      pageNum: issuePage.value,
      pageSize: issuePageSize,
    })
    if (res.success && res.data) {
      issueReviews.value = res.data.records || res.data.value || []
      issueTotalPages.value = res.data.totalPages || 1
    }
  } catch (e) { console.error('加载差评归因评价失败', e) }
  finally { issueReviewsLoading.value = false }
}

// 关键词点击筛选
function filterByKeyword(word) {
  searchKeyword.value = word
  page.value = 1
  loadReviews()
}

// 清除关键词筛选
function clearKeywordFilter() {
  searchKeyword.value = ''
  page.value = 1
  loadReviews()
}

// ========== 计算属性 ==========
const sentimentDonutSegments = computed(() => {
  let offset = 0
  const total = 2 * Math.PI * 80
  return sentimentDist.value.map(item => {
    const length = (item.percentage / 100) * total
    const seg = {
      color: item.color,
      dashArray: `${length} ${total - length}`,
      dashOffset: -offset,
    }
    offset += length
    return seg
  })
})

const visiblePages = computed(() => {
  const pages = []
  for (let p = Math.max(1, page.value - 2); p <= Math.min(totalPages.value, page.value + 2); p++) {
    pages.push(p)
  }
  return pages
})

// ========== 方法 ==========
function sentimentLabel(s) {
  const map = { POSITIVE: '👍 正面', NEGATIVE: '👎 负面', NEUTRAL: '➖ 中性', MIXED: '🔄 混合' }
  return map[s] || s
}

function aspectLabel(c) {
  const map = { TASTE: '口味', ENVIRONMENT: '环境', SERVICE: '服务', PRICE: '价格', SPEED: '速度', PORTION: '分量', HYGIENE: '卫生', QUEUE_TIME: '排队', PARKING: '停车' }
  return map[c] || c
}

function modelNameLabel(name) {
  if (!name) return '未知'
  if (name.includes('hybrid')) return '⚡ 混合模式（本地AI + 云端）'
  if (name.includes('fallback:llm') || name.includes('fallback:local')) return '⚠️ 降级分析（云端AI）'
  if (name.includes('local:')) return '🚀 本地AI模型'
  if (name.includes('llm:') || name.includes('DeepSeek')) return '☁️ 云端AI'
  if (name.includes('hybrid-fallback')) return '⚡ 混合模式（仅本地）'
  return name
}

function confidenceColor(v) {
  if (v >= 0.8) return '#52c41a'
  if (v >= 0.6) return '#faad14'
  return '#ff4d4f'
}

function rankColor(idx) {
  const colors = ['#ff4d4f', '#ff7a45', '#faad14', '#ffc53d', '#ffd666']
  return colors[idx] || '#bbb'
}

function truncate(text, len) {
  if (!text) return ''
  return text.length > len ? text.slice(0, len) + '...' : text
}

async function loadAll() {
  await Promise.all([loadSummary(), loadReviews(), loadHighlights()])
}

// 获取当前有效的 merchantId 列表（支持"全部店铺"）
function getEffectiveIds() {
  if (selectedStoreId.value > 0) return [selectedStoreId.value]
  return stores.value.filter(s => s.id > 0).map(s => s.id)
}

// 消息提示
const messageText = ref('')
const messageType = ref('info')
let messageTimer = null

function showMessage(text, type = 'info') {
  messageText.value = text
  messageType.value = type
  clearTimeout(messageTimer)
  messageTimer = setTimeout(() => { messageText.value = '' }, 8000)
}

async function loadSummary() {
  try {
    const ids = getEffectiveIds()
    if (ids.length === 0) return

    // 并发请求所有店铺的汇总
    const results = await Promise.all(ids.map(id =>
      getSentimentSummary({ merchantId: id, timeRange: timeRange.value })
    ))

    // 合并多个店铺的数据
    let totalR = 0, totalA = 0, posCount = 0, negCount = 0, neuCount = 0, mixCount = 0
    let topIssue = { categoryName: '-', count: 0 }
    const dimAgg = {}
    const kwAgg = {}
    const issueAgg = {}

    for (const res of results) {
      if (!res.success || !res.data) continue
      const d = res.data
      totalR += d.totalReviews || 0
      totalA += d.totalAnalyzed || 0
      // 情感分布
      const sd = d.sentimentDistribution || {}
      posCount += (sd.POSITIVE?.count || 0)
      negCount += (sd.NEGATIVE?.count || 0)
      neuCount += (sd.NEUTRAL?.count || 0)
      mixCount += (sd.MIXED?.count || 0)
      // 维度
      if (d.dimensions) {
        for (const [k, v] of Object.entries(d.dimensions)) {
          if (!dimAgg[k]) dimAgg[k] = { positiveCount: 0, negativeCount: 0, mentioned: 0 }
          dimAgg[k].positiveCount += v.positiveCount || 0
          dimAgg[k].negativeCount += v.negativeCount || 0
          dimAgg[k].mentioned += (v.positiveCount + v.negativeCount) || 0
        }
      }
      // 关键词
      for (const kw of (d.positiveKeywords || [])) {
        kwAgg[kw.word] = (kwAgg[kw.word] || 0) + kw.count
      }
      // 差评问题
      for (const iss of (d.complaintIssues || [])) {
        if (!issueAgg[iss.category]) {
          issueAgg[iss.category] = { count: 0, categoryName: iss.categoryName }
        }
        issueAgg[iss.category].count += iss.count
        if (!topIssue.categoryName || issueAgg[iss.category].count > topIssue.count) {
          topIssue = { categoryName: iss.categoryName, count: issueAgg[iss.category].count }
        }
      }
    }

    const analyzedTotal = totalA > 0 ? totalA : 1
    summary.value = {
      totalReviews: totalR,
      totalAnalyzed: totalA,
      positiveRate: Math.round(posCount * 1000 / analyzedTotal) / 10,
      negativeRate: Math.round(negCount * 1000 / analyzedTotal) / 10,
      positiveTrend: 0, negativeTrend: 0,
      topComplaintDimension: topIssue.categoryName,
      topComplaintCount: topIssue.count,
    }

    sentimentDist.value[0].count = posCount; sentimentDist.value[0].percentage = Math.round(posCount * 1000 / analyzedTotal) / 10
    sentimentDist.value[1].count = negCount; sentimentDist.value[1].percentage = Math.round(negCount * 1000 / analyzedTotal) / 10
    sentimentDist.value[2].count = neuCount; sentimentDist.value[2].percentage = Math.round(neuCount * 1000 / analyzedTotal) / 10
    sentimentDist.value[3].count = mixCount; sentimentDist.value[3].percentage = Math.round(mixCount * 1000 / analyzedTotal) / 10

    for (const dim of dimensionData.value) {
      const agg = dimAgg[dim.key]
      if (agg && agg.mentioned > 0) {
        dim.positivePct = Math.round(agg.positiveCount * 1000 / agg.mentioned) / 10
        dim.neutralPct = 0
        dim.negativePct = Math.round(agg.negativeCount * 1000 / agg.mentioned) / 10
        dim.positiveCount = agg.positiveCount
        dim.negativeCount = agg.negativeCount
        dim.coverage = Math.round(agg.mentioned * 1000 / totalR) / 10
      }
    }

    const kwSorted = Object.entries(kwAgg).sort((a, b) => b[1] - a[1]).slice(0, 10)
    positiveKeywords.value = kwSorted.map(([w, c]) => ({ word: w, count: c }))
    maxPositiveKwCount.value = Math.max(1, ...kwSorted.map(e => e[1]))

    const issueEntries = Object.entries(issueAgg).sort((a, b) => b[1].count - a[1].count)
    complaintIssues.value = issueEntries.map(([cat, info]) => ({
      category: cat,
      categoryName: info.categoryName || cat,
      count: info.count,
      percentage: Math.round(info.count * 1000 / analyzedTotal) / 10,
    }))

    lastUpdateTime.value = new Date().toLocaleString('zh-CN')
    if (totalA === 0 && totalR > 0) {
      showMessage(`共有 ${totalR} 条评价待分析，请点击"一键分析"按钮开始 AI 情感分析`, 'info')
    }
  } catch (e) {
    console.error('加载汇总数据失败', e)
  }
}

async function loadReviews() {
  try {
    const ids = getEffectiveIds()
    if (ids.length === 0) return

    // 并发请求所有店铺
    const results = await Promise.all(ids.map(id =>
      getSentimentReviews({
        merchantId: id,
        timeRange: timeRange.value,
        sentiment: filterSentiment.value || undefined,
        dimension: filterDimension.value || undefined,
        keyword: searchKeyword.value || undefined,
        page: 1,
        pageSize: 200,
      })
    ))

    // 合并结果
    let allItems = []
    for (const res of results) {
      if (res.success && res.data) {
        allItems.push(...(res.data.records || []))
      }
    }
    // 前分页
    totalCount.value = allItems.length
    totalPages.value = Math.max(1, Math.ceil(allItems.length / pageSize.value))
    const start = (page.value - 1) * pageSize.value
    reviewList.value = allItems.slice(start, start + pageSize.value)
  } catch (e) {
    console.error('加载评论列表失败', e)
  }
}

async function triggerAnalysis() {
  if (analyzing.value) return
  analyzing.value = true
  showMessage('', '')
  try {
    const ids = getEffectiveIds()
    if (ids.length === 0) return
    let totalOk = 0, totalFail = 0, backfilledTotal = 0
    for (const id of ids) {
      const res = await triggerBatchAnalysis({
        merchantId: id,
        timeRange: timeRange.value,
        analysisMode: analysisMode.value,
      })
      if (res.success) {
        const d = res.data
        totalOk += d.analyzedCount || 0
        totalFail += d.failCount || 0
        backfilledTotal += d.backfilledIssueRelations || 0
      } else {
        totalFail++
      }
    }
    let msg
    if (totalOk > 0) {
      msg = `分析完成！成功 ${totalOk} 条` + (totalFail > 0 ? `，失败 ${totalFail} 条` : '')
    } else if (backfilledTotal > 0) {
      msg = `差评归因数据已补充 ${backfilledTotal} 条，可刷新页面查看`
    } else {
      msg = '所有评价已分析完成'
    }
    showMessage(msg, (totalOk > 0 || backfilledTotal > 0) ? 'success' : 'info')
    lastUpdateTime.value = new Date().toLocaleString('zh-CN')
    await loadAll()
  } catch (e) {
    console.error('批量分析失败', e)
    showMessage('批量分析失败：无法连接到 AI 服务', 'error')
  } finally {
    analyzing.value = false
  }
}

function openDetail(rv) {
  currentDetail.value = rv
  detailVisible.value = true
  fetchReplyDraft(rv.reviewId || rv.id)
}

// Reset reply state when detail closes
watch(detailVisible, (val) => {
  if (!val) {
    replyDraft.value = null
    replyLoading.value = false
    replyEditing.value = false
    replyEditText.value = ''
    replyPublishing.value = false
    replyError.value = ''
  }
})

let searchTimer = null
function onSearchInput() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    page.value = 1
    loadReviews()
  }, 400)
}

// ==================== 辅助回复方法 ====================

function getEffectiveContent(draft) {
  if (!draft) return ''
  return draft.editedContent || draft.generatedContent || ''
}

async function fetchReplyDraft(reviewId) {
  if (!reviewId) return
  replyDraft.value = null
  replyError.value = ''
  try {
    const res = await getReplyDraft(reviewId)
    if (res.success && res.data) {
      replyDraft.value = res.data
    }
  } catch (e) { /* 没有草稿是正常的 */ }
}

async function handleGenerateReply() {
  const reviewId = currentDetail.value?.reviewId || currentDetail.value?.id
  if (!reviewId) return
  replyLoading.value = true
  replyError.value = ''
  try {
    const res = await generateReplyDraft(reviewId)
    if (res.success && res.data) {
      if (res.data.status === 'FAILED') {
        replyError.value = res.data.errorMessage || 'AI 回复生成失败，请稍后重试'
        replyDraft.value = null
        showMessage(replyError.value, 'error')
      } else {
        replyDraft.value = res.data
        showMessage('AI 回复建议已生成', 'success')
      }
    } else {
      replyError.value = res.message || '生成回复失败'
      showMessage(replyError.value, 'error')
    }
  } catch (e) {
    replyError.value = '网络请求失败，请检查服务状态后重试'
    showMessage(replyError.value, 'error')
  } finally {
    replyLoading.value = false
  }
}

function handleStartEdit() {
  replyEditText.value = getEffectiveContent(replyDraft.value)
  replyEditing.value = true
  replyError.value = ''
}

function handleCancelEdit() {
  replyEditing.value = false
  replyEditText.value = ''
}

async function handleSaveEdit() {
  const reviewId = currentDetail.value?.reviewId || currentDetail.value?.id
  if (!reviewId || !replyEditText.value.trim()) {
    showMessage('回复内容不能为空', 'info')
    return
  }
  replyLoading.value = true
  try {
    const res = await editReplyDraft(reviewId, replyEditText.value.trim())
    if (res.success && res.data) {
      replyDraft.value = res.data
      replyEditing.value = false
      replyEditText.value = ''
      showMessage('草稿已保存', 'success')
    } else {
      showMessage(res.message || '保存失败', 'error')
    }
  } catch (e) {
    showMessage('网络请求失败', 'error')
  } finally {
    replyLoading.value = false
  }
}

async function handlePublishDraft() {
  const reviewId = currentDetail.value?.reviewId || currentDetail.value?.id
  if (!reviewId) return
  replyPublishing.value = true
  replyError.value = ''
  try {
    const res = await publishReplyDraft(reviewId)
    if (res.success && res.data) {
      replyDraft.value = { ...replyDraft.value, status: 'PUBLISHED' }
      showMessage('回复已发布成功', 'success')
    } else {
      replyError.value = res.message || '发布失败'
      showMessage(replyError.value, 'error')
    }
  } catch (e) {
    replyError.value = '发布请求失败'
    showMessage(replyError.value, 'error')
  } finally {
    replyPublishing.value = false
  }
}

async function handleDiscardDraft() {
  const reviewId = currentDetail.value?.reviewId || currentDetail.value?.id
  if (!reviewId) return
  if (!confirm('确定要丢弃此回复草稿吗？丢弃后可重新生成。')) return
  replyLoading.value = true
  try {
    const res = await discardReplyDraft(reviewId)
    if (res.success) {
      replyDraft.value = null
      replyEditing.value = false
      replyEditText.value = ''
      replyError.value = ''
      showMessage('草稿已丢弃', 'success')
    } else {
      showMessage(res.message || '丢弃失败', 'error')
    }
  } catch (e) {
    showMessage('网络请求失败', 'error')
  } finally {
    replyLoading.value = false
  }
}

async function loadStores() {
  try {
    const res = await getMyMerchants()
    if (res.success && res.data) {
      stores.value = [{ id: 0, name: '全部店铺' }, ...res.data]
    }
  } catch (e) { /* use default */ }
}

onMounted(async () => {
  await loadStores()
  if (stores.value.length > 0 && selectedStoreId.value === 0) {
    selectedStoreId.value = stores.value[0].id
  }
  loadAll()
})

// 监听店铺/时间切换，自动刷新
watch([selectedStoreId, timeRange], () => {
  loadAll()
})

</script>

<style scoped>
.sentiment-container { width: 100%; }

/* 消息提示 */
.message-banner {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 20px; border-radius: 8px; margin-bottom: 16px;
  font-size: 14px; font-weight: 500;
}
.message-banner.info { background: #e6f7ff; color: #0958d9; border: 1px solid #91d5ff; }
.message-banner.error { background: #fff2f0; color: #cf1322; border: 1px solid #ffccc7; }
.message-banner.success { background: #f6ffed; color: #389e0d; border: 1px solid #b7eb8f; }
.message-close { padding: 4px 8px; background: none; border: none; cursor: pointer; font-size: 14px; opacity: 0.6; }
.message-close:hover { opacity: 1; }

/* ===== 操作栏 ===== */
.toolbar {
  display: flex; justify-content: space-between; align-items: center;
  background: #fff; border-radius: 12px; padding: 16px 24px;
  margin-bottom: 24px; box-shadow: 0 2px 8px rgba(0,0,0,0.04);
  flex-wrap: wrap; gap: 12px;
}
.toolbar-left { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.toolbar-right { display: flex; align-items: center; gap: 12px; }
.toolbar-label { font-size: 13px; color: #999; margin-right: 6px; }
.toolbar-select {
  padding: 8px 12px; border: 1px solid #d9d9d9; border-radius: 6px;
  font-size: 14px; color: #1f2d3d; background: #fff; cursor: pointer; outline: none;
}
.toolbar-select:focus { border-color: #52c41a; }
.btn-analyze {
  display: flex; align-items: center; gap: 6px;
  padding: 9px 20px; font-size: 14px; font-weight: 500; color: #fff;
  background: linear-gradient(135deg, #ff7a45 0%, #ff4d4f 100%);
  border: none; border-radius: 8px; cursor: pointer; transition: all 0.2s;
}
.btn-analyze:hover:not(:disabled) { opacity: 0.9; transform: translateY(-1px); }
.btn-analyze:disabled { opacity: 0.6; cursor: not-allowed; }
.last-update { font-size: 13px; color: #999; }
.btn-refresh {
  padding: 8px; background: #f5f7fa; border: 1px solid #d9d9d9;
  border-radius: 6px; color: #667085; cursor: pointer; transition: all 0.2s;
}
.btn-refresh:hover { background: #eef2f7; }

/* ===== 概览卡片 ===== */
.summary-cards {
  display: grid; grid-template-columns: repeat(4, 1fr);
  gap: 20px; margin-bottom: 24px;
}
.summary-card {
  background: #fff; border-radius: 12px; padding: 20px 24px;
  display: flex; align-items: center; gap: 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}
.summary-icon {
  width: 52px; height: 52px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.summary-icon.total { background: linear-gradient(135deg, #1890ff, #40a9ff); }
.summary-icon.positive { background: linear-gradient(135deg, #52c41a, #73d13d); }
.summary-icon.negative { background: linear-gradient(135deg, #ff4d4f, #ff7875); }
.summary-icon.complaint { background: linear-gradient(135deg, #faad14, #ffc53d); }
.summary-info { flex: 1; min-width: 0; }
.summary-label { font-size: 13px; color: #999; margin: 0; }
.summary-value { font-size: 26px; font-weight: 700; color: #1f2d3d; margin: 2px 0 0; }
.summary-sub { font-size: 12px; color: #999; margin: 2px 0 0; }
.summary-sub.trend-up { color: #52c41a; }
.summary-sub.trend-down { color: #ff4d4f; }

/* ===== 图表行 ===== */
.charts-row {
  display: grid; grid-template-columns: repeat(2, 1fr);
  gap: 24px; margin-bottom: 24px;
}
.chart-card {
  background: #fff; border-radius: 12px; padding: 24px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}
.chart-header {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;
}
.chart-header h3 { font-size: 15px; font-weight: 600; color: #1f2d3d; margin: 0; }
.chart-body { }

/* 情感分布甜甜圈 */
.donut-chart-wrapper { display: flex; align-items: center; gap: 24px; }
.donut-chart { width: 210px; height: 210px; flex-shrink: 0; }
.donut-segment { transition: all 0.3s; }
.donut-center-value { font-size: 22px; font-weight: 700; fill: #1f2d3d; }
.donut-center-label { font-size: 12px; fill: #999; }
.donut-legend { flex: 1; }
.legend-row {
  display: flex; align-items: center; gap: 8px; padding: 6px 0;
  font-size: 13px;
}
.legend-dot { width: 10px; height: 10px; border-radius: 3px; flex-shrink: 0; }
.legend-label { color: #667085; min-width: 36px; }
.legend-value { color: #1f2d3d; font-weight: 600; min-width: 30px; text-align: right; }
.legend-pct { color: #999; }

/* 维度标签切换 */
.chart-tabs { display: flex; gap: 6px; }
.dim-tab {
  padding: 4px 12px; font-size: 12px; color: #667085;
  background: #f5f7fa; border: none; border-radius: 4px; cursor: pointer; transition: all 0.2s;
}
.dim-tab.active { background: #52c41a; color: #fff; }
.dim-tab:hover:not(.active) { background: #eef2f7; }

/* 维度条形图 */
.dimension-bars { display: flex; flex-direction: column; gap: 16px; }
.dimension-item { }
.dim-header { display: flex; justify-content: space-between; margin-bottom: 6px; }
.dim-name { font-size: 14px; font-weight: 500; color: #1f2d3d; }
.dim-score { font-size: 12px; color: #999; }
.dim-bar-track {
  position: relative; height: 36px; background: #f5f5f5;
  border-radius: 18px; overflow: hidden; display: flex;
}
.dim-bar-positive {
  height: 100%; background: linear-gradient(90deg, #52c41a, #73d13d);
  border-radius: 14px 0 0 14px; display: flex; align-items: center;
  justify-content: center; font-size: 12px; color: #fff; font-weight: 600;
  transition: width 0.4s; position: absolute; left: 0; top: 0;
}
.dim-bar-neutral {
  height: 100%; background: linear-gradient(90deg, #1890ff, #40a9ff);
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; color: #fff; font-weight: 600;
  transition: width 0.4s; position: absolute; top: 0;
}
.dim-bar-negative {
  height: 100%; background: linear-gradient(90deg, #ff7875, #ff4d4f);
  border-radius: 0 14px 14px 0; display: flex; align-items: center;
  justify-content: center; font-size: 12px; color: #fff; font-weight: 600;
  transition: width 0.4s; position: absolute; top: 0;
}
.dim-legend-row { display: flex; justify-content: space-between; margin-top: 4px; }
.dim-legend-pos { font-size: 12px; color: #52c41a; }
.dim-legend-neg { font-size: 12px; color: #ff4d4f; }

/* 关键词云 */
.keyword-cloud {
  display: flex; flex-wrap: wrap; gap: 10px; align-items: center;
  justify-content: center; padding: 12px 0; min-height: 120px;
}
.keyword-tag.positive {
  display: inline-flex; align-items: baseline; gap: 2px;
  color: #52c41a; font-weight: 600; cursor: default;
  transition: transform 0.2s;
}
.keyword-tag.positive:hover { transform: scale(1.15); }
.keyword-tag.positive small { font-size: 11px; opacity: 0.7; }

/* 差评问题归类 */
.issue-list { display: flex; flex-direction: column; gap: 10px; padding: 4px 0; }
.issue-row { display: flex; align-items: center; gap: 10px; }
.issue-rank {
  width: 24px; height: 24px; border-radius: 6px;
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 700; color: #fff; flex-shrink: 0;
}
.issue-info { flex: 1; min-width: 0; }
.issue-name { font-size: 13px; color: #1f2d3d; display: block; margin-bottom: 3px; }
.issue-bar-track { height: 8px; background: #f0f0f0; border-radius: 4px; overflow: hidden; }
.issue-bar-fill { height: 100%; border-radius: 4px; transition: width 0.4s; }
.issue-count { font-size: 13px; color: #667085; min-width: 36px; text-align: right; }
.issue-pct { font-size: 13px; font-weight: 600; color: #1f2d3d; min-width: 36px; text-align: right; }
.empty-hint { text-align: center; color: #ccc; padding: 24px 0; font-size: 14px; }

/* AI 口碑摘要 */
.ai-summary-card {
  background: linear-gradient(135deg, #fffbe6 0%, #fff7e6 50%, #f6ffed 100%);
  border: 1px solid #ffe58f; border-radius: 12px; padding: 20px 24px;
  margin-bottom: 24px;
}
.ai-summary-header {
  display: flex; align-items: center; gap: 12px; margin-bottom: 16px;
}
.ai-summary-header h3 {
  font-size: 16px; font-weight: 600; color: #d48806; margin: 0;
  display: flex; align-items: center; gap: 6px;
}
.ai-badge {
  font-size: 11px; color: #d48806; background: #fffbe6;
  border: 1px solid #ffe58f; padding: 2px 10px; border-radius: 12px;
}
.ai-summary-body { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 20px; }
.ai-summary-section h4 { font-size: 14px; font-weight: 600; color: #1f2d3d; margin: 0 0 8px; }
.ai-summary-section ul { margin: 0; padding-left: 20px; }
.ai-summary-section li { font-size: 13px; color: #667085; margin-bottom: 4px; line-height: 1.6; }
.ai-summary-section li strong { color: #1f2d3d; }
.recommend-tags { display: flex; flex-wrap: wrap; gap: 8px; }
.recommend-tag {
  padding: 6px 14px; background: #fff; border: 1px solid #ffe58f;
  border-radius: 20px; font-size: 13px; color: #d48806; font-weight: 500;
}
.recommend-tag small { opacity: 0.7; font-weight: 400; }

/* 评论列表 */
.reviews-card {
  background: #fff; border-radius: 12px; padding: 24px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}
.list-filters { display: flex; align-items: center; gap: 10px; }
.filter-select {
  padding: 6px 12px; border: 1px solid #d9d9d9; border-radius: 6px;
  font-size: 13px; color: #1f2d3d; background: #fff; cursor: pointer; outline: none;
}
.filter-select:focus { border-color: #52c41a; }
.search-box {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 10px; border: 1px solid #d9d9d9; border-radius: 6px;
  background: #fff;
}
.search-box:focus-within { border-color: #52c41a; }
.filter-search {
  border: none; outline: none; font-size: 13px; color: #1f2d3d; width: 140px;
}
.filter-search::placeholder { color: #ccc; }

.reviews-table-wrapper { overflow-x: auto; margin-top: 12px; }
.reviews-table { width: 100%; border-collapse: collapse; }
.reviews-table th {
  padding: 12px 14px; text-align: left; font-size: 13px;
  color: #999; font-weight: 600; background: #fafafa; border-bottom: 2px solid #f0f0f0;
  white-space: nowrap;
}
.reviews-table td {
  padding: 14px; font-size: 13px; color: #1f2d3d;
  border-bottom: 1px solid #f0f0f0; vertical-align: middle;
}
.reviews-table tbody tr { cursor: pointer; transition: background 0.15s; }
.reviews-table tbody tr:hover td { background: #fafafa; }
.content-cell { max-width: 260px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.empty-cell { text-align: center; color: #ccc; padding: 40px 14px; cursor: default; }

/* 评分标签 */
.rating-badge {
  display: inline-block; padding: 3px 10px; border-radius: 4px;
  font-size: 12px; font-weight: 600; color: #fff;
}
.rating-badge.r1,.rating-badge.r2 { background: #bbb; }
.rating-badge.r3 { background: #faad14; }
.rating-badge.r4,.rating-badge.r5 { background: #52c41a; }

/* 情感标签 */
.sentiment-tag {
  display: inline-block; padding: 3px 10px; border-radius: 4px;
  font-size: 12px; font-weight: 600;
}
.sentiment-tag.positive { color: #52c41a; background: #f6ffed; border: 1px solid #b7eb8f; }
.sentiment-tag.negative { color: #ff4d4f; background: #fff2f0; border: 1px solid #ffccc7; }
.sentiment-tag.neutral { color: #1890ff; background: #e6f7ff; border: 1px solid #91d5ff; }
.sentiment-tag.mixed { color: #faad14; background: #fffbe6; border: 1px solid #ffe58f; }

/* 置信度条 */
.confidence-bar { width: 60px; height: 6px; background: #f0f0f0; border-radius: 3px; overflow: hidden; }
.confidence-fill { height: 100%; border-radius: 3px; transition: width 0.3s; }

/* 维度小标签 */
.aspect-mini-tags { display: flex; gap: 4px; flex-wrap: wrap; }
.aspect-mini-tag {
  display: inline-block; padding: 2px 8px; border-radius: 3px;
  font-size: 11px; font-weight: 500;
}
.aspect-mini-tag.positive { color: #52c41a; background: #f6ffed; }
.aspect-mini-tag.negative { color: #ff4d4f; background: #fff2f0; }
.aspect-mini-tag.neutral { color: #999; background: #f5f5f5; }

/* 关键词小标签 */
.keyword-mini { display: flex; gap: 4px; flex-wrap: wrap; }
.kw-tag {
  display: inline-block; padding: 2px 8px; background: #f5f7fa;
  border-radius: 3px; font-size: 11px; color: #667085;
}

.btn-detail {
  padding: 4px 12px; font-size: 12px; color: #1890ff; background: #e6f7ff;
  border: 1px solid #91d5ff; border-radius: 4px; cursor: pointer; transition: all 0.2s;
}
.btn-detail:hover { background: #bae7ff; }

/* 分页 */
.pagination {
  display: flex; justify-content: center; align-items: center; gap: 6px;
  margin-top: 20px; padding-top: 16px; border-top: 1px solid #f0f0f0;
}
.pagination button {
  padding: 6px 14px; border: 1px solid #d9d9d9; border-radius: 6px;
  background: #fff; font-size: 13px; color: #1f2d3d; cursor: pointer; transition: all 0.2s;
}
.pagination button:hover:not(:disabled) { border-color: #52c41a; color: #52c41a; }
.pagination button:disabled { opacity: 0.4; cursor: not-allowed; }
.page-btn {
  width: 34px; height: 34px; display: flex; align-items: center; justify-content: center;
  font-size: 13px; color: #1f2d3d; border: 1px solid #d9d9d9; border-radius: 6px;
  cursor: pointer; transition: all 0.2s;
}
.page-btn:hover { border-color: #52c41a; color: #52c41a; }
.page-btn.active { background: #52c41a; color: #fff; border-color: #52c41a; }

/* ===== 详情弹窗 ===== */
.modal-overlay {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.5); display: flex; align-items: center;
  justify-content: center; z-index: 1000; padding: 32px;
}
.modal-panel {
  background: #fff; border-radius: 16px; width: 100%; max-width: 700px;
  max-height: 85vh; overflow-y: auto; box-shadow: 0 16px 48px rgba(0,0,0,0.15);
}
.modal-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 20px 28px; border-bottom: 1px solid #f0f0f0;
}
.modal-header h3 { font-size: 17px; font-weight: 600; color: #1f2d3d; margin: 0; }
.modal-close {
  width: 36px; height: 36px; display: flex; align-items: center; justify-content: center;
  border: none; background: #f5f5f5; border-radius: 50%; cursor: pointer;
  color: #999; transition: all 0.2s;
}
.modal-close:hover { background: #eee; color: #333; }
.modal-body { padding: 24px 28px; }
.modal-footer { padding: 16px 28px; border-top: 1px solid #f0f0f0; display: flex; justify-content: flex-end; }

.detail-original { margin-bottom: 20px; }
.detail-original p {
  font-size: 14px; color: #1f2d3d; line-height: 1.8; margin: 8px 0;
  padding: 12px 16px; background: #fafafa; border-radius: 8px; border-left: 3px solid #1890ff;
}
.detail-meta { display: flex; gap: 20px; font-size: 13px; color: #999; }
.detail-label { font-size: 14px; font-weight: 600; color: #1f2d3d; margin-bottom: 8px; }
.detail-label-sm { font-size: 13px; font-weight: 600; color: #667085; display: block; margin: 10px 0 6px; }
.detail-sentiment-row {
  display: flex; align-items: center; gap: 12px;
  font-size: 14px; color: #667085; margin-bottom: 12px;
}
.detail-model-row {
  display: flex; align-items: center; gap: 8px;
  margin-bottom: 12px; padding: 6px 10px;
  background: #fafafa; border-radius: 6px; font-size: 12px;
}
.model-name-tag {
  display: inline-block; padding: 2px 10px; border-radius: 4px;
  font-size: 12px; font-weight: 500; color: #722ed1;
  background: #f9f0ff; border: 1px solid #efdbff;
}
.aspect-cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 8px; }
.aspect-card {
  padding: 10px 14px; border-radius: 8px; font-size: 12px;
  display: flex; flex-direction: column; gap: 4px;
}
.aspect-card.positive { background: #f6ffed; border: 1px solid #b7eb8f; }
.aspect-card.negative { background: #fff2f0; border: 1px solid #ffccc7; }
.aspect-card.neutral { background: #f5f5f5; border: 1px solid #e0e0e0; }
.aspect-card-label { font-weight: 600; color: #1f2d3d; }
.aspect-card-sentiment.positive { color: #52c41a; }
.aspect-card-sentiment.negative { color: #ff4d4f; }
.aspect-card-sentiment.neutral { color: #999; }
.aspect-card-text { color: #999; font-size: 11px; }

.detail-issues .issue-item {
  display: flex; align-items: center; gap: 10px; margin-top: 6px;
  font-size: 12px; flex-wrap: wrap;
}
.issue-tag {
  padding: 2px 10px; background: #fff2f0; color: #ff4d4f;
  border-radius: 4px; font-weight: 600;
}
.issue-conf { color: #999; }
.issue-evidence { color: #667085; font-style: italic; }

.btn-secondary {
  padding: 8px 20px; background: #f5f5f5; border: 1px solid #d9d9d9;
  border-radius: 6px; font-size: 14px; color: #667085; cursor: pointer; transition: all 0.2s;
}
.btn-secondary:hover { background: #eee; }

/* 响应式 */
@media (max-width: 1200px) {
  .summary-cards { grid-template-columns: repeat(2, 1fr); }
  .charts-row { grid-template-columns: 1fr; }
}
@media (max-width: 768px) {
  .summary-cards { grid-template-columns: 1fr; }
  .toolbar { flex-direction: column; align-items: flex-start; }
  .toolbar-left { flex-direction: column; align-items: flex-start; width: 100%; }
  .list-filters { flex-wrap: wrap; }
  .donut-chart-wrapper { flex-direction: column; }
}

/* 关键词点击样式 */
.keyword-tag.positive { cursor: pointer; }
.keyword-tag.positive:hover { transform: scale(1.15); color: #389e0d; }

/* 差评归因行可点击 */
.issue-row { cursor: pointer; transition: background 0.15s; border-radius: 6px; padding: 4px 8px; margin: 0 -8px; }
.issue-row:hover { background: #fff2f0; }

/* 清除搜索 */
.clear-search-btn {
  padding: 2px 7px; background: #ff4d4f; color: #fff; border: none;
  border-radius: 50%; font-size: 11px; cursor: pointer; line-height: 1.4;
}
.clear-search-btn:hover { background: #ff7875; }

/* 差评归因钻取列表 */
.issue-review-list { display: flex; flex-direction: column; gap: 12px; }
.issue-review-item {
  padding: 14px; background: #fafafa; border-radius: 8px;
  border-left: 3px solid #ff4d4f;
}
.ir-header { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.ir-confidence { font-size: 12px; color: #999; }
.ir-content { font-size: 14px; color: #444; line-height: 1.6; margin: 8px 0; }
.ir-evidence {
  font-size: 13px; color: #ff4d4f; background: #fff2f0;
  padding: 8px 12px; border-radius: 6px; margin-top: 8px;
}
.ir-evidence-label { font-weight: 600; }

/* ===== 亮点挖掘 ===== */
.highlights-card {
  background: #fff; border-radius: 12px; padding: 24px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04); margin-bottom: 24px;
}
.btn-generate-highlights {
  padding: 6px 16px; font-size: 13px; color: #fff;
  background: linear-gradient(135deg, #722ed1, #9254de);
  border: none; border-radius: 6px; cursor: pointer; transition: all 0.2s;
}
.btn-generate-highlights:hover:not(:disabled) { opacity: 0.9; }
.btn-generate-highlights:disabled { opacity: 0.6; cursor: not-allowed; }
.highlight-status-warn {
  padding: 16px; background: #fffbe6; border: 1px solid #ffe58f;
  border-radius: 8px; font-size: 14px; color: #ad8b00; text-align: center;
}
.highlights-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }
.highlight-item {
  display: flex; gap: 12px; padding: 16px; border-radius: 12px;
  border: 1px solid #f0f0f0; cursor: pointer; transition: all 0.2s;
}
.highlight-item:hover { transform: translateY(-2px); box-shadow: 0 6px 16px rgba(0,0,0,0.08); }
.hl-dish { border-left: 4px solid #ff7a45; }
.hl-env { border-left: 4px solid #52c41a; }
.hl-service { border-left: 4px solid #1890ff; }
.hl-price { border-left: 4px solid #722ed1; }
.hl-brand { border-left: 4px solid #faad14; }
.hl-type-icon { font-size: 28px; flex-shrink: 0; }
.hl-content { flex: 1; min-width: 0; }
.hl-content h4 { margin: 0 0 4px; font-size: 14px; font-weight: 600; color: #1f2d3d; }
.hl-content p { margin: 0 0 8px; font-size: 13px; color: #667085; line-height: 1.5; }
.hl-meta { display: flex; gap: 12px; font-size: 12px; color: #999; }
.hl-count { color: #ff7a45; font-weight: 600; }
.hl-ratio { color: #52c41a; font-weight: 600; }

/* ===== 趋势追踪 ===== */
.trend-tabs { display: flex; gap: 6px; }
.trend-svg { width: 100%; height: auto; display: block; }

/* ===== 辅助回复 ===== */
.detail-reply-section {
  margin-bottom: 20px;
  padding: 16px;
  background: #fafafa;
  border-radius: 10px;
  border: 1px solid #f0f0f0;
}
.reply-error-banner {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 14px; background: #fff2f0; border: 1px solid #ffccc7;
  border-radius: 6px; margin-bottom: 12px; font-size: 13px; color: #cf1322;
}
.reply-error-close {
  padding: 2px 8px; background: none; border: none; cursor: pointer;
  font-size: 14px; color: #cf1322; opacity: 0.6;
}
.reply-error-close:hover { opacity: 1; }

.reply-card {
  padding: 14px 16px; border-radius: 8px; margin-top: 8px;
}
.reply-card.reply-published { background: #f6ffed; border: 1px solid #b7eb8f; border-left: 4px solid #52c41a; }
.reply-card.reply-draft { background: #fffbe6; border: 1px solid #ffe58f; }
.reply-card.reply-editing { background: #fff; border: 1px solid #d9d9d9; }
.reply-card.reply-generate { text-align: center; padding: 20px 16px; background: #fff; border: 1px dashed #d9d9d9; }

.reply-card-header { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.reply-status-badge {
  display: inline-block; padding: 2px 10px; border-radius: 4px;
  font-size: 12px; font-weight: 600;
}
.reply-status-badge.published { color: #52c41a; background: #f6ffed; }
.reply-status-badge.draft { color: #d48806; background: #fffbe6; }
.reply-strategy-hint { font-size: 12px; color: #999; }
.reply-edit-mode-label { font-weight: 600; font-size: 13px; color: #1f2d3d; }

.reply-content-text {
  font-size: 14px; color: #333; line-height: 1.7;
  white-space: pre-wrap; margin: 0 0 10px;
}
.reply-card-meta { font-size: 12px; color: #999; margin-top: 4px; }

.reply-draft-actions {
  display: flex; justify-content: flex-end; gap: 8px;
  margin-top: 14px; padding-top: 12px; border-top: 1px solid #f0f0f0;
}
.reply-edit-actions {
  display: flex; justify-content: flex-end; align-items: center; gap: 8px;
  margin-top: 12px;
}
.reply-char-count { font-size: 12px; color: #999; margin-right: auto; }

.reply-edit-textarea {
  width: 100%; padding: 10px 14px; border: 1px solid #d9d9d9;
  border-radius: 6px; font-size: 14px; line-height: 1.6;
  color: #1f2d3d; resize: vertical; font-family: inherit;
  box-sizing: border-box;
}
.reply-edit-textarea:focus { outline: none; border-color: #52c41a; box-shadow: 0 0 0 2px rgba(82,196,26,0.1); }

.reply-generate-hint {
  font-size: 13px; color: #999; margin: 0 0 16px; line-height: 1.6;
}

.btn-primary-sm {
  padding: 8px 18px; font-size: 13px; font-weight: 500; color: #fff;
  background: linear-gradient(135deg, #52c41a, #73d13d); border: none;
  border-radius: 6px; cursor: pointer; transition: all 0.2s;
}
.btn-primary-sm:hover:not(:disabled) { opacity: 0.9; }
.btn-primary-sm:disabled { opacity: 0.6; cursor: not-allowed; }

.btn-danger-text {
  padding: 8px 16px; font-size: 13px; color: #ff4d4f; background: none;
  border: 1px solid #ffccc7; border-radius: 6px; cursor: pointer; transition: all 0.2s;
}
.btn-danger-text:hover:not(:disabled) { background: #fff2f0; }
.btn-danger-text:disabled { opacity: 0.5; cursor: not-allowed; }

.btn-publish {
  padding: 8px 18px; font-size: 13px; font-weight: 500; color: #fff;
  background: linear-gradient(135deg, #52c41a, #73d13d); border: none;
  border-radius: 6px; cursor: pointer; transition: all 0.2s;
}
.btn-publish:hover:not(:disabled) { opacity: 0.9; transform: translateY(-1px); }
.btn-publish:disabled { opacity: 0.6; cursor: not-allowed; }

.btn-generate-reply {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 10px 24px; font-size: 14px; font-weight: 500; color: #fff;
  background: linear-gradient(135deg, #1890ff, #40a9ff); border: none;
  border-radius: 8px; cursor: pointer; transition: all 0.2s;
}
.btn-generate-reply:hover:not(:disabled) { opacity: 0.9; transform: translateY(-1px); }
.btn-generate-reply:disabled { opacity: 0.6; cursor: not-allowed; }
</style>
