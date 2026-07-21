<template>
  <div class="merchant-layout">
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="logo-section">
        <img src="../assets/images/greedy-cat.png" alt="食尚参谋" class="logo-img" />
        <div class="logo-text" v-show="!sidebarCollapsed">
          <span class="brand-name">食尚参谋</span>
          <span class="brand-subtitle">商户管理</span>
        </div>
      </div>
      
      <nav class="sidebar-nav">
        <div class="nav-group">
          <span class="nav-group-title" v-show="!sidebarCollapsed">店铺管理</span>
          <router-link 
            v-for="item in navItems" 
            :key="item.path"
            :to="item.path"
            :class="['nav-item', { active: currentPath === item.path }]"
          >
            <svg :viewBox="item.iconViewBox" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
              <path :d="item.iconPath" />
            </svg>
            <span>{{ item.label }}</span>
          </router-link>
        </div>
      </nav>
      
      <div class="sidebar-footer">
        <button class="logout-btn" @click="handleLogout">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 12v-a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
            <circle cx="12" cy="12" r="3"></circle>
            <path d="M17 16l4-4-4-4"></path>
          </svg>
          <span>{{ sidebarCollapsed ? '' : '退出登录' }}</span>
        </button>
      </div>
    </aside>

    <main class="main-content">
      <header class="top-header">
        <div class="header-left">
          <button class="sidebar-toggle" @click="toggleSidebar">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M4 6h16M4 12h16M4 18h16" v-if="!sidebarCollapsed" />
              <path d="M6 18L18 6M6 6l12 12" v-else />
            </svg>
          </button>
          <div class="header-info">
            <h1>{{ title }}</h1>
            <p>{{ subtitle }}</p>
          </div>
        </div>
        <div class="header-user">
          <div class="user-avatar">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#fff" stroke-width="2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
              <circle cx="12" cy="7" r="4"></circle>
            </svg>
          </div>
          <span>{{ userInfo.nickname || userInfo.username || '商户' }}</span>
        </div>
      </header>

      <div class="content-wrapper">
        <div class="page-content">
          <slot></slot>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, provide } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getMyMerchants } from '../api/merchantConsole'

const router = useRouter()
const route = useRoute()

defineProps({
  title: {
    type: String,
    default: ''
  },
  subtitle: {
    type: String,
    default: ''
  }
})

const sidebarCollapsed = ref(false)

const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

const currentPath = computed(() => route.path)

const userInfo = computed(() => {
  const user = localStorage.getItem('user')
  return user ? JSON.parse(user) : {}
})

// 店铺选择器
const myStores = ref([])
const activeStoreId = ref(null)

provide('activeMerchantId', activeStoreId)

const loadStores = async () => {
  try {
    const response = await getMyMerchants()
    if (response.success && response.data) {
      myStores.value = response.data
      if (myStores.value.length > 0) {
        const savedId = localStorage.getItem('activeMerchantId')
        const savedIdNum = savedId ? Number(savedId) : null
        const exists = myStores.value.some(s => s.id === savedIdNum)
        if (exists) {
          activeStoreId.value = savedIdNum
        } else if (!activeStoreId.value) {
          activeStoreId.value = myStores.value[0].id
        }
      }
    }
  } catch (error) {
    console.error('加载店铺列表失败:', error)
  }
}

const onStoreChange = () => {
  if (activeStoreId.value) {
    localStorage.setItem('activeMerchantId', String(activeStoreId.value))
  }
}

defineExpose({ loadStores, activeStoreId })

