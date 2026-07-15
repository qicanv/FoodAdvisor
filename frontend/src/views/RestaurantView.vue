<template>
  <main class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>餐厅列表</span>
          <el-button @click="loadRestaurants">刷新</el-button>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="restaurants"
        empty-text="暂无餐厅数据"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="餐厅名称" />
        <el-table-column prop="category" label="类型" />
        <el-table-column prop="address" label="地址" />
        <el-table-column prop="averagePrice" label="人均价格" />
        <el-table-column prop="rating" label="评分" />
      </el-table>

      <el-alert
        v-if="errorMessage"
        class="error-alert"
        type="error"
        :title="errorMessage"
        show-icon
      />
    </el-card>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getRestaurants } from '../api/restaurant'

const restaurants = ref([])
const loading = ref(false)
const errorMessage = ref('')

const loadRestaurants = async () => {
  loading.value = true
  errorMessage.value = ''

  try {
    const response = await getRestaurants()
    restaurants.value = response.data
  } catch (error) {
    errorMessage.value = '无法连接后端服务，请确认 Spring Boot 已启动。'
    console.error(error)
  } finally {
    loading.value = false
  }
}

onMounted(loadRestaurants)
</script>

<style scoped>
.page-container {
  max-width: 1100px;
  margin: 40px auto;
  padding: 0 20px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.error-alert {
  margin-top: 20px;
}
</style>