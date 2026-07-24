<template>
  <div class="diner-home">
    <nav class="diner-nav">
      <div class="nav-container">
        <button type="button" class="brand-home" @click="router.push('/diner/home')">
          <span class="brand-logo-shell">
            <img
              src="../../assets/images/greedy-cat.png"
              alt="食尚参谋"
              class="logo-img"
            />
          </span>

          <span class="brand-copy">
            <strong>食尚参谋</strong>
            <small>AI 美食决策助手</small>
          </span>
        </button>

        <div class="nav-actions">
          <UserAccountMenu
            role="diner"
            profile-path="/diner/profile"
          />
        </div>
      </div>
    </nav>

    <main class="home-main">
      <section class="hero-section">
        <div class="container">
          <div class="hero-panel">
            <div class="hero-content">
              <span class="hero-eyebrow">
                ✦ AI 餐厅推荐 · 基于真实商家与评价
              </span>

              <h1>
                今天，想吃点
                <span>什么？</span>
              </h1>

              <p>
                告诉我人数、预算、口味和用餐场景，
                食尚参谋帮你更快找到合适的餐厅。
              </p>

              <div class="hero-actions">
                <button
                  type="button"
                  class="hero-primary-button"
                  @click="goToAiDining"
                >
                  和 AI 聊聊
                  <span>→</span>
                </button>

                <button
                  type="button"
                  class="hero-secondary-button"
                  @click="goToRanking"
                >
                  查看热门榜单
                </button>
              </div>

              <div class="hero-trust-row">
                <span>✓ 条件智能提取</span>
                <span>✓ 推荐依据可查看</span>
                <span>✓ 支持连续对话</span>
              </div>
            </div>

            <div class="hero-visual" aria-hidden="true">
              <div class="visual-glow"></div>
              <div class="food-orbit food-orbit-main">🍲</div>
              <div class="food-orbit food-orbit-top">🌶️</div>
              <div class="food-orbit food-orbit-bottom">🥟</div>

              <div class="ai-status-card">
                <span class="ai-status-dot"></span>
                AI 正在为你选店
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="feature-section">
        <div class="container">
          <div class="feature-grid">
            <button
              type="button"
              class="feature-card feature-card-ai"
              @click="goToAiDining"
            >
              <span class="feature-icon feature-icon-ai">✨</span>

              <span class="feature-content">
                <span class="feature-label">智能推荐</span>
                <strong>AI 探店</strong>
                <small>
                  说出人数、预算和口味，获得个性化商家推荐
                </small>
              </span>

              <span class="feature-link">
                开始对话
                <span>→</span>
              </span>
            </button>

            <button
              type="button"
              class="feature-card feature-card-ranking"
              @click="goToRanking"
            >
              <span class="feature-icon feature-icon-ranking">🔥</span>

              <span class="feature-content">
                <span class="feature-label">口碑趋势</span>
                <strong>热门商家榜单</strong>
                <small>
                  查看本周热议、月度新晋与高性价比商家
                </small>
              </span>

              <span class="feature-link">
                查看榜单
                <span>→</span>
              </span>
            </button>
          </div>
        </div>
      </section>

      <section class="scenes-section">
        <div class="container">
          <div class="section-heading">
            <div>
              <span class="section-eyebrow">按场景发现美食</span>
              <h2 class="section-title">今天是什么用餐场景？</h2>
              <p>选择场景后，我们会自动调整推荐重点。</p>
            </div>

            <span class="scene-count">8 种场景</span>
          </div>

          <div class="scenes-grid">
            <div
              v-for="scene in scenes"
              :key="scene.id"
              class="scene-card"
              :class="{ active: selectedScene?.id === scene.id }"
              @click="selectScene(scene)"
            >
              <span
                v-if="selectedScene?.id === scene.id"
                class="scene-check"
              >
                ✓
              </span>

              <div class="scene-icon" :style="{ background: scene.color }">
                <span class="icon-emoji">{{ scene.emoji }}</span>
              </div>

              <h3>{{ scene.name }}</h3>
              <p class="scene-desc">{{ scene.description }}</p>

              <span class="scene-action">
                选择场景
                <span>→</span>
              </span>
            </div>
          </div>
        </div>
      </section>

      <section v-if="selectedScene" class="filters-section">
        <div class="container">
          <div class="filters-card">
            <h3 class="filters-title">
              <span class="scene-tag">{{ selectedScene.emoji }} {{ selectedScene.name }}</span>
              <span class="scene-rules">推荐重点：{{ selectedScene.rules.join('、') }}</span>
            </h3>
            <div class="filters-row">
              <div class="filter-item">
                <label>预算范围</label>
                <select v-model="filters.budget" class="filter-select">
                  <option value="">不限</option>
                  <option value="low">￥50以下</option>
                  <option value="medium">￥50-100</option>
                  <option value="high">￥100-200</option>
                  <option value="premium">￥200以上</option>
                </select>
              </div>
              <div class="filter-item">
                <label>菜系偏好</label>
                <select v-model="filters.cuisine" class="filter-select">
                  <option value="">不限</option>
                  <option value="chinese">中餐</option>
                  <option value="western">西餐</option>
                  <option value="japanese">日料</option>
                  <option value="korean">韩餐</option>
                  <option value="thai">泰餐</option>
                  <option value="hotpot">火锅</option>
                  <option value="snacks">小吃</option>
                  <option value="breakfast">早餐</option>
                </select>
              </div>
              <div class="filter-item">
                <label>距离范围</label>
                <select v-model="filters.distance" class="filter-select">
                  <option value="">不限</option>
                  <option value="1km">1公里内</option>
                  <option value="3km">3公里内</option>
                  <option value="5km">5公里内</option>
                  <option value="10km">10公里内</option>
                </select>
              </div>
              <div class="filter-item">
                <label>人数</label>
                <select v-model="filters.people" class="filter-select">
                  <option value="">不限</option>
                  <option value="1">1人</option>
                  <option value="2">2人</option>
                  <option value="3-4">3-4人</option>
                  <option value="5-10">5-10人</option>
                  <option value="10+">10人以上</option>
                </select>
              </div>
            </div>
            <div class="filter-actions">
              <button class="reset-btn" @click="resetFilters">重置筛选</button>
              <button class="search-btn" @click="searchRestaurants">开始推荐</button>
            </div>
          </div>
        </div>
      </section>

      <section v-if="loading" class="loading-section">
        <div class="container">
          <div class="loading-card">
            <div class="loading-spinner"></div>
            <p>正在获取餐厅数据...</p>
          </div>
        </div>
      </section>

      <section v-if="selectedScene && !loading && restaurants.length > 0" class="results-section">
        <div class="container">
          <h2 class="section-title">
            推荐餐厅
            <span class="result-count">共 {{ restaurants.length }} 家</span>
          </h2>
          <div class="restaurants-grid">
            <div v-for="restaurant in restaurants" :key="restaurant.id" class="restaurant-card" @click="handleMerchantClick(restaurant)">
              <div class="rest-img">
                <div class="rest-placeholder" :style="{ background: restaurant.color }">
                  <span class="placeholder-emoji">{{ restaurant.emoji }}</span>
                </div>
              </div>
              <div class="rest-info">
                <div class="rest-header">
                  <h3>{{ restaurant.name }}</h3>
                  <span class="rest-rating">★ {{ restaurant.rating }}</span>
                </div>
                <p class="rest-category">{{ restaurant.category }}</p>
                <p class="rest-price">人均 ￥{{ restaurant.avgPrice }}</p>
                <p class="rest-distance">{{ restaurant.distance }}</p>
                <div class="rest-tags">
                  <span v-for="tag in restaurant.tags" :key="tag" class="tag">{{ tag }}</span>
                </div>
                <div class="recommend-reason">
                  <span class="reason-icon">💡</span>
                  <span class="reason-text">{{ restaurant.recommendReason }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMerchants } from '../../api/restaurant'
