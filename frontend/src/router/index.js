import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/home/HomeView.vue'
import RestaurantView from '../views/admin/RestaurantView.vue'

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
    path: '/merchant',
    name: 'merchant-login',
    component: () => import('../views/merchant/MerchantView.vue'),
  },
  {
    path: '/merchant/home',
    name: 'merchant-home',
    component: () => import('../views/merchant/MerchantView.vue'),
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
    path: '/admin/model-configs',
    name: 'model-configs',
    component: () => import('../views/admin/ModelConfigView.vue'),
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