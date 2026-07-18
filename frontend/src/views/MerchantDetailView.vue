<template>
  <main class="merchant-detail">
    <el-page-header content="商家详情" @back="router.push('/restaurants')" />

    <section v-loading="loading" class="detail-layout">
      <el-card class="merchant-panel">
        <template #header>
          <div class="merchant-title">
            <div>
              <h1>{{ merchant?.merchantName || '商家详情' }}</h1>
              <p>{{ merchant?.category || '-' }} · {{ merchant?.address || '-' }}</p>
            </div>
            <el-tag :type="canReview ? 'success' : 'info'">
              {{ canReview ? '营业中' : '不可评价' }}
            </el-tag>
          </div>
        </template>

        <div class="rating-grid">
          <div>
            <strong>{{ formatRating(merchant?.averageRating || merchant?.rating) }}</strong>
            <span>综合评分</span>
          </div>
          <div>
            <strong>{{ formatRating(merchant?.averageTasteRating) }}</strong>
            <span>口味</span>
          </div>
          <div>
            <strong>{{ formatRating(merchant?.averageEnvironmentRating) }}</strong>
            <span>环境</span>
          </div>
          <div>
            <strong>{{ formatRating(merchant?.averageServiceRating) }}</strong>
            <span>服务</span>
          </div>
          <div>
            <strong>{{ merchant?.ratingCount || 0 }}</strong>
            <span>评分人数</span>
          </div>
        </div>
      </el-card>

      <el-card class="review-panel">
        <template #header>
          <div class="card-header">
            <span>发表评价</span>
            <el-tag v-if="existingImages.length">{{ existingImages.length }} 张已保存图片</el-tag>
          </div>
        </template>

        <el-alert
          v-if="!isLoggedIn"
          title="请先登录后再发表评价"
          type="warning"
          show-icon
          :closable="false"
        />
        <el-alert
          v-else-if="!canReview"
          title="商家停业或禁用后不可新增评价，历史评价仍会保留。"
          type="info"
          show-icon
          :closable="false"
        />

        <el-form label-position="top" class="review-form" @submit.prevent>
          <el-form-item label="评价正文">
            <el-input
              v-model="form.content"
              type="textarea"
              :autosize="{ minRows: 5, maxRows: 9 }"
              maxlength="2000"
              show-word-limit
              placeholder="写下真实的消费体验，至少 10 个字符"
              :disabled="!formEnabled"
            />
          </el-form-item>

          <div class="score-grid">
            <el-form-item label="综合评分">
              <el-rate v-model="form.rating" :disabled="!formEnabled" />
            </el-form-item>
            <el-form-item label="口味">
              <el-rate v-model="form.tasteRating" clearable :disabled="!formEnabled" />
            </el-form-item>
            <el-form-item label="环境">
              <el-rate v-model="form.environmentRating" clearable :disabled="!formEnabled" />
            </el-form-item>
            <el-form-item label="服务">
              <el-rate v-model="form.serviceRating" clearable :disabled="!formEnabled" />
            </el-form-item>
          </div>

          <div class="meta-grid">
            <el-form-item label="人均消费">
              <el-input-number
                v-model="form.averageSpend"
                :min="0"
                :precision="2"
                :disabled="!formEnabled"
                controls-position="right"
              />
            </el-form-item>
            <el-form-item label="消费日期">
              <el-date-picker
                v-model="form.consumptionDate"
                type="date"
                value-format="YYYY-MM-DD"
                :disabled="!formEnabled"
              />
            </el-form-item>
            <el-form-item label="标签">
              <el-input
                v-model="form.tags"
                placeholder="朋友聚餐、性价比高"
                :disabled="!formEnabled"
              />
            </el-form-item>
          </div>

          <el-form-item label="图片">
            <div class="image-tools">
              <el-button
                :disabled="!formEnabled || totalImageCount >= 9"
                @click="fileInput?.click()"
              >
                选择图片
              </el-button>
              <span>{{ totalImageCount }}/9，支持 JPEG、PNG、WebP，单张不超过 10MB</span>
              <input
                ref="fileInput"
                class="hidden-input"
                type="file"
                multiple
                accept="image/jpeg,image/png,image/webp"
                @change="handleFiles"
              />
            </div>
          </el-form-item>

          <div v-if="imageItems.length" class="image-grid">
            <div v-for="(item, index) in imageItems" :key="item.key" class="image-item">
              <img :src="item.url" alt="评价图片预览" />
              <div class="image-actions">
                <el-button size="small" text :disabled="index === 0" @click="moveImage(index, -1)">上移</el-button>
                <el-button size="small" text :disabled="index === imageItems.length - 1" @click="moveImage(index, 1)">下移</el-button>
                <el-button size="small" text type="danger" @click="removeImage(index)">删除</el-button>
              </div>
            </div>
          </div>

          <el-alert
            v-if="errorMessage"
            class="form-alert"
            type="error"
            :title="errorMessage"
            show-icon
          />
          <el-alert
            v-if="successMessage"
            class="form-alert"
            type="success"
            :title="successMessage"
            show-icon
          />

          <div class="form-actions">
            <el-button type="primary" :loading="submitting" :disabled="!formEnabled" @click="submitReview">
              提交评价
            </el-button>
          </div>
        </el-form>
      </el-card>
    </section>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getMerchantDetail } from '../api/restaurant'
