<template>
  <div class="admin-layout">
    <aside class="sidebar">
      <div class="logo-section">
        <img src="../assets/images/greedy-cat.png" alt="食尚参谋" class="logo-img" />
        <div class="logo-text">
          <span class="brand-name">食尚参谋</span>
          <span class="brand-subtitle">管理后台</span>
        </div>
      </div>
      
      <nav class="sidebar-nav">
        <div class="nav-group">
          <span class="nav-group-title">系统管理</span>
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
          <span>退出登录</span>
        </button>
      </div>
    </aside>

    <main class="main-content">
      <header class="top-header">
        <div class="header-info">
          <h1>{{ title }}</h1>
          <p>{{ subtitle }}</p>
        </div>
        <div class="header-user">
          <div class="user-avatar">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#fff" stroke-width="2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
              <circle cx="12" cy="7" r="4"></circle>
            </svg>
          </div>
          <span>{{ userInfo.nickname || userInfo.username || '管理员' }}</span>
        </div>
      </header>

      <div class="content-wrapper" :class="{ 'has-sidebar': $slots.sidebar }">
        <aside v-if="$slots.sidebar" class="page-sidebar">
          <slot name="sidebar"></slot>
        </aside>
        <div class="page-content">
          <slot></slot>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'

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

const currentPath = computed(() => route.path)

const userInfo = ref(() => {
  const user = localStorage.getItem('user')
  return user ? JSON.parse(user) : {}
})

const navItems = [
  { path: '/admin/home', label: '系统首页', iconViewBox: '0 0 24 24', iconPath: 'M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9z M9 22l3-3 3 3' },
  { path: '/admin/dashboard', label: '运营数据', iconViewBox: '0 0 24 24', iconPath: 'M9 19v-6a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2zm0 0V9a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v10m-6 0a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2m0 0V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-2a2 2 0 0 1-2-2z' },
  { path: '/admin/merchant-statistics', label: '商家统计', iconViewBox: '0 0 24 24', iconPath: 'M9 19v-6a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2zm0 0V9a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v10m-6 0a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2m0 0V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-2a2 2 0 0 1-2-2z' },
  { path: '/admin/restaurants', label: '商家管理', iconViewBox: '0 0 24 24', iconPath: 'M8 21l1-17a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4l1 17' },
  { path: '/admin/model-configs', label: '模型配置', iconViewBox: '0 0 24 24', iconPath: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z M9 12l2 2 4-4' },
  { path: '/admin/logs', label: '审计日志', iconViewBox: '0 0 24 24', iconPath: 'M4 4h16v16H4z M8 9h8 M8 13h8 M8 17h5' },
]

const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  localStorage.removeItem('userRole')
  router.push('/admin')
}
</script>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
  background: #f5f7fa;
}

.sidebar {
  width: 260px;
  background: linear-gradient(180deg, #1f2d3d 0%, #15202b 100%);
  color: #fff;
  display: flex;
  flex-direction: column;
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  z-index: 100;
}

.logo-section {
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
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
}

.nav-item:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.1);
}

.nav-item.active {
  color: #fff;
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
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
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.content-wrapper {
  padding: 32px;
}

.content-wrapper.has-sidebar {
  display: flex;
  gap: 24px;
}

.page-sidebar {
  width: 250px;
  flex-shrink: 0;
  background: rgba(20, 20, 40, 0.85);
  backdrop-filter: blur(20px);
  border-radius: 24px;
  padding: 0;
  box-shadow: 
    0 8px 32px rgba(0, 0, 0, 0.3),
    inset 0 1px 0 rgba(255, 255, 255, 0.1);
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.1);
  position: relative;
}

.page-sidebar::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -20%;
  width: 150px;
  height: 150px;
  background: radial-gradient(circle, rgba(102, 126, 234, 0.3) 0%, transparent 70%);
  border-radius: 50%;
  pointer-events: none;
}

.page-sidebar::after {
  content: '';
  position: absolute;
  bottom: -30%;
  left: -10%;
  width: 100px;
  height: 100px;
  background: radial-gradient(circle, rgba(118, 75, 162, 0.2) 0%, transparent 70%);
  border-radius: 50%;
  pointer-events: none;
}

.page-sidebar-nav {
  padding: 28px 20px;
  position: relative;
  z-index: 1;
}

.page-sidebar-title {
  font-size: 12px;
  font-weight: 700;
  color: #ffffff;
  text-transform: uppercase;
  letter-spacing: 2px;
  margin-bottom: 24px;
  padding: 0 12px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-sidebar-title::before {
  content: '';
  width: 28px;
  height: 3px;
  background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
  border-radius: 2px;
  box-shadow: 0 0 10px rgba(102, 126, 234, 0.5);
}

.page-sidebar-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 24px;
  color: #ffffff;
  text-decoration: none;
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 12px;
  position: relative;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.15);
  width: calc(100% - 24px);
  margin-left: 12px;
}

.page-sidebar-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
  background: linear-gradient(180deg, #667eea 0%, #764ba2 100%);
  border-radius: 0 2px 2px 0;
  opacity: 0;
  transform: scaleY(0);
  transform-origin: center;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.page-sidebar-item::after {
  content: '';
  position: absolute;
  top: -50%;
  right: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle, rgba(102, 126, 234, 0.15) 0%, transparent 60%);
  opacity: 0;
  transition: opacity 0.4s ease;
}

.page-sidebar-item:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.1);
  transform: translateX(4px);
  border-color: rgba(102, 126, 234, 0.3);
  box-shadow: 
    0 4px 16px rgba(102, 126, 234, 0.15),
    inset 0 0 0 1px rgba(102, 126, 234, 0.2);
}

.page-sidebar-item:hover::before {
  opacity: 1;
  transform: scaleY(1);
}

.page-sidebar-item:hover::after {
  opacity: 1;
}

.page-sidebar-item.active {
  color: #fff;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.4) 0%, rgba(118, 75, 162, 0.3) 100%);
  border-color: rgba(102, 126, 234, 0.5);
  box-shadow: 
    0 8px 28px rgba(102, 126, 234, 0.4),
    inset 0 1px 0 rgba(255, 255, 255, 0.1);
  transform: translateX(0);
}

.page-sidebar-item.active::before {
  opacity: 1;
  transform: scaleY(1);
}

.page-sidebar-item.active::after {
  opacity: 1;
}

.page-sidebar-item svg {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.page-sidebar-item:hover svg {
  transform: scale(1.15);
}

.page-sidebar-item.active svg {
  transform: scale(1.15);
  filter: drop-shadow(0 0 10px rgba(102, 126, 234, 0.9));
}

.page-content {
  flex: 1;
  min-width: 0;
}

@media (max-width: 1200px) {
  .main-content {
    margin-left: 0;
  }
  
  .sidebar {
    display: none;
  }
  
  .content-wrapper.has-sidebar {
    flex-direction: column;
  }
  
  .page-sidebar {
    width: 100%;
  }
}
</style>
