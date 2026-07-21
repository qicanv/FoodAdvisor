import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/home/HomeView.vue'

const routes = [
  {
    path: '/',
    name: 'home',
    component: HomeView,
  },
  {
    path: '/diner',
    name: 'diner-login',
    component: () => import('../views/diner/DinerView.vue'),
  },
  {
    path: '/diner/home',
    name: 'diner-home',
    component: () => import('../views/diner/DinerHomeView.vue'),
    meta: { requiresAuth: true, role: 'diner' },
  },
  {
    path: '/diner/profile',
    name: 'diner-profile',
    component: () => import('../views/diner/ProfileView.vue'),
    meta: { requiresAuth: true, role: 'diner' },
  },
  {
    path: '/diner/my-reviews',
    name: 'diner-my-reviews',
    component: () => import('../views/diner/MyReviewsView.vue'),
    meta: { requiresAuth: true, role: 'diner' },
  },
  {
    path: '/diner/my-reviews/:id',
    name: 'diner-my-review-detail',
    component: () => import('../views/diner/MyReviewDetailView.vue'),
    meta: { requiresAuth: true, role: 'diner' },
  },
  {
    path: '/diner/review/edit/:id',
    name: 'diner-review-edit',
    component: () => import('../views/diner/ReviewEditView.vue'),
    meta: { requiresAuth: true, role: 'diner' },
  },
  {
    path: '/diner/notifications',
    name: 'diner-notifications',
    component: () => import('../views/diner/NotificationView.vue'),
    meta: { requiresAuth: true, role: 'diner' },
  },
  {
    path: '/diner/my-reports',
    name: 'diner-my-reports',
    component: () => import('../views/diner/MyReportsView.vue'),
    meta: { requiresAuth: true, role: 'diner' },
  },
  {
    path: '/diner/ranking',
    name: 'diner-ranking',
    component: () => import('../views/diner/HotRankingView.vue'),
    meta: { requiresAuth: true, role: 'diner' },
  },
  {
    path: '/diner/ai-dining',
    name: 'diner-ai-dining',
    component: () => import('../views/diner/AiDiningView.vue'),
    meta: { requiresAuth: true, role: 'diner' },
  },
  {
    path: '/diner/merchant/:id',
    name: 'diner-merchant-detail',
    component: () => import('../views/diner/MerchantDetailView.vue'),
    meta: { requiresAuth: true, role: 'diner' },
  },
  {
    path: '/merchant',
    name: 'merchant-login',
    component: () => import('../views/merchant/MerchantView.vue'),
  },
  {
    path: '/merchant/home',
    name: 'merchant-home',
    component: () => import('../views/merchant/MerchantHomeView.vue'),
    meta: { requiresAuth: true, role: 'merchant' },
  },
  {
    path: '/merchant/dishes',
    name: 'merchant-dishes',
    component: () => import('../views/merchant/MerchantDishesView.vue'),
    meta: { requiresAuth: true, role: 'merchant' },
  },
  {
    path: '/merchant/statistics',
    name: 'merchant-statistics',
    component: () => import('../views/merchant/MerchantHomeView.vue'),
    meta: { requiresAuth: true, role: 'merchant' },
  },
  {
    path: '/admin',
    name: 'admin-login',
    component: () => import('../views/admin/LoginView.vue'),
  },
  {
    path: '/admin/home',
    name: 'admin-home',
    component: () => import('../views/admin/DashboardView.vue'),
    meta: { requiresAuth: true, role: 'admin' },
  },
  {
    path: '/admin/restaurants',
    name: 'admin-restaurants',
    component: () => import('../views/admin/RestaurantView.vue'),
    meta: { requiresAuth: true, role: 'admin' },
  },
  {
    path: '/admin/diners',
    name: 'admin-diners',
    component: () => import('../views/admin/AdminDinerView.vue'),
    meta: { requiresAuth: true, role: 'admin' },
  },
  {
    path: '/admin/topics',
    name: 'admin-topics',
    component: () => import('../views/admin/TopicManagement.vue'),
    meta: { requiresAuth: true, role: 'admin' },
  },
  {
    path: '/admin/model-configs',
    name: 'model-configs',
    component: () => import('../views/admin/ModelConfigView.vue'),
    meta: { requiresAuth: true, role: 'admin' },
  },
  {
    path: '/admin/dashboard',
    name: 'admin-dashboard',
    component: () => import('../views/admin/OperationsDashboard.vue'),
    meta: { requiresAuth: true, role: 'admin' },
  },
  {
    path: '/admin/logs',
    name: 'admin-logs',
    component: () => import('../views/admin/AuditLogView.vue'),
    meta: { requiresAuth: true, role: 'admin' },
  },
  {
    path: '/admin/ai-traces',
    name: 'admin-ai-traces',
    component: () => import('../views/admin/AiTraceView.vue'),
    meta: { requiresAuth: true, role: 'admin', allowedRoles: ['ADMIN', 'OPERATOR'] },
  },
  {
    path: '/admin/merchant-statistics',
    name: 'admin-merchant-statistics',
    component: () => import('../views/admin/MerchantStatistics.vue'),
    meta: { requiresAuth: true, role: 'admin' },
  },
  {
    path: '/admin/analytics',
    name: 'admin-analytics',
    component: () => import('../views/admin/AnalyticsView.vue'),
    meta: { requiresAuth: true, role: 'admin' },
  },
  {
    path: '/admin/regional-hotspots',
    name: 'admin-regional-hotspots',
    component: () => import('../views/admin/RegionalHotspotView.vue'),
    meta: { requiresAuth: true, role: 'admin' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  const userRole = localStorage.getItem('userRole')
  let authenticatedRole = ''
  try {
    authenticatedRole = String(JSON.parse(localStorage.getItem('user') || '{}').role || '').toUpperCase()
  } catch {
    authenticatedRole = ''
  }

  if (to.meta.requiresAuth) {
    if (!token) {
      const role = to.meta.role
      next({ path: `/${role}` })
      return
    }

    if (userRole && userRole !== to.meta.role) {
      next({ path: `/${userRole}/home` })
      return
    }

    if (to.meta.allowedRoles && !to.meta.allowedRoles.includes(authenticatedRole)) {
      next({ path: userRole === 'admin' ? '/admin/home' : '/' })
      return
    }

    next()
    return
  }

  if (token && userRole && to.path === `/${userRole}`) {
    next({ path: `/${userRole}/home` })
    return
  }

  next()
})

export default router