import { submitMerchantReview } from '../api/review'

const route = useRoute()
const router = useRouter()
const merchant = ref(null)
const loading = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const fileInput = ref(null)
const existingImages = ref([])
const newImages = ref([])

const form = reactive({
  content: '',
  rating: 0,
  tasteRating: 0,
  environmentRating: 0,
  serviceRating: 0,
  averageSpend: null,
  consumptionDate: '',
  tags: '',
})

const currentUser = computed(() => {
  const raw = localStorage.getItem('userInfo') || localStorage.getItem('user')
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
})

const isLoggedIn = computed(() => Boolean(currentUser.value || localStorage.getItem('accessToken') || localStorage.getItem('token')))
const currentUserId = computed(() => currentUser.value?.id || currentUser.value?.userId || localStorage.getItem('userId') || 1)
const canReview = computed(() => merchant.value?.platformStatus === 'ACTIVE' && merchant.value?.businessStatus === 'OPERATING')
const formEnabled = computed(() => isLoggedIn.value && canReview.value && !submitting.value)
const totalImageCount = computed(() => existingImages.value.length + newImages.value.length)
const imageItems = computed(() => [
  ...existingImages.value.map(image => ({
    key: `existing-${image.id}`,
    type: 'existing',
    id: image.id,
    url: image.thumbnailUrl || image.imageUrl,
  })),
  ...newImages.value.map(image => ({
    key: image.key,
    type: 'new',
    url: image.previewUrl,
  })),
])

const loadMerchant = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await getMerchantDetail(route.params.id)
    if (!response.success) {
      throw new Error(response.message || '商家详情加载失败')
    }
    merchant.value = response.data
  } catch (error) {
    errorMessage.value = error.message || '商家详情加载失败'
  } finally {
    loading.value = false
  }
}

const formatRating = value => {
  if (value === null || value === undefined || value === '') return '-'
  return Number(value).toFixed(1)
}

const handleFiles = event => {
  errorMessage.value = ''
  const files = Array.from(event.target.files || [])
  for (const file of files) {
    if (totalImageCount.value >= 9) break
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      errorMessage.value = '仅支持 JPEG、PNG、WebP 图片'
      continue
    }
    if (file.size > 10 * 1024 * 1024) {
      errorMessage.value = '单张图片不能超过 10MB'
      continue
    }
    newImages.value.push({
      key: `${file.name}-${file.lastModified}-${Math.random()}`,
      file,
      previewUrl: URL.createObjectURL(file),
    })
  }
  event.target.value = ''
}

const moveImage = (index, offset) => {
  const items = imageItems.value
  const nextIndex = index + offset
  const current = items[index]
  const next = items[nextIndex]
  if (!current || !next) return

  const source = current.type === 'existing' ? existingImages.value : newImages.value
  const target = next.type === 'existing' ? existingImages.value : newImages.value
  if (source !== target) {
    return
  }
  const innerIndex = source.findIndex(item => (current.type === 'existing' ? item.id === current.id : item.key === current.key))
  const moved = source.splice(innerIndex, 1)[0]
  source.splice(innerIndex + offset, 0, moved)
}

