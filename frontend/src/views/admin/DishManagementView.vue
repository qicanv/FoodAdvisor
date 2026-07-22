<template>
  <AdminLayout title="菜品管理" subtitle="管理平台所有菜品，支持状态变更、恢复和操作历史追溯">
    <template #sidebar>
      <div class="page-sidebar-nav">
        <span class="page-sidebar-title">菜品管理</span>
        <div class="page-sidebar-items-wrapper">
          <div
            v-for="item in sidebarItems"
            :key="item.key"
            :class="['page-sidebar-item', { active: activeTab === item.key }]"
            @click="activeTab = item.key"
          >
            <span class="menu-icon">{{ item.icon }}</span>
            <span>{{ item.label }}</span>
          </div>
        </div>
      </div>
    </template>

    <!-- 菜品列表 -->
    <div v-if="activeTab === 'list'" class="tab-content">
      <div class="search-bar">
        <div class="search-item">
          <input
            type="text"
            v-model="searchKeyword"
            placeholder="搜索菜品名称、分类或描述..."
            class="search-input"
            @keyup.enter="loadDishes"
          />
        </div>
        <div class="search-item">
          <select v-model="searchStatus" class="search-select" @change="loadDishes">
            <option value="">全部状态</option>
            <option value="ACTIVE">上架中</option>
            <option value="OFF_SHELF">已下架</option>
            <option value="ARCHIVED">已归档</option>
          </select>
        </div>
        <div class="search-item">
          <select v-model="searchMerchantId" class="search-select" @change="loadDishes" :disabled="!merchantListLoaded">
            <option value="">{{ merchantListLoaded ? '全部商家' : '加载中...' }}</option>
            <option v-for="m in merchantList" :key="m.id" :value="m.id">{{ m.name }}</option>
          </select>
        </div>
        <button class="search-btn" @click="loadDishes">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"></circle>
            <path d="M21 21l-4.35-4.35"></path>
          </svg>
          <span>搜索</span>
        </button>
      </div>

      <div class="table-container">
        <table class="dish-table">
          <thead>
            <tr>
              <th>菜品名称</th>
              <th>分类</th>
              <th>价格</th>
              <th>所属商家ID</th>
              <th>推荐</th>
              <th>状态</th>
              <th>更新时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="dish in dishes" :key="dish.id">
              <td class="name-cell">{{ dish.name }}</td>
              <td>{{ dish.category || '-' }}</td>
              <td>{{ dish.price ? '￥' + dish.price : '-' }}</td>
              <td>{{ dish.merchantId }}</td>
              <td>{{ dish.recommended ? '是' : '否' }}</td>
              <td>
                <span :class="['status-tag', getStatusClass(dish.status)]">
                  {{ getStatusText(dish.status) }}
                </span>
              </td>
              <td>{{ formatDate(dish.updatedAt) }}</td>
              <td class="actions-cell">
                <button class="action-btn edit-btn" @click="openEditModal(dish)" title="编辑">
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M17 3a2.828 2.828 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5L17 3z"></path>
                  </svg>
                </button>
                <button class="action-btn status-btn" @click="openStatusModal(dish)" title="修改状态">
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="3"></circle>
                    <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82V9a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"></path>
                  </svg>
                </button>
                <button
                  v-if="dish.status !== 'ACTIVE'"
                  class="action-btn restore-btn"
                  @click="restoreDish(dish)"
                  title="恢复菜品"
                >
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="1 4 1 10 7 10"></polyline>
                    <path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"></path>
                  </svg>
                </button>
              </td>
            </tr>
            <tr v-if="loading">
              <td colspan="8" class="loading-cell">
                <div class="loading-spinner"></div>
                <span>加载中...</span>
              </td>
            </tr>
            <tr v-if="!loading && dishes.length === 0">
              <td colspan="8" class="empty-cell">暂无菜品数据</td>
            </tr>
          </tbody>
        </table>

        <div class="pagination" v-if="total > 0">
          <button class="pagination-btn" :disabled="currentPage <= 1" @click="goToPage(currentPage - 1)">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M15 19l-7-7 7-7"></path>
            </svg>
          </button>
          <span class="pagination-info">第 {{ currentPage }} / {{ totalPages }} 页</span>
          <button class="pagination-btn" :disabled="currentPage >= totalPages" @click="goToPage(currentPage + 1)">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 5l7 7-7 7"></path>
            </svg>
          </button>
          <span class="pagination-total">共 {{ total }} 条记录</span>
        </div>
      </div>
    </div>

    <!-- 状态变更弹窗 -->
    <div class="modal-overlay" v-if="showStatusModal" @click="closeStatusModal">
      <div class="modal-content status-modal" @click.stop>
        <div class="modal-header">
          <h2>修改菜品状态</h2>
          <button class="close-btn" @click="closeStatusModal">&times;</button>
        </div>
        <div class="modal-body">
          <div class="status-info">
            <div class="status-item">
              <span class="status-label">菜品名称</span>
              <span class="status-value">{{ statusDish?.name }}</span>
            </div>
            <div class="status-item">
              <span class="status-label">当前状态</span>
              <span :class="['status-tag', getStatusClass(statusDish?.status)]">
                {{ getStatusText(statusDish?.status) }}
              </span>
            </div>
          </div>
          <div class="form-item">
            <label>新状态</label>
            <select v-model="statusForm.status" class="form-select">
              <option value="ACTIVE">上架</option>
              <option value="OFF_SHELF">下架</option>
              <option value="ARCHIVED">归档</option>
            </select>
          </div>
          <div class="form-item" style="margin-top: 12px;">
            <label>变更原因</label>
            <textarea v-model="statusForm.reason" class="form-textarea" placeholder="请输入变更原因（选填）" rows="2"></textarea>
          </div>

          <!-- 状态变更历史 -->
          <div class="status-history-section" v-if="statusHistory.length > 0">
            <h4 class="history-title">状态变更历史</h4>
            <div class="history-list">
              <div v-for="record in statusHistory" :key="record.id" class="history-item">
                <div class="history-header">
                  <span class="history-time">{{ formatDateTime(record.createdAt) }}</span>
                </div>
                <div class="history-detail">
                  <span class="status-tag status-archived">{{ record.oldStatus || '创建' }}</span>
                  <span style="margin: 0 6px; color: #909399;">→</span>
                  <span :class="['status-tag', getStatusClass(record.newStatus)]">{{ record.newStatus }}</span>
                </div>
                <div class="history-reason" v-if="record.reason">{{ record.reason }}</div>
              </div>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-cancel" @click="closeStatusModal">取消</button>
          <button class="btn btn-primary" @click="submitStatus">确认修改</button>
        </div>
      </div>
    </div>

    <!-- 编辑菜品弹窗 -->
    <div class="modal-overlay" v-if="showEditModal" @click="closeEditModal">
      <div class="modal-content" @click.stop>
        <div class="modal-header">
          <h2>{{ isEditing ? '编辑菜品' : '新增菜品' }}</h2>
          <button class="close-btn" @click="closeEditModal">&times;</button>
        </div>
        <div class="modal-body">
          <div class="form-grid">
            <div class="form-item">
              <label>菜品名称 <span class="required">*</span></label>
              <input type="text" v-model="formData.name" class="form-input" placeholder="请输入菜品名称" />
            </div>
            <div class="form-item">
              <label>所属商家ID <span class="required">*</span></label>
              <input type="number" v-model="formData.merchantId" class="form-input" placeholder="请输入商家ID" />
            </div>
            <div class="form-item">
              <label>价格</label>
              <input type="number" v-model="formData.price" class="form-input" placeholder="请输入价格" min="0" />
            </div>
            <div class="form-item">
              <label>分类</label>
              <input type="text" v-model="formData.category" class="form-input" placeholder="请输入分类" />
            </div>
            <div class="form-item">
              <label>状态</label>
              <select v-model="formData.status" class="form-select">
                <option value="ACTIVE">上架</option>
                <option value="OFF_SHELF">下架</option>
                <option value="ARCHIVED">归档</option>
              </select>
            </div>
            <div class="form-item">
              <label>推荐</label>
              <select v-model="formData.recommended" class="form-select">
                <option :value="true">是</option>
                <option :value="false">否</option>
              </select>
            </div>
            <div class="form-item full-width">
              <label>描述</label>
              <textarea v-model="formData.description" class="form-textarea" placeholder="请输入菜品描述" rows="2"></textarea>
            </div>
            <div class="form-item full-width">
              <label>图片URL</label>
              <input type="text" v-model="formData.imageUrl" class="form-input" placeholder="请输入图片URL" />
            </div>
          </div>
          <div class="error-message" v-if="errorMessage">{{ errorMessage }}</div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-cancel" @click="closeEditModal">取消</button>
          <button class="btn btn-primary" @click="submitForm">{{ isEditing ? '保存修改' : '确认新增' }}</button>
        </div>
      </div>
    </div>
  </AdminLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import AdminLayout from '../../components/AdminLayout.vue'
import {
  getAdminDishes,
  createAdminDish,
  updateAdminDish,
  updateAdminDishStatus,
  restoreAdminDish,
  getAdminDishStatusHistory,
  deleteAdminDish
} from '../../api/adminDish'
import { getAdminMerchants } from '../../api/adminMerchant'

const activeTab = ref('list')

const sidebarItems = [
  { key: 'list', label: '菜品列表', icon: '🍽️' },
]

const dishes = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

const searchKeyword = ref('')
const searchStatus = ref('')
const searchMerchantId = ref('')

// 商家列表缓存（一次性加载，供筛选/表格/表单共用）
const merchantList = ref([])
const merchantMap = ref({})
const merchantListLoaded = ref(false)

const totalPages = computed(() => Math.ceil(total.value / pageSize.value))

const showStatusModal = ref(false)
const statusDish = ref(null)
const statusForm = ref({ status: '', reason: '' })
const statusHistory = ref([])

const showEditModal = ref(false)
const isEditing = ref(false)
const errorMessage = ref('')
const formData = ref({
  id: null,
  name: '',
  merchantId: '',
  price: '',
  category: '',
  status: 'ACTIVE',
  recommended: false,
  description: '',
  imageUrl: ''
})

const loadDishes = async () => {
  loading.value = true
  try {
    const params = {
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      keyword: searchKeyword.value || undefined,
      status: searchStatus.value || undefined,
      merchantId: searchMerchantId.value ? Number(searchMerchantId.value) : undefined
    }
    const response = await getAdminDishes(params)
    if (response.success) {
      dishes.value = response.data?.records || []
      total.value = response.data?.total || 0
    }
  } catch (error) {
    console.error('加载菜品列表失败:', error)
  } finally {
    loading.value = false
  }
}

