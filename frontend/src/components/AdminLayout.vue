<template>
  <div class="admin-layout">
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="logo-section">
        <img src="../assets/images/greedy-cat.png" alt="食尚参谋" class="logo-img" />
        <div class="logo-text" v-show="!sidebarCollapsed">
          <span class="brand-name">食尚参谋</span>
          <span class="brand-subtitle">管理后台</span>
        </div>
      </div>
      
      <nav class="sidebar-nav">
        <div class="nav-group">
          <span class="nav-group-title" v-show="!sidebarCollapsed">系统管理</span>
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
            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"></path>
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

const sidebarCollapsed = ref(false)

const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

const currentPath = computed(() => route.path)

const userInfo = computed(() => {
  const user = localStorage.getItem('user')
  return user ? JSON.parse(user) : {}
})

const allNavItems = [
  { path: '/admin/home', label: '系统首页', iconViewBox: '0 0 24 24', iconPath: 'M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9z M9 22l3-3 3 3' },
  { path: '/admin/dashboard', label: '运营数据', iconViewBox: '0 0 24 24', iconPath: 'M9 19v-6a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2zm0 0V9a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v10m-6 0a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2m0 0V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-2a2 2 0 0 1-2-2z' },
  { path: '/admin/analytics', label: '数据分析', iconViewBox: '0 0 24 24', iconPath: 'M11 3v10a1 1 0 0 0 1 1h1a1 1 0 0 0 1-1V3a1 1 0 0 0-1-1h-1a1 1 0 0 0-1 1zm-5 0v10a1 1 0 0 0 1 1h1a1 1 0 0 0 1-1V3a1 1 0 0 0-1-1H7a1 1 0 0 0-1 1zm8 0v10a1 1 0 0 0 1 1h1a1 1 0 0 0 1-1V3a1 1 0 0 0-1-1h-1a1 1 0 0 0-1 1z' },
  { path: '/admin/merchant-statistics', label: '商家统计', iconViewBox: '0 0 24 24', iconPath: 'M9 19v-6a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2zm0 0V9a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v10m-6 0a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2m0 0V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-2a2 2 0 0 1-2-2z' },
  { path: '/admin/regional-hotspots', label: '区域热点', iconViewBox: '0 0 24 24', iconPath: 'M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z' },
  { path: '/admin/behavior-analysis', label: '行为分析', iconViewBox: '0 0 24 24', iconPath: 'M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0zm9-2a9 9 0 1 0-9 9M9 10H3v2h6v-2M15 10h6v2h-6v-2M9 16H3v2h6v-2M15 16h6v2h-6v-2' },
  {
    path: '/admin/recommendation-evaluations',
    label: '推荐评测',
    allowedRoles: ['ADMIN', 'OPERATOR'],
    iconViewBox: '0 0 24 24',
    iconPath:
      'M9 2h6 M10 2v5l-5 9a4 4 0 0 0 3.5 6h7a4 4 0 0 0 3.5-6l-5-9V2 M8 14h8',
  },
  {
    path: '/admin/faithfulness-test',
    label: '摘要忠实性测试',
    allowedRoles: ['ADMIN', 'OPERATOR'],
    iconViewBox: '0 0 24 24',
    iconPath:
      'M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z',
  },
  { path: '/admin/restaurants', label: '商家管理', iconViewBox: '0 0 24 24', iconPath: 'M8 21l1-17a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4l1 17' },
  { path: '/admin/diners', label: '食客管理', iconViewBox: '0 0 24 24', iconPath: 'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2 M17 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0' },
  { path: '/admin/topics', label: '专题管理', iconViewBox: '0 0 24 24', iconPath: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z M14 2v6h6M16 13H8M16 17H8M10 9H8' },
  { path: '/admin/violation-text', label: '违规文本', allowedRoles: ['ADMIN', 'OPERATOR'], iconViewBox: '0 0 24 24', iconPath: 'M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2M9 5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2M9 5h6 M9 12l2 2 4-4' },
  { path: '/admin/moderation', label: '内容审核', iconViewBox: '0 0 24 24', iconPath: 'M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3zm0 18a3 3 0 0 0-3 3v2a3 3 0 0 0 6 0v-2a3 3 0 0 0-3-3z' },
  { path: '/admin/model-configs', label: '模型配置', iconViewBox: '0 0 24 24', iconPath: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z M9 12l2 2 4-4' },
    {
    path: '/admin/prompts',
    label: '提示词管理',
    allowedRoles: ['ADMIN'],
    iconViewBox: '0 0 24 24',
    iconPath:
      'M4 4h16v16H4z M8 8h8 M8 12h8 M8 16h5',
  },
  { path: '/admin/ai-monitor', label: 'AI监控', iconViewBox: '0 0 24 24', iconPath: 'M13 2L3 14h9l-1 8 10-12h-9l1-8z M12 14l4-4' },
  { path: '/admin/logs', label: '审计日志', iconViewBox: '0 0 24 24', iconPath: 'M4 4h16v16H4z M8 9h8 M8 13h8 M8 17h5' },
  { path: '/admin/ai-traces', label: 'AI 请求追踪', allowedRoles: ['ADMIN', 'OPERATOR'], iconViewBox: '0 0 24 24', iconPath: 'M12 3a9 9 0 1 0 9 9 M12 7v5l3 2 M19 3v5h-5' },
  { path: '/admin/reports', label: '举报审核', allowedRoles: ['ADMIN', 'OPERATOR'], iconViewBox: '0 0 24 24', iconPath: 'M3 6h18 M3 12h18 M3 18h18 M8 6v12 M16 6v12' },
  { path: '/admin/sensitive-alerts', label: '敏感预警', allowedRoles: ['ADMIN', 'OPERATOR'], iconViewBox: '0 0 24 24', iconPath: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z M12 8v4 M12 16h.01' },
  { path: '/admin/analysis-feedback', label: '分析反馈', allowedRoles: ['ADMIN', 'OPERATOR'], iconViewBox: '0 0 24 24', iconPath: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z M9 15l2 2 4-4' },
  { path: '/admin/fraud-cases', label: '刷评检测', allowedRoles: ['ADMIN', 'OPERATOR'], iconViewBox: '0 0 24 24', iconPath: 'M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5' },
]

const navItems = computed(() => {
  const role = String(userInfo.value.role || '').toUpperCase()
  return allNavItems.filter(item => !item.allowedRoles || item.allowedRoles.includes(role))
})

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
  border-color: #1890ff;
  color: #1890ff;
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
  
  .content-wrapper.has-sidebar {
    flex-direction: column;
  }
  
  .page-sidebar {
    width: 100%;
  }
}
/* 穿透所有页面侧边，统一控制字体、间距 */
:deep(.page-sidebar-title) {
  font-size: 16px !important;
  margin-bottom: 24px !important;
  padding: 0 12px !important;
}

:deep(.page-sidebar-item) {
  font-size: 15px !important;
  padding: 16px 20px !important;
  margin-left: 10px !important;
  gap: 14px !important;
}

:deep(.page-sidebar-item .menu-icon) {
  font-size: 22px !important;
}

/* 全局穿透所有页面内边栏，统一样式，保留原有玻璃发光边框风格 */
:deep(.page-sidebar-title) {
  font-size: 16px !important;
  margin-bottom: 24px !important;
  padding: 0 12px !important;
  font-weight: 700 !important;
  color: rgba(15, 43, 97, 0.95) !important;
  text-transform: uppercase !important;
  letter-spacing: 1.2px !important;
  display: flex !important;
  align-items: center !important;
  gap: 20px !important;
}
:deep(.page-sidebar-title::before) {
  content: '' !important;
  width: 44px !important;
  height: 4px !important;
  background: linear-gradient(90deg, #667eea 0%, #764ba2 100%) !important;
  border-radius: 2px !important;
  box-shadow: 0 0 14px rgba(102, 126, 234, 0.5) !important;
}

:deep(.page-sidebar-items-wrapper) {
  display: flex !important;
  flex-direction: column !important;
  gap: 16px !important;
}

:deep(.page-sidebar-item) {
  font-size: 15px !important;
  padding: 16px 20px !important;
  margin-left: 10px !important;
  gap: 14px !important;
  color: rgba(110, 149, 171, 0.75) !important;
  text-decoration: none !important;
  border-radius: 20px !important;
  cursor: pointer !important;
  transition: all 0.3s ease !important;
  font-weight: 600 !important;
  position: relative !important;
  overflow: hidden !important;
  /* 保留半透背景+装饰边框 */
  background: rgba(255, 255, 255, 0.05) !important;
  border: 1px solid rgba(255, 255, 255, 0.08) !important;
  width: calc(100% - 40px) !important;
  backdrop-filter: blur(12px) !important;
}
/* 左侧渐变装饰竖条 */
:deep(.page-sidebar-item::before) {
  content: '' !important;
  position: absolute !important;
  left: 0 !important;
  top: 50% !important;
  transform: translateY(-50%) !important;
  width: 5px !important;
  height: 36px !important;
  background: linear-gradient(180deg, #667eea 0%, #764ba2 100%) !important;
  border-radius: 0 4px 4px 0 !important;
  opacity: 0 !important;
  transition: all 0.3s ease !important;
}
/* 悬浮渐变底色层 */
:deep(.page-sidebar-item::after) {
  content: '' !important;
  position: absolute !important;
  top: 0 !important;
  left: 0 !important;
  right: 0 !important;
  bottom: 0 !important;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.08) 0%, rgba(118, 75, 162, 0.06) 100%) !important;
  opacity: 0 !important;
  transition: opacity 0.3s ease !important;
}
/* hover悬浮效果，边框提亮+右移 */
:deep(.page-sidebar-item:hover) {
  color: #4e6ca0 !important;
  background: rgba(255, 255, 255, 0.08) !important;
  border-color: rgba(102, 126, 234, 0.2) !important;
  transform: translateX(4px) !important;
  box-shadow: 0 4px 20px rgba(102, 126, 234, 0.1) !important;
}
:deep(.page-sidebar-item:hover::before),
:deep(.page-sidebar-item:hover::after) {
  opacity: 1 !important;
}

/* 激活选中项：加深边框、发光、底色高亮 */
:deep(.page-sidebar-item.active) {
  color: #4e6ca0 !important;
  background: rgba(102, 126, 234, 0.15) !important;
  border-color: rgba(102, 126, 234, 0.3) !important;
  box-shadow: 
    0 8px 24px rgba(102, 126, 234, 0.25),
    inset 0 1px 0 rgba(255, 255, 255, 0.1) !important;
  transform: translateX(0) !important;
}
:deep(.page-sidebar-item.active::before),
:deep(.page-sidebar-item.active::after) {
  opacity: 1 !important;
}

/* 图标大小适配 */
:deep(.page-sidebar-item .menu-icon) {
  font-size: 22px !important;
  flex-shrink: 0 !important;
  transition: all 0.3s ease !important;
}
:deep(.page-sidebar-item:hover .menu-icon),
:deep(.page-sidebar-item.active .menu-icon) {
  transform: scale(1.15) !important;
  filter: drop-shadow(0 0 10px rgba(102, 126, 234, 0.7)) !important;
}
</style>
