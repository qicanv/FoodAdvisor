<template>
  <MerchantLayout title="菜品管理" subtitle="管理店铺菜单、菜品和价格信息">
    <div class="dishes-container">
      <div class="search-bar">
        <div class="search-group">
          <input 
            type="text" 
            v-model="searchKeyword" 
            placeholder="搜索菜品名称" 
            class="search-input"
            @keyup.enter="loadDishes"
          />
        </div>
        <div class="status-filter">
          <select v-model="statusFilter" class="filter-select" @change="loadDishes">
            <option value="">全部状态</option>
            <option value="ACTIVE">上架中</option>
            <option value="OFF_SHELF">已下架</option>
          </select>
        </div>
        <button class="add-btn" @click="showAddModal = true">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="#fff" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"></line>
            <line x1="5" y1="12" x2="19" y2="12"></line>
          </svg>
          <span>新增菜品</span>
        </button>
      </div>

      <div class="stats-bar">
        <div class="stat-item">
          <span class="stat-value">{{ dishes.length }}</span>
          <span class="stat-label">全部菜品</span>
        </div>
        <div class="stat-item">
          <span class="stat-value">{{ activeCount }}</span>
          <span class="stat-label">上架中</span>
        </div>
        <div class="stat-item">
          <span class="stat-value">{{ offShelfCount }}</span>
          <span class="stat-label">已下架</span>
        </div>
      </div>

      <div v-if="dishes.length > 0" class="dishes-table">
        <table>
          <thead>
            <tr>
              <th>菜品名称</th>
              <th>分类</th>
              <th>价格</th>
              <th>口味标签</th>
              <th>推荐状态</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="dish in filteredDishes" :key="dish.id">
              <td>
                <div class="dish-name-cell">
                  <img v-if="dish.imageUrl" :src="dish.imageUrl" alt="" class="dish-thumb" />
                  <span>{{ dish.name }}</span>
                </div>
              </td>
              <td>{{ dish.category || '-' }}</td>
              <td class="price-cell">¥{{ dish.price }}</td>
              <td>
                <div class="tags-container">
                  <span v-for="tag in getTasteTags(dish)" :key="tag" class="taste-tag">{{ tag }}</span>
                </div>
              </td>
              <td>
                <span :class="['recommend-badge', { recommended: dish.recommended }]">
                  {{ dish.recommended ? '推荐' : '普通' }}
                </span>
              </td>
              <td>
                <span :class="['status-badge', dish.status.toLowerCase()]">
                  {{ dish.status === 'ACTIVE' ? '上架中' : '已下架' }}
                </span>
              </td>
              <td>
                <div class="action-buttons">
                  <button class="action-btn edit-btn" @click="editDish(dish)">
                    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="#666" stroke-width="2">
                      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                      <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
                    </svg>
                  </button>
                  <button 
                    v-if="dish.status === 'ACTIVE'" 
                    class="action-btn shelf-btn" 
                    @click="toggleStatus(dish, 'OFF_SHELF')"
                  >
                    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="#faad14" stroke-width="2">
                      <path d="M12 21a9 9 0 1 1 0-18 9 9 0 0 1 0 18z"></path>
                      <path d="M12 9v6"></path>
                      <path d="M12 8h.01"></path>
                    </svg>
                  </button>
                  <button 
                    v-else 
                    class="action-btn unshelf-btn" 
                    @click="toggleStatus(dish, 'ACTIVE')"
                  >
                    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="#52c41a" stroke-width="2">
                      <path d="M12 21a9 9 0 1 1 0-18 9 9 0 0 1 0 18z"></path>
                      <path d="M12 9v6"></path>
                      <path d="M12 8h.01"></path>
                    </svg>
                  </button>
                  <button class="action-btn delete-btn" @click="deleteDish(dish)">
                    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="#ff4d4f" stroke-width="2">
                      <path d="M3 6h18"></path>
                      <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"></path>
                      <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"></path>
                    </svg>
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else class="empty-state">
        <svg viewBox="0 0 24 24" width="64" height="64" fill="none" stroke="#ccc" stroke-width="2">
          <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path>
          <circle cx="12" cy="10" r="3"></circle>
        </svg>
        <p>暂无菜品</p>
        <button class="add-btn" @click="showAddModal = true">新增菜品</button>
      </div>

      <div v-if="showAddModal || showEditModal" class="modal-overlay" @click.self="closeModal">
        <div class="modal-content">
          <div class="modal-header">
            <h3>{{ showEditModal ? '编辑菜品' : '新增菜品' }}</h3>
            <button class="close-btn" @click="closeModal">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="#999" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"></line>
                <line x1="6" y1="6" x2="18" y2="18"></line>
              </svg>
            </button>
          </div>
          
          <form @submit.prevent="saveDish" class="dish-form">
            <div class="form-row">
              <div class="form-group">
                <label>菜品名称 <span class="required">*</span></label>
                <input 
                  type="text" 
                  v-model="formData.name" 
                  placeholder="请输入菜品名称" 
                  class="form-input"
                  :class="{ error: formErrors.name }"
                />
                <span v-if="formErrors.name" class="error-text">{{ formErrors.name }}</span>
              </div>
              <div class="form-group">
                <label>分类</label>
                <input 
                  type="text" 
                  v-model="formData.category" 
                  placeholder="请输入菜品分类" 
                  class="form-input"
                />
              </div>
            </div>

            <div class="form-row">
              <div class="form-group">
                <label>价格 <span class="required">*</span></label>
                <input 
                  type="number" 
                  v-model="formData.price" 
                  placeholder="请输入菜品价格" 
                  class="form-input"
                  :class="{ error: formErrors.price }"
                  step="0.01"
                  min="0"
                  max="99999.99"
                />
                <span v-if="formErrors.price" class="error-text">{{ formErrors.price }}</span>
              </div>
              <div class="form-group">
                <label>图片地址</label>
                <input 
                  type="text" 
                  v-model="formData.imageUrl" 
                  placeholder="请输入图片URL" 
                  class="form-input"
                />
              </div>
            </div>

            <div class="form-group">
              <label>口味标签</label>
              <div class="tags-input">
                <div class="tag-item" v-for="(tag, index) in formData.tasteTags" :key="index">
                  <span>{{ tag }}</span>
                  <button type="button" @click="removeTag(index)">×</button>
                </div>
                <input 
                  type="text" 
                  v-model="newTag" 
                  placeholder="输入标签后按回车添加" 
                  class="tag-input"
                  @keyup.enter="addTag"
                />
              </div>
            </div>

            <div class="form-group">
              <label>菜品描述</label>
              <textarea 
                v-model="formData.description" 
                placeholder="请输入菜品描述" 
                class="form-textarea"
                rows="4"
              ></textarea>
            </div>

            <div class="form-row">
              <div class="form-group checkbox-group">
                <label class="checkbox-label">
                  <input type="checkbox" v-model="formData.recommended" />
                  <span>设为推荐菜品</span>
                </label>
              </div>
              <div class="form-group">
                <label>状态</label>
                <select v-model="formData.status" class="form-select">
                  <option value="ACTIVE">上架中</option>
                  <option value="OFF_SHELF">已下架</option>
                </select>
              </div>
            </div>

            <div class="form-actions">
              <button type="button" class="cancel-btn" @click="closeModal">取消</button>
              <button type="submit" class="submit-btn" :disabled="loading">
                {{ loading ? '保存中...' : '保存' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  </MerchantLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import MerchantLayout from '../../components/MerchantLayout.vue'
import request from '../../api/request'

const dishes = ref([])
const searchKeyword = ref('')
const statusFilter = ref('')
const showAddModal = ref(false)
const showEditModal = ref(false)
const loading = ref(false)
const editingDish = ref(null)

const formData = ref({
  name: '',
  category: '',
  price: '',
  description: '',
  tasteTags: [],
  imageUrl: '',
  recommended: false,
  status: 'ACTIVE'
})

const newTag = ref('')

const formErrors = ref({
  name: '',
  price: ''
})

const activeCount = computed(() => dishes.value.filter(d => d.status === 'ACTIVE').length)
const offShelfCount = computed(() => dishes.value.filter(d => d.status === 'OFF_SHELF').length)

const filteredDishes = computed(() => {
  let result = dishes.value
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    result = result.filter(d => d.name.toLowerCase().includes(keyword))
  }
  if (statusFilter.value) {
    result = result.filter(d => d.status === statusFilter.value)
  }
  return result
})

const loadDishes = async () => {
  try {
    const params = {}
    if (statusFilter.value) {
      params.status = statusFilter.value
    }
    const response = await request.get('/api/merchant-dishes', { params })
    if (response.success) {
      dishes.value = response.data
    }
  } catch (error) {
    console.error('加载菜品失败:', error)
  }
}

const getTasteTags = (dish) => {
  if (!dish.tasteTags) return []
  try {
    return JSON.parse(dish.tasteTags)
  } catch {
    return []
  }
}

const addTag = () => {
  const tag = newTag.value.trim()
  if (tag && !formData.value.tasteTags.includes(tag)) {
    formData.value.tasteTags.push(tag)
    newTag.value = ''
  }
}

const removeTag = (index) => {
  formData.value.tasteTags.splice(index, 1)
}

const editDish = (dish) => {
  editingDish.value = dish
  formData.value = {
    name: dish.name,
    category: dish.category || '',
    price: dish.price.toString(),
    description: dish.description || '',
    tasteTags: getTasteTags(dish),
    imageUrl: dish.imageUrl || '',
    recommended: dish.recommended || false,
    status: dish.status || 'ACTIVE'
  }
  showEditModal.value = true
}

const closeModal = () => {
  showAddModal.value = false
  showEditModal.value = false
  editingDish.value = null
  formData.value = {
    name: '',
    category: '',
    price: '',
    description: '',
    tasteTags: [],
    imageUrl: '',
    recommended: false,
    status: 'ACTIVE'
  }
  formErrors.value = { name: '', price: '' }
}

const validateForm = () => {
  formErrors.value = { name: '', price: '' }
  let valid = true

  if (!formData.value.name.trim()) {
    formErrors.value.name = '菜品名称不能为空'
    valid = false
  } else if (formData.value.name.length > 100) {
    formErrors.value.name = '菜品名称长度不能超过100个字符'
    valid = false
  }

  if (!formData.value.price) {
    formErrors.value.price = '菜品价格不能为空'
    valid = false
  } else {
    const price = parseFloat(formData.value.price)
    if (isNaN(price)) {
      formErrors.value.price = '菜品价格必须是数字'
      valid = false
    } else if (price < 0) {
      formErrors.value.price = '菜品价格不能为负数'
      valid = false
    } else if (price > 99999.99) {
      formErrors.value.price = '菜品价格不能超过99999.99'
      valid = false
    }
  }

  return valid
}

const saveDish = async () => {
  if (!validateForm()) return

  loading.value = true
  try {
    const body = {
      ...formData.value,
      tasteTags: formData.value.tasteTags.length > 0 ? formData.value.tasteTags : null
    }

    if (editingDish.value) {
      await request.put(`/api/merchant-dishes/${editingDish.value.id}`, body)
    } else {
      await request.post('/api/merchant-dishes', body)
    }

    closeModal()
    loadDishes()
  } catch (error) {
    console.error('保存菜品失败:', error)
    if (error.response && error.response.data && error.response.data.message) {
      alert(error.response.data.message)
    }
  } finally {
    loading.value = false
  }
}

const toggleStatus = async (dish, status) => {
  try {
    await request.put(`/api/merchant-dishes/${dish.id}/status`, { status })
    loadDishes()
  } catch (error) {
    console.error('修改状态失败:', error)
  }
}

const deleteDish = async (dish) => {
  if (!confirm('确定要下架该菜品吗？下架后将不再显示在菜单中，但历史评价仍会保留。')) return
  try {
    await request.delete(`/api/merchant-dishes/${dish.id}`)
    loadDishes()
  } catch (error) {
    console.error('下架菜品失败:', error)
  }
}

onMounted(() => {
  loadDishes()
})
</script>

<style scoped>
.dishes-container {
  width: 100%;
}

.search-bar {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
  align-items: center;
}

.search-group {
  flex: 1;
  max-width: 300px;
}

.search-input {
  width: 100%;
  padding: 10px 16px;
  border: 1.5px solid #e8e8e8;
  border-radius: 8px;
  font-size: 14px;
  transition: all 0.2s;
}

.search-input:focus {
  outline: none;
  border-color: #52c41a;
  box-shadow: 0 0 0 3px rgba(82, 196, 26, 0.1);
}

.status-filter {
  flex-shrink: 0;
}

.filter-select {
  padding: 10px 16px;
  border: 1.5px solid #e8e8e8;
  border-radius: 8px;
  font-size: 14px;
  background: #fff;
  cursor: pointer;
}

.filter-select:focus {
  outline: none;
  border-color: #52c41a;
}

.add-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.add-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(82, 196, 26, 0.3);
}

