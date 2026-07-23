<template>
  <AdminLayout title="刷评检测" subtitle="识别异常评价行为，维护评价数据真实性">
    <!-- 扫描按钮行 -->
    <div class="page-header">
      <el-button type="primary" @click="handleScan" :loading="scanning">
        <el-icon><Search /></el-icon> 立即扫描
      </el-button>
    </div>

    <!-- 扫描结果提示 -->
    <el-alert
      v-if="scanResult"
      :title="scanResult"
      type="success" :closable="true" show-icon
      @close="scanResult = null"
      style="margin-bottom: 16px"
    />

    <!-- 筛选条件 -->
    <el-card style="margin-bottom: 16px">
      <el-form :model="filters" inline>
        <el-form-item label="触发规则">
          <el-select v-model="filters.ruleType" placeholder="全部" clearable style="width: 140px" @change="handleSearch">
            <el-option label="集中评价" value="CONCENTRATION" />
            <el-option label="文本相似" value="SIMILARITY" />
            <el-option label="频繁评价" value="FREQUENCY" />
            <el-option label="评分异常" value="RATING_ANOMALY" />
          </el-select>
        </el-form-item>
        <el-form-item label="风险等级">
          <el-select v-model="filters.riskLevel" placeholder="全部" clearable style="width: 120px" @change="handleSearch">
            <el-option label="高风险" value="HIGH" />
            <el-option label="中风险" value="MEDIUM" />
            <el-option label="低风险" value="LOW" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 130px" @change="handleSearch">
            <el-option label="疑似刷评" value="SUSPICIOUS" />
            <el-option label="待复核" value="PENDING_REVIEW" />
            <el-option label="已复核" value="REVIEWED" />
            <el-option label="已排除" value="DISMISSED" />
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
        <el-table-column prop="caseId" label="案例ID" width="80">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row.caseId)">{{ row.caseId }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="merchantName" label="商家" width="120" />
        <el-table-column label="触发规则" width="130">
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
        <el-table-column label="可疑评论ID" min-width="200">
          <template #default="{ row }">
            <el-tag
              v-for="rid in (row.matchedReviewIds || [])"
              :key="rid"
              size="small"
              style="margin: 2px"
            >{{ rid }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="relatedReviewCount" label="数量" width="60" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ row.statusText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'SUSPICIOUS' || row.status === 'PENDING_REVIEW'"
              type="warning" size="small" @click="openActionDialog(row)"
            >处理</el-button>
            <span v-else style="color: #999; font-size: 13px">
              {{ row.reviewConclusionText || '已处理' }}
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

    <!-- 处理对话框 -->
    <el-dialog v-model="actionDialog.visible" title="处理刷评案例" width="550px">
      <el-descriptions :column="1" border style="margin-bottom: 16px">
        <el-descriptions-item label="商家">{{ actionDialog.merchantName }}</el-descriptions-item>
        <el-descriptions-item label="触发规则">{{ actionDialog.ruleTypeText }}</el-descriptions-item>
        <el-descriptions-item label="风险等级">
          <el-tag :type="riskTagType(actionDialog.riskLevel)" size="small">
            {{ riskLevelText(actionDialog.riskLevel) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="涉及评论">
          <el-tag
            v-for="rid in (actionDialog.reviewIds || [])"
            :key="rid" size="small" style="margin: 2px"
          >{{ rid }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <el-form label-width="80px">
        <el-form-item label="处理结论" required>
          <el-radio-group v-model="actionDialog.conclusion">
            <el-radio value="CONFIRMED_FRAUD">确认刷评（隐藏这些评论）</el-radio>
            <el-radio value="DISMISSED">排除嫌疑</el-radio>
            <el-radio value="NEED_FURTHER_CHECK">需进一步调查</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="actionDialog.remark"
            type="textarea" :rows="3"
            placeholder="复核备注（选填）"
            maxlength="500" show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="actionDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitAction" :loading="actionDialog.submitting">
          确认处理
        </el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗：查看评论内容 + 管理评论状态 -->
    <el-dialog v-model="detailDialog.visible" :title="'案例 #' + detailDialog.caseId + ' 详情'" width="900px" top="30px">
      <div v-loading="detailDialog.loading">
        <!-- 案例基本信息 -->
        <el-descriptions :column="3" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item label="商家">{{ detailDialog.merchantName }}</el-descriptions-item>
          <el-descriptions-item label="触发规则">{{ detailDialog.ruleTypeText }}</el-descriptions-item>
          <el-descriptions-item label="风险等级">
            <el-tag :type="riskTagType(detailDialog.riskLevel)" size="small">{{ riskLevelText(detailDialog.riskLevel) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="检测摘要" :span="3">{{ detailDialog.summary }}</el-descriptions-item>
        </el-descriptions>

        <!-- 批量操作栏 -->
        <div style="margin-bottom: 12px; display: flex; gap: 12px; align-items: center">
          <span style="font-size: 13px; color: #666">
            已选 {{ detailDialog.selectedIds.length }} / {{ detailDialog.reviews.length }} 条
          </span>
          <el-select v-model="detailDialog.batchStatus" placeholder="批量修改状态" size="small" style="width: 150px">
            <el-option label="公开发布" value="PUBLISHED" />
            <el-option label="隐藏" value="HIDDEN" />
            <el-option label="删除" value="DELETED" />
          </el-select>
          <el-button type="primary" size="small" :disabled="!detailDialog.batchStatus || detailDialog.selectedIds.length === 0" @click="batchApplyStatus">
            应用
          </el-button>
        </div>

        <!-- 评论列表 -->
        <el-table :data="detailDialog.reviews" stripe max-height="450" style="width: 100%"
          @selection-change="detailDialog.selectedIds = $event.map(r => r.reviewId)">
          <el-table-column type="selection" width="40" />
          <el-table-column prop="reviewId" label="评论ID" width="70" />
          <el-table-column prop="userNickname" label="用户" width="100" />
          <el-table-column prop="rating" label="评分" width="60" />
          <el-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip />
          <el-table-column label="状态" width="130">
            <template #default="{ row }">
              <el-select v-model="row.reviewStatus" size="small" style="width: 110px" @change="(val) => updateSingleStatus(row.reviewId, val)">
                <el-option label="已发布" value="PUBLISHED" />
                <el-option label="隐藏" value="HIDDEN" />
                <el-option label="已删除" value="DELETED" />
              </el-select>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <template #footer>
        <el-button @click="detailDialog.visible = false">关闭</el-button>
      </template>
    </el-dialog>
  </AdminLayout>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import AdminLayout from '@/components/AdminLayout.vue'
import { triggerScan, getFraudCases, getFraudCaseDetail, submitReview, batchUpdateReviewStatus } from '@/api/fraudDetection'

const loading = ref(false)
const scanning = ref(false)
const scanResult = ref(null)
const tableData = ref([])

const filters = reactive({
  ruleType: null,
  riskLevel: null,
  status: null,
})

const pagination = reactive({
  pageNum: 1, pageSize: 20, total: 0,
})

const detailDialog = reactive({
  visible: false,
  loading: false,
  caseId: null,
  merchantName: '',
  ruleTypeText: '',
  riskLevel: '',
  summary: '',
  reviews: [],
  selectedIds: [],
  batchStatus: null,
})

const actionDialog = reactive({
  visible: false,
  submitting: false,
  caseId: null,
  merchantName: '',
  ruleTypeText: '',
  riskLevel: '',
  reviewIds: [],
  conclusion: '',
  remark: '',
})

// ---- 工具函数 ----
const riskTagType = (l) => ({ HIGH: 'danger', MEDIUM: 'warning', LOW: 'info' }[l] || 'info')
const riskLevelText = (l) => ({ HIGH: '高风险', MEDIUM: '中风险', LOW: '低风险' }[l] || l)
const statusTagType = (s) => ({ SUSPICIOUS: 'warning', PENDING_REVIEW: 'primary', REVIEWED: 'success', DISMISSED: 'info' }[s] || 'info')

// ---- 加载列表 ----
const loadData = async () => {
  loading.value = true
  try {
    const response = await getFraudCases({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      ruleType: filters.ruleType || undefined,
      riskLevel: filters.riskLevel || undefined,
      status: filters.status || undefined,
    })
    if (response.success && response.data) {
      tableData.value = response.data.records || []
      pagination.total = response.data.total || 0
    }
  } catch (e) {
    console.error('loadData error:', e)
  } finally {
    loading.value = false
  }
}

// ---- 立即扫描 ----
const handleScan = async () => {
  scanning.value = true
  try {
    const response = await triggerScan()
    if (response.success && response.data) {
      const n = response.data.totalCasesCreated
      scanResult.value = n > 0
        ? `扫描完成，发现 ${n} 个新的可疑案例`
        : '扫描完成，未发现新的可疑案例（已存在的案例不会被重复创建）'
      ElMessage.success(scanResult.value)
      await loadData()
    }
  } catch (e) {
    console.error('scan error:', e)
    ElMessage.error('扫描失败')
  } finally {
    scanning.value = false
  }
}

// ---- 筛选 ----
const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  filters.ruleType = null
  filters.riskLevel = null
  filters.status = null
  pagination.pageNum = 1
  loadData()
}

// ---- 打开处理对话框 ----
const openActionDialog = (row) => {
  actionDialog.visible = true
  actionDialog.caseId = row.caseId
  actionDialog.merchantName = row.merchantName
  actionDialog.ruleTypeText = row.ruleTypeText
  actionDialog.riskLevel = row.riskLevel
  actionDialog.reviewIds = row.matchedReviewIds || []
  actionDialog.conclusion = ''
  actionDialog.remark = ''
}

// ---- 提交处理 ----
const submitAction = async () => {
  if (!actionDialog.conclusion) {
    ElMessage.warning('请选择处理结论')
    return
  }
  actionDialog.submitting = true
  try {
    const response = await submitReview(actionDialog.caseId, {
      conclusion: actionDialog.conclusion,
      remark: actionDialog.remark,
    })
    if (response.success) {
      ElMessage.success(response.message || '处理完成')
      actionDialog.visible = false
      await loadData()
    } else {
      ElMessage.error(response.message || '处理失败')
    }
  } catch (e) {
    console.error('submitAction error:', e)
    ElMessage.error('处理失败')
  } finally {
    actionDialog.submitting = false
  }
}

// ---- 详情弹窗 ----
const openDetail = async (caseId) => {
  detailDialog.visible = true
  detailDialog.loading = true
  detailDialog.caseId = caseId
  try {
    const response = await getFraudCaseDetail(caseId)
    if (response.success && response.data) {
      const d = response.data
      detailDialog.merchantName = d.merchantName || ''
      detailDialog.ruleTypeText = d.ruleTypeText || ''
      detailDialog.riskLevel = d.riskLevel || ''
      detailDialog.summary = d.summary || ''
      detailDialog.reviews = (d.relatedReviews || []).map(r => ({
        ...r,
        reviewStatus: r.reviewStatus || 'PUBLISHED',
      }))
      detailDialog.selectedIds = []
      detailDialog.batchStatus = null
    }
  } catch (e) {
    console.error('openDetail error:', e)
    ElMessage.error('加载详情失败')
  } finally {
    detailDialog.loading = false
  }
}

const updateSingleStatus = async (reviewId, newStatus) => {
  try {
    const response = await batchUpdateReviewStatus({
      reviewIds: [reviewId],
      newStatus,
    })
    if (response.success) {
      ElMessage.success(`评论 #${reviewId} 状态已更新`)
    }
  } catch (e) {
    console.error('updateSingleStatus error:', e)
    ElMessage.error('状态更新失败')
  }
}

const batchApplyStatus = async () => {
  if (!detailDialog.batchStatus || detailDialog.selectedIds.length === 0) return
  try {
    const response = await batchUpdateReviewStatus({
      reviewIds: detailDialog.selectedIds,
      newStatus: detailDialog.batchStatus,
    })
    if (response.success) {
      ElMessage.success(`已更新 ${detailDialog.selectedIds.length} 条评论`)
      // 更新本地状态
      detailDialog.reviews.forEach(r => {
        if (detailDialog.selectedIds.includes(r.reviewId)) {
          r.reviewStatus = detailDialog.batchStatus
        }
      })
      detailDialog.selectedIds = []
      detailDialog.batchStatus = null
    }
  } catch (e) {
    console.error('batchApplyStatus error:', e)
    ElMessage.error('批量更新失败')
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
}
</style>