const loadMerchantList = async () => {
  if (merchantListLoaded.value) return
  try {
    const response = await getAdminMerchants({ pageNum: 1, pageSize: 1000 })
    if (response.success) {
      const records = response.data?.records || []
      merchantList.value = records
      const map = {}
      records.forEach(m => { map[m.id] = m.name })
      merchantMap.value = map
      merchantListLoaded.value = true
    }
  } catch (error) {
    console.error('加载商家列表失败:', error)
  }
}

const goToPage = (page) => {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
  loadDishes()
}

const getStatusClass = (status) => {
  switch (status) {
    case 'ACTIVE': return 'status-active'
    case 'OFF_SHELF': return 'status-disabled'
    case 'ARCHIVED': return 'status-archived'
    default: return ''
  }
}

const getStatusText = (status) => {
  switch (status) {
    case 'ACTIVE': return '上架中'
    case 'OFF_SHELF': return '已下架'
    case 'ARCHIVED': return '已归档'
    default: return status || '-'
  }
}

const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  try {
    return new Date(dateStr).toLocaleDateString('zh-CN')
  } catch {
    return dateStr
  }
}

const formatDateTime = (dateStr) => {
  if (!dateStr) return '-'
  try {
    return new Date(dateStr).toLocaleString('zh-CN')
  } catch {
    return dateStr
  }
}

// ===== 状态管理 =====

const openStatusModal = async (dish) => {
  statusDish.value = dish
  statusForm.value = { status: dish.status || 'ACTIVE', reason: '' }
  statusHistory.value = []
  showStatusModal.value = true

  try {
    const response = await getAdminDishStatusHistory(dish.id)
    if (response.success && response.data) {
      statusHistory.value = response.data
    }
  } catch (error) {
    console.error('加载状态历史失败:', error)
  }
}

const closeStatusModal = () => {
  showStatusModal.value = false
  statusDish.value = null
}

const submitStatus = async () => {
  if (!statusDish.value) return
  try {
    const response = await updateAdminDishStatus(statusDish.value.id, {
      status: statusForm.value.status,
      reason: statusForm.value.reason || null
    })
    if (response.success) {
      closeStatusModal()
      loadDishes()
    } else {
      alert(response.message || '修改状态失败')
    }
  } catch (error) {
    console.error('修改状态失败:', error)
  }
}

const restoreDish = async (dish) => {
  if (!confirm(`确认恢复菜品"${dish.name}"吗？恢复后将重新上架。`)) return
  try {
    const response = await restoreAdminDish(dish.id, { reason: '管理员手动恢复' })
    if (response.success) {
      loadDishes()
    } else {
      alert(response.message || '恢复失败')
    }
  } catch (error) {
    console.error('恢复失败:', error)
  }
}

// ===== 编辑管理 =====

const openEditModal = (dish = null) => {
  errorMessage.value = ''
  if (dish) {
    isEditing.value = true
    formData.value = {
      id: dish.id,
      name: dish.name,
      merchantId: dish.merchantId,
      price: dish.price || '',
      category: dish.category || '',
      status: dish.status || 'ACTIVE',
      recommended: dish.recommended || false,
      description: dish.description || '',
      imageUrl: dish.imageUrl || ''
    }
  } else {
    isEditing.value = false
    formData.value = {
      id: null, name: '', merchantId: '', price: '', category: '',
      status: 'ACTIVE', recommended: false, description: '', imageUrl: ''
    }
  }
  showEditModal.value = true
}

const closeEditModal = () => {
  showEditModal.value = false
}

const submitForm = async () => {
  errorMessage.value = ''
  if (!formData.value.name.trim()) {
    errorMessage.value = '菜品名称不能为空'
    return
  }
  if (!formData.value.merchantId) {
    errorMessage.value = '所属商家ID不能为空'
    return
  }

  try {
    let response
    const data = {
      name: formData.value.name.trim(),
      merchantId: Number(formData.value.merchantId),
      price: formData.value.price ? Number(formData.value.price) : null,
      category: formData.value.category || null,
      status: formData.value.status,
      recommended: formData.value.recommended,
      description: formData.value.description || null,
      imageUrl: formData.value.imageUrl || null
    }

    if (isEditing.value) {
      response = await updateAdminDish(formData.value.id, data)
    } else {
      response = await createAdminDish(data)
    }

    if (response.success) {
      closeEditModal()
      loadDishes()
    } else {
      errorMessage.value = response.message || '操作失败'
    }
  } catch (error) {
    errorMessage.value = '网络请求失败'
    console.error('提交失败:', error)
  }
}

onMounted(() => {
  loadDishes()
  loadMerchantList()
})
</script>

<style scoped>
.tab-content { width: 100%; }

.search-bar {
  display: flex; gap: 16px; margin-bottom: 24px; flex-wrap: wrap;
  padding: 24px; background: #fff; border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
}

.search-item:first-child { flex: 1 1 250px; }
.search-item:not(:first-child) { flex: 0 0 150px; }

.search-input {
  width: 100%; padding: 12px 16px; border: 2px solid #e8e8e8;
  border-radius: 12px; font-size: 14px; background: #fafafa;
}

.search-input:focus {
  outline: none; border-color: #1890ff; background: #fff;
}

.search-select {
  width: 100%; padding: 12px 16px; border: 2px solid #e8e8e8;
  border-radius: 12px; font-size: 14px; background: #fafafa; cursor: pointer;
}

.search-btn {
  display: flex; align-items: center; gap: 8px; padding: 12px 28px;
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
  color: #fff; border: none; border-radius: 12px; font-size: 14px;
  font-weight: 600; cursor: pointer;
}

.table-container {
  background: #fff; border-radius: 16px; box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.dish-table { width: 100%; border-collapse: collapse; }

.dish-table thead { background: linear-gradient(180deg, #fafafa 0%, #f5f5f5 100%); }

.dish-table th {
  padding: 16px 20px; text-align: left; font-weight: 600;
  color: #5a6a7a; font-size: 14px; border-bottom: 2px solid #e8e8e8;
}

.dish-table td {
  padding: 16px 20px; font-size: 14px; color: #1f2d3d;
  border-bottom: 1px solid #f0f0f0;
}

.dish-table tbody tr:hover td { background-color: #f8fcff; }

.name-cell { font-weight: 600; color: #1890ff; }

.status-tag {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 6px 14px; border-radius: 20px; font-size: 12px; font-weight: 600;
}

.status-active { background: rgba(82, 196, 26, 0.1); color: #52c41a; }
.status-disabled { background: rgba(255, 77, 79, 0.1); color: #ff4d4f; }
.status-archived { background: rgba(144, 147, 153, 0.1); color: #909399; }

.actions-cell { display: flex; gap: 10px; }

.action-btn {
  width: 40px; height: 40px; border: none; border-radius: 10px;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
}

.action-btn:hover { transform: translateY(-2px); }

.edit-btn { background: rgba(24, 144, 255, 0.1); color: #1890ff; }
.status-btn { background: rgba(82, 196, 26, 0.1); color: #52c41a; }
.restore-btn { background: rgba(82, 196, 26, 0.1); color: #52c41a; }

.loading-cell, .empty-cell { text-align: center; padding: 60px !important; color: #909399; }

.loading-spinner {
  width: 32px; height: 32px; border: 4px solid #f0f0f0;
  border-top-color: #1890ff; border-radius: 50%; animation: spin 0.8s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.pagination {
  display: flex; align-items: center; justify-content: flex-end;
  gap: 20px; padding: 24px; border-top: 1px solid #f0f0f0; background: #fafafa;
}

.pagination-btn {
  width: 40px; height: 40px; border: 2px solid #e8e8e8; border-radius: 10px;
  background: #fff; cursor: pointer; display: flex; align-items: center; justify-content: center;
}

.pagination-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.pagination-info { font-size: 14px; color: #5a6a7a; font-weight: 500; }
.pagination-total { font-size: 14px; color: #909399; }

/* Modal */
.modal-overlay {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.5); display: flex; align-items: center;
  justify-content: center; z-index: 1000;
}

.modal-content {
  background: #fff; border-radius: 16px; width: 90%; max-width: 600px;
  max-height: 90vh; overflow-y: auto;
}

.status-modal { max-width: 500px; }

.modal-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 20px 24px; border-bottom: 1px solid #f0f0f0;
}

.modal-header h2 { font-size: 18px; font-weight: 600; color: #1f2d3d; margin: 0; }

.close-btn {
  width: 32px; height: 32px; border: none; background: #f5f5f5;
  border-radius: 8px; cursor: pointer; font-size: 18px; color: #666;
}

.modal-body { padding: 24px; }

.modal-footer {
  display: flex; justify-content: flex-end; gap: 12px;
  padding: 20px 24px; border-top: 1px solid #f0f0f0;
}

.btn {
  padding: 10px 24px; border-radius: 8px; font-size: 14px;
  font-weight: 500; cursor: pointer; transition: all 0.2s;
}

.btn-cancel { background: #f5f5f5; color: #666; border: none; }
.btn-primary {
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
  color: #fff; border: none;
}

.status-info {
  margin-bottom: 20px; padding: 16px; background: #fafafa; border-radius: 12px;
}

.status-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 0; border-bottom: 1px solid #f0f0f0;
}
.status-item:last-child { border-bottom: none; }
.status-label { font-size: 14px; color: #667085; }
.status-value { font-size: 14px; font-weight: 600; color: #1f2d3d; }

.form-item { display: flex; flex-direction: column; gap: 8px; }
.form-item label { font-size: 14px; font-weight: 600; color: #5a6a7a; }
.required { color: #ff4d4f; margin-left: 4px; }
.form-input, .form-select {
  padding: 14px 16px; border: 2px solid #e8e8e8; border-radius: 12px;
  font-size: 14px; background: #fafafa;
}
.form-input:focus, .form-select:focus {
  outline: none; border-color: #1890ff; background: #fff;
}
.form-textarea {
  padding: 14px 16px; border: 2px solid #e8e8e8; border-radius: 12px;
  font-size: 14px; resize: vertical; min-height: 60px; background: #fafafa;
}
.form-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; }
.form-item.full-width { grid-column: span 2; }

.error-message {
  margin-top: 20px; padding: 16px; background: rgba(255, 77, 79, 0.1);
  color: #ff4d4f; border-radius: 12px; font-size: 14px; border-left: 4px solid #ff4d4f;
}

/* 状态历史 */
.status-history-section {
  margin-top: 20px; padding-top: 16px; border-top: 2px solid #f0f0f0;
}

.history-title { font-size: 14px; font-weight: 600; color: #5a6a7a; margin-bottom: 12px; }
.history-list { max-height: 200px; overflow-y: auto; }

.history-item {
  padding: 10px 12px; border: 1px solid #f0f0f0;
  border-radius: 8px; margin-bottom: 8px; background: #fafafa;
}

.history-header { margin-bottom: 6px; }
.history-time { font-size: 12px; color: #909399; }
.history-detail { display: flex; align-items: center; gap: 8px; }
.history-reason { font-size: 12px; color: #667085; margin-top: 6px; font-style: italic; }
</style>