.stats-bar {
  display: flex;
  gap: 24px;
  margin-bottom: 20px;
  padding: 16px 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.stat-item {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #52c41a;
}

.stat-label {
  font-size: 13px;
  color: #667085;
}

.dishes-table {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.dishes-table table {
  width: 100%;
  border-collapse: collapse;
}

.dishes-table th {
  padding: 16px 20px;
  text-align: left;
  font-size: 14px;
  font-weight: 600;
  color: #667085;
  background: #f5f7fa;
  border-bottom: 1px solid #e8e8e8;
}

.dishes-table td {
  padding: 16px 20px;
  font-size: 14px;
  color: #1f2d3d;
  border-bottom: 1px solid #f0f0f0;
}

.dishes-table tr:hover td {
  background: #fafafa;
}

.dish-name-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.dish-thumb {
  width: 40px;
  height: 40px;
  border-radius: 6px;
  object-fit: cover;
}

.price-cell {
  font-weight: 600;
  color: #52c41a;
}

.tags-container {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.taste-tag {
  padding: 4px 10px;
  background: #f6ffed;
  color: #52c41a;
  font-size: 12px;
  border-radius: 4px;
}

.recommend-badge {
  padding: 4px 12px;
  background: #f5f5f5;
  color: #999;
  font-size: 12px;
  border-radius: 4px;
}

.recommend-badge.recommended {
  background: #fffbe6;
  color: #faad14;
}

.status-badge {
  padding: 4px 12px;
  font-size: 12px;
  border-radius: 4px;
}

.status-badge.active {
  background: #f6ffed;
  color: #52c41a;
}

.status-badge.off_shelf {
  background: #fff2f0;
  color: #ff4d4f;
}

.action-buttons {
  display: flex;
  gap: 8px;
}

.action-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: #f5f5f5;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.action-btn:hover {
  background: #e8e8e8;
}

.delete-btn:hover {
  background: #fff2f0;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 40px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.empty-state svg {
  margin-bottom: 16px;
}

.empty-state p {
  font-size: 16px;
  color: #999;
  margin: 0 0 20px;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  width: 100%;
  max-width: 600px;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid #f0f0f0;
}

.modal-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: #1f2d3d;
  margin: 0;
}

.close-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: #f5f5f5;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.dish-form {
  padding: 24px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: #333;
  margin-bottom: 8px;
}

