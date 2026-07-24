<template>
  <div class="ranking-view">
    <nav class="ranking-nav">
      <div class="nav-container">
        <button
          type="button"
          class="brand-home"
          @click="goBack"
        >
          <span class="brand-logo-shell">
            <img
              src="../../assets/images/greedy-cat.png"
              alt="食尚参谋"
              class="logo-img"
            />
          </span>

          <span class="brand-copy">
            <strong>食尚参谋</strong>
            <small>热门榜单</small>
          </span>
        </button>

        <div class="nav-actions">
          <button
            type="button"
            class="back-btn"
            @click="goBack"
          >
            <span>←</span>
            返回首页
          </button>

          <UserAccountMenu
            role="diner"
            profile-path="/diner/profile"
          />
        </div>
      </div>
    </nav>

    <main class="ranking-main">
      <section class="hero-section">
        <div class="container">
          <div class="hero-panel">
            <div class="hero-copy">
              <span class="hero-eyebrow">POPULAR RESTAURANTS</span>

              <div class="hero-title-row">
                <span class="hero-icon-shell">🔥</span>

                <div>
                  <h1>热门商家榜单</h1>
                  <p>
                    从近期热议、新晋表现、性价比和人气等维度，
                    发现值得尝试的餐厅。
                  </p>
                </div>
              </div>
            </div>

            <div class="hero-update-card">
              <span class="update-label">榜单更新时间</span>
              <strong class="update-time">{{ updateTime || '正在获取' }}</strong>

              <button
                type="button"
                class="refresh-btn"
                :disabled="refreshing"
                @click="refreshRankings"
              >
                <span :class="{ spinning: refreshing }">↻</span>
                {{ refreshing ? '刷新中' : '刷新榜单' }}
              </button>
            </div>
          </div>
        </div>
      </section>

      <section class="ranking-content">
        <div class="container">
          <div class="tabs-wrapper" role="tablist" aria-label="热门榜单分类">
            <button
              v-for="tab in tabs"
              :key="tab.key"
              type="button"
              class="tab-btn"
              :class="{ active: activeTab === tab.key }"
              :aria-selected="activeTab === tab.key"
              role="tab"
              @click="activeTab = tab.key"
            >
              <span class="tab-icon">{{ tab.icon }}</span>

              <span class="tab-copy">
                <strong>{{ tab.name }}</strong>
                <small>{{ tab.description }}</small>
              </span>
            </button>
          </div>

          <section class="rules-card">
            <span class="rules-icon">📊</span>

            <div class="rules-content">
              <span class="rules-eyebrow">当前排序规则</span>
              <h2>{{ activeTabName }}</h2>
              <p>{{ currentRules }}</p>
            </div>
          </section>

          <section class="list-section">
            <div class="list-heading">
              <div>
                <span class="list-eyebrow">TOP RESTAURANTS</span>
                <h2>{{ activeTabName }}榜单</h2>
                <p>展示当前综合指标排名前五的商家</p>
              </div>

              <span class="ranking-count">
                共 {{ currentRanking.length }} 家
              </span>
            </div>

            <div v-if="refreshing && !currentRanking.length" class="state-card">
              <span class="state-spinner"></span>
              <strong>正在生成榜单</strong>
              <p>正在读取商家信息并计算排名</p>
            </div>

            <div v-else-if="loadError && !currentRanking.length" class="state-card error">
              <span class="state-icon">!</span>
              <strong>榜单加载失败</strong>
              <p>{{ loadError }}</p>

              <button
                type="button"
                class="state-action"
                @click="refreshRankings"
              >
                重新加载
              </button>
            </div>

            <div v-else-if="!currentRanking.length" class="state-card">
              <span class="state-icon">🍽️</span>
              <strong>暂无榜单数据</strong>
              <p>当前还没有可参与排名的商家</p>
            </div>

            <div v-else class="ranking-list">
              <article
                v-for="(item, index) in currentRanking"
                :key="item.id"
                class="ranking-item"
                :class="{
                  'top-three': index < 3,
                  first: index === 0,
                  second: index === 1,
                  third: index === 2
                }"
                role="button"
                tabindex="0"
                @click="goToMerchantDetail(item.id)"
                @keydown.enter="goToMerchantDetail(item.id)"
              >
                <div class="rank-column">
                  <span class="rank-label">RANK</span>

                  <span class="rank-badge">
                    <span v-if="index === 0" class="rank-icon">🥇</span>
                    <span v-else-if="index === 1" class="rank-icon">🥈</span>
                    <span v-else-if="index === 2" class="rank-icon">🥉</span>
                    <span v-else class="rank-number">{{ index + 1 }}</span>
                  </span>
                </div>

                <div class="merchant-info">
                  <div class="merchant-header">
                    <div class="merchant-title-block">
                      <h3 class="merchant-name">
                        {{ item.name || '商家名称暂无' }}
                      </h3>

                      <div class="merchant-tags">
                        <span class="tag cuisine">
                          {{ item.cuisine || '餐饮商家' }}
                        </span>

                        <span class="tag rating">
                          <span>★</span>
                          {{ item.rating }}
                        </span>
                      </div>
                    </div>

                    <span
                      v-if="index < 3"
                      class="top-mark"
                    >
                      TOP {{ index + 1 }}
                    </span>
                  </div>

                  <p class="merchant-desc">
                    {{ item.description || '暂无商家简介，点击卡片查看商家详情。' }}
                  </p>

                  <div class="merchant-metrics">
                    <span class="metric featured">
                      <small>{{ item.metricLabel }}</small>
                      <strong>{{ item.metricValue }}</strong>
                    </span>

                    <span class="metric">
                      <small>评价数</small>
                      <strong>{{ item.reviewCount }} 条</strong>
                    </span>

                    <span class="metric">
                      <small>人均消费</small>
                      <strong>￥{{ item.averagePrice }}</strong>
                    </span>
                  </div>
                </div>

                <div class="reason-section">
                  <span class="reason-tag">上榜理由</span>
                  <p class="reason-text">{{ item.reason }}</p>
                </div>

                <div class="detail-action">
                  <span>查看详情</span>
                  <span class="arrow-icon">→</span>
                </div>
              </article>
            </div>
          </section>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '../../api/request'
