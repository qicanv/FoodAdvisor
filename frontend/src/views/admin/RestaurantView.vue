<template>
  <AdminLayout title="商家管理" subtitle="维护商家基础资料，为搜索、推荐和口碑分析提供准确数据">
    <template #sidebar>
      <div class="page-sidebar-nav">
        <span class="page-sidebar-title">商家管理</span>
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

    <div v-if="activeTab === 'list'" class="tab-content">
      <div class="search-bar">
        <div class="search-item">
          <input 
            type="text" 
            v-model="searchKeyword" 
            placeholder="搜索店名、编号、类型或地址..."
            class="search-input"
            @keyup.enter="loadMerchants"
          />
        </div>
        <div class="search-item">
          <select v-model="searchOperationStatus" class="search-select" @change="loadMerchants">
            <option value="">全部营业状态</option>
            <option value="OPERATING">营业中</option>
            <option value="SUSPENDED">停业中</option>
            <option value="CLOSED_PERMANENTLY">永久关闭</option>
          </select>
        </div>
        <button class="search-btn" @click="loadMerchants">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"></circle>
            <path d="M21 21l-4.35-4.35"></path>
          </svg>
          <span>搜索</span>
        </button>
      </div>

      <div class="table-container">
        <table class="merchant-table">
          <thead>
            <tr>
              <th>商家编号</th>
              <th>店名</th>
              <th>类型</th>
              <th>菜系</th>
              <th>地址</th>
              <th>人均消费</th>
              <th>评分</th>
              <th>平台状态</th>
              <th>营业状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="merchant in merchants" :key="merchant.id">
              <td>{{ merchant.merchantCode }}</td>
              <td class="name-cell">{{ merchant.name }}</td>
              <td>{{ merchant.category }}</td>
              <td>{{ merchant.cuisine || '-' }}</td>
              <td class="address-cell">{{ merchant.address }}</td>
              <td>{{ merchant.averagePrice ? '￥' + merchant.averagePrice : '-' }}</td>
              <td>{{ merchant.rating || '0' }}</td>
              <td>
                <span :class="['status-tag', getPlatformStatusClass(merchant.platformStatus)]">
                  {{ getPlatformStatusText(merchant.platformStatus) }}
                </span>
              </td>
              <td>
                <span :class="['status-tag', getOperationStatusClass(merchant.operationStatus)]">
                  {{ getOperationStatusText(merchant.operationStatus) }}
                </span>
              </td>
              <td class="actions-cell">
                <button class="action-btn edit-btn" @click="openEditModal(merchant)">
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M17 3a2.828 2.828 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5L17 3z"></path>
                  </svg>
                </button>
                <button 
                  class="action-btn status-btn" 
                  @click="openStatusModal(merchant)"
                  :title="getChangeStatusTitle(merchant)"
                >
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="3"></circle>
                    <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"></path>
                  </svg>
                </button>
              </td>
            </tr>
            <tr v-if="loading">
              <td colspan="10" class="loading-cell">
                <div class="loading-spinner"></div>
                <span>加载中...</span>
              </td>
            </tr>
            <tr v-if="!loading && merchants.length === 0">
              <td colspan="10" class="empty-cell">暂无商家数据</td>
            </tr>
          </tbody>
        </table>

        <div class="pagination" v-if="total > 0">
          <button 
            class="pagination-btn" 
            :disabled="currentPage <= 1"
            @click="goToPage(currentPage - 1)"
          >
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M15 19l-7-7 7-7"></path>
            </svg>
          </button>
          <span class="pagination-info">第 {{ currentPage }} / {{ totalPages }} 页</span>
          <button 
            class="pagination-btn" 
            :disabled="currentPage >= totalPages"
            @click="goToPage(currentPage + 1)"
          >
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 5l7 7-7 7"></path>
            </svg>
          </button>
          <span class="pagination-total">共 {{ total }} 条记录</span>
        </div>
      </div>
    </div>

    <div v-if="activeTab === 'add'" class="tab-content">
      <div class="form-container">
        <h2 class="form-title">新增商家</h2>
        <div class="form-grid">
          <div class="form-item">
            <label>商家编号 <span class="required">*</span></label>
            <input 
              type="text" 
              v-model="formData.merchantCode" 
              class="form-input"
              placeholder="请输入商家编号"
            />
          </div>
          <div class="form-item">
            <label>店名 <span class="required">*</span></label>
            <input 
              type="text" 
              v-model="formData.name" 
              class="form-input"
              placeholder="请输入店名"
            />
          </div>
          <div class="form-item">
            <label>商家类型 <span class="required">*</span></label>
            <select v-model="formData.category" class="form-select">
              <option value="">请选择商家类型</option>
              <option value="中餐厅">中餐厅</option>
              <option value="西餐厅">西餐厅</option>
              <option value="日料">日料</option>
              <option value="韩餐">韩餐</option>
              <option value="火锅">火锅</option>
              <option value="快餐">快餐</option>
              <option value="甜品">甜品</option>
              <option value="咖啡厅">咖啡厅</option>
              <option value="烧烤">烧烤</option>
              <option value="其他">其他</option>
            </select>
          </div>
          <div class="form-item">
            <label>菜系</label>
            <input 
              type="text" 
              v-model="formData.cuisine" 
              class="form-input"
              placeholder="请输入菜系"
            />
          </div>
          <div class="form-item">
            <label>地址 <span class="required">*</span></label>
            <input 
              type="text" 
              v-model="formData.address" 
              class="form-input"
              placeholder="请输入地址"
            />
          </div>
          <div class="form-item">
            <label>联系电话</label>
            <input 
              type="text" 
              v-model="formData.phone" 
              class="form-input"
              placeholder="请输入联系电话"
            />
          </div>
          <div class="form-item">
            <label>经度</label>
            <input 
              type="text" 
              v-model="formData.longitude" 
              class="form-input"
              placeholder="请输入经度"
            />
          </div>
          <div class="form-item">
            <label>纬度</label>
            <input 
              type="text" 
              v-model="formData.latitude" 
              class="form-input"
              placeholder="请输入纬度"
            />
          </div>
          <div class="form-item">
            <label>人均消费</label>
            <input 
              type="number" 
              v-model="formData.averagePrice" 
              class="form-input"
              placeholder="请输入人均消费"
              min="0"
            />
          </div>
          <div class="form-item">
            <label>综合评分</label>
            <input 
              type="number" 
              v-model="formData.rating" 
              class="form-input"
              placeholder="请输入综合评分"
              min="0"
              max="5"
              step="0.1"
            />
          </div>
          <div class="form-item">
            <label>平台状态 <span class="required">*</span></label>
            <select v-model="formData.platformStatus" class="form-select">
              <option value="ACTIVE">正常</option>
              <option value="DISABLED">禁用</option>
              <option value="ARCHIVED">归档</option>
            </select>
          </div>
          <div class="form-item">
            <label>营业状态 <span class="required">*</span></label>
            <select v-model="formData.operationStatus" class="form-select">
              <option value="OPERATING">营业中</option>
              <option value="SUSPENDED">停业中</option>
              <option value="CLOSED_PERMANENTLY">永久关闭</option>
            </select>
          </div>
          <div class="form-item full-width">
            <label>环境标签（逗号分隔）</label>
            <input 
              type="text" 
              v-model="formData.environmentTagsInput" 
              class="form-input"
              placeholder="如：朋友聚会,环境舒适,适合拍照"
            />
          </div>
          <div class="form-item full-width">
            <label>商家描述</label>
            <textarea 
              v-model="formData.description" 
              class="form-textarea"
              placeholder="请输入商家描述"
              rows="3"
            ></textarea>
          </div>
        </div>
        <div class="error-message" v-if="errorMessage">{{ errorMessage }}</div>
        <div class="form-actions">
          <button class="btn btn-cancel" @click="resetForm">重置</button>
          <button class="btn btn-primary" @click="submitAddForm">确认新增</button>
        </div>
      </div>
    </div>

    <div v-if="activeTab === 'status'" class="tab-content">
      <div class="status-container">
        <h2 class="form-title">批量状态管理</h2>
        <div class="status-info-card">
          <div class="status-stat">
            <div class="stat-value">{{ totalCount }}</div>
            <div class="stat-label">总商家数</div>
          </div>
          <div class="status-stat">
            <div class="stat-value success">{{ activeCount }}</div>
            <div class="stat-label">正常营业</div>
          </div>
          <div class="status-stat">
            <div class="stat-value warning">{{ disabledCount }}</div>
            <div class="stat-label">已禁用</div>
          </div>
          <div class="status-stat">
            <div class="stat-value danger">{{ suspendedCount }}</div>
            <div class="stat-label">停业中</div>
          </div>
        </div>
        <div class="status-tips">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="#faad14" stroke-width="2">
            <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path>
            <line x1="12" y1="9" x2="12" y2="13"></line>
            <line x1="12" y1="17" x2="12.01" y2="17"></line>
          </svg>
          <span>商家设为禁用或停业后，将不再出现在用户搜索和推荐结果中，但历史评论和分析记录仍可查询。</span>
        </div>
      </div>
    </div>

    <div class="modal-overlay" v-if="showModal" @click="closeModal">
      <div class="modal-content" @click.stop>
        <div class="modal-header">
          <h2>{{ isEditing ? '编辑商家' : '新增商家' }}</h2>
          <button class="close-btn" @click="closeModal">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M18 6L6 18M6 6l12 12"></path>
            </svg>
          </button>
        </div>
        <div class="modal-body">
          <div class="form-grid">
            <div class="form-item">
              <label>商家编号 <span class="required">*</span></label>
              <input 
                type="text" 
                v-model="formData.merchantCode" 
                :disabled="isEditing"
                class="form-input"
                placeholder="请输入商家编号"
              />
            </div>
            <div class="form-item">
              <label>店名 <span class="required">*</span></label>
              <input 
                type="text" 
                v-model="formData.name" 
                class="form-input"
                placeholder="请输入店名"
              />
            </div>
            <div class="form-item">
              <label>商家类型 <span class="required">*</span></label>
              <select v-model="formData.category" class="form-select">
                <option value="">请选择商家类型</option>
                <option value="中餐厅">中餐厅</option>
                <option value="西餐厅">西餐厅</option>
                <option value="日料">日料</option>
                <option value="韩餐">韩餐</option>
                <option value="火锅">火锅</option>
                <option value="快餐">快餐</option>
                <option value="甜品">甜品</option>
                <option value="咖啡厅">咖啡厅</option>
                <option value="烧烤">烧烤</option>
                <option value="其他">其他</option>
              </select>
            </div>
            <div class="form-item">
              <label>菜系</label>
              <input 
                type="text" 
                v-model="formData.cuisine" 
                class="form-input"
                placeholder="请输入菜系"
              />
            </div>
            <div class="form-item">
              <label>地址 <span class="required">*</span></label>
              <input 
                type="text" 
                v-model="formData.address" 
                class="form-input"
                placeholder="请输入地址"
              />
            </div>
            <div class="form-item">
              <label>联系电话</label>
              <input 
                type="text" 
                v-model="formData.phone" 
                class="form-input"
                placeholder="请输入联系电话"
              />
            </div>
            <div class="form-item">
              <label>经度</label>
              <input 
                type="text" 
                v-model="formData.longitude" 
                class="form-input"
                placeholder="请输入经度"
              />
            </div>
            <div class="form-item">
              <label>纬度</label>
              <input 
                type="text" 
                v-model="formData.latitude" 
                class="form-input"
                placeholder="请输入纬度"
              />
            </div>
            <div class="form-item">
              <label>人均消费</label>
              <input 
                type="number" 
                v-model="formData.averagePrice" 
                class="form-input"
                placeholder="请输入人均消费"
                min="0"
              />
            </div>
            <div class="form-item">
              <label>综合评分</label>
              <input 
                type="number" 
                v-model="formData.rating" 
                class="form-input"
                placeholder="请输入综合评分"
                min="0"
                max="5"
                step="0.1"
              />
            </div>
            <div class="form-item">
              <label>平台状态 <span class="required">*</span></label>
              <select v-model="formData.platformStatus" class="form-select">
                <option value="ACTIVE">正常</option>
                <option value="DISABLED">禁用</option>
                <option value="ARCHIVED">归档</option>
              </select>
            </div>
            <div class="form-item">
              <label>营业状态 <span class="required">*</span></label>
              <select v-model="formData.operationStatus" class="form-select">
                <option value="OPERATING">营业中</option>
                <option value="SUSPENDED">停业中</option>
                <option value="CLOSED_PERMANENTLY">永久关闭</option>
              </select>
            </div>
            <div class="form-item full-width">
              <label>环境标签（逗号分隔）</label>
              <input 
                type="text" 
                v-model="formData.environmentTagsInput" 
                class="form-input"
                placeholder="如：朋友聚会,环境舒适,适合拍照"
              />
            </div>
            <div class="form-item full-width">
              <label>商家描述</label>
              <textarea 
                v-model="formData.description" 
                class="form-textarea"
                placeholder="请输入商家描述"
                rows="3"
              ></textarea>
            </div>
          </div>
          <div class="error-message" v-if="errorMessage">{{ errorMessage }}</div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-cancel" @click="closeModal">取消</button>
          <button class="btn btn-primary" @click="submitForm">{{ isEditing ? '保存修改' : '确认新增' }}</button>
        </div>
      </div>
    </div>

    <div class="modal-overlay" v-if="showStatusModal" @click="closeStatusModal">
      <div class="modal-content status-modal" @click.stop>
        <div class="modal-header">
          <h2>修改商家状态</h2>
          <button class="close-btn" @click="closeStatusModal">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M18 6L6 18M6 6l12 12"></path>
            </svg>
          </button>
        </div>
        <div class="modal-body">
          <div class="status-info">
            <div class="status-item">
              <span class="status-label">商家名称</span>
              <span class="status-value">{{ statusMerchant?.name }}</span>
            </div>
            <div class="status-item">
              <span class="status-label">当前平台状态</span>
              <span :class="['status-tag', getPlatformStatusClass(statusMerchant?.platformStatus)]">
                {{ getPlatformStatusText(statusMerchant?.platformStatus) }}
              </span>
            </div>
            <div class="status-item">
              <span class="status-label">当前营业状态</span>
              <span :class="['status-tag', getOperationStatusClass(statusMerchant?.operationStatus)]">
                {{ getOperationStatusText(statusMerchant?.operationStatus) }}
              </span>
            </div>
          </div>
          <div class="form-grid">
            <div class="form-item">
              <label>平台状态</label>
              <select v-model="statusForm.platformStatus" class="form-select">
                <option value="ACTIVE">正常</option>
                <option value="DISABLED">禁用</option>
                <option value="ARCHIVED">归档</option>
              </select>
            </div>
            <div class="form-item">
              <label>营业状态</label>
              <select v-model="statusForm.operationStatus" class="form-select">
                <option value="OPERATING">营业中</option>
                <option value="SUSPENDED">停业中</option>
                <option value="CLOSED_PERMANENTLY">永久关闭</option>
              </select>
            </div>
          </div>
          <div class="warning-message" v-if="isDisabling">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="#faad14" stroke-width="2">
              <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path>
              <line x1="12" y1="9" x2="12" y2="13"></line>
              <line x1="12" y1="17" x2="12.01" y2="17"></line>
            </svg>
            <span>商家设为禁用或停业后，将不再出现在用户搜索和推荐结果中，但历史评论和分析记录仍可查询。</span>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-cancel" @click="closeStatusModal">取消</button>
          <button class="btn btn-primary" @click="submitStatus">{{ '确认修改' }}</button>
        </div>
      </div>
    </div>
  </AdminLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import AdminLayout from '../../components/AdminLayout.vue'
