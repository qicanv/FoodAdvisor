<template>
  <AdminLayout title="刷评检测" subtitle="识别异常评价行为，维护评价数据真实性">
    <div class="page-header">
      <el-button type="primary" @click="handleScan" :loading="scanning">
        <el-icon><Search /></el-icon> 立即扫描
      </el-button>
    </div>

    <!-- 扫描结果提示 -->
    <el-alert
      v-if="scanResult"
      :title="scanResult.message"
      type="success"
      :closable="true"
      show-icon
      @close="scanResult = null"
      style="margin-bottom: 16px"
    />

    <!-- 筛选条件 -->
    <el-card style="margin-bottom: 16px">
      <el-form :model="filterForm" inline>
        <el-form-item label="状态">
          <el-select v-model="filterForm.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="疑似刷评" value="SUSPICIOUS" />
            <el-option label="待复核" value="PENDING_REVIEW" />
            <el-option label="已复核" value="REVIEWED" />
            <el-option label="已排除" value="DISMISSED" />
          </el-select>
        </el-form-item>
        <el-form-item label="风险等级">
          <el-select v-model="filterForm.riskLevel" placeholder="全部" clearable style="width: 120px">
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则类型">
          <el-select v-model="filterForm.ruleType" placeholder="全部" clearable style="width: 140px">
            <el-option label="集中评价" value="CONCENTRATION" />
            <el-option label="文本相似" value="SIMILARITY" />
            <el-option label="频繁评价" value="FREQUENCY" />
            <el-option label="评分异常" value="RATING_ANOMALY" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 案例表格 -->
    <el-card>
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="caseId" label="案例ID" width="80" />
        <el-table-column prop="merchantName" label="商家" min-width="140" />
        <el-table-column label="规则类型" width="120">
          <template #default="{ row }">
            <el-tag>{{ row.ruleTypeText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="风险等级" width="90">
          <template #default="{ row }">
            <el-tag :type="riskTagType(row.riskLevel)" size="small">
              {{ riskLevelText(row.riskLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ row.statusText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="summary" label="检测摘要" min-width="260" show-overflow-tooltip />
        <el-table-column prop="relatedReviewCount" label="关联评价数" width="100" />
        <el-table-column prop="detectedAt" label="检测时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.detectedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="viewDetail(row.caseId)">查看详情</el-button>
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
  </AdminLayout>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import AdminLayout from '@/components/AdminLayout.vue'
import { triggerScan, getFraudCases } from '@/api/fraudDetection'

const router = useRouter()

const loading = ref(false)
const scanning = ref(false)
const scanResult = ref(null)
const tableData = ref([])

const filterForm = reactive({
  status: null,
  riskLevel: null,
  ruleType: null,
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0,
})

// 风险等级标签样式
const riskTagType = (level) => {
  const map = { HIGH: 'danger', MEDIUM: 'warning', LOW: 'info' }
  return map[level] || 'info'
}

const riskLevelText = (level) => {
  const map = { HIGH: '高风险', MEDIUM: '中风险', LOW: '低风险' }
  return map[level] || level
}

const statusTagType = (status) => {
  const map = { SUSPICIOUS: 'warning', PENDING_REVIEW: 'primary', REVIEWED: 'success', DISMISSED: 'info' }
  return map[status] || 'info'
}

const formatTime = (t) => {
  if (!t) return '-'
  return new Date(t).toLocaleString('zh-CN')
}

// 加载列表数据
const loadData = async () => {
  loading.value = true
  try {
    const params = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      status: filterForm.status || undefined,
      riskLevel: filterForm.riskLevel || undefined,
      ruleType: filterForm.ruleType || undefined,
    }
    const res = await getFraudCases(params)
    const data = res.data || res
    if (data.records) {
      tableData.value = data.records
      pagination.total = data.total
    }
  } catch (e) {
    ElMessage.error('加载案例列表失败')
  } finally {
    loading.value = false
  }
}

// 手动触发扫描
const handleScan = async () => {
  scanning.value = true
  try {
    const res = await triggerScan()
    const data = res.data || res
    scanResult.value = {
      message: `扫描完成，共发现 ${data.totalCasesCreated} 个可疑案例`,
    }
    ElMessage.success(`扫描完成，共发现 ${data.totalCasesCreated} 个可疑案例`)
    await loadData()
  } catch (e) {
    ElMessage.error('扫描失败')
  } finally {
    scanning.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  filterForm.status = null
  filterForm.riskLevel = null
  filterForm.ruleType = null
  pagination.pageNum = 1
  loadData()
}

const viewDetail = (caseId) => {
  router.push(`/admin/fraud-cases/${caseId}`)
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
}
</style>