import UserAccountMenu from '../../components/UserAccountMenu.vue'

const router = useRouter()
const activeTab = ref('weekly')
const updateTime = ref('')
const merchants = ref([])
const refreshing = ref(false)
const loadError = ref('')

const tabs = [
  {
    key: 'weekly',
    name: '本周热议',
    icon: '🔥',
    description: '近期讨论度'
  },
  {
    key: 'monthly',
    name: '月度新晋',
    icon: '🌟',
    description: '本月新表现'
  },
  {
    key: 'value',
    name: '性价比',
    icon: '💰',
    description: '价格与体验'
  },
  {
    key: 'popular',
    name: '人气商家',
    icon: '⭐',
    description: '综合受欢迎度'
  }
]

const rules = {
  weekly:
    '综合近 7 天用户讨论量、点评增长和分享次数等指标计算。热议指数越高，代表商家近期受到的关注越多。',
  monthly:
    '综合本月新店入驻、好评增长和新客比例等指标计算。新晋指数越高，代表商家近期表现越突出。',
  value:
    '综合人均消费、用户评分和分量评价等指标计算。性价比指数越高，代表相同预算下获得的体验越好。',
  popular:
    '综合累计点击量、收藏数、评分和到店热度等指标计算。人气指数越高，代表商家总体受欢迎程度越高。'
}

const metricLabels = {
  weekly: '热议指数',
  monthly: '新晋指数',
  value: '性价比指数',
  popular: '人气指数'
}

const currentRules = computed(() => rules[activeTab.value])

const activeTabName = computed(
  () =>
    tabs.find(tab => tab.key === activeTab.value)?.name ||
    '热门商家'
)