import { 
  getAdminMerchants, 
  createAdminMerchant, 
  updateAdminMerchant, 
  updateAdminMerchantStatus,
  getAdminMerchantStatistics
} from '../../api/adminMerchant'

const activeTab = ref('list')

const sidebarItems = [
  { key: 'list', label: '商家列表', icon: '🏪' },
  { key: 'add', label: '新增商家', icon: '✨' },
  { key: 'status', label: '状态管理', icon: '⚙️' },
]

const merchants = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

const searchKeyword = ref('')
const searchOperationStatus = ref('')

const showModal = ref(false)
const isEditing = ref(false)
const errorMessage = ref('')

const formData = ref({
  id: null,
  merchantCode: '',
  name: '',
  category: '',
  cuisine: '',
  address: '',
  phone: '',
  longitude: '',
  latitude: '',
  averagePrice: '',
  rating: '',
  platformStatus: 'ACTIVE',
  operationStatus: 'OPERATING',
  environmentTags: '',
  environmentTagsInput: '',
  description: ''
})

const showStatusModal = ref(false)
const statusMerchant = ref(null)
const statusForm = ref({
  platformStatus: '',
  operationStatus: ''
})

const totalPages = computed(() => Math.ceil(total.value / pageSize.value))

const isDisabling = computed(() => {
  return statusForm.value.platformStatus === 'DISABLED' || 
         statusForm.value.operationStatus === 'SUSPENDED' ||
         statusForm.value.operationStatus === 'CLOSED_PERMANENTLY'
})

