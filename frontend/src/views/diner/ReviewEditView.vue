<template>
  <div class="review-edit-view">
    <nav class="diner-nav">
      <div class="nav-container">
        <div class="logo-section">
          <img src="../../assets/images/greedy-cat.png" alt="食尚参谋" class="logo-img" />
          <span class="brand-name">食尚参谋 - 编辑评价</span>
        </div>
        <div class="nav-links">
          <button class="back-btn" @click="goBack">← 返回评价详情</button>
        </div>
      </div>
    </nav>

    <main class="edit-main">
      <div class="container" v-if="review">
        <div class="edit-card">
          <h2 class="edit-title">编辑评价</h2>

          <div class="form-group">
            <label class="form-label">商家</label>
            <div class="merchant-info">{{ review.merchantName }}</div>
          </div>

          <div class="form-group">
            <label class="form-label">评分</label>
            <div class="rating-input">
              <button
                v-for="i in 5"
                :key="i"
                class="star-btn"
                :class="{ active: i <= Math.round(formData.rating || 0) }"
                @click="formData.rating = i"
              >
                {{ i <= Math.round(formData.rating || 0) ? '⭐' : '☆' }}
              </button>
              <span class="rating-value">{{ formData.rating || 0 }}分</span>
            </div>
          </div>

          <div class="form-group">
            <label class="form-label">评价内容</label>
            <textarea
              v-model="formData.content"
              class="content-textarea"
              placeholder="请输入评价内容（至少10字）"
              maxlength="2000"
            ></textarea>
            <span class="char-count">{{ formData.content.length }}/2000</span>
          </div>

          <div class="form-actions">
            <button class="action-btn cancel" @click="goBack">取消</button>
            <button class="action-btn submit" @click="handleSubmit">保存修改</button>
          </div>
        </div>
      </div>

      <div v-else class="loading-state">
        <span class="loading-icon">⏳</span>
        <p>加载中...</p>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import request from '../../api/request'

const router = useRouter()
const route = useRoute()
const review = ref(null)

const formData = reactive({
  rating: 0,
  content: ''
})

const loadReview = async () => {
  const reviewId = route.params.id
  if (!reviewId) return

  try {
    const response = await request.get(`/api/reviews/my-reviews/${reviewId}`)
    if (response.success && response.data) {
      review.value = response.data
      formData.rating = response.data.rating || 0
      formData.content = response.data.content || ''
    }
  } catch (error) {
    console.error('获取评价详情失败:', error)
    alert('获取评价详情失败')
    goBack()
  }
}

const goBack = () => {
  router.push(`/diner/my-reviews/${route.params.id}`)
}

const handleSubmit = () => {
  if (!formData.rating || formData.rating < 1 || formData.rating > 5) {
    alert('请选择评分（1-5星）')
    return
  }
  if (!formData.content || formData.content.length < 10) {
    alert('评价内容至少10字')
    return
  }

  request.put(`/api/reviews/${route.params.id}/simple`, {
    rating: formData.rating,
    content: formData.content
  })
    .then(response => {
      if (response.success) {
        alert('评价修改成功')
        goBack()
      } else {
        alert(response.message || '修改失败')
      }
    })
    .catch(error => {
      console.error('修改评价失败:', error)
      alert('修改失败，请重试')
    })
}

onMounted(() => {
  loadReview()
})
</script>

<style scoped>
.review-edit-view {
  min-height: 100vh;
  background: #f5f7fa;
}

.container {
  max-width: 800px;
  margin: 0 auto;
  padding: 0 24px;
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
  max-width: 800px;
  margin: 0 auto;
  padding: 0 24px;
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

.back-btn {
  padding: 8px 16px;
  background: #fff5f0;
  color: #ff6700;
  border: 1px solid #ffccb3;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
}

.edit-main {
  padding-top: 80px;
  padding-bottom: 40px;
}

.edit-card {
  background: #fff;
  border-radius: 16px;
  padding: 30px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
}

.edit-title {
  font-size: 24px;
  font-weight: 700;
  color: #333;
  margin: 0 0 30px 0;
  padding-bottom: 20px;
  border-bottom: 1px solid #f0f0f0;
}

.form-group {
  margin-bottom: 24px;
}

.form-label {
  display: block;
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin-bottom: 12px;
}

.merchant-info {
  font-size: 16px;
  color: #666;
  padding: 12px;
  background: #fafafa;
  border-radius: 8px;
}

.rating-input {
  display: flex;
  align-items: center;
  gap: 8px;
}

.star-btn {
  font-size: 32px;
  background: none;
  border: none;
  cursor: pointer;
  transition: transform 0.2s;
}

.star-btn:hover {
  transform: scale(1.2);
}

.rating-value {
  margin-left: 16px;
  font-size: 18px;
  font-weight: 600;
  color: #ff6700;
}

.content-textarea {
  width: 100%;
  min-height: 200px;
  padding: 16px;
  border: 1px solid #e8e8e8;
  border-radius: 10px;
  font-size: 15px;
  line-height: 1.8;
  resize: vertical;
  box-sizing: border-box;
}

.content-textarea:focus {
  outline: none;
  border-color: #ff6700;
}

.char-count {
  display: block;
  text-align: right;
  font-size: 13px;
  color: #999;
  margin-top: 8px;
}

.form-actions {
  display: flex;
  gap: 16px;
  padding-top: 20px;
  border-top: 1px solid #f0f0f0;
}

.action-btn {
  flex: 1;
  padding: 14px 20px;
  border-radius: 10px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  border: none;
  transition: all 0.2s;
}

.action-btn.cancel {
  background: #f5f5f5;
  color: #666;
}

.action-btn.cancel:hover {
  background: #e8e8e8;
}

.action-btn.submit {
  background: #ff6700;
  color: #fff;
}

.action-btn.submit:hover {
  background: #e55a00;
}

.loading-state {
  text-align: center;
  padding: 100px 20px;
}

.loading-icon {
  font-size: 48px;
  display: block;
  margin-bottom: 16px;
}

.loading-state p {
  font-size: 16px;
  color: #999;
}
</style>