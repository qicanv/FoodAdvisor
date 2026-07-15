import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import RestaurantView from '../views/RestaurantView.vue'

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
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router