const normalizeNumber = value => {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

const currentRanking = computed(() => {
  const sorted = [...merchants.value]
    .sort((a, b) => {
      const ratingA = normalizeNumber(a.rating ?? a.averageRating)
      const ratingB = normalizeNumber(b.rating ?? b.averageRating)
      const reviewsA = normalizeNumber(a.reviewCount ?? a.ratingCount)
      const reviewsB = normalizeNumber(b.reviewCount ?? b.ratingCount)
      const priceA = Math.max(normalizeNumber(a.averagePrice), 1)
      const priceB = Math.max(normalizeNumber(b.averagePrice), 1)

      if (activeTab.value === 'weekly') {
        return ratingB - ratingA
      }

      if (activeTab.value === 'monthly') {
        return reviewsB - reviewsA
      }

      if (activeTab.value === 'value') {
        return ratingB / priceB - ratingA / priceA
      }

      return ratingB - ratingA
    })
    .slice(0, 5)

  return sorted.map(merchant => {
    const rating = normalizeNumber(
      merchant.rating ?? merchant.averageRating
    )
    const reviewCount = normalizeNumber(
      merchant.reviewCount ?? merchant.ratingCount
    )
    const price = normalizeNumber(merchant.averagePrice)
    const merchantName =
      merchant.name ||
      merchant.merchantName ||
      '商家名称暂无'

    let reason = ''

    if (activeTab.value === 'weekly') {
      reason = `${merchantName}近期讨论热度较高，评分 ${rating} 分，已有 ${reviewCount} 条评价。`
    } else if (activeTab.value === 'monthly') {
      reason = `${merchantName}本月表现较突出，评分 ${rating} 分，人均消费约 ￥${price}。`
    } else if (activeTab.value === 'value') {
      reason = `${merchantName}当前价格与评分表现较均衡，评分 ${rating} 分，人均约 ￥${price}。`
    } else {
      reason = `${merchantName}综合人气表现较好，评分 ${rating} 分，已有 ${reviewCount} 条评价。`
    }

    const baseValue =
      activeTab.value === 'value'
        ? rating / Math.max(price, 1) * 100
        : rating * 10 + reviewCount / 2

    const metricValue = Math.min(
      99.9,
      Math.round(baseValue * 10) / 10
    ).toFixed(1)

    return {
      id: merchant.id || merchant.merchantId,
      name: merchantName,
      cuisine:
        merchant.cuisine ||
        merchant.category ||
        merchant.merchantType ||
        '餐饮商家',
      rating: rating || '暂无',
      reviewCount,
      averagePrice: price || '暂无',
      description: merchant.description || '',
      metricLabel: metricLabels[activeTab.value],
      metricValue,
      reason
    }
  })
})

const formatUpdateTime = () => {
  const now = new Date()

  return now.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const refreshRankings = async () => {
  if (refreshing.value) return

  refreshing.value = true
  loadError.value = ''

  try {
    const response = await request.get('/api/merchants', {
      params: {
        pageNum: 1,
        pageSize: 20
      }
    })

    if (!response.success || !response.data) {
      throw new Error(response.message || '商家列表加载失败')
    }

    merchants.value =
      response.data.records ||
      response.data.list ||
      response.data

    updateTime.value = formatUpdateTime()
  } catch (error) {
    console.error('获取商家列表失败:', error)
    loadError.value =
      error?.message ||
      '暂时无法获取商家数据，请稍后重试'
  } finally {
    refreshing.value = false
  }
}

const goBack = () => {
  router.push('/diner/home')
}

const goToMerchantDetail = id => {
  if (!id) return
  router.push(`/diner/merchant/${id}`)
}

onMounted(refreshRankings)
</script>

<style scoped>
.ranking-view,
.ranking-view *,
.ranking-view *::before,
.ranking-view *::after {
  box-sizing: border-box;
}

.ranking-view {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  min-height: 100vh;
  overflow-x: hidden;
  overflow-x: clip;
  color: #2d2722;
  font-family:
    "Microsoft YaHei",
    "PingFang SC",
    "Noto Sans SC",
    Arial,
    sans-serif;
  background:
    radial-gradient(
      circle at 8% 4%,
      rgba(255, 231, 207, 0.62),
      transparent 24%
    ),
    radial-gradient(
      circle at 92% 8%,
      rgba(255, 244, 220, 0.72),
      transparent 24%
    ),
    #f8f6f2;
}

.ranking-view button {
  font-family: inherit;
}

.container,
.nav-container {
  width: calc(100% - 48px);
  max-width: 1180px;
  min-width: 0;
  margin-right: auto;
  margin-left: auto;
}

.ranking-nav {
  position: sticky;
  top: 0;
  z-index: 100;
  width: 100%;
  border-bottom: 1px solid rgba(229, 222, 212, 0.84);
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(18px);
}

.nav-container {
  display: flex;
  min-height: 72px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.brand-home {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
  padding: 0;
  border: 0;
  color: inherit;
  text-align: left;
  background: transparent;
  cursor: pointer;
}

.brand-logo-shell {
  display: grid;
  width: 44px;
  height: 44px;
  flex: 0 0 44px;
  place-items: center;
  overflow: hidden;
  border: 1px solid #f2dfd0;
  border-radius: 14px;
  background: #fff8f1;
}

.logo-img {
  width: 38px;
  height: 38px;
  object-fit: cover;
}

.brand-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.brand-copy strong {
  overflow: hidden;
  color: #2d2925;
  font-size: 18px;
  font-weight: 750;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.brand-copy small {
  color: #9a8f85;
  font-size: 12px;
  line-height: 1.35;
}

.nav-actions {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.back-btn {
  min-height: 40px;
  padding: 0 13px;
  border-radius: 11px;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition:
    border-color 0.2s,
    color 0.2s,
    background 0.2s;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  border: 1px solid #fed7aa;
  color: #c2410c;
  background: #fff8f1;
}

.back-btn:hover {
  border-color: #fb923c;
  background: #fff1e6;
}

.ranking-main {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  padding-bottom: 46px;
}

.hero-section {
  width: 100%;
  padding: 24px 0 18px;
}

.hero-panel {
  display: grid;
  width: 100%;
  min-width: 0;
  grid-template-columns: minmax(0, 1.5fr) minmax(260px, 0.5fr);
  align-items: center;
  gap: 28px;
  padding: 32px 36px;
  overflow: hidden;
  border: 1px solid #f1dcc8;
  border-radius: 28px;
  background:
    radial-gradient(
      circle at 85% 18%,
      rgba(251, 146, 60, 0.18),
      transparent 28%
    ),
    linear-gradient(135deg, #fffaf4 0%, #fff3e7 100%);
  box-shadow: 0 18px 46px rgba(111, 75, 43, 0.09);
}

.hero-copy {
  min-width: 0;
}

.hero-eyebrow,
.list-eyebrow {
  display: block;
  color: #ea580c;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.11em;
}

.hero-title-row {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 18px;
  margin-top: 9px;
}

.hero-icon-shell {
  display: grid;
  width: 70px;
  height: 70px;
  flex: 0 0 70px;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.84);
  border-radius: 22px;
  font-size: 36px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow:
    0 12px 28px rgba(133, 83, 39, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.9);
}

.hero-title-row > div {
  min-width: 0;
}

.hero-title-row h1 {
  margin: 0;
  color: #29231e;
  font-size: 38px;
  font-weight: 800;
  line-height: 1.2;
  letter-spacing: -1px;
  overflow-wrap: anywhere;
}

.hero-title-row p {
  max-width: 670px;
  margin: 9px 0 0;
  color: #776d64;
  font-size: 16px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.hero-update-card {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: flex-start;
  padding: 18px;
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 12px 28px rgba(91, 60, 30, 0.08);
  backdrop-filter: blur(10px);
}

.update-label {
  color: #9a8f85;
  font-size: 12px;
  font-weight: 600;
}

.update-time {
  margin-top: 5px;
  color: #4a413a;
  font-size: 15px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.refresh-btn {
  display: inline-flex;
  min-height: 40px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 14px;
  padding: 0 14px;
  border: 1px solid #fdba74;
  border-radius: 11px;
  color: #c2410c;
  font-size: 14px;
  font-weight: 700;
  background: #fff;
  cursor: pointer;
}

.refresh-btn:hover:not(:disabled) {
  border-color: #f97316;
  background: #fff7ed;
}

.refresh-btn:disabled {
  opacity: 0.6;
  cursor: wait;
}

.spinning {
  animation: spin 0.9s linear infinite;
}

.ranking-content {
  width: 100%;
  padding-top: 4px;
}

.tabs-wrapper {
  display: grid;
  width: 100%;
  min-width: 0;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  padding: 0;
}

.tab-btn {
  display: flex;
  width: 100%;
  min-width: 0;
  min-height: 78px;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  overflow: hidden;
  border: 1px solid #e9e2db;
  border-radius: 16px;
  color: #5f574f;
  text-align: left;
  background: rgba(255, 255, 255, 0.88);
  cursor: pointer;
  transition:
    transform 0.2s,
    border-color 0.2s,
    box-shadow 0.2s,
    background 0.2s;
}

.tab-btn:hover {
  transform: translateY(-2px);
  border-color: #fdba74;
  box-shadow: 0 10px 22px rgba(91, 66, 43, 0.07);
}

.tab-btn.active {
  border-color: #f97316;
  color: #9a3412;
  background:
    linear-gradient(
      135deg,
      rgba(255, 247, 237, 0.98),
      rgba(255, 237, 213, 0.98)
    );
  box-shadow: 0 11px 24px rgba(234, 88, 12, 0.11);
}

.tab-icon {
  display: grid;
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  place-items: center;
  border-radius: 13px;
  font-size: 21px;
  background: #f8f6f2;
}

.tab-btn.active .tab-icon {
  background: rgba(255, 255, 255, 0.8);
}

.tab-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.tab-copy strong {
  overflow: hidden;
  font-size: 16px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tab-copy small {
  overflow: hidden;
  color: #9a9087;
  font-size: 12px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tab-btn.active .tab-copy small {
  color: #c26a36;
}

.rules-card {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: flex-start;
  gap: 15px;
  margin-top: 16px;
  padding: 20px 22px;
  border: 1px solid #eee5dc;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.88);
}

.rules-icon {
  display: grid;
  width: 46px;
  height: 46px;
  flex: 0 0 46px;
  place-items: center;
  border-radius: 14px;
  font-size: 22px;
  background: #fff3e8;
}

.rules-content {
  min-width: 0;
}

.rules-eyebrow {
  display: block;
  color: #ea580c;
  font-size: 12px;
  font-weight: 700;
}

.rules-content h2 {
  margin: 3px 0 5px;
  color: #342e29;
  font-size: 18px;
  line-height: 1.4;
}

.rules-content p {
  margin: 0;
  color: #756b62;
  font-size: 15px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.list-section {
  width: 100%;
  min-width: 0;
  padding-top: 26px;
}

.list-heading {
  display: flex;
  min-width: 0;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
}

.list-heading > div {
  min-width: 0;
}

.list-heading h2 {
  margin: 5px 0 0;
  color: #29231e;
  font-size: 28px;
  font-weight: 750;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.list-heading p {
  margin: 5px 0 0;
  color: #938980;
  font-size: 14px;
}

.ranking-count {
  flex: 0 0 auto;
  padding: 7px 11px;
  border: 1px solid #e9e2db;
  border-radius: 999px;
  color: #8a8178;
  font-size: 13px;
  background: #fff;
  white-space: nowrap;
}

.ranking-list {
  display: flex;
  width: 100%;
  min-width: 0;
  flex-direction: column;
  gap: 13px;
}

.ranking-item {
  position: relative;
  display: grid;
  width: 100%;
  min-width: 0;
  grid-template-columns: 74px minmax(0, 1fr) minmax(210px, 0.38fr) auto;
  align-items: center;
  gap: 20px;
  padding: 21px 22px;
  overflow: hidden;
  border: 1px solid #e9e2db;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 5px 18px rgba(80, 61, 43, 0.05);
  cursor: pointer;
  transition:
    transform 0.22s,
    border-color 0.22s,
    box-shadow 0.22s;
}

.ranking-item:hover {
  transform: translateY(-3px);
  border-color: #fdba74;
  box-shadow: 0 15px 32px rgba(91, 66, 43, 0.1);
}

.ranking-item:focus-visible {
  outline: 3px solid rgba(249, 115, 22, 0.16);
  outline-offset: 2px;
}

.ranking-item.top-three::before {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 4px;
  content: "";
}

.ranking-item.first {
  border-color: #f4d26a;
  background:
    linear-gradient(
      135deg,
      rgba(255, 253, 242, 0.98),
      rgba(255, 255, 255, 0.98)
    );
}

.ranking-item.first::before {
  background: #eab308;
}

.ranking-item.second {
  border-color: #d8dde5;
  background:
    linear-gradient(
      135deg,
      rgba(248, 250, 252, 0.98),
      rgba(255, 255, 255, 0.98)
    );
}

.ranking-item.second::before {
  background: #94a3b8;
}

.ranking-item.third {
  border-color: #edc49e;
  background:
    linear-gradient(
      135deg,
      rgba(255, 249, 244, 0.98),
      rgba(255, 255, 255, 0.98)
    );
}

.ranking-item.third::before {
  background: #c97b3a;
}

.rank-column {
  display: flex;
  min-width: 0;
  align-items: center;
  flex-direction: column;
  gap: 7px;
}

.rank-label {
  color: #aaa097;
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.rank-badge {
  display: grid;
  width: 52px;
  height: 52px;
  place-items: center;
  border: 1px solid #ebe5df;
  border-radius: 16px;
  background: #f8f6f2;
}

.rank-icon {
  font-size: 29px;
  line-height: 1;
}

.rank-number {
  color: #8d837a;
  font-size: 22px;
  font-weight: 800;
}

.merchant-info {
  min-width: 0;
}

.merchant-header {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.merchant-title-block {
  min-width: 0;
}

.merchant-name {
  margin: 0;
  color: #2f2924;
  font-size: 21px;
  font-weight: 750;
  line-height: 1.4;
  overflow-wrap: anywhere;
}

.merchant-tags {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 8px;
}

.tag {
  max-width: 100%;
  padding: 4px 8px;
  border-radius: 7px;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.3;
  overflow-wrap: anywhere;
}

.tag.cuisine {
  border: 1px solid #ffdfc5;
  color: #c65d1e;
  background: #fff8f0;
}

.tag.rating {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 1px solid #fde68a;
  color: #a16207;
  background: #fffbeb;
}

.top-mark {
  flex: 0 0 auto;
  padding: 4px 8px;
  border-radius: 7px;
  color: #8a8178;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.05em;
  background: rgba(255, 255, 255, 0.78);
}

.merchant-desc {
  display: -webkit-box;
  margin: 12px 0 0;
  overflow: hidden;
  color: #756b62;
  font-size: 15px;
  line-height: 1.65;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.merchant-metrics {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: 9px;
  margin-top: 14px;
}

.metric {
  display: flex;
  min-width: 110px;
  align-items: flex-start;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
  border: 1px solid #eee8e1;
  border-radius: 10px;
  background: #faf8f5;
}

.metric.featured {
  border-color: #fed7aa;
  background: #fff7ed;
}

.metric small {
  color: #9a9087;
  font-size: 11px;
}

.metric strong {
  color: #3f3832;
  font-size: 15px;
  line-height: 1.35;
}

.metric.featured strong {
  color: #c2410c;
}

.reason-section {
  min-width: 0;
  padding: 14px 16px;
  border-left: 1px dashed #e7e0d8;
}

.reason-tag {
  display: inline-flex;
  padding: 4px 8px;
  border-radius: 7px;
  color: #c2410c;
  font-size: 12px;
  font-weight: 700;
  background: #fff3e8;
}

.reason-text {
  margin: 8px 0 0;
  color: #6f665e;
  font-size: 14px;
  line-height: 1.65;
  overflow-wrap: anywhere;
}

.detail-action {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 7px;
  color: #a09890;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}

.arrow-icon {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border: 1px solid #ebe5df;
  border-radius: 10px;
  color: #8d837a;
  font-size: 18px;
  background: #fff;
  transition:
    color 0.2s,
    border-color 0.2s,
    transform 0.2s;
}

.ranking-item:hover .detail-action {
  color: #c2410c;
}

.ranking-item:hover .arrow-icon {
  transform: translateX(3px);
  border-color: #fdba74;
  color: #ea580c;
}

.state-card {
  display: flex;
  min-height: 260px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 8px;
  padding: 30px;
  border: 1px dashed #ddd4cb;
  border-radius: 20px;
  color: #80766e;
  text-align: center;
  background: rgba(255, 255, 255, 0.72);
}

.state-card strong {
  color: #4a413a;
  font-size: 18px;
}

.state-card p {
  margin: 0;
  font-size: 14px;
}

.state-card.error {
  border-color: #fecaca;
  color: #b42318;
  background: #fef3f2;
}

.state-icon {
  display: grid;
  width: 48px;
  height: 48px;
  place-items: center;
  border-radius: 15px;
  font-size: 24px;
  background: #fff3e8;
}

.state-spinner {
  width: 38px;
  height: 38px;
  border: 3px solid #eee8e1;
  border-top-color: #f97316;
  border-radius: 50%;
  animation: spin 0.9s linear infinite;
}

.state-action {
  min-height: 38px;
  margin-top: 8px;
  padding: 0 14px;
  border: 1px solid #fca5a5;
  border-radius: 9px;
  color: #b42318;
  font-size: 13px;
  font-weight: 700;
  background: #fff;
  cursor: pointer;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1020px) {
  .hero-panel {
    grid-template-columns: minmax(0, 1fr);
  }

  .hero-update-card {
    align-items: center;
    flex-direction: row;
    flex-wrap: wrap;
    gap: 8px 14px;
  }

  .refresh-btn {
    margin-top: 0;
    margin-left: auto;
  }

  .tabs-wrapper {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .ranking-item {
    grid-template-columns: 66px minmax(0, 1fr) auto;
  }

  .reason-section {
    grid-column: 2 / 3;
    padding: 10px 0 0;
    border-top: 1px dashed #e7e0d8;
    border-left: 0;
  }

  .detail-action {
    grid-column: 3;
    grid-row: 1 / 3;
  }
}

@media (max-width: 760px) {
  .container,
  .nav-container {
    width: calc(100% - 32px);
  }

  .hero-section {
    padding-top: 16px;
  }

  .hero-panel {
    padding: 25px;
    border-radius: 22px;
  }

  .hero-title-row {
    align-items: flex-start;
  }

  .hero-icon-shell {
    width: 58px;
    height: 58px;
    flex-basis: 58px;
    border-radius: 18px;
    font-size: 29px;
  }

  .hero-title-row h1 {
    font-size: 32px;
  }

  .hero-update-card {
    align-items: flex-start;
    flex-direction: column;
  }

  .refresh-btn {
    width: 100%;
    margin-left: 0;
  }

  .list-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
  }

  .ranking-item {
    grid-template-columns: 56px minmax(0, 1fr);
    gap: 14px;
    padding: 18px;
  }

  .rank-badge {
    width: 46px;
    height: 46px;
  }

  .reason-section {
    grid-column: 2;
  }

  .detail-action {
    grid-column: 2;
    grid-row: auto;
    justify-content: flex-end;
  }
}

@media (max-width: 520px) {
  .brand-copy small,
  .back-btn span:first-child {
    display: none;
  }

  .nav-actions {
    gap: 7px;
  }

  .back-btn {
    padding: 0 10px;
  }

  .hero-panel {
    padding: 22px 18px;
  }

  .hero-title-row {
    gap: 12px;
  }

  .hero-icon-shell {
    width: 50px;
    height: 50px;
    flex-basis: 50px;
    font-size: 25px;
  }

  .hero-title-row h1 {
    font-size: 28px;
  }

  .hero-title-row p {
    font-size: 14px;
  }

  .tabs-wrapper {
    grid-template-columns: minmax(0, 1fr);
  }

  .tab-btn {
    min-height: 68px;
  }

  .rules-card {
    padding: 17px;
  }

  .ranking-item {
    grid-template-columns: minmax(0, 1fr);
  }

  .rank-column {
    align-items: center;
    flex-direction: row;
  }

  .rank-label {
    display: none;
  }

  .rank-badge {
    width: 42px;
    height: 42px;
  }

  .merchant-info,
  .reason-section,
  .detail-action {
    grid-column: 1;
  }

  .reason-section {
    padding-top: 12px;
  }

  .detail-action {
    justify-content: space-between;
  }

  .metric {
    min-width: calc(50% - 5px);
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
