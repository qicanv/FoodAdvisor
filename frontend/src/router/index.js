import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import RestaurantView from '../views/RestaurantView.vue'
import ModelConfigView from '../views/ModelConfigView.vue'

const routes = [
  {
    path: '/',
    name: 'home',
    component: HomeView,
  },
  {
    path: '/restaurants',
    name: 'restaurants',
    component: RestaurantView,
  },
  {
    path: '/diner',
    name: 'diner',
    component: () => import('../views/DinerView.vue'),
  },
  {
    path: '/merchant',
    name: 'merchant',
    component: () => import('../views/MerchantView.vue'),
  },
  {
    path: '/admin',
    name: 'admin',
    component: () => import('../views/AdminView.vue'),
    path: '/admin/model-configs',
    name: 'model-configs',
    component: ModelConfigView,
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
