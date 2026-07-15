<template>
  <main class="page-container">
    <el-card>
      <h1>FoodAdvisor</h1>
      <p>智能餐厅推荐系统前端服务已成功启动。</p>

      <el-button type="primary" @click="goToRestaurants">
        查看餐厅列表
      </el-button>
    </el-card>
  </main>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const goToRestaurants = () => {
  router.push('/restaurants')
}
</script>

<style scoped>
.page-container {
  max-width: 960px;
  margin: 40px auto;
  padding: 0 20px;
}
</style>