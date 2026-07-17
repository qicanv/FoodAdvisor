<template>
  <div class="diner-home">
    <nav class="diner-nav">
      <div class="nav-container">
        <div class="logo-section">
          <img src="../../assets/images/greedy-cat.png" alt="食尚参谋" class="logo-img" />
          <span class="brand-name">食尚参谋 - 食客端</span>
        </div>
        <div class="nav-links">
          <div class="user-info">
            <button class="profile-btn" @click="goToProfile">
              <span class="profile-icon">👤</span>
              <span class="user-name">{{ userInfo.username }}</span>
            </button>
            <button class="logout-btn" @click="handleLogout">退出登录</button>
          </div>
        </div>
      </div>
    </nav>

    <main class="home-main">
      <section class="hero-section">
        <div class="container">
          <div class="hero-content">
            <h1>今天想吃点什么？</h1>
            <p>选择一个场景，食尚参谋为您推荐最合适的餐厅</p>
          </div>
        </div>
      </section>

      <section class="ranking-entry-section">
        <div class="container">
          <div class="ranking-card" @click="goToRanking">
            <div class="ranking-icon">🔥</div>
            <div class="ranking-content">
              <h3 class="ranking-title">热门商家榜单</h3>
              <p class="ranking-subtitle">本周热议、月度新晋、性价比、人气商家</p>
            </div>
            <div class="ranking-arrow">
              <span class="arrow-text">查看榜单</span>
              <span class="arrow-icon">→</span>
            </div>
          </div>
        </div>
      </section>

      <section class="scenes-section">
        <div class="container">
          <h2 class="section-title">选择用餐场景</h2>
          <div class="scenes-grid">
            <div 
              v-for="scene in scenes" 
              :key="scene.id"
              class="scene-card"
              :class="{ active: selectedScene?.id === scene.id }"
              @click="selectScene(scene)"
            >
              <div class="scene-icon" :style="{ background: scene.color }">
                <span class="icon-emoji">{{ scene.emoji }}</span>
              </div>
              <h3>{{ scene.name }}</h3>
              <p class="scene-desc">{{ scene.description }}</p>
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
                  <option value="low">¥50以下</option>
                  <option value="medium">¥50-100</option>
                  <option value="high">¥100-200</option>
                  <option value="premium">¥200以上</option>
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
            <div v-for="restaurant in restaurants" :key="restaurant.id" class="restaurant-card">
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
                <p class="rest-price">人均 ¥{{ restaurant.avgPrice }}</p>
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

      <section v-if="!selectedScene && !loading" class="tips-section">
        <div class="container">
          <div class="tips-card">
            <div class="tips-icon">👆</div>
            <h3>请选择用餐场景</h3>
            <p>选择一个场景后，我们将根据场景特点为您推荐最合适的餐厅</p>
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
    color: 'linear-gradient(135deg, #ff6700 0%, #ff9500 100%)',
    rules: ['人数', '分量', '性价比', '氛围']
  },
  {
    id: 'family',
    name: '家庭聚餐',
    emoji: '👨‍👩‍👧‍👦',
    description: '温馨舒适，老少皆宜',
    color: 'linear-gradient(135deg, #52c41a 0%, #73d13d 100%)',
    rules: ['环境', '口味', '空间', '服务']
  },
  {
    id: 'date',
    name: '约会',
    emoji: '🌹',
    description: '浪漫氛围，私密环境',
    color: 'linear-gradient(135deg, #ff6b9d 0%, #ff85b3 100%)',
    rules: ['环境', '氛围', '评价', '私密性']
  },
  {
    id: 'breakfast',
    name: '早餐',
    emoji: '🍳',
    description: '营养丰富，中西兼有',
    color: 'linear-gradient(135deg, #faad14 0%, #ffc53d 100%)',
    rules: ['营养', '便捷', '口味', '营业时间']
  },
  {
    id: 'alone',
    name: '独自用餐',
    emoji: '🥢',
    description: '安静舒适，一人食',
    color: 'linear-gradient(135deg, #1890ff 0%, #40a9ff 100%)',
    rules: ['安静', '便捷', '性价比', '单人座']
  },
  {
    id: 'afternoon',
    name: '下午茶',
    emoji: '☕',
    description: '悠闲时光，精致甜点',
    color: 'linear-gradient(135deg, #722ed1 0%, #9254de 100%)',
    rules: ['环境', '甜点', '饮品', '氛围']
  },
  {
    id: 'supper',
    name: '夜宵',
    emoji: '🌙',
    description: '深夜美食，营业时间长',
    color: 'linear-gradient(135deg, #fa8c16 0%, #ffc53d 100%)',
    rules: ['营业时间', '距离', '口味', '夜宵特色']
  },
  {
    id: 'birthday',
    name: '生日聚会',
    emoji: '🎂',
    description: '庆祝氛围，蛋糕服务',
    color: 'linear-gradient(135deg, #eb2f96 0%, #ff69c1 100%)',
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
    '川菜': 'linear-gradient(135deg, #ff6b6b 0%, #ffa502 100%)',
    '粤菜': 'linear-gradient(135deg, #7bed9f 0%, #70a1ff 100%)',
    '烧烤': 'linear-gradient(135deg, #ff9a9e 0%, #fecfef 100%)',
    '轻食沙拉': 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
    '日料': 'linear-gradient(135deg, #dfe6e9 0%, #b2bec3 100%)',
    '西餐': 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    '火锅': 'linear-gradient(135deg, #ff758c 0%, #ff7eb3 100%)',
    '快餐': 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
    '咖啡': 'linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%)',
    '甜品': 'linear-gradient(135deg, #ffdde1 0%, #ee9ca7 100%)',
    '早茶': 'linear-gradient(135deg, #73d13d 0%, #95de64 100%)',
    '高端': 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'
  }
  return colorMap[category] || 'linear-gradient(135deg, #ff6700 0%, #ff9500 100%)'
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

