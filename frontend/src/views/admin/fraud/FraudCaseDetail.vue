<template>
  <AdminLayout title="刷评案例详情" subtitle="查看命中规则、关联评价并进行人工复核">
    <div class="page-header">
      <el-button @click="goBack" :icon="ArrowLeft">返回列表</el-button>
    </div>

    <div v-loading="loading">
      <!-- 案例基本信息 -->
      <el-card style="margin-bottom: 16px">
        <template #header><span>案例信息</span></template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="案例ID">{{ detail.caseId }}</el-descriptions-item>
          <el-descriptions-item label="商家">{{ detail.merchantName }}</el-descriptions-item>
          <el-descriptions-item label="规则类型">
            <el-tag>{{ detail.ruleTypeText }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="风险等级">
            <el-tag :type="riskTagType(detail.riskLevel)">{{ riskLevelText(detail.riskLevel) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(detail.status)">{{ detail.statusText }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="检测时间">{{ formatTime(detail.detectedAt) }}</el-descriptions-item>
          <el-descriptions-item label="检测摘要" :span="2">{{ detail.summary }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 命中规则详情 -->
      <el-card style="margin-bottom: 16px">
        <template #header><span>命中规则详情</span></template>
        <el-descriptions :column="2" border v-if="detail.matchedRuleSnapshot">
          <el-descriptions-item
            v-for="(value, key) in formatRuleSnapshot(detail.matchedRuleSnapshot)"
            :key="key"
            :label="key"
          >
            {{ value }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 关联评价列表 -->
      <el-card style="margin-bottom: 16px">
        <template #header>
          <span>关联可疑评价（{{ detail.relatedReviews ? detail.relatedReviews.length : 0 }}条）</span>
        </template>
        <el-table :data="detail.relatedReviews || []" stripe style="width: 100%" max-height="500">
          <el-table-column prop="reviewId" label="评价ID" width="80" />
          <el-table-column prop="userNickname" label="用户" width="120" />
          <el-table-column prop="rating" label="评分" width="70" />
          <el-table-column prop="content" label="评价内容" min-width="260" show-overflow-tooltip />
          <el-table-column label="风险等级" width="90">
            <template #default="{ row }">
              <el-tag :type="riskTagType(row.riskLevel)" size="small">
                {{ riskLevelText(row.riskLevel) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="发表时间" width="180">
            <template #default="{ row }">
              {{ formatTime(row.createdAt) }}
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 复核记录 -->
      <el-card style="margin-bottom: 16px" v-if="detail.reviewHistory && detail.reviewHistory.reviewedAt">
        <template #header><span>复核记录</span></template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="复核人员">{{ detail.reviewHistory.reviewedByName || detail.reviewHistory.reviewedBy }}</el-descriptions-item>
          <el-descriptions-item label="复核时间">{{ formatTime(detail.reviewHistory.reviewedAt) }}</el-descriptions-item>
          <el-descriptions-item label="复核结论">
            <el-tag :type="conclusionTagType(detail.reviewHistory.reviewConclusion)">
              {{ detail.reviewHistory.reviewConclusionText }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="复核备注">{{ detail.reviewHistory.reviewRemark || '无' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 人工复核表单（仅待复核状态显示） -->
      <el-card v-if="detail.status === 'PENDING_REVIEW'">
        <template #header><span>人工复核</span></template>
        <el-form :model="reviewForm" label-width="100px" style="max-width: 600px">
          <el-form-item label="复核结论" required>
            <el-radio-group v-model="reviewForm.conclusion">
              <el-radio value="CONFIRMED_FRAUD">确认刷评</el-radio>
              <el-radio value="DISMISSED">排除嫌疑</el-radio>
              <el-radio value="NEED_FURTHER_CHECK">需进一步调查</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="复核备注">
            <el-input
              v-model="reviewForm.remark"
              type="textarea"
              :rows="4"
              placeholder="请填写复核备注..."
              maxlength="500"
              show-word-limit
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleSubmitReview" :loading="submitting">
              提交复核
            </el-button>
            <el-button @click="goBack">取消</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 复核已完成提示 -->
      <el-alert
        v-if="detail.status === 'REVIEWED' || detail.status === 'DISMISSED'"
        :title="detail.status === 'REVIEWED' ? '该案例已完成复核' : '该案例已排除嫌疑'"
        :type="detail.status === 'REVIEWED' ? 'success' : 'info'"
        :closable="false"
        show-icon
      />
    </div>
  </AdminLayout>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import AdminLayout from '@/components/AdminLayout.vue'
import { getFraudCaseDetail, submitReview } from '@/api/fraudDetection'

const route = useRoute()
const router = useRouter()
const caseId = route.params.caseId

const loading = ref(false)
const submitting = ref(false)
const detail = ref({})

const reviewForm = reactive({
  conclusion: '',
  remark: '',
})

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

const conclusionTagType = (conclusion) => {
  const map = { CONFIRMED_FRAUD: 'danger', DISMISSED: 'info', NEED_FURTHER_CHECK: 'warning' }
  return map[conclusion] || 'info'
}

const formatTime = (t) => {
  if (!t) return '-'
  return new Date(t).toLocaleString('zh-CN')
}

// 格式化规则快照，转换为中文标签
const formatRuleSnapshot = (snapshot) => {
  if (!snapshot) return {}
  const labelMap = {
    ruleName: '规则名称',
    threshold: '阈值',
    windowMinutes: '时间窗口（分钟）',
    windowHours: '时间窗口（小时）',
    minGroupSize: '最少相似评价数',
    minCount: '最少评价数量',
    sameRatingRatio: '评分集中比例阈值',
    actualCount: '实际触发数量',
    actualGroupSize: '实际相似评价数',
    actualRatio: '实际评分集中比例',
    totalCount: '总评价数',
    dominantRating: '主导评分',
    dominantCount: '主导评分数',
    userId: '用户ID',
  }
  const result = {}
  for (const [key, value] of Object.entries(snapshot)) {
    const label = labelMap[key] || key
    result[label] = value
  }
  return result
}

const loadDetail = async () => {
  loading.value = true
  try {
    const res = await getFraudCaseDetail(caseId)
    detail.value = res.data || res
  } catch (e) {
    ElMessage.error('加载案例详情失败')
    router.push('/admin/fraud-cases')
  } finally {
    loading.value = false
  }
}

const handleSubmitReview = async () => {
  if (!reviewForm.conclusion) {
    ElMessage.warning('请选择复核结论')
    return
  }
  submitting.value = true
  try {
    await submitReview(caseId, {
      conclusion: reviewForm.conclusion,
      remark: reviewForm.remark,
    })
    ElMessage.success('复核完成')
    await loadDetail()
  } catch (e) {
    const msg = e.response?.data?.message || '复核提交失败'
    ElMessage.error(msg)
  } finally {
    submitting.value = false
  }
}

const goBack = () => {
  router.push('/admin/fraud-cases')
}

onMounted(() => {
  loadDetail()
})
</script>

<style scoped>
.page-header {
  margin-bottom: 16px;
}
</style>