const removeImage = index => {
  const item = imageItems.value[index]
  if (!item) return
  if (item.type === 'existing') {
    existingImages.value = existingImages.value.filter(image => image.id !== item.id)
    return
  }
  const removed = newImages.value.find(image => image.key === item.key)
  if (removed) URL.revokeObjectURL(removed.previewUrl)
  newImages.value = newImages.value.filter(image => image.key !== item.key)
}

const validateForm = () => {
  const contentLength = form.content.trim().length
  if (contentLength < 10) return '评价正文至少 10 个字符'
  if (contentLength > 2000) return '评价正文不能超过 2000 个字符'
  if (!form.rating) return '请选择综合评分'
  return ''
}

const appendOptional = (data, key, value) => {
  if (value !== null && value !== undefined && value !== '' && value !== 0) {
    data.append(key, value)
  }
}

const submitReview = async () => {
  errorMessage.value = validateForm()
  successMessage.value = ''
  if (errorMessage.value) return

  submitting.value = true
  try {
    const data = new FormData()
    data.append('content', form.content.trim())
    data.append('rating', form.rating)
    appendOptional(data, 'tasteRating', form.tasteRating)
    appendOptional(data, 'environmentRating', form.environmentRating)
    appendOptional(data, 'serviceRating', form.serviceRating)
    appendOptional(data, 'averageSpend', form.averageSpend)
    appendOptional(data, 'consumptionDate', form.consumptionDate)
    appendOptional(data, 'tags', form.tags.trim())
    existingImages.value.forEach(image => data.append('keepImageIds', image.id))
    newImages.value.forEach(image => data.append('images', image.file))

    const response = await submitMerchantReview(route.params.id, data)
    if (!response.success) {
      throw new Error(response.message || '评价提交失败')
    }

    existingImages.value = response.data.images || []
    newImages.value.forEach(image => URL.revokeObjectURL(image.previewUrl))
    newImages.value = []
    successMessage.value = response.data.status === 'PENDING'
      ? '评价已提交，正在等待审核。'
      : '评价已发布。'
    await loadMerchant()
  } catch (error) {
    errorMessage.value = error.message || '评价提交失败'
  } finally {
    submitting.value = false
  }
}

onMounted(loadMerchant)
onBeforeUnmount(() => {
  newImages.value.forEach(image => URL.revokeObjectURL(image.previewUrl))
})
</script>

<style scoped>
.merchant-detail {
  max-width: 1180px;
  margin: 32px auto;
  padding: 0 20px 48px;
}

.detail-layout {
  display: grid;
  grid-template-columns: 1fr;
  gap: 20px;
  margin-top: 20px;
}

.merchant-title,
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.merchant-title h1 {
  margin: 0 0 4px;
  font-size: 24px;
  letter-spacing: 0;
}

.merchant-title p {
  margin: 0;
  color: #666666;
}

.rating-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(120px, 1fr));
  gap: 12px;
}

.rating-grid div {
  padding: 14px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fafafa;
}

.rating-grid strong {
  display: block;
  font-size: 22px;
  color: #ff6700;
}

.rating-grid span {
  color: #666666;
  font-size: 13px;
}

.review-form {
  margin-top: 8px;
}

.score-grid,
.meta-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(150px, 1fr));
  gap: 16px;
}

.meta-grid {
  grid-template-columns: repeat(3, minmax(180px, 1fr));
}

.image-tools {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  color: #666666;
  font-size: 13px;
}

.hidden-input {
  display: none;
}

.image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.image-item {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  overflow: hidden;
  background: #ffffff;
}

.image-item img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  display: block;
}

.image-actions {
  display: flex;
  justify-content: space-around;
  padding: 6px;
}

.form-alert {
  margin-bottom: 14px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 820px) {
  .rating-grid,
  .score-grid,
  .meta-grid {
    grid-template-columns: 1fr;
  }
}
</style>