const totalCount = ref(0)
const activeCount = ref(0)
const disabledCount = ref(0)
const suspendedCount = ref(0)

const loadMerchants = async () => {
  loading.value = true
  try {
    const params = {
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      keyword: searchKeyword.value || undefined,
      operationStatus: searchOperationStatus.value || undefined
    }
    const response = await getAdminMerchants(params)
    if (response.success) {
      merchants.value = response.data.value || response.data.records || []
      total.value = response.data.total || 0
    } else {
      console.error('加载商家列表失败:', response.message)
    }
  } catch (error) {
    console.error('加载商家列表失败:', error)
  } finally {
    loading.value = false
  }
}

const goToPage = (page) => {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
  loadMerchants()
}

const openAddModal = () => {
  isEditing.value = false
  errorMessage.value = ''
  formData.value = {
    id: null,
    merchantCode: '',
    name: '',
    category: '',
    cuisine: '',
    address: '',
    phone: '',
    longitude: '',
    latitude: '',
    averagePrice: '',
    rating: '',
    platformStatus: 'ACTIVE',
    operationStatus: 'OPERATING',
    environmentTags: '',
    environmentTagsInput: '',
    description: ''
  }
  showModal.value = true
}

const openEditModal = (merchant) => {
  isEditing.value = true
  errorMessage.value = ''
  formData.value = {
    id: merchant.id,
    merchantCode: merchant.merchantCode,
    name: merchant.name,
    category: merchant.category,
    cuisine: merchant.cuisine || '',
    address: merchant.address,
    phone: merchant.phone || '',
    longitude: merchant.longitude ? merchant.longitude.toString() : '',
    latitude: merchant.latitude ? merchant.latitude.toString() : '',
    averagePrice: merchant.averagePrice ? merchant.averagePrice.toString() : '',
    rating: merchant.rating ? merchant.rating.toString() : '',
    platformStatus: merchant.platformStatus || 'ACTIVE',
    operationStatus: merchant.operationStatus || 'OPERATING',
    environmentTags: merchant.environmentTags || '',
    environmentTagsInput: merchant.environmentTags ? 
      JSON.parse(merchant.environmentTags).join(',') : '',
    description: merchant.description || ''
  }
  showModal.value = true
}