.required {
  color: #ff4d4f;
}

.form-input, .form-select {
  width: 100%;
  padding: 10px 14px;
  border: 1.5px solid #e8e8e8;
  border-radius: 8px;
  font-size: 14px;
  box-sizing: border-box;
  transition: all 0.2s;
}

.form-input:focus, .form-select:focus {
  outline: none;
  border-color: #52c41a;
  box-shadow: 0 0 0 3px rgba(82, 196, 26, 0.1);
}

.form-input.error {
  border-color: #ff4d4f;
}

.error-text {
  display: block;
  font-size: 12px;
  color: #ff4d4f;
  margin-top: 4px;
}

.form-textarea {
  width: 100%;
  padding: 10px 14px;
  border: 1.5px solid #e8e8e8;
  border-radius: 8px;
  font-size: 14px;
  box-sizing: border-box;
  resize: vertical;
}

.form-textarea:focus {
  outline: none;
  border-color: #52c41a;
  box-shadow: 0 0 0 3px rgba(82, 196, 26, 0.1);
}

.tags-input {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px;
  border: 1.5px solid #e8e8e8;
  border-radius: 8px;
  min-height: 44px;
}

.tag-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: #f6ffed;
  color: #52c41a;
  font-size: 13px;
  border-radius: 4px;
}

.tag-item button {
  border: none;
  background: none;
  color: #52c41a;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
}

.tag-input {
  flex: 1;
  min-width: 120px;
  border: none;
  outline: none;
  font-size: 14px;
  background: transparent;
}

.checkbox-group {
  display: flex;
  align-items: center;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.checkbox-label span {
  font-size: 14px;
  color: #333;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
}

.cancel-btn {
  padding: 10px 24px;
  background: #f5f5f5;
  color: #666;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.cancel-btn:hover {
  background: #e8e8e8;
}

.submit-btn {
  padding: 10px 24px;
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(82, 196, 26, 0.3);
}

.submit-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

@media (max-width: 768px) {
  .form-row {
    grid-template-columns: 1fr;
  }
  
  .dishes-table {
    overflow-x: auto;
  }
  
  .dishes-table table {
    min-width: 800px;
  }
}
</style>