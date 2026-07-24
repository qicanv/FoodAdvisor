<template>
  <AdminLayout title="敏感话题预警" subtitle="食品安全、集中投诉、服务纠纷等风险事件实时监控">
    <!-- 顶部操作栏 -->
    <div class="page-header">
      <div class="header-left">
        <el-button type="primary" @click="handleDetect" :loading="detecting">
          <el-icon><Search /></el-icon> 立即检测
        </el-button>
        <el-tag v-if="pendingCount > 0" type="danger" effect="dark" round>
          {{ pendingCount }} 条待处理
        </el-tag>
      </div>
    </div>

    <!-- 检测结果提示 -->
    <el-alert
      v-if="detectResult"
      :title="detectResult"
      type="success" :closable="true" show-icon
      @close="detectResult = null"
      style="margin-bottom: 16px"
    />

    <!-- 筛选条件 -->
    <el-card style="margin-bottom: 16px">
      <el-form :model="filters" inline>
        <el-form-item label="话题类型">
          <el-select v-model="filters.topicType" placeholder="全部" clearable style="width: 150px" @change="handleSearch">
            <el-option label="食品安全" value="FOOD_SAFETY" />
            <el-option label="卫生问题" value="HYGIENE" />
            <el-option label="集中投诉" value="CONCENTRATED_COMPLAINT" />
            <el-option label="严重服务纠纷" value="SERVICE_DISPUTE" />
          </el-select>
        </el-form-item>
        <el-form-item label="风险等级">
          <el-select v-model="filters.riskLevel" placeholder="全部" clearable style="width: 120px" @change="handleSearch">
            <el-option label="高风险" value="HIGH" />
            <el-option label="中风险" value="MEDIUM" />
            <el-option label="低风险" value="LOW" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 120px" @change="handleSearch">
            <el-option label="待处理" value="PENDING" />
            <el-option label="处理中" value="PROCESSING" />
            <el-option label="已处理" value="RESOLVED" />
            <el-option label="已忽略" value="DISMISSED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 预警表格 -->
    <el-card>
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="merchantName" label="商家" width="120" show-overflow-tooltip />
        <el-table-column label="话题类型" width="120">
          <template #default="{ row }">
            <el-tag :type="topicTagType(row.topicType)" size="small">
              {{ row.topicTypeName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="风险等级" width="90">
          <template #default="{ row }">
            <el-tag :type="riskTagType(row.riskLevel)" size="small">
              {{ row.riskLevelName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reviewCount" label="涉及评价" width="80" align="center" />
        <el-table-column label="关键词" min-width="180">
          <template #default="{ row }">
            <el-tag
              v-for="(kw, idx) in (row.keywords || []).slice(0, 5)"
              :key="idx"
              size="small"
              style="margin: 2px"
              type="info"
            >{{ kw }}</el-tag>
            <el-tag v-if="(row.keywords || []).length > 5" size="small" type="info" style="margin: 2px">
              +{{ row.keywords.length - 5 }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="firstOccurredAt" label="首次出现" width="170">
          <template #default="{ row }">{{ formatTime(row.firstOccurredAt) }}</template>
        </el-table-column>
        <el-table-column prop="lastOccurredAt" label="最近出现" width="170">
          <template #default="{ row }">{{ formatTime(row.lastOccurredAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ row.statusName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openDetail(row.id)">详情</el-button>
            <el-button
              v-if="row.status === 'PENDING' || row.status === 'PROCESSING'"
              type="warning" link size="small"
              @click="openHandleDialog(row)"
            >处理</el-button>
            <span v-else style="color: #999; font-size: 12px">
              {{ row.handledUsername || '已处理' }}
            </span>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top: 16px; display: flex; justify-content: flex-end">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadData"
          @size-change="loadData"
        />
      </div>
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailDialog.visible" title="预警详情" width="900px" top="30px">
      <div v-loading="detailDialog.loading">
        <el-descriptions :column="3" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item label="商家">{{ detailDialog.merchantName }}</el-descriptions-item>
          <el-descriptions-item label="话题类型">
            <el-tag :type="topicTagType(detailDialog.topicType)" size="small">
              {{ detailDialog.topicTypeName }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="风险等级">
            <el-tag :type="riskTagType(detailDialog.riskLevel)" size="small">
              {{ detailDialog.riskLevelName }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="涉及评价数">{{ detailDialog.reviewCount }}</el-descriptions-item>
          <el-descriptions-item label="首次出现">{{ formatTime(detailDialog.firstOccurredAt) }}</el-descriptions-item>
          <el-descriptions-item label="最近出现">{{ formatTime(detailDialog.lastOccurredAt) }}</el-descriptions-item>
          <el-descriptions-item label="处理状态">
            <el-tag :type="statusTagType(detailDialog.status)" size="small">
              {{ detailDialog.statusName }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="处理人">{{ detailDialog.handledUsername || '-' }}</el-descriptions-item>
          <el-descriptions-item label="处理时间">{{ formatTime(detailDialog.handledAt) || '-' }}</el-descriptions-item>
          <el-descriptions-item label="关键词" :span="3">
            <el-tag v-for="(kw, idx) in (detailDialog.keywords || [])" :key="idx" size="small" style="margin: 2px">
              {{ kw }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="处理备注" :span="3">{{ detailDialog.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 关联评价列表 -->
        <h4 style="margin: 16px 0 12px; color: #333">关联评价</h4>
        <el-table :data="detailDialog.reviews" stripe max-height="400" style="width: 100%">
          <el-table-column prop="reviewId" label="评价ID" width="80" />
          <el-table-column prop="reviewRating" label="评分" width="60" align="center">
            <template #default="{ row }">
              <span :style="{ color: row.reviewRating <= 2 ? '#f56c6c' : '#e6a23c' }">
                {{ row.reviewRating ?? '-' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="reviewContent" label="评价内容" min-width="250" show-overflow-tooltip />
          <el-table-column prop="evidenceExcerpt" label="匹配片段" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <span style="color: #e6a23c">{{ row.evidenceExcerpt || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="reviewCreatedAt" label="评价时间" width="170">
            <template #default="{ row }">{{ formatTime(row.reviewCreatedAt) }}</template>
          </el-table-column>
        </el-table>
      </div>

      <template #footer>
        <el-button @click="detailDialog.visible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 处理对话框 -->
    <el-dialog v-model="handleDialog.visible" title="处理预警" width="500px">
      <el-descriptions :column="1" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="商家">{{ handleDialog.merchantName }}</el-descriptions-item>
        <el-descriptions-item label="话题">
          <el-tag :type="topicTagType(handleDialog.topicType)" size="small">
            {{ handleDialog.topicTypeName }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="风险等级">
          <el-tag :type="riskTagType(handleDialog.riskLevel)" size="small">
            {{ handleDialog.riskLevelName }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <el-form label-width="80px">
        <el-form-item label="处理操作" required>
          <el-radio-group v-model="handleDialog.targetStatus">
            <el-radio value="PROCESSING">标记为处理中</el-radio>
            <el-radio value="RESOLVED">标记为已处理</el-radio>
            <el-radio value="DISMISSED">忽略此预警</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="handleDialog.remark"
            type="textarea" :rows="3"
            placeholder="处理备注（选填）"
            maxlength="500" show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="handleDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitHandle" :loading="handleDialog.submitting">
          确认
        </el-button>
      </template>
    </el-dialog>
  </AdminLayout>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import AdminLayout from '@/components/AdminLayout.vue'
import {
  getSensitiveAlerts,
  getSensitiveAlertDetail,
  updateAlertStatus,
  detectSensitiveTopics,
  getPendingCount
} from '@/api/sensitiveAlert'

// ---- 数据 ----
const loading = ref(false)
const detecting = ref(false)
const detectResult = ref(null)
const pendingCount = ref(0)
const tableData = ref([])

const filters = reactive({
  topicType: null,
  riskLevel: null,
  status: null,
})

const pagination = reactive({
  pageNum: 1, pageSize: 20, total: 0,
})

// ---- 详情对话框 ----
const detailDialog = reactive({
  visible: false,
  loading: false,
  merchantName: '',
  topicType: '',
  topicTypeName: '',
  riskLevel: '',
  riskLevelName: '',
  reviewCount: 0,
  keywords: [],
  firstOccurredAt: null,
  lastOccurredAt: null,
  status: '',
  statusName: '',
  handledUsername: '',
  handledAt: null,
  remark: '',
  reviews: [],
})

// ---- 处理对话框 ----
const handleDialog = reactive({
  visible: false,
  submitting: false,
  alertId: null,
  merchantName: '',
  topicType: '',
  topicTypeName: '',
  riskLevel: '',
  riskLevelName: '',
  targetStatus: 'RESOLVED',
  remark: '',
})

// ---- 工具函数 ----
const topicTagType = (t) => ({
  FOOD_SAFETY: 'danger', HYGIENE: 'warning', CONCENTRATED_COMPLAINT: 'primary', SERVICE_DISPUTE: 'danger'
}[t] || 'info')
const riskTagType = (l) => ({ HIGH: 'danger', MEDIUM: 'warning', LOW: 'info' }[l] || 'info')
const statusTagType = (s) => ({
  PENDING: 'danger', PROCESSING: 'warning', RESOLVED: 'success', DISMISSED: 'info'
}[s] || 'info')

const formatTime = (t) => {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN')
}

// ---- 加载列表 ----
const loadData = async () => {
  loading.value = true
  try {
    const res = await getSensitiveAlerts({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      topicType: filters.topicType || undefined,
      riskLevel: filters.riskLevel || undefined,
      status: filters.status || undefined,
    })
    if (res.success && res.data) {
      tableData.value = res.data.records || []
      pagination.total = res.data.total || 0
    }
  } catch (e) {
    console.error('loadData error:', e)
  } finally {
    loading.value = false
  }
}

// ---- 加载待处理数量 ----
const loadPendingCount = async () => {
  try {
    const res = await getPendingCount()
    if (res.success) pendingCount.value = res.data || 0
  } catch (e) { /* ignore */ }
}

// ---- 筛选 ----
const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  filters.topicType = null
  filters.riskLevel = null
  filters.status = null
  pagination.pageNum = 1
  loadData()
}

// ---- 手动检测 ----
const handleDetect = async () => {
  detecting.value = true
  try {
    const res = await detectSensitiveTopics({ threshold: 3 })
    if (res.success) {
      const count = Array.isArray(res.data) ? res.data.length : 0
      detectResult.value = `检测完成，共生成/更新 ${count} 条预警`
      ElMessage.success(detectResult.value)
      await loadData()
      await loadPendingCount()
    }
  } catch (e) {
    console.error('detect error:', e)
    ElMessage.error('检测失败')
  } finally {
    detecting.value = false
  }
}

// ---- 打开详情 ----
const openDetail = async (id) => {
  detailDialog.visible = true
  detailDialog.loading = true
  try {
    const res = await getSensitiveAlertDetail(id)
    if (res.success && res.data) {
      const d = res.data
      detailDialog.merchantName = d.merchantName || ''
      detailDialog.topicType = d.topicType || ''
      detailDialog.topicTypeName = d.topicTypeName || ''
      detailDialog.riskLevel = d.riskLevel || ''
      detailDialog.riskLevelName = d.riskLevelName || ''
      detailDialog.reviewCount = d.reviewCount || 0
      detailDialog.keywords = d.keywords || []
      detailDialog.firstOccurredAt = d.firstOccurredAt
      detailDialog.lastOccurredAt = d.lastOccurredAt
      detailDialog.status = d.status || ''
      detailDialog.statusName = d.statusName || ''
      detailDialog.handledUsername = d.handledUsername || ''
      detailDialog.handledAt = d.handledAt
      detailDialog.remark = d.remark || ''
      detailDialog.reviews = d.reviews || []
    }
  } catch (e) {
    console.error('openDetail error:', e)
    ElMessage.error('加载详情失败')
  } finally {
    detailDialog.loading = false
  }
}

// ---- 打开处理对话框 ----
const openHandleDialog = (row) => {
  handleDialog.visible = true
  handleDialog.alertId = row.id
  handleDialog.merchantName = row.merchantName || ''
  handleDialog.topicType = row.topicType || ''
  handleDialog.topicTypeName = row.topicTypeName || ''
  handleDialog.riskLevel = row.riskLevel || ''
  handleDialog.riskLevelName = row.riskLevelName || ''
  handleDialog.targetStatus = 'RESOLVED'
  handleDialog.remark = ''
}

// ---- 提交处理 ----
const submitHandle = async () => {
  if (!handleDialog.targetStatus) {
    ElMessage.warning('请选择处理操作')
    return
  }
  handleDialog.submitting = true
  try {
    const res = await updateAlertStatus(handleDialog.alertId, {
      status: handleDialog.targetStatus,
      remark: handleDialog.remark,
    })
    if (res.success) {
      ElMessage.success('处理完成')
      handleDialog.visible = false
      await loadData()
      await loadPendingCount()
    } else {
      ElMessage.error(res.message || '处理失败')
    }
  } catch (e) {
    console.error('submitHandle error:', e)
    ElMessage.error('处理失败')
  } finally {
    handleDialog.submitting = false
  }
}

// ---- 初始化 ----
onMounted(() => {
  loadData()
  loadPendingCount()
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
</style>