const closeModal = () => {
  showModal.value = false
}

const resetForm = () => {
  formData.value = {
    id: null,
    merchantCode: '',
    name: '',
    category: '',
    cuisine: '',
    address: '',
    phone: '',
    longitude: '',
    latitude: '',
    averagePrice: '',
    rating: '',
    platformStatus: 'ACTIVE',
    operationStatus: 'OPERATING',
    environmentTags: '',
    environmentTagsInput: '',
    description: ''
  }
  errorMessage.value = ''
}

const submitAddForm = async () => {
  errorMessage.value = ''
  
  if (!formData.value.merchantCode.trim()) {
    errorMessage.value = '商家编号不能为空'
    return
  }
  if (!formData.value.name.trim()) {
    errorMessage.value = '店名不能为空'
    return
  }
  if (!formData.value.category) {
    errorMessage.value = '商家类型不能为空'
    return
  }
  if (!formData.value.address.trim()) {
    errorMessage.value = '地址不能为空'
    return
  }

  const submitData = { ...formData.value }
  
  if (formData.value.environmentTagsInput.trim()) {
    const tags = formData.value.environmentTagsInput.split(/[,，]/).map(t => t.trim()).filter(t => t)
    submitData.environmentTags = JSON.stringify(tags)
  } else {
    submitData.environmentTags = JSON.stringify([])
  }

  if (formData.value.averagePrice) {
    submitData.averagePrice = parseFloat(formData.value.averagePrice)
  }
  if (formData.value.rating) {
    submitData.rating = parseFloat(formData.value.rating)
  }
  if (formData.value.longitude) {
    submitData.longitude = parseFloat(formData.value.longitude)
  }
  if (formData.value.latitude) {
    submitData.latitude = parseFloat(formData.value.latitude)
  }

  delete submitData.environmentTagsInput

  try {
    const response = await createAdminMerchant(submitData)
    
    if (response.success) {
      resetForm()
      activeTab.value = 'list'
      loadMerchants()
    } else {
      errorMessage.value = response.message || '操作失败'
    }
  } catch (error) {
    errorMessage.value = '网络请求失败'
    console.error('提交表单失败:', error)
  }
}