const navItems = [
  { path: '/merchant/home', label: '店铺首页', iconViewBox: '0 0 24 24', iconPath: 'M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9z M9 22l3-3 3 3' },
  { path: '/merchant/stores', label: '店铺管理', iconViewBox: '0 0 24 24', iconPath: 'M8 21l1-17a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4l1 17' },
  { path: '/merchant/dishes', label: '菜品管理', iconViewBox: '0 0 24 24', iconPath: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2 M12 7a4 4 0 1 1 0 8 4 4 0 0 1 0-8z' },
  { path: '/merchant/statistics', label: '经营统计', iconViewBox: '0 0 24 24', iconPath: 'M9 19v-6a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2zm0 0V9a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v10m-6 0a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2m0 0V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-2a2 2 0 0 1-2-2z' },
  // { path: '/merchant/review-sentiment', label: '评价情感分析', iconViewBox: '0 0 24 24', iconPath: 'M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm-2 15l-3-3 1.41-1.41L10 14.17l5.59-5.59L17 10l-7 7z M9 9h6 M9 13h6' },
  { path: '/merchant/sentiment', label: '评论情感分析', iconViewBox: '0 0 24 24', iconPath: 'M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z' },
]

const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  localStorage.removeItem('userRole')
  localStorage.removeItem('activeMerchantId')
  router.push('/merchant')
}

onMounted(() => {
  loadStores()
})
</script>

<style scoped>
.merchant-layout {
  display: flex;
  min-height: 100vh;
  background: #f5f7fa;
}

.sidebar {
  width: 260px;
  background: linear-gradient(180deg, #2d5a27 0%, #1e3d1a 100%);
  color: #fff;
  display: flex;
  flex-direction: column;
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  z-index: 100;
  overflow: hidden;
  transition: width 0.3s ease;
}

.sidebar.collapsed {
  width: 64px;
}

.logo-section {
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  justify-content: center;
}

.logo-img {
  width: 48px;
  height: 48px;
  border-radius: 12px;
}

.logo-text {
  display: flex;
  flex-direction: column;
}

.brand-name {
  font-size: 18px;
  font-weight: 700;
  color: #fff;
}

.brand-subtitle {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
}

.sidebar-nav {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  overflow-x: hidden;
  scrollbar-width: thin;
  scrollbar-color: rgba(255, 255, 255, 0.2) transparent;
}

.sidebar-nav::-webkit-scrollbar {
  width: 4px;
}

.sidebar-nav::-webkit-scrollbar-track {
  background: transparent;
}

.sidebar-nav::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.2);
  border-radius: 2px;
}

.sidebar-nav::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.3);
}

.nav-group-title {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
  text-transform: uppercase;
  letter-spacing: 1px;
  margin-bottom: 12px;
  padding-left: 8px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  color: rgba(255, 255, 255, 0.7);
  text-decoration: none;
  border-radius: 8px;
  transition: all 0.2s;
  margin-bottom: 4px;
  justify-content: flex-start;
}

.sidebar.collapsed .nav-item {
  justify-content: center;
  padding: 12px 8px;
}

.nav-item:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.1);
}

.nav-item.active {
  color: #fff;
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
}

.sidebar-footer {
  padding: 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.logout-btn {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.05);
  color: rgba(255, 255, 255, 0.7);
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}

.logout-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
}

.main-content {
  flex: 1;
  margin-left: 260px;
  min-height: 100vh;
  transition: margin-left 0.3s ease;
}

.sidebar.collapsed + .main-content {
  margin-left: 64px;
}

.top-header {
  background: #fff;
  padding: 20px 32px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  position: sticky;
  top: 0;
  z-index: 50;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.sidebar-toggle {
  padding: 8px;
  background: #f5f7fa;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  color: #667085;
  cursor: pointer;
  transition: all 0.2s;
}

.sidebar-toggle:hover {
  background: #eef2f7;
  border-color: #52c41a;
  color: #52c41a;
}

.header-info h1 {
  font-size: 24px;
  font-weight: 700;
  color: #1f2d3d;
  margin: 0;
}

.header-info p {
  font-size: 14px;
  color: #667085;
  margin: 4px 0 0;
}

.header-user {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  color: #1f2d3d;
}

.user-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.content-wrapper {
  padding: 32px;
}

.page-content {
  flex: 1;
  min-width: 0;
}

@media (max-width: 1200px) {
  .main-content {
    margin-left: 0 !important;
  }
  
  .sidebar {
    transform: translateX(-100%);
  }
  
  .sidebar.collapsed {
    transform: translateX(0);
    width: 64px;
  }
}
</style>