import { logSceneEntry, logSearch, logMerchantClick } from '../../api/behavior'
import UserAccountMenu from '../../components/UserAccountMenu.vue'

const router = useRouter()
const userInfo = ref({ username: '' })
const selectedScene = ref(null)
const restaurants = ref([])
const loading = ref(false)
const allMerchants = ref([])

const filters = reactive({
  budget: '',
  cuisine: '',
  distance: '',
  people: ''
})

const scenes = [
  {
    id: 'friends',
    name: '朋友聚会',
    emoji: '👯‍♀️',
    description: '热闹氛围，量大实惠',
    color: 'linear-gradient(135deg, #fff1e8 0%, #ffe4d1 100%)',
    rules: ['人数', '分量', '性价比', '氛围']
  },
  {
    id: 'family',
    name: '家庭聚餐',
    emoji: '👨‍👩‍👧‍👦',
    description: '温馨舒适，老少皆宜',
    color: 'linear-gradient(135deg, #effbea 0%, #dcf5d4 100%)',
    rules: ['环境', '口味', '空间', '服务']
  },
  {
    id: 'date',
    name: '约会',
    emoji: '🌹',
    description: '浪漫氛围，私密环境',
    color: 'linear-gradient(135deg, #fff0f5 0%, #ffe1ec 100%)',
    rules: ['环境', '氛围', '评价', '私密性']
  },
  {
    id: 'breakfast',
    name: '早餐',
    emoji: '🍳',
    description: '营养丰富，中西兼有',
    color: 'linear-gradient(135deg, #fff8e1 0%, #ffedbf 100%)',
    rules: ['营养', '便捷', '口味', '营业时间']
  },
  {
    id: 'alone',
    name: '独自用餐',
    emoji: '🥢',
    description: '安静舒适，一人食',
    color: 'linear-gradient(135deg, #edf7ff 0%, #dceeff 100%)',
    rules: ['安静', '便捷', '性价比', '单人座']
  },
  {
    id: 'afternoon',
    name: '下午茶',
    emoji: '☕',
    description: '悠闲时光，精致甜点',
    color: 'linear-gradient(135deg, #f6f0ff 0%, #e9ddff 100%)',
    rules: ['环境', '甜点', '饮品', '氛围']
  },
  {
    id: 'supper',
    name: '夜宵',
    emoji: '🌙',
    description: '深夜美食，营业时间长',
    color: 'linear-gradient(135deg, #fff5e6 0%, #ffe8c2 100%)',
    rules: ['营业时间', '距离', '口味', '夜宵特色']
  },
  {
    id: 'birthday',
    name: '生日聚会',
    emoji: '🎂',
    description: '庆祝氛围，蛋糕服务',
    color: 'linear-gradient(135deg, #fff0f7 0%, #ffdeee 100%)',
    rules: ['氛围', '空间', '蛋糕服务', '私密性']
  }
]

const allRestaurants = [
  { id: 1, name: 'Rose Garden 玫瑰园', category: '西餐', rating: 4.9, avgPrice: 268, distance: '1.2公里', tags: ['浪漫', '环境优雅', '适合约会'], emoji: '🌹', color: 'linear-gradient(135deg, #ffccd5 0%, #ffe5ec 100%)', recommendReason: '环境浪漫，灯光柔和，适合情侣约会，主厨推荐的法式牛排非常美味', envScore: 95, privacyLevel: 90, openHours: '11:00-22:00', capacity: 50 },
  { id: 2, name: '海底捞火锅', category: '火锅', rating: 4.8, avgPrice: 128, distance: '2.5公里', tags: ['服务好', '分量足', '适合聚会'], emoji: '🍲', color: 'linear-gradient(135deg, #ff758c 0%, #ff7eb3 100%)', recommendReason: '服务周到，食材新鲜，包间宽敞，非常适合朋友聚会', envScore: 80, privacyLevel: 70, openHours: '10:00-02:00', capacity: 200 },
  { id: 3, name: '外婆家', category: '江浙菜', rating: 4.6, avgPrice: 78, distance: '3.1公里', tags: ['家庭聚餐', '口味清淡', '性价比高'], emoji: '🏠', color: 'linear-gradient(135deg, #84fab0 0%, #8fd3f4 100%)', recommendReason: '菜品口味清淡适合老人小孩，环境温馨，价格实惠', envScore: 75, privacyLevel: 60, openHours: '10:30-21:30', capacity: 150 },
  { id: 4, name: '真功夫', category: '快餐', rating: 4.2, avgPrice: 32, distance: '500米', tags: ['便捷', '单人餐', '健康'], emoji: '🍱', color: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)', recommendReason: '出餐快，座位舒适，适合独自用餐，营养均衡', envScore: 60, privacyLevel: 40, openHours: '07:00-22:00', capacity: 30 },
  { id: 5, name: '星巴克', category: '咖啡', rating: 4.7, avgPrice: 45, distance: '800米', tags: ['下午茶', '环境舒适', '咖啡'], emoji: '☕', color: 'linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%)', recommendReason: '环境优雅舒适，咖啡和甜点都很精致，适合下午茶时光', envScore: 85, privacyLevel: 50, openHours: '07:00-22:00', capacity: 40 },
  { id: 6, name: '烧烤夜市', category: '烧烤', rating: 4.5, avgPrice: 68, distance: '1.8公里', tags: ['夜宵', '烧烤', '营业到凌晨'], emoji: '🍢', color: 'linear-gradient(135deg, #ff9a9e 0%, #fecfef 100%)', recommendReason: '营业到凌晨3点，烤串味道正宗，是夜宵的绝佳选择', envScore: 55, privacyLevel: 30, openHours: '17:00-03:00', capacity: 80 },
  { id: 7, name: '生日派对餐厅', category: '西餐', rating: 4.8, avgPrice: 198, distance: '4.2公里', tags: ['生日聚会', '蛋糕服务', '派对布置'], emoji: '🎂', color: 'linear-gradient(135deg, #ffecd2 0%, #fcb69f 100%)', recommendReason: '提供生日蛋糕和派对布置服务，包间宽敞，氛围热烈', envScore: 90, privacyLevel: 80, openHours: '11:00-22:30', capacity: 120 },
  { id: 8, name: '川湘汇', category: '川菜', rating: 4.5, avgPrice: 98, distance: '2.1公里', tags: ['朋友聚会', '辣味', '量大'], emoji: '🌶️', color: 'linear-gradient(135deg, #ff6b6b 0%, #ffa502 100%)', recommendReason: '川菜正宗，分量足，价格实惠，适合朋友聚会', envScore: 65, privacyLevel: 45, openHours: '11:00-21:00', capacity: 100 },
  { id: 9, name: '粤港茶餐厅', category: '粤菜', rating: 4.6, avgPrice: 68, distance: '1.5公里', tags: ['家庭聚餐', '早茶', '点心'], emoji: '🍵', color: 'linear-gradient(135deg, #7bed9f 0%, #70a1ff 100%)', recommendReason: '粤式点心种类丰富，口味正宗，适合家庭聚餐', envScore: 70, privacyLevel: 55, openHours: '07:00-21:00', capacity: 80 },
  { id: 10, name: '深夜食堂', category: '日料', rating: 4.7, avgPrice: 88, distance: '900米', tags: ['夜宵', '日料', '安静'], emoji: '🍣', color: 'linear-gradient(135deg, #dfe6e9 0%, #b2bec3 100%)', recommendReason: '日式居酒屋风格，安静舒适，营业到凌晨2点', envScore: 75, privacyLevel: 65, openHours: '17:00-02:00', capacity: 25 },
  { id: 11, name: '私人会所', category: '高端', rating: 4.9, avgPrice: 588, distance: '5.5公里', tags: ['约会', '私密', '高端'], emoji: '💎', color: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)', recommendReason: '私人会所环境私密，服务一对一，非常适合重要约会', envScore: 98, privacyLevel: 98, openHours: '11:00-23:00', capacity: 30 },
  { id: 12, name: '甜品屋', category: '甜品', rating: 4.6, avgPrice: 52, distance: '600米', tags: ['下午茶', '甜品', '拍照'], emoji: '🍰', color: 'linear-gradient(135deg, #ffdde1 0%, #ee9ca7 100%)', recommendReason: '甜品精致美观，适合拍照发朋友圈，下午茶首选', envScore: 82, privacyLevel: 55, openHours: '10:00-21:00', capacity: 20 },
  { id: 13, name: '永和豆浆', category: '早餐', rating: 4.3, avgPrice: 20, distance: '600米', tags: ['早餐', '中式', '快捷'], emoji: '🥛', color: 'linear-gradient(135deg, #fff7e6 0%, #ffe7ba 100%)', recommendReason: '经典中式早餐，豆浆油条一应俱全，营养丰富，出餐快捷', envScore: 55, privacyLevel: 35, openHours: '06:00-20:00', capacity: 25 },
  { id: 14, name: '肯德基', category: '快餐', rating: 4.4, avgPrice: 35, distance: '800米', tags: ['早餐', '西式', '便捷'], emoji: '🍗', color: 'linear-gradient(135deg, #ffc53d 0%, #ffa940 100%)', recommendReason: '西式早餐选择丰富，帕尼尼、薯饼、咖啡应有尽有，适合上班族', envScore: 60, privacyLevel: 30, openHours: '06:00-23:00', capacity: 50 },
  { id: 15, name: '早茶坊', category: '粤菜', rating: 4.5, avgPrice: 58, distance: '1.2公里', tags: ['早餐', '广式早茶', '点心'], emoji: '🍤', color: 'linear-gradient(135deg, #73d13d 0%, #95de64 100%)', recommendReason: '广式早茶，点心精致多样，虾饺、烧卖、肠粉一应俱全', envScore: 70, privacyLevel: 50, openHours: '06:30-15:00', capacity: 60 },
  { id: 16, name: '星巴克早餐', category: '咖啡', rating: 4.6, avgPrice: 42, distance: '800米', tags: ['早餐', '西式', '咖啡'], emoji: '🥐', color: 'linear-gradient(135deg, #ffd666 0%, #ffc53d 100%)', recommendReason: '西式早餐配咖啡，牛角包、三明治新鲜美味，开启活力一天', envScore: 80, privacyLevel: 45, openHours: '07:00-22:00', capacity: 35 }
]

onMounted(async () => {
  const user = localStorage.getItem('user')
  if (user) {
    userInfo.value = JSON.parse(user)
  }
  await fetchMerchants()
})

const fetchMerchants = async () => {
  loading.value = true
  try {
    const response = await getMerchants({ pageNum: 1, pageSize: 100 })
    if (response.success && response.data) {
      const merchants = response.data.records || response.data
      allMerchants.value = merchants.map(merchant => ({
        id: merchant.id,
        name: merchant.name,
        category: merchant.category,
        rating: parseFloat(merchant.rating) || 0,
        avgPrice: parseFloat(merchant.averagePrice) || 0,
        distance: calculateDistance(merchant) + '公里',
        tags: parseTags(merchant.environmentTags),
        emoji: getCategoryEmoji(merchant.category),
        color: getCategoryColor(merchant.category),
        recommendReason: merchant.description || '暂无推荐理由',
        envScore: merchant.rating ? merchant.rating * 20 : 50,
        privacyLevel: getPrivacyLevel(merchant),
        openHours: getOpenHours(merchant),
        capacity: merchant.reviewCount ? merchant.reviewCount * 10 : 50
      }))
    }
  } catch (error) {
    console.error('获取商家数据失败:', error)
    allMerchants.value = [...allRestaurants]
  } finally {
    loading.value = false
  }
}

const calculateDistance = (merchant) => {
  const userLat = 30.5728 // 默认用户位置（成都）
  const userLng = 104.0668
  const merchantLat = parseFloat(merchant.latitude) || userLat
  const merchantLng = parseFloat(merchant.longitude) || userLng
  
  const R = 6371
  const dLat = (merchantLat - userLat) * Math.PI / 180
  const dLon = (merchantLng - userLng) * Math.PI / 180
  const a = 
    Math.sin(dLat/2) * Math.sin(dLat/2) +
    Math.cos(userLat * Math.PI / 180) * Math.cos(merchantLat * Math.PI / 180) * 
    Math.sin(dLon/2) * Math.sin(dLon/2)
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a))
  return (R * c).toFixed(2)
}

const parseTags = (tags) => {
  if (!tags) return []
  try {
    return typeof tags === 'string' ? JSON.parse(tags) : tags
  } catch {
    return []
  }
}

const getCategoryEmoji = (category) => {
  const emojiMap = {
    '川菜': '🌶️', '粤菜': '🍵', '烧烤': '🍢', '轻食沙拉': '🥗', 
    '日料': '🍣', '西餐': '🍽️', '火锅': '🍲', '快餐': '🍱',
    '咖啡': '☕', '甜品': '🍰', '早茶': '🍤', '高端': '💎'
  }
  return emojiMap[category] || '🍽️'
}

const getCategoryColor = (category) => {
  const colorMap = {
    '中餐': 'linear-gradient(135deg, #fff4e8 0%, #ffe7d5 100%)',
    '川菜': 'linear-gradient(135deg, #fff0ec 0%, #ffded6 100%)',
    '川湘菜': 'linear-gradient(135deg, #fff0ec 0%, #ffded6 100%)',
    '粤菜': 'linear-gradient(135deg, #eefaf2 0%, #dcf3e4 100%)',
    '烧烤': 'linear-gradient(135deg, #fff0f3 0%, #ffdee6 100%)',
    '轻食沙拉': 'linear-gradient(135deg, #eef9f3 0%, #dbf1e4 100%)',
    '休闲餐饮': 'linear-gradient(135deg, #f5f2ff 0%, #e8e1fa 100%)',
    '日料': 'linear-gradient(135deg, #f1f5fb 0%, #e1e8f2 100%)',
    '西餐': 'linear-gradient(135deg, #f4f0ff 0%, #e7defb 100%)',
    '火锅': 'linear-gradient(135deg, #fff0f5 0%, #ffdee9 100%)',
    '快餐': 'linear-gradient(135deg, #fff6e9 0%, #ffe9ce 100%)',
    '咖啡': 'linear-gradient(135deg, #f6f0eb 0%, #eadfd5 100%)',
    '咖啡甜品': 'linear-gradient(135deg, #f6f0ff 0%, #e8ddfa 100%)',
    '甜品': 'linear-gradient(135deg, #fff1f6 0%, #ffdfeb 100%)',
    '早茶': 'linear-gradient(135deg, #f2faea 0%, #dff1d2 100%)',
    '早餐': 'linear-gradient(135deg, #fff8e8 0%, #ffedc8 100%)',
    '高端': 'linear-gradient(135deg, #f2eff8 0%, #e3ddeb 100%)'
  }

  return colorMap[category]
    || 'linear-gradient(135deg, #fff5eb 0%, #ffe8d7 100%)'
}

const getPrivacyLevel = (merchant) => {
  const tags = parseTags(merchant.environmentTags) || []
  if (tags.some(t => t.includes('包间') || t.includes('私密') || t.includes('情侣'))) {
    return 80
  }
  if (tags.some(t => t.includes('安静') || t.includes('日式') || t.includes('榻榻米'))) {
    return 65
  }
  if (tags.some(t => t.includes('家庭') || t.includes('聚会'))) {
    return 50
  }
  return 40
}

const getOpenHours = (merchant) => {
  const category = merchant.category
  if (category === '烧烤') return '17:00-02:00'
  if (category === '粤菜' || category === '早茶') return '08:00-22:00'
  if (category === '轻食沙拉') return '09:00-21:00'
  if (category === '日料') return '11:00-23:00'
  return '10:00-22:00'
}

const selectScene = (scene) => {
  selectedScene.value = scene
  resetFilters()
  searchRestaurants()
  
  const userId = userInfo.value.id
  const sceneTypeMap = {
    friends: 'FRIENDS',
    family: 'FAMILY',
    date: 'DATE',
    breakfast: 'BREAKFAST',
    alone: 'ALONE',
    afternoon: 'AFTERNOON',
    supper: 'LATE_NIGHT',
    birthday: 'BIRTHDAY'
  }
  logSceneEntry({ userId, sceneType: sceneTypeMap[scene.id] || scene.id.toUpperCase() }).catch(() => {})
}

const resetFilters = () => {
  filters.budget = ''
  filters.cuisine = ''
  filters.distance = ''
  filters.people = ''
}

const getSceneScore = (restaurant, scene) => {
  let score = 0
  const distance = parseFloat(restaurant.distance) || 0
  
  const getOpenHoursScore = (sceneId) => {
    const openHours = restaurant.openHours || '00:00-23:59'
    const [start, end] = openHours.split('-')
    const [startH, startM] = start.split(':').map(Number)
    const [endH, endM] = end.split(':').map(Number)
    const startMinutes = startH * 60 + startM
    const endMinutes = endH * 60 + endM
    
    const isOpenLate = endMinutes >= 180 || (endMinutes < 60 && endH === 0)
    const isOpenEarly = startMinutes <= 420
    
    switch(sceneId) {
      case 'supper':
        return isOpenLate ? 100 : (endMinutes >= 120 ? 60 : (endMinutes >= 90 ? 30 : 0))
      case 'breakfast':
        return isOpenEarly ? 100 : (startMinutes <= 480 ? 60 : (startMinutes <= 540 ? 30 : 0))
      case 'afternoon':
        return (startMinutes <= 600 && endMinutes >= 1020) ? 100 : 50
      default:
        return 50
    }
  }
  
  const getAffordabilityScore = () => {
    if (restaurant.avgPrice < 50) return 100
    if (restaurant.avgPrice < 100) return 80
    if (restaurant.avgPrice < 200) return 50
    return 20
  }
  
  const getConvenienceScore = () => {
    const distScore = distance <= 0.5 ? 100 : (distance <= 1 ? 80 : (distance <= 2 ? 60 : (distance <= 3 ? 40 : 20)))
    const priceScore = restaurant.avgPrice < 50 ? 100 : (restaurant.avgPrice < 100 ? 70 : 40)
    return (distScore * 0.6 + priceScore * 0.4)
  }
  
  const tagMatchScore = () => {
    const sceneTags = {
      date: ['浪漫', '环境优雅', '适合约会', '私密', '高端'],
      friends: ['适合聚会', '分量足', '辣味', '量大'],
      family: ['家庭聚餐', '口味清淡', '广式早茶', '点心'],
      alone: ['单人餐', '安静', '便捷'],
      afternoon: ['下午茶', '甜品', '咖啡', '环境舒适'],
      supper: ['夜宵', '烧烤', '营业到凌晨'],
      birthday: ['生日聚会', '蛋糕服务', '派对布置'],
      breakfast: ['早餐', '中式', '西式', '快捷', '广式早茶', '点心']
    }
    const tags = sceneTags[scene.id] || []
    let matchScore = 0
    tags.forEach(tag => {
      if (restaurant.tags.some(t => t.includes(tag))) {
        matchScore += 100 / tags.length
      }
    })
    return matchScore
  }
  
  const scores = {
    envScore: restaurant.envScore || 50,
    privacyLevel: restaurant.privacyLevel || 50,
    capacityScore: restaurant.capacity ? (restaurant.capacity >= 100 ? 100 : (restaurant.capacity >= 50 ? 70 : (restaurant.capacity >= 20 ? 40 : 20))) : 50,
    ratingScore: restaurant.rating * 20,
    distanceScore: Math.max(0, (10 - distance) * 10),
    openHoursScore: getOpenHoursScore(scene.id),
    affordabilityScore: getAffordabilityScore(),
    convenienceScore: getConvenienceScore(),
    tagScore: tagMatchScore()
  }
  
  const weightModels = {
    date: { envScore: 0.30, privacyLevel: 0.25, ratingScore: 0.20, tagScore: 0.15, distanceScore: 0.10 },
    friends: { capacityScore: 0.25, affordabilityScore: 0.25, ratingScore: 0.20, tagScore: 0.15, distanceScore: 0.15 },
    family: { envScore: 0.25, affordabilityScore: 0.20, ratingScore: 0.20, capacityScore: 0.15, tagScore: 0.15, distanceScore: 0.05 },
    alone: { convenienceScore: 0.35, distanceScore: 0.25, affordabilityScore: 0.20, envScore: 0.15, ratingScore: 0.05 },
    afternoon: { envScore: 0.30, tagScore: 0.25, ratingScore: 0.20, distanceScore: 0.15, privacyLevel: 0.10 },
    supper: { openHoursScore: 0.40, distanceScore: 0.30, ratingScore: 0.20, tagScore: 0.10 },
    birthday: { capacityScore: 0.25, privacyLevel: 0.20, envScore: 0.20, tagScore: 0.20, ratingScore: 0.15 },
    breakfast: { openHoursScore: 0.35, convenienceScore: 0.30, affordabilityScore: 0.20, distanceScore: 0.10, ratingScore: 0.05 }
  }
  
  const weights = weightModels[scene.id] || weightModels.friends
  
  Object.keys(weights).forEach(key => {
    score += scores[key] * weights[key]
  })
  
  return score
}

const searchRestaurants = () => {
  let result = [...(allMerchants.value.length > 0 ? allMerchants.value : allRestaurants)]
  
  const userId = userInfo.value.id
  const searchKeywords = []
  if (filters.cuisine) {
    const cuisineNames = {
      chinese: '中餐', western: '西餐', japanese: '日料', 
      korean: '韩餐', thai: '泰餐', hotpot: '火锅', 
      snacks: '小吃', breakfast: '早餐'
    }
    searchKeywords.push(cuisineNames[filters.cuisine] || filters.cuisine)
  }
  if (filters.budget) {
    const budgetNames = {
      low: '低价', medium: '中等', high: '高价', premium: '高端'
    }
    searchKeywords.push(budgetNames[filters.budget] || filters.budget)
  }
  if (searchKeywords.length > 0) {
    logSearch({ userId, keyword: searchKeywords.join(' ') }).catch(() => {})
  }
  
  if (filters.budget) {
    const budgetMap = {
      low: r => r.avgPrice < 50,
      medium: r => r.avgPrice >= 50 && r.avgPrice <= 100,
      high: r => r.avgPrice > 100 && r.avgPrice <= 200,
      premium: r => r.avgPrice > 200
    }
    result = result.filter(budgetMap[filters.budget])
  }
  
  if (filters.cuisine) {
    const cuisineMap = {
      chinese: ['江浙菜', '川菜', '粤菜'],
      western: ['西餐', '高端'],
      japanese: ['日料'],
      korean: ['韩餐'],
      thai: ['泰餐'],
      hotpot: ['火锅'],
      snacks: ['快餐', '烧烤', '甜品'],
      breakfast: ['早餐']
    }
    result = result.filter(r => cuisineMap[filters.cuisine]?.includes(r.category))
  }
  
  if (filters.distance) {
    const distanceMap = {
      '1km': 1,
      '3km': 3,
      '5km': 5,
      '10km': 10
    }
    const maxDistance = distanceMap[filters.distance]
    result = result.filter(r => {
      const num = parseFloat(r.distance)
      return num <= maxDistance
    })
  }
  
  if (selectedScene.value) {
    result.sort((a, b) => getSceneScore(b, selectedScene.value) - getSceneScore(a, selectedScene.value))
  }
  
  restaurants.value = result
}

const goToRanking = () => {
  router.push('/diner/ranking')
}

const goToAiDining = () => {
  router.push('/diner/ai-dining')
}

const handleMerchantClick = (restaurant) => {
  const userId = userInfo.value.id
  logMerchantClick({ userId, merchantId: restaurant.id }).catch(() => {})
  router.push(`/diner/merchant/${restaurant.id}`)
}
</script>

<style scoped>
.diner-home,
.diner-home *,
.diner-home *::before,
.diner-home *::after {
  box-sizing: border-box;
}

.diner-home {
  position: relative;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  min-height: 100vh;
  overflow-x: hidden;
  overflow-x: clip;
  color: #25211d;
  font-family:
    "Microsoft YaHei",
    "PingFang SC",
    "Noto Sans SC",
    Arial,
    sans-serif;
  font-size: 16px;
  -webkit-font-smoothing: antialiased;
  text-rendering: optimizeLegibility;
  background:
    radial-gradient(
      circle at 10% 5%,
      rgba(255, 237, 213, 0.65),
      transparent 28%
    ),
    #f8f6f2;
}

.diner-home button,
.diner-home select {
  font-family: inherit;
}

.diner-nav,
.home-main,
.hero-section,
.feature-section,
.scenes-section,
.filters-section,
.loading-section,
.results-section {
  width: 100%;
  max-width: 100%;
  min-width: 0;
}

.diner-nav {
  position: sticky;
  top: 0;
  z-index: 100;
  overflow-x: clip;
  border-bottom: 1px solid rgba(229, 222, 212, 0.82);
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(18px);
}

.nav-container,
.container {
  width: calc(100% - 48px);
  max-width: 1180px;
  min-width: 0;
  margin-right: auto;
  margin-left: auto;
}

.nav-container {
  display: flex;
  min-height: 72px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.container {
  overflow: visible;
}

.brand-home {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
  padding: 0;
  border: 0;
  color: inherit;
  font: inherit;
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
  font-weight: 700;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.brand-copy small {
  color: #9a8f85;
  font-size: 12px;
  line-height: 1.35;
  white-space: nowrap;
}

.nav-actions {
  display: flex;
  min-width: 0;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
}

.home-main {
  padding-bottom: 48px;
  overflow-x: hidden;
  overflow-x: clip;
}

.hero-section {
  padding: 30px 0 16px;
}

.hero-panel {
  position: relative;
  display: grid;
  width: 100%;
  min-width: 0;
  min-height: 360px;
  grid-template-columns: minmax(0, 1.35fr) minmax(250px, 0.65fr);
  overflow: hidden;
  border: 1px solid #f1ddca;
  border-radius: 32px;
  background:
    radial-gradient(
      circle at 78% 18%,
      rgba(251, 146, 60, 0.2),
      transparent 28%
    ),
    linear-gradient(135deg, #fffaf4 0%, #fff4e8 54%, #ffead5 100%);
  box-shadow: 0 20px 55px rgba(111, 75, 43, 0.1);
}

.hero-content {
  position: relative;
  z-index: 2;
  min-width: 0;
  padding: 54px 30px 46px 54px;
  text-align: left;
}

.hero-eyebrow {
  display: inline-flex;
  max-width: 100%;
  align-items: center;
  padding: 7px 12px;
  border: 1px solid #fed7aa;
  border-radius: 999px;
  color: #c2410c;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.45;
  background: rgba(255, 255, 255, 0.68);
  overflow-wrap: anywhere;
}

.hero-content h1 {
  max-width: 610px;
  margin: 20px 0 14px;
  color: #29231e;
  font-size: clamp(42px, 5vw, 62px);
  font-weight: 800;
  line-height: 1.08;
  letter-spacing: -2px;
  overflow-wrap: anywhere;
}

.hero-content h1 span {
  color: #ea580c;
}

.hero-content p {
  max-width: 590px;
  margin: 0;
  color: #746b63;
  font-size: 17px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}

.hero-actions {
  display: flex;
  max-width: 100%;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 28px;
}

.hero-primary-button,
.hero-secondary-button {
  min-height: 48px;
  padding: 0 20px;
  border-radius: 14px;
  font: inherit;
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
  cursor: pointer;
  transition:
    transform 0.2s,
    box-shadow 0.2s,
    background 0.2s;
}

.hero-primary-button {
  display: inline-flex;
  align-items: center;
  gap: 14px;
  border: 0;
  color: #fff;
  background: #ea580c;
  box-shadow: 0 10px 24px rgba(234, 88, 12, 0.24);
}

.hero-primary-button:hover {
  transform: translateY(-2px);
  background: #c2410c;
  box-shadow: 0 14px 28px rgba(194, 65, 12, 0.28);
}

.hero-secondary-button {
  border: 1px solid #e4d8ce;
  color: #4d453e;
  background: rgba(255, 255, 255, 0.72);
}

.hero-secondary-button:hover {
  border-color: #fdba74;
  background: #fff;
}

.hero-trust-row {
  display: flex;
  max-width: 100%;
  flex-wrap: wrap;
  gap: 12px 18px;
  margin-top: 25px;
  color: #8a8178;
  font-size: 14px;
  line-height: 1.5;
}

.hero-visual {
  position: relative;
  min-width: 0;
  min-height: 360px;
  overflow: hidden;
}

.visual-glow {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 250px;
  max-width: 80%;
  aspect-ratio: 1;
  border-radius: 50%;
  background: rgba(251, 146, 60, 0.2);
  filter: blur(8px);
  transform: translate(-50%, -50%);
}

.food-orbit {
  position: absolute;
  display: grid;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.86);
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 18px 36px rgba(129, 78, 36, 0.14);
  backdrop-filter: blur(12px);
}

.food-orbit-main {
  top: 86px;
  right: 72px;
  width: 142px;
  height: 142px;
  font-size: 72px;
  transform: rotate(-5deg);
}

.food-orbit-top {
  top: 43px;
  right: 26px;
  width: 65px;
  height: 65px;
  font-size: 30px;
  transform: rotate(10deg);
}

.food-orbit-bottom {
  right: 34px;
  bottom: 57px;
  width: 76px;
  height: 76px;
  font-size: 35px;
  transform: rotate(8deg);
}

.ai-status-card {
  position: absolute;
  right: 112px;
  bottom: 41px;
  display: flex;
  max-width: calc(100% - 36px);
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border: 1px solid rgba(255, 255, 255, 0.86);
  border-radius: 13px;
  color: #655c54;
  font-size: 14px;
  white-space: nowrap;
  background: rgba(255, 255, 255, 0.84);
  box-shadow: 0 12px 25px rgba(91, 60, 30, 0.1);
}

.ai-status-dot {
  width: 8px;
  height: 8px;
  flex: 0 0 8px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 0 4px rgba(34, 197, 94, 0.13);
}

.feature-section {
  padding: 12px 0 24px;
}

.feature-grid {
  display: grid;
  width: 100%;
  min-width: 0;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.feature-card {
  display: grid;
  width: 100%;
  min-width: 0;
  min-height: 132px;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 16px;
  padding: 24px;
  overflow: hidden;
  border-radius: 22px;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    transform 0.22s,
    box-shadow 0.22s,
    border-color 0.22s;
}

.feature-card:hover {
  transform: translateY(-3px);
}

.feature-card-ai {
  border: 1px solid #ddd6fe;
  color: #332a47;
  background: linear-gradient(135deg, #faf8ff, #f4f0ff);
}

.feature-card-ai:hover {
  border-color: #c4b5fd;
  box-shadow: 0 14px 28px rgba(109, 40, 217, 0.1);
}

.feature-card-ranking {
  border: 1px solid #fed7aa;
  color: #4b3226;
  background: linear-gradient(135deg, #fffaf5, #fff3e6);
}

.feature-card-ranking:hover {
  border-color: #fdba74;
  box-shadow: 0 14px 28px rgba(234, 88, 12, 0.1);
}

.feature-icon {
  display: grid;
  width: 58px;
  height: 58px;
  flex: 0 0 58px;
  place-items: center;
  border-radius: 18px;
  font-size: 28px;
}

.feature-icon-ai {
  background: #ede9fe;
}

.feature-icon-ranking {
  background: #ffedd5;
}

.feature-content {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.feature-label {
  margin-bottom: 5px;
  color: #9b8f84;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.06em;
}

.feature-content strong {
  margin-bottom: 7px;
  font-size: 22px;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.feature-content small {
  color: #80766e;
  font-size: 15px;
  line-height: 1.65;
  overflow-wrap: anywhere;
}

.feature-link {
  display: flex;
  align-items: center;
  gap: 9px;
  color: #6f6257;
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
}

.feature-card:hover .feature-link span {
  transform: translateX(4px);
}

.feature-link span {
  transition: transform 0.2s;
}

.scenes-section {
  padding: 28px 0 30px;
}

.section-heading {
  display: flex;
  min-width: 0;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 20px;
}

.section-heading > div {
  min-width: 0;
}

.section-eyebrow {
  display: block;
  margin-bottom: 7px;
  color: #ea580c;
  font-size: 14px;
  font-weight: 700;
}

.section-title {
  margin: 0;
  color: #29231e;
  font-size: 30px;
  font-weight: 750;
  line-height: 1.35;
  letter-spacing: -0.5px;
  overflow-wrap: anywhere;
}

.section-heading p {
  margin: 8px 0 0;
  color: #92877d;
  font-size: 16px;
  line-height: 1.6;
}

.scene-count {
  flex: 0 0 auto;
  padding: 7px 12px;
  border: 1px solid #ebe4dd;
  border-radius: 999px;
  color: #8d8278;
  font-size: 14px;
  background: #fff;
  white-space: nowrap;
}

.scenes-grid {
  display: grid;
  width: 100%;
  min-width: 0;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.scene-card {
  position: relative;
  width: 100%;
  min-width: 0;
  min-height: 190px;
  padding: 21px;
  overflow: hidden;
  border: 1px solid #eae5df;
  border-radius: 20px;
  text-align: left;
  background: rgba(255, 255, 255, 0.9);
  cursor: pointer;
  transition:
    transform 0.22s,
    border-color 0.22s,
    box-shadow 0.22s;
}

.scene-card:hover {
  transform: translateY(-4px);
  border-color: #fdba74;
  box-shadow: 0 14px 30px rgba(92, 67, 43, 0.09);
}

.scene-card.active {
  border-color: #f97316;
  background: #fffaf5;
  box-shadow: 0 12px 28px rgba(234, 88, 12, 0.11);
}

.scene-check {
  position: absolute;
  top: 14px;
  right: 14px;
  display: grid;
  width: 26px;
  height: 26px;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  background: #f97316;
}

.scene-icon {
  display: grid;
  width: 60px;
  height: 60px;
  margin: 0 0 18px;
  place-items: center;
  border: 1px solid rgba(214, 199, 185, 0.45);
  border-radius: 18px;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.85),
    0 6px 14px rgba(91, 67, 45, 0.06);
  transition:
    transform 0.2s,
    border-color 0.2s,
    box-shadow 0.2s;
}

.icon-emoji {
  font-size: 30px;
  line-height: 1;
}

.scene-card:hover .scene-icon {
  transform: translateY(-2px);
  border-color: rgba(249, 115, 22, 0.35);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.9),
    0 9px 18px rgba(91, 67, 45, 0.09);
}

.scene-card.active .scene-icon {
  border-color: rgba(234, 88, 12, 0.48);
  box-shadow:
    0 0 0 3px rgba(249, 115, 22, 0.08),
    0 9px 18px rgba(234, 88, 12, 0.1);
}

.scene-card h3 {
  margin: 0 0 7px;
  color: #302a25;
  font-size: 19px;
  font-weight: 700;
  line-height: 1.4;
  overflow-wrap: anywhere;
}

.scene-desc {
  margin: 0;
  color: #938980;
  font-size: 15px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.scene-action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 17px;
  color: #b0a49a;
  font-size: 14px;
}

.scene-card:hover .scene-action,
.scene-card.active .scene-action {
  color: #ea580c;
}

.filters-section {
  margin-bottom: 40px;
}

.filters-card {
  width: 100%;
  min-width: 0;
  padding: 30px;
  overflow: hidden;
  border-radius: 18px;
  background: #ffffff;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.filters-title {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 9px;
  margin: 0 0 26px;
  color: #29231e;
  line-height: 1.4;
}

.scene-tag {
  color: #29231e;
  font-size: 23px;
  font-weight: 700;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.scene-rules {
  color: #d85b16;
  font-size: 16px;
  font-weight: 500;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.filters-row {
  display: grid;
  width: 100%;
  min-width: 0;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 24px;
}

.filter-item {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 10px;
}

.filter-item label {
  color: #514840;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.4;
}

.filter-select {
  display: block;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  min-height: 52px;
  padding: 0 44px 0 16px;
  border: 2px solid #e4ddd6;
  border-radius: 12px;
  color: #2f2924;
  font-size: 18px;
  font-weight: 500;
  line-height: 1.4;
  background-color: #ffffff;
  cursor: pointer;
}

.filter-select:focus {
  outline: none;
  border-color: #f97316;
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.1);
}

.filter-select option {
  color: #2f2924;
  font-family:
    "Microsoft YaHei",
    "PingFang SC",
    Arial,
    sans-serif;
  font-size: 18px;
  font-weight: 500;
}

.filter-actions {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid #f0f0f0;
}

.reset-btn,
.search-btn {
  min-height: 46px;
  padding: 10px 24px;
  border: 0;
  border-radius: 10px;
  font-size: 16px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition:
    transform 0.2s,
    background 0.2s,
    box-shadow 0.2s;
}

.reset-btn {
  color: #666;
  background: #f5f5f5;
}

.reset-btn:hover {
  background: #e8e8e8;
}

.search-btn {
  color: #fff;
  background: linear-gradient(135deg, #ff6700 0%, #ff9500 100%);
}

.search-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(255, 103, 0, 0.3);
}

.loading-section {
  margin-bottom: 40px;
}

.loading-card {
  padding: 40px;
  border-radius: 16px;
  text-align: center;
  background: #ffffff;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.loading-spinner {
  width: 48px;
  height: 48px;
  margin: 0 auto 16px;
  border: 4px solid #f3f3f3;
  border-top-color: #ff6700;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.loading-card p {
  margin: 0;
  color: #667085;
  font-size: 16px;
}

.results-section {
  padding-bottom: 36px;
}

.results-section > .container > .section-title {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px 12px;
  margin: 0 0 18px;
  color: #25211d;
  font-size: 30px;
  font-weight: 750;
  line-height: 1.35;
  letter-spacing: -0.4px;
}

.result-count {
  color: #8f847b;
  font-size: 16px;
  font-weight: 500;
  letter-spacing: 0;
}

.restaurants-grid {
  display: grid;
  width: 100%;
  min-width: 0;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
}

.restaurant-card {
  display: flex;
  width: 100%;
  min-width: 0;
  overflow: hidden;
  border: 1px solid #ebe5df;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 5px 18px rgba(80, 61, 43, 0.06);
  cursor: pointer;
  transition:
    transform 0.22s,
    border-color 0.22s,
    box-shadow 0.22s;
}

.restaurant-card:hover {
  transform: translateY(-3px);
  border-color: #fdc99f;
  box-shadow: 0 15px 32px rgba(91, 66, 43, 0.11);
}

.rest-img {
  width: 180px;
  height: 180px;
  flex: 0 0 180px;
}

.rest-placeholder {
  position: relative;
  display: flex;
  width: 100%;
  height: 100%;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.rest-placeholder::before {
  position: absolute;
  inset: 14px;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.16);
  content: "";
}

.placeholder-emoji {
  position: relative;
  z-index: 1;
  display: grid;
  width: 72px;
  height: 72px;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.78);
  border-radius: 22px;
  font-size: 38px;
  line-height: 1;
  background: rgba(255, 255, 255, 0.56);
  box-shadow:
    0 10px 24px rgba(80, 55, 35, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(6px);
}

.rest-info {
  display: flex;
  min-width: 0;
  flex: 1 1 auto;
  flex-direction: column;
  padding: 20px;
}

.rest-header {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 7px;
}

.rest-header h3 {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: #25211d;
  font-size: 20px;
  font-weight: 700;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rest-rating {
  flex: 0 0 auto;
  padding-top: 1px;
  color: #ea580c;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.4;
  white-space: nowrap;
}

.rest-category {
  margin: 0 0 5px;
  color: #766c63;
  font-size: 16px;
  font-weight: 500;
  line-height: 1.45;
}

.rest-price {
  margin: 0 0 4px;
  color: #302a25;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.45;
}

.rest-distance {
  margin: 0 0 10px;
  color: #968b82;
  font-size: 14px;
  line-height: 1.45;
}

.rest-tags {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}

.tag {
  max-width: 100%;
  padding: 4px 8px;
  border: 1px solid #ffdfc5;
  border-radius: 7px;
  color: #c65d1e;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.25;
  overflow-wrap: anywhere;
  background: #fff8f0;
}

.recommend-reason {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: 7px;
  margin-top: auto;
  padding: 9px 11px;
  border-radius: 11px;
  background: #faf8f5;
}

.reason-icon {
  flex: 0 0 auto;
  padding-top: 1px;
  font-size: 15px;
  line-height: 1.5;
}

.reason-text {
  display: -webkit-box;
  min-width: 0;
  overflow: hidden;
  color: #6f665e;
  font-size: 14px;
  line-height: 1.6;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 992px) {
  .hero-panel {
    grid-template-columns: minmax(0, 1fr);
  }

  .hero-visual {
    display: none;
  }

  .scenes-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .filters-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .restaurants-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 760px) {
  .nav-container,
  .container {
    width: calc(100% - 32px);
  }

  .feature-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .scenes-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .section-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .filters-card {
    padding: 24px;
  }

  .restaurant-card {
    flex-direction: column;
  }

  .rest-img {
    width: 100%;
    height: 170px;
    flex-basis: 170px;
  }
}

@media (max-width: 560px) {
  .brand-copy small {
    display: none;
  }

  .hero-section {
    padding-top: 18px;
  }

  .hero-panel {
    min-height: auto;
    border-radius: 24px;
  }

  .hero-content {
    padding: 32px 24px;
  }

  .hero-content h1 {
    font-size: 38px;
    letter-spacing: -1px;
  }

  .hero-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .hero-primary-button,
  .hero-secondary-button {
    justify-content: center;
    width: 100%;
  }

  .hero-trust-row {
    flex-direction: column;
    gap: 9px;
  }

  .feature-card {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .feature-link {
    grid-column: 2;
    margin-top: 4px;
  }

  .scenes-grid,
  .filters-row {
    grid-template-columns: minmax(0, 1fr);
  }

  .filter-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .reset-btn,
  .search-btn {
    width: 100%;
  }

  .results-section > .container > .section-title {
    font-size: 27px;
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

body {
  position: relative;
}

img,
svg,
canvas,
video {
  max-width: 100%;
}
</style>