const submitForm = async () => {
  errorMessage.value = ''
  
  if (!formData.value.merchantCode.trim()) {
    errorMessage.value = '商家编号不能为空'
    return
  }
  if (!formData.value.name.trim()) {
    errorMessage.value = '店名不能为空'
    return
  }
  if (!formData.value.category) {
    errorMessage.value = '商家类型不能为空'
    return
  }
  if (!formData.value.address.trim()) {
    errorMessage.value = '地址不能为空'
    return
  }
  if (!formData.value.platformStatus) {
    errorMessage.value = '平台状态不能为空'
    return
  }
  if (!formData.value.operationStatus) {
    errorMessage.value = '营业状态不能为空'
    return
  }

  const submitData = { ...formData.value }
  
  if (formData.value.environmentTagsInput.trim()) {
    const tags = formData.value.environmentTagsInput.split(/[,，]/).map(t => t.trim()).filter(t => t)
    submitData.environmentTags = JSON.stringify(tags)
  } else {
    submitData.environmentTags = JSON.stringify([])
  }

  if (formData.value.averagePrice) {
    submitData.averagePrice = parseFloat(formData.value.averagePrice)
  }
  if (formData.value.rating) {
    submitData.rating = parseFloat(formData.value.rating)
  }
  if (formData.value.longitude) {
    submitData.longitude = parseFloat(formData.value.longitude)
  }
  if (formData.value.latitude) {
    submitData.latitude = parseFloat(formData.value.latitude)
  }

  delete submitData.environmentTagsInput

  try {
    let response
    if (isEditing.value) {
      response = await updateAdminMerchant(formData.value.id, submitData)
    } else {
      response = await createAdminMerchant(submitData)
    }
    
    if (response.success) {
      closeModal()
      loadMerchants()
    } else {
      errorMessage.value = response.message || '操作失败'
    }
  } catch (error) {
    errorMessage.value = '网络请求失败'
    console.error('提交表单失败:', error)
  }
}

