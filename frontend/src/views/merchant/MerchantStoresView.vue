<template>
  <MerchantLayout title="店铺管理" subtitle="管理您的店铺信息和经营状态">
    <div class="stores-container">
      <div class="toolbar">
        <h2 class="section-title">我的店铺（{{ stores.length }}）</h2>
        <button class="add-btn" @click="openCreateModal">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="#fff" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"></line>
            <line x1="5" y1="12" x2="19" y2="12"></line>
          </svg>
          <span>新建店铺</span>
        </button>
      </div>

      <div v-if="loading" class="loading-state">
        <div class="loading-spinner"></div>
        <span>加载中...</span>
      </div>

      <div v-else-if="stores.length === 0" class="empty-state">
        <svg viewBox="0 0 24 24" width="64" height="64" fill="none" stroke="#ccc" stroke-width="2">
          <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"></path>
          <polyline points="9 22 9 12 15 12 15 22"></polyline>
        </svg>
        <p>您还没有管理任何店铺</p>
        <button class="add-first-btn" @click="openCreateModal">创建第一个店铺</button>
      </div>

      <div v-else class="stores-grid">
        <div v-for="store in stores" :key="store.id" class="store-card">
          <div class="store-header">
            <h3 class="store-name">{{ store.name }}</h3>
            <span :class="['status-badge', getStatusClass(store.operationStatus)]">
              {{ getStatusText(store.operationStatus) }}
            </span>
          </div>
          <div class="store-info">
            <div class="info-row">
              <span class="info-label">编号</span>
              <span class="info-value">{{ store.merchantCode }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">类型</span>
              <span class="info-value">{{ store.category }} / {{ store.cuisine || '-' }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">地址</span>
              <span class="info-value">{{ store.address }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">人均</span>
              <span class="info-value">{{ store.averagePrice ? '¥' + store.averagePrice : '-' }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">评分</span>
              <span class="info-value">{{ store.rating || '暂无' }}</span>
            </div>
          </div>
          <div class="store-actions">
            <button class="action-btn dishes-btn" @click="manageDishes(store)">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                <circle cx="12" cy="7" r="4"></circle>
              </svg>
              <span>菜品</span>
            </button>
            <button class="action-btn edit-btn" @click="openEditModal(store)">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
              </svg>
              <span>编辑</span>
            </button>
            <button
              v-if="store.operationStatus === 'OPERATING'"
              class="action-btn suspend-btn"
              @click="confirmSuspend(store)"
            >
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"></circle>
                <line x1="12" y1="8" x2="12" y2="12"></line>
                <line x1="12" y1="16" x2="12.01" y2="16"></line>
              </svg>
              <span>暂停营业</span>
            </button>
            <button
              v-else
              class="action-btn resume-btn"
              @click="confirmResume(store)"
            >
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                <polygon points="5 3 19 12 5 21 5 3"></polygon>
              </svg>
              <span>恢复营业</span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 新建/编辑弹窗 -->
    <div v-if="showModal" class="modal-overlay" @click.self="closeModal">
      <div class="modal-content" @click.stop>
        <div class="modal-header">
          <h3>{{ isEditing ? '编辑店铺' : '新建店铺' }}</h3>
          <button class="close-btn" @click="closeModal">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="#999" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>
        </div>
        <form @submit.prevent="submitForm" class="store-form">
          <div class="form-row">
            <div class="form-group">
              <label>店名 <span class="required">*</span></label>
              <input type="text" v-model="form.name" placeholder="请输入店名" class="form-input" />
            </div>
            <div class="form-group">
              <label>商家类型 <span class="required">*</span></label>
              <select v-model="form.category" class="form-select">
                <option value="">请选择类型</option>
                <option value="中餐厅">中餐厅</option>
                <option value="西餐厅">西餐厅</option>
                <option value="日料">日料</option>
                <option value="韩餐">韩餐</option>
                <option value="火锅">火锅</option>
                <option value="快餐">快餐</option>
                <option value="甜品">甜品</option>
                <option value="咖啡厅">咖啡厅</option>
                <option value="烧烤">烧烤</option>
                <option value="轻食沙拉">轻食沙拉</option>
                <option value="其他">其他</option>
              </select>
            </div>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>菜系</label>
              <input type="text" v-model="form.cuisine" placeholder="请输入菜系" class="form-input" />
            </div>
            <div class="form-group">
              <label>人均消费</label>
              <input type="number" v-model="form.averagePrice" placeholder="请输入人均消费" class="form-input" min="0" />
            </div>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>地址 <span class="required">*</span></label>
              <input type="text" v-model="form.address" placeholder="请输入地址" class="form-input" />
            </div>
            <div class="form-group">
              <label>联系电话</label>
              <input type="text" v-model="form.phone" placeholder="请输入联系电话" class="form-input" />
            </div>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>区域编码</label>
              <input type="text" v-model="form.regionCode" placeholder="如 CD-JJ" class="form-input" />
            </div>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>经度</label>
              <input type="text" v-model="form.longitude" placeholder="请输入经度" class="form-input" />
            </div>
            <div class="form-group">
              <label>纬度</label>
              <input type="text" v-model="form.latitude" placeholder="请输入纬度" class="form-input" />
            </div>
          </div>
          <div class="form-group">
            <label>环境标签（JSON数组格式，如 ["安静","适合聚餐"]）</label>
            <input type="text" v-model="form.environmentTags" placeholder='["安静","适合聚餐"]' class="form-input" />
          </div>
          <div class="form-group">
            <label>店铺描述</label>
            <textarea v-model="form.description" placeholder="请输入店铺描述" class="form-textarea" rows="3"></textarea>
          </div>
          <div v-if="errorMsg" class="error-message">{{ errorMsg }}</div>
          <div class="form-actions">
            <button type="button" class="cancel-btn" @click="closeModal">取消</button>
            <button type="submit" class="submit-btn" :disabled="submitting">
              {{ submitting ? '保存中...' : (isEditing ? '保存修改' : '确认创建') }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- 状态确认弹窗 -->
    <div v-if="showStatusModal" class="modal-overlay" @click.self="showStatusModal = false">
      <div class="modal-content status-modal" @click.stop>
        <div class="modal-header">
          <h3>{{ statusAction === 'suspend' ? '暂停营业' : '恢复营业' }}</h3>
          <button class="close-btn" @click="showStatusModal = false">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="#999" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>
        </div>
        <div class="modal-body">
          <p class="status-confirm-text">
            {{ statusAction === 'suspend'
              ? `确定要将「${statusTarget?.name}」设为暂停营业吗？暂停后用户将无法搜索到该店铺。`
              : `确定要将「${statusTarget?.name}」恢复为营业中吗？` }}
          </p>
        </div>
        <div class="modal-footer">
          <button class="cancel-btn" @click="showStatusModal = false">取消</button>
          <button class="submit-btn" @click="executeStatusChange" :disabled="submitting">
            {{ submitting ? '处理中...' : '确认' }}
          </button>
        </div>
      </div>
    </div>
  </MerchantLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import MerchantLayout from '../../components/MerchantLayout.vue'
import {
  getMyMerchants,
  createMyMerchant,
  updateMyMerchant,
  updateMyMerchantOperationStatus
} from '../../api/merchantConsole'

const router = useRouter()

const stores = ref([])
const loading = ref(false)
const showModal = ref(false)
const isEditing = ref(false)
const submitting = ref(false)
const errorMsg = ref('')

const showStatusModal = ref(false)
const statusAction = ref('')
const statusTarget = ref(null)

const form = ref({
  name: '',
  category: '',
  cuisine: '',
  averagePrice: '',
  address: '',
  regionCode: '',
  longitude: '',
  latitude: '',
  phone: '',
  description: '',
  environmentTags: ''
})

const resetForm = () => {
  form.value = {
    name: '',
    category: '',
    cuisine: '',
    averagePrice: '',
    address: '',
    regionCode: '',
    longitude: '',
    latitude: '',
    phone: '',
    description: '',
    environmentTags: ''
  }
  errorMsg.value = ''
}

const loadStores = async () => {
  loading.value = true
  try {
    const response = await getMyMerchants()
    if (response.success) {
      stores.value = response.data || []
    }
  } catch (error) {
    console.error('加载店铺列表失败:', error)
  } finally {
    loading.value = false
  }
}

const openCreateModal = () => {
  isEditing.value = false
  resetForm()
  showModal.value = true
}

const openEditModal = (store) => {
  isEditing.value = true
  form.value = {
    id: store.id,
    name: store.name || '',
    category: store.category || '',
    cuisine: store.cuisine || '',
    averagePrice: store.averagePrice != null ? String(store.averagePrice) : '',
    address: store.address || '',
    regionCode: store.regionCode || '',
    longitude: store.longitude != null ? String(store.longitude) : '',
    latitude: store.latitude != null ? String(store.latitude) : '',
    phone: store.phone || '',
    description: store.description || '',
    environmentTags: store.environmentTags || ''
  }
  showModal.value = true
}

const closeModal = () => {
  showModal.value = false
  resetForm()
}

const submitForm = async () => {
  errorMsg.value = ''
  if (!form.value.name.trim()) { errorMsg.value = '店名不能为空'; return }
  if (!form.value.category) { errorMsg.value = '商家类型不能为空'; return }
  if (!form.value.address.trim()) { errorMsg.value = '地址不能为空'; return }

  submitting.value = true
  try {
    const data = {
      name: form.value.name.trim(),
      category: form.value.category,
      cuisine: form.value.cuisine || null,
      averagePrice: form.value.averagePrice ? Number(form.value.averagePrice) : null,
      address: form.value.address.trim(),
      regionCode: form.value.regionCode || null,
      longitude: form.value.longitude ? Number(form.value.longitude) : null,
      latitude: form.value.latitude ? Number(form.value.latitude) : null,
      phone: form.value.phone || null,
      description: form.value.description || null,
      environmentTags: form.value.environmentTags || '[]'
    }

    let response
    if (isEditing.value) {
      response = await updateMyMerchant(form.value.id, data)
    } else {
      response = await createMyMerchant(data)
    }

    if (response.success) {
      closeModal()
      loadStores()
    } else {
      errorMsg.value = response.message || '操作失败'
    }
  } catch (error) {
    errorMsg.value = '网络请求失败'
    console.error('保存店铺失败:', error)
  } finally {
    submitting.value = false
  }
}

const confirmSuspend = (store) => {
  statusTarget.value = store
  statusAction.value = 'suspend'
  showStatusModal.value = true
}

const confirmResume = (store) => {
  statusTarget.value = store
  statusAction.value = 'resume'
  showStatusModal.value = true
}

const executeStatusChange = async () => {
  if (!statusTarget.value) return
  submitting.value = true
  try {
    const newStatus = statusAction.value === 'suspend' ? 'SUSPENDED' : 'OPERATING'
    const response = await updateMyMerchantOperationStatus(statusTarget.value.id, {
      operationStatus: newStatus
    })
    if (response.success) {
      showStatusModal.value = false
      loadStores()
    } else {
      alert(response.message || '操作失败')
    }
  } catch (error) {
    console.error('修改状态失败:', error)
  } finally {
    submitting.value = false
  }
}

const getStatusClass = (status) => {
  switch (status) {
    case 'OPERATING': return 'status-operating'
    case 'SUSPENDED': return 'status-suspended'
    case 'CLOSED_PERMANENTLY': return 'status-closed'
    default: return ''
  }
}

const getStatusText = (status) => {
  switch (status) {
    case 'OPERATING': return '营业中'
    case 'SUSPENDED': return '停业中'
    case 'CLOSED_PERMANENTLY': return '永久关闭'
    default: return status || '-'
  }
}

const manageDishes = (store) => {
  localStorage.setItem('activeMerchantId', String(store.id))
  router.push('/merchant/dishes')
}

onMounted(() => {
  loadStores()
})
</script>

<style scoped>
.stores-container { width: 100%; }

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2d3d;
  margin: 0;
}

.add-btn, .add-first-btn {
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

.add-btn:hover, .add-first-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(82, 196, 26, 0.3);
}

.loading-state, .empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 40px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.loading-spinner {
  width: 32px; height: 32px;
  border: 4px solid #f0f0f0;
  border-top-color: #52c41a;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 16px;
}

@keyframes spin { to { transform: rotate(360deg); } }

.empty-state svg { margin-bottom: 16px; }
.empty-state p { font-size: 16px; color: #999; margin: 0 0 20px; }

.stores-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 20px;
}

.store-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  transition: transform 0.2s, box-shadow 0.2s;
  display: flex;
  flex-direction: column;
}

.store-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.store-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.store-name {
  font-size: 18px;
  font-weight: 600;
  color: #1f2d3d;
  margin: 0;
}

.status-badge {
  padding: 4px 12px;
  font-size: 12px;
  font-weight: 600;
  border-radius: 12px;
}

.status-operating { background: #f6ffed; color: #52c41a; }
.status-suspended { background: #fff7e6; color: #faad14; }
.status-closed { background: #fff2f0; color: #ff4d4f; }

.store-info { flex: 1; margin-bottom: 16px; }

.info-row {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  border-bottom: 1px solid #fafafa;
}

.info-label { font-size: 13px; color: #999; }
.info-value { font-size: 13px; color: #1f2d3d; font-weight: 500; }

.store-actions { display: flex; gap: 12px; }

.action-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.dishes-btn { background: #f6ffed; color: #52c41a; }
.dishes-btn:hover { background: #d9f7be; }

.edit-btn { background: #e6f7ff; color: #1890ff; }
.edit-btn:hover { background: #bae7ff; }

.suspend-btn { background: #fff7e6; color: #faad14; }
.suspend-btn:hover { background: #ffe58f; }

.resume-btn { background: #f6ffed; color: #52c41a; }
.resume-btn:hover { background: #b7eb8f; }

/* Modal */
.modal-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: #fff;
  border-radius: 12px;
  width: 90%;
  max-width: 640px;
  max-height: 90vh;
  overflow: hidden;
}

.status-modal { max-width: 420px; }

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
  width: 32px; height: 32px;
  border: none;
  background: #f5f5f5;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal-body {
  padding: 24px;
  max-height: 60vh;
  overflow-y: auto;
}

.modal-footer {
  padding: 16px 24px;
  border-top: 1px solid #f0f0f0;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.store-form { padding: 24px; }

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.form-group { margin-bottom: 16px; }

.form-group label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: #333;
  margin-bottom: 8px;
}

.required { color: #ff4d4f; }

.form-input, .form-select, .form-textarea {
  width: 100%;
  padding: 10px 14px;
  border: 1.5px solid #e8e8e8;
  border-radius: 8px;
  font-size: 14px;
  box-sizing: border-box;
  transition: all 0.2s;
}

.form-input:focus, .form-select:focus, .form-textarea:focus {
  outline: none;
  border-color: #52c41a;
  box-shadow: 0 0 0 3px rgba(82, 196, 26, 0.1);
}

.form-select { background: #fff; cursor: pointer; }

.error-message {
  padding: 12px;
  background: #fff2f0;
  color: #ff4d4f;
  border-radius: 8px;
  font-size: 14px;
  border-left: 4px solid #ff4d4f;
  margin-top: 12px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 20px;
}

.cancel-btn {
  padding: 10px 24px;
  background: #f5f5f5;
  color: #666;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
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

.submit-btn:disabled { opacity: 0.7; cursor: not-allowed; }

.status-confirm-text {
  font-size: 15px;
  color: #1f2d3d;
  line-height: 1.6;
  margin: 0;
}

@media (max-width: 768px) {
  .stores-grid { grid-template-columns: 1fr; }
  .form-row { grid-template-columns: 1fr; }
}
</style>