const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  localStorage.removeItem('userRole')
  router.push('/diner')
}

const goToProfile = () => {
  router.push('/diner/profile')
}

const goToRanking = () => {
  router.push('/diner/ranking')
}
</script>

<style scoped>
.diner-home {
  min-height: 100vh;
  background: #f5f7fa;
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
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
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

.user-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-name {
  font-size: 14px;
  color: #333333;
}

.profile-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: #ff6700;
  color: #ffffff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
  margin-right: 12px;
}

.profile-btn:hover {
  background: #e55a00;
}

.profile-icon {
  font-size: 16px;
}

.logout-btn {
  padding: 8px 16px;
  background: #f5f5f5;
  color: #666666;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.logout-btn:hover {
  background: #e8e8e8;
}

.home-main {
  padding-top: 80px;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}

.hero-section {
  background: linear-gradient(135deg, #ff6700 0%, #ff7a00 30%, #ff9500 60%, #ffcc00 100%);
  padding: 60px 20px;
  margin-bottom: 40px;
}

.hero-content {
  text-align: center;
}

.hero-content h1 {
  font-size: 36px;
  font-weight: 700;
  color: #ffffff;
  margin: 0;
}

.hero-content p {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.9);
  margin: 12px 0 0;
}

.section-title {
  font-size: 24px;
  font-weight: 700;
  color: #1f2d3d;
  margin-bottom: 24px;
}

.result-count {
  font-size: 14px;
  font-weight: 400;
  color: #667085;
  margin-left: 12px;
}

.ranking-entry-section {
  padding: 20px 0;
}

.ranking-card {
  display: flex;
  align-items: center;
  gap: 16px;
  background: linear-gradient(135deg, #ff6b35 0%, #ff8c42 100%);
  border-radius: 16px;
  padding: 24px;
  cursor: pointer;
  transition: all 0.3s;
  box-shadow: 0 4px 16px rgba(255, 107, 53, 0.3);
}

.ranking-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(255, 107, 53, 0.4);
}

.ranking-icon {
  font-size: 40px;
}

.ranking-content {
  flex: 1;
}

.ranking-title {
  font-size: 20px;
  font-weight: 700;
  color: #fff;
  margin-bottom: 4px;
}

.ranking-subtitle {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.9);
}

.ranking-arrow {
  display: flex;
  align-items: center;
  gap: 8px;
}

.arrow-text {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.9);
}

.arrow-icon {
  font-size: 20px;
  color: #fff;
  transition: transform 0.3s;
}

.ranking-card:hover .arrow-icon {
  transform: translateX(4px);
}

.scenes-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.scene-card {
  background: #ffffff;
  border-radius: 16px;
  padding: 24px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
  border: 2px solid transparent;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.scene-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

.scene-card.active {
  border-color: #ff6700;
  background: #fff8f0;
}

.scene-icon {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 16px;
}

.icon-emoji {
  font-size: 32px;
}

.scene-card h3 {
  font-size: 18px;
  font-weight: 600;
  color: #1f2d3d;
  margin: 0 0 8px;
}

.scene-desc {
  font-size: 13px;
  color: #667085;
  margin: 0;
}

.filters-section {
  margin-bottom: 40px;
}

.filters-card {
  background: #ffffff;
  border-radius: 16px;
  padding: 28px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.filters-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2d3d;
  margin: 0 0 20px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.scene-tag {
  font-size: 20px;
}

.scene-rules {
  font-size: 14px;
  font-weight: 400;
  color: #ff6700;
}

.filters-row {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.filter-item label {
  font-size: 14px;
  font-weight: 500;
  color: #667085;
}

.filter-select {
  width: 160px;
  padding: 10px 14px;
  border: 1.5px solid #e8e8e8;
  border-radius: 10px;
  font-size: 14px;
  background: #ffffff;
  cursor: pointer;
}

.filter-select:focus {
  outline: none;
  border-color: #ff6700;
}

.filter-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid #f0f0f0;
}

.reset-btn {
  padding: 10px 24px;
  background: #f5f5f5;
  color: #666666;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.reset-btn:hover {
  background: #e8e8e8;
}

.search-btn {
  padding: 10px 24px;
  background: linear-gradient(135deg, #ff6700 0%, #ff9500 100%);
  color: #ffffff;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.search-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(255, 103, 0, 0.3);
}

.restaurants-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
}

.restaurant-card {
  background: #ffffff;
  border-radius: 16px;
  overflow: hidden;
  display: flex;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  transition: all 0.3s;
}

.restaurant-card:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  transform: translateY(-2px);
}

.rest-img {
  width: 180px;
  height: 180px;
  flex-shrink: 0;
}

.rest-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.placeholder-emoji {
  font-size: 48px;
}

.rest-info {
  flex: 1;
  padding: 20px;
  display: flex;
  flex-direction: column;
}

.rest-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.rest-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: #1f2d3d;
  margin: 0;
}

.rest-rating {
  font-size: 15px;
  font-weight: 600;
  color: #ff6700;
}

.rest-category {
  font-size: 13px;
  color: #667085;
  margin: 0 0 4px;
}

.rest-price {
  font-size: 14px;
  font-weight: 500;
  color: #1f2d3d;
  margin: 0 0 4px;
}

.rest-distance {
  font-size: 13px;
  color: #999999;
  margin: 0 0 12px;
}

.rest-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.tag {
  padding: 4px 10px;
  background: #fff7e6;
  color: #ff6700;
  border-radius: 6px;
  font-size: 12px;
}

.recommend-reason {
  display: flex;
  gap: 8px;
  margin-top: auto;
}

.reason-icon {
  font-size: 14px;
}

.reason-text {
  font-size: 13px;
  color: #667085;
  line-height: 1.5;
}

.loading-section {
  margin-bottom: 40px;
}

.loading-card {
  background: #ffffff;
  border-radius: 16px;
  padding: 40px;
  text-align: center;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.loading-spinner {
  width: 48px;
  height: 48px;
  border: 4px solid #f3f3f3;
  border-top: 4px solid #ff6700;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 16px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.loading-card p {
  font-size: 15px;
  color: #667085;
  margin: 0;
}

.tips-section {
  margin-bottom: 40px;
}

.tips-card {
  background: #ffffff;
  border-radius: 16px;
  padding: 40px;
  text-align: center;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.tips-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.tips-card h3 {
  font-size: 20px;
  font-weight: 600;
  color: #1f2d3d;
  margin: 0 0 8px;
}

.tips-card p {
  font-size: 15px;
  color: #667085;
  margin: 0;
}

@media (max-width: 992px) {
  .scenes-grid {
    grid-template-columns: repeat(3, 1fr);
  }
  
  .restaurants-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .scenes-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .hero-content h1 {
    font-size: 28px;
  }
  
  .filters-row {
    flex-direction: column;
  }
  
  .filter-select {
    width: 100%;
  }
  
  .restaurant-card {
    flex-direction: column;
  }
  
  .rest-img {
    width: 100%;
    height: 160px;
  }
}

@media (max-width: 480px) {
  .scenes-grid {
    grid-template-columns: 1fr;
  }
}
</style>