const openStatusModal = (merchant) => {
  statusMerchant.value = merchant
  statusForm.value = {
    platformStatus: merchant.platformStatus || 'ACTIVE',
    operationStatus: merchant.operationStatus || 'OPERATING'
  }
  showStatusModal.value = true
}

const closeStatusModal = () => {
  showStatusModal.value = false
  statusMerchant.value = null
}

const submitStatus = async () => {
  if (!statusMerchant.value) return
  
  try {
    const response = await updateAdminMerchantStatus(statusMerchant.value.id, {
      platformStatus: statusForm.value.platformStatus,
      operationStatus: statusForm.value.operationStatus
    })
    
    if (response.success) {
      closeStatusModal()
      loadMerchants()
      loadStatistics()
    } else {
      console.error('修改状态失败:', response.message)
    }
  } catch (error) {
    console.error('修改状态失败:', error)
  }
}

const getPlatformStatusClass = (status) => {
  switch (status) {
    case 'ACTIVE': return 'status-active'
    case 'DISABLED': return 'status-disabled'
    case 'ARCHIVED': return 'status-archived'
    default: return ''
  }
}

const getPlatformStatusText = (status) => {
  switch (status) {
    case 'ACTIVE': return '正常'
    case 'DISABLED': return '禁用'
    case 'ARCHIVED': return '归档'
    default: return status || '-'
  }
}

const getOperationStatusClass = (status) => {
  switch (status) {
    case 'OPERATING': return 'status-operating'
    case 'SUSPENDED': return 'status-suspended'
    case 'CLOSED_PERMANENTLY': return 'status-closed'
    default: return ''
  }
}

const getOperationStatusText = (status) => {
  switch (status) {
    case 'OPERATING': return '营业中'
    case 'SUSPENDED': return '停业中'
    case 'CLOSED_PERMANENTLY': return '永久关闭'
    default: return status || '-'
  }
}

const getChangeStatusTitle = (merchant) => {
  return `修改商家状态：${merchant.name}`
}

const loadStatistics = async () => {
  try {
    const response = await getAdminMerchantStatistics()
    if (response.success && response.data) {
      totalCount.value = Number(response.data.total) || 0
      activeCount.value = Number(response.data.activeCount) || 0
      disabledCount.value = Number(response.data.disabledCount) || 0
      suspendedCount.value = Number(response.data.suspendedCount) || 0
    }
  } catch (error) {
    console.error('加载统计数据失败:', error)
  }
}

onMounted(() => {
  loadMerchants()
  loadStatistics()
})
</script>

<style scoped>
.tab-content {
  width: 100%;
}

.search-bar {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
  flex-wrap: wrap;
  padding: 24px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
}

.search-item {
  flex: 1;
  min-width: 180px;
  position: relative;
}

.search-item svg {
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  width: 16px;
  height: 16px;
  color: #909399;
  pointer-events: none;
}

.search-input {
  width: 100%;
  padding: 12px 16px 12px 42px;
  border: 2px solid #e8e8e8;
  border-radius: 12px;
  font-size: 14px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  background: #fafafa;
}

.search-input:focus {
  outline: none;
  border-color: #1890ff;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(24, 144, 255, 0.1);
}

.search-select {
  width: 100%;
  padding: 12px 32px 12px 16px;
  border: 2px solid #e8e8e8;
  border-radius: 12px;
  font-size: 14px;
  background: #fafafa;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 24 24' width='14' height='14' fill='none' stroke='%23909399' stroke-width='2'%3E%3Cpath d='M6 9l6 6 6-6'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
}

.search-select:focus {
  outline: none;
  border-color: #1890ff;
  background-color: #fff;
  box-shadow: 0 0 0 3px rgba(24, 144, 255, 0.1);
}

.search-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 28px;
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
  color: #fff;
  border: none;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.3);
}

.search-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(24, 144, 255, 0.4);
}

.search-btn:active {
  transform: translateY(0);
}

.table-container {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  border: 1px solid #f0f0f0;
}

.merchant-table {
  width: 100%;
  border-collapse: collapse;
}

.merchant-table thead {
  background: linear-gradient(180deg, #fafafa 0%, #f5f5f5 100%);
  position: sticky;
  top: 0;
  z-index: 1;
}

.merchant-table th {
  padding: 16px 20px;
  text-align: left;
  font-weight: 600;
  color: #5a6a7a;
  font-size: 14px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  border-bottom: 2px solid #e8e8e8;
}

.merchant-table td {
  padding: 16px 20px;
  text-align: left;
  font-size: 14px;
  color: #1f2d3d;
  border-bottom: 1px solid #f0f0f0;
  transition: background-color 0.2s;
}

.merchant-table tbody tr:hover td {
  background-color: #f8fcff;
}

.name-cell {
  font-weight: 600;
  color: #1890ff;
}

.address-cell {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #667085;
}

.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.3px;
}

.status-tag::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.status-active {
  background: linear-gradient(135deg, rgba(82, 196, 26, 0.1) 0%, rgba(82, 196, 26, 0.05) 100%);
  color: #52c41a;
}

.status-active::before {
  background: #52c41a;
}

.status-disabled {
  background: linear-gradient(135deg, rgba(255, 77, 79, 0.1) 0%, rgba(255, 77, 79, 0.05) 100%);
  color: #ff4d4f;
}

.status-disabled::before {
  background: #ff4d4f;
}

.status-archived {
  background: linear-gradient(135deg, rgba(144, 147, 153, 0.1) 0%, rgba(144, 147, 153, 0.05) 100%);
  color: #909399;
}

.status-archived::before {
  background: #909399;
}

.status-operating {
  background: linear-gradient(135deg, rgba(82, 196, 26, 0.1) 0%, rgba(82, 196, 26, 0.05) 100%);
  color: #52c41a;
}

.status-operating::before {
  background: #52c41a;
}

.status-suspended {
  background: linear-gradient(135deg, rgba(255, 177, 0, 0.1) 0%, rgba(255, 177, 0, 0.05) 100%);
  color: #ffb100;
}

.status-suspended::before {
  background: #ffb100;
}

.status-closed {
  background: linear-gradient(135deg, rgba(255, 77, 79, 0.1) 0%, rgba(255, 77, 79, 0.05) 100%);
  color: #ff4d4f;
}

.status-closed::before {
  background: #ff4d4f;
}

.actions-cell {
  display: flex;
  gap: 10px;
}

.action-btn {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
}

.action-btn:hover {
  transform: translateY(-2px);
}

.action-btn:active {
  transform: translateY(0);
}

.edit-btn {
  background: linear-gradient(135deg, rgba(24, 144, 255, 0.1) 0%, rgba(24, 144, 255, 0.05) 100%);
  color: #1890ff;
  box-shadow: 0 2px 8px rgba(24, 144, 255, 0.15);
}

.edit-btn:hover {
  background: linear-gradient(135deg, rgba(24, 144, 255, 0.2) 0%, rgba(24, 144, 255, 0.1) 100%);
  box-shadow: 0 4px 16px rgba(24, 144, 255, 0.3);
}

.status-btn {
  background: linear-gradient(135deg, rgba(82, 196, 26, 0.1) 0%, rgba(82, 196, 26, 0.05) 100%);
  color: #52c41a;
  box-shadow: 0 2px 8px rgba(82, 196, 26, 0.15);
}

.status-btn:hover {
  background: linear-gradient(135deg, rgba(82, 196, 26, 0.2) 0%, rgba(82, 196, 26, 0.1) 100%);
  box-shadow: 0 4px 16px rgba(82, 196, 26, 0.3);
}

.loading-cell,
.empty-cell {
  text-align: center;
  padding: 60px !important;
  color: #909399;
}

.loading-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
}

.loading-spinner {
  width: 32px;
  height: 32px;
  border: 4px solid #f0f0f0;
  border-top-color: #1890ff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 20px;
  padding: 24px;
  border-top: 1px solid #f0f0f0;
  background: #fafafa;
}

.pagination-btn {
  width: 40px;
  height: 40px;
  border: 2px solid #e8e8e8;
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #667085;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.pagination-btn:hover:not(:disabled) {
  border-color: #1890ff;
  color: #1890ff;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.2);
}

.pagination-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.pagination-info {
  font-size: 14px;
  color: #5a6a7a;
  font-weight: 500;
}

.pagination-total {
  font-size: 14px;
  color: #909399;
}

.form-container {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
  padding: 32px;
  border: 1px solid #f0f0f0;
}

.form-title {
  font-size: 20px;
  font-weight: 700;
  color: #1f2d3d;
  margin-bottom: 28px;
  padding-bottom: 16px;
  border-bottom: 2px solid #f0f0f0;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-item.full-width {
  grid-column: span 2;
}

.form-item label {
  font-size: 14px;
  font-weight: 600;
  color: #5a6a7a;
}

.required {
  color: #ff4d4f;
  margin-left: 4px;
}

.form-input,
.form-select {
  padding: 14px 16px;
  border: 2px solid #e8e8e8;
  border-radius: 12px;
  font-size: 14px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  background: #fafafa;
}

.form-input:focus,
.form-select:focus {
  outline: none;
  border-color: #1890ff;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(24, 144, 255, 0.1);
}

.form-input:disabled {
  background: #f0f0f0;
  color: #909399;
  cursor: not-allowed;
}

.form-select {
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 24 24' width='14' height='14' fill='none' stroke='%23909399' stroke-width='2'%3E%3Cpath d='M6 9l6 6 6-6'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 14px center;
  cursor: pointer;
}

.form-textarea {
  padding: 14px 16px;
  border: 2px solid #e8e8e8;
  border-radius: 12px;
  font-size: 14px;
  resize: vertical;
  min-height: 100px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  background: #fafafa;
}

.form-textarea:focus {
  outline: none;
  border-color: #1890ff;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(24, 144, 255, 0.1);
}

.error-message {
  margin-top: 20px;
  padding: 16px;
  background: linear-gradient(135deg, rgba(255, 77, 79, 0.1) 0%, rgba(255, 77, 79, 0.05) 100%);
  color: #ff4d4f;
  border-radius: 12px;
  font-size: 14px;
  border-left: 4px solid #ff4d4f;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 16px;
  margin-top: 32px;
  padding-top: 24px;
  border-top: 2px solid #f0f0f0;
}

.status-container {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
  padding: 32px;
  border: 1px solid #f0f0f0;
}

.status-info-card {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
  margin-bottom: 28px;
}

.status-stat {
  text-align: center;
  padding: 24px;
  background: linear-gradient(135deg, #fafafa 0%, #f5f5f5 100%);
  border-radius: 16px;
  border: 1px solid #f0f0f0;
  transition: all 0.3s;
}

.status-stat:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

.stat-value {
  font-size: 36px;
  font-weight: 700;
  color: #1f2d3d;
}

.stat-value.success {
  color: #52c41a;
}

.stat-value.warning {
  color: #ffb100;
}

.stat-value.danger {
  color: #ff4d4f;
}

.stat-label {
  font-size: 14px;
  color: #667085;
  margin-top: 8px;
  font-weight: 500;
}

.status-tips {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 20px;
  background: linear-gradient(135deg, rgba(250, 173, 20, 0.1) 0%, rgba(250, 173, 20, 0.05) 100%);
  color: #d48806;
  border-radius: 16px;
  font-size: 14px;
  border-left: 4px solid #faad14;
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
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.modal-content {
  background: #fff;
  border-radius: 16px;
  width: 90%;
  max-width: 700px;
  max-height: 90vh;
  overflow: hidden;
}

.status-modal {
  max-width: 500px;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid #f0f0f0;
}

.modal-header h2 {
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
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #666;
  transition: all 0.2s;
}

.close-btn:hover {
  background: #e8e8e8;
}

.modal-body {
  padding: 24px;
  max-height: 60vh;
  overflow-y: auto;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 20px 24px;
  border-top: 1px solid #f0f0f0;
}

.btn {
  padding: 10px 24px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-cancel {
  background: #f5f5f5;
  color: #666;
  border: none;
}

.btn-cancel:hover {
  background: #e8e8e8;
}

.btn-primary {
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
  color: #fff;
  border: none;
}

.btn-primary:hover {
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.3);
}

.warning-message {
  margin-top: 16px;
  padding: 12px;
  background: rgba(250, 173, 20, 0.1);
  color: #faad14;
  border-radius: 8px;
  font-size: 14px;
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.status-info {
  margin-bottom: 20px;
  padding: 16px;
  background: #fafafa;
  border-radius: 12px;
}

.status-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.status-item:last-child {
  border-bottom: none;
}

.status-label {
  font-size: 14px;
  color: #667085;
}

.status-value {
  font-size: 14px;
  font-weight: 600;
  color: #1f2d3d;
}

@media (max-width: 1200px) {
  .main-content {
    margin-left: 0;
  }
  
  .sidebar {
    display: none;
  }
}

@media (max-width: 768px) {
  .search-bar {
    flex-direction: column;
  }
  
  .search-item {
    width: 100%;
  }
  
  .form-grid {
    grid-template-columns: 1fr;
  }
  
  .form-item.full-width {
    grid-column: span 1;
  }
  
  .modal-content {
    width: 95%;
  }
  
  .merchant-table {
    display: block;
    overflow-x: auto;
  }
  
  .status-info-card {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>