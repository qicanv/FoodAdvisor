<template>
  <div class="audit-log-page">
    <div class="page-header">
      <div>
        <h1>系统审计日志</h1>
        <p>查询用户登录、管理员操作、AI 调用、接口异常等系统日志。</p>
      </div>

      <el-button type="primary" :loading="loading" @click="loadAuditLogs">
        刷新
      </el-button>
    </div>

    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="操作类型">
          <el-select
            v-model="queryForm.operationType"
            placeholder="全部类型"
            clearable
            style="width: 200px"
          >
            <el-option label="登录审计" value="LOGIN" />
            <el-option label="管理员操作" value="ADMIN_OPERATION" />
            <el-option label="AI 调用" value="AI_CALL" />
            <el-option label="接口异常" value="API_EXCEPTION" />
            <el-option label="数据导入" value="DATA_IMPORT" />
            <el-option label="内容审核" value="CONTENT_MODERATION" />
          </el-select>
        </el-form-item>

        <el-form-item label="模块">
          <el-input
            v-model.trim="queryForm.module"
            placeholder="例如 AUTH、AI"
            clearable
            style="width: 180px"
            @keyup.enter="loadAuditLogs"
          />
        </el-form-item>

        <el-form-item label="日志级别">
          <el-select
            v-model="queryForm.level"
            placeholder="全部级别"
            clearable
            style="width: 150px"
          >
            <el-option label="信息" value="INFO" />
            <el-option label="警告" value="WARN" />
            <el-option label="错误" value="ERROR" />
          </el-select>
        </el-form-item>

        <el-form-item label="操作人">
          <el-input
            v-model.trim="queryForm.operatorUsername"
            placeholder="请输入用户名"
            clearable
            style="width: 180px"
            @keyup.enter="loadAuditLogs"
          />
        </el-form-item>

        <el-form-item label="发生时间">
          <el-date-picker
            v-model="queryForm.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            clearable
            style="width: 390px"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            @click="loadAuditLogs"
          >
            查询
          </el-button>

          <el-button @click="resetQuery">
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
      class="error-alert"
    />

    <el-table
      v-loading="loading"
      :data="records"
      border
      stripe
      empty-text="暂无审计日志"
    >
      <el-table-column
        prop="createdAt"
        label="发生时间"
        min-width="190"
      />

      <el-table-column
        prop="operationType"
        label="操作类型"
        min-width="150"
      >
        <template #default="{ row }">
          <el-tag>
            {{ getOperationTypeText(row.operationType) }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column
        prop="module"
        label="模块"
        min-width="120"
      />

      <el-table-column
        prop="operatorUsername"
        label="操作人"
        min-width="130"
      >
        <template #default="{ row }">
          {{ row.operatorUsername || '系统' }}
        </template>
      </el-table-column>

      <el-table-column
        prop="level"
        label="日志级别"
        width="110"
      >
        <template #default="{ row }">
          <el-tag :type="getLevelTagType(row.level)">
            {{ getLevelText(row.level) }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column
        prop="result"
        label="处理结果"
        width="110"
      >
        <template #default="{ row }">
          <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'">
            {{ getResultText(row.result) }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column
        prop="objectType"
        label="对象类型"
        min-width="130"
      />

      <el-table-column
        prop="objectId"
        label="对象 ID"
        min-width="110"
      />

      <el-table-column
        prop="errorMessage"
        label="错误信息"
        min-width="180"
      >
        <template #default="{ row }">
          {{ getErrorMessageText(row) }}
        </template>
      </el-table-column>

      <el-table-column
        label="操作"
        width="90"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            type="primary"
            link
            @click="openDetail(row)"
          >
            详情
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="detailVisible"
      title="审计日志详情"
      width="760px"
    >
      <el-descriptions
        v-if="selectedLog"
        :column="2"
        border
      >
        <el-descriptions-item label="日志 ID">
          {{ selectedLog.id ?? '-' }}
        </el-descriptions-item>

        <el-descriptions-item label="发生时间">
          {{ selectedLog.createdAt || '-' }}
        </el-descriptions-item>

        <el-descriptions-item label="操作类型">
          {{ getOperationTypeText(selectedLog.operationType) }}
        </el-descriptions-item>

        <el-descriptions-item label="模块">
          {{ selectedLog.module || '-' }}
        </el-descriptions-item>

        <el-descriptions-item label="日志级别">
          {{ getLevelText(selectedLog.level) }}
        </el-descriptions-item>

        <el-descriptions-item label="处理结果">
          {{ getResultText(selectedLog.result) }}
        </el-descriptions-item>

        <el-descriptions-item label="操作人 ID">
          {{ selectedLog.operatorUserId ?? '-' }}
        </el-descriptions-item>

        <el-descriptions-item label="操作人">
          {{ selectedLog.operatorUsername || '系统' }}
        </el-descriptions-item>

        <el-descriptions-item label="操作人角色">
          {{ selectedLog.operatorRole || '-' }}
        </el-descriptions-item>

        <el-descriptions-item label="对象类型">
          {{ selectedLog.objectType || '-' }}
        </el-descriptions-item>

        <el-descriptions-item label="对象 ID">
          {{ selectedLog.objectId || '-' }}
        </el-descriptions-item>

        <el-descriptions-item label="业务追踪 ID">
          {{ selectedLog.businessTraceId || '-' }}
        </el-descriptions-item>

        <el-descriptions-item label="请求方法">
          {{ selectedLog.requestMethod || '-' }}
        </el-descriptions-item>

        <el-descriptions-item label="请求地址">
          {{ selectedLog.requestUri || '-' }}
        </el-descriptions-item>

        <el-descriptions-item label="错误代码">
          {{ selectedLog.errorCode || '-' }}
        </el-descriptions-item>

        <el-descriptions-item label="客户端 IP">
          {{ selectedLog.ipAddress || '-' }}
        </el-descriptions-item>

        <el-descriptions-item label="错误信息" :span="2">
          {{ getErrorMessageText(selectedLog) }}
        </el-descriptions-item>

        <el-descriptions-item label="用户代理" :span="2">
          {{ selectedLog.userAgent || '-' }}
        </el-descriptions-item>

        <el-descriptions-item label="元数据" :span="2">
          <pre class="metadata-content">{{ formatMetadata(selectedLog.metadata) }}</pre>
        </el-descriptions-item>
      </el-descriptions>

      <template #footer>
        <el-button @click="detailVisible = false">
          关闭
        </el-button>
      </template>
    </el-dialog>

    <div class="page-summary">
      当前显示 {{ records.length }} 条，共 {{ total }} 条
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getAuditLogs } from '../../api/auditLog'

const loading = ref(false)
const errorMessage = ref('')
const records = ref([])
const total = ref(0)
const detailVisible = ref(false)
const selectedLog = ref(null)

const queryForm = ref({
  operationType: 'LOGIN',
  module: '',
  level: '',
  operatorUsername: '',
  timeRange: [],
})

const operationTypeTextMap = {
  LOGIN: '登录审计',
  ADMIN_OPERATION: '管理员操作',
  AI_CALL: 'AI 调用',
  API_EXCEPTION: '接口异常',
  DATA_IMPORT: '数据导入',
  CONTENT_MODERATION: '内容审核',
}

const levelTextMap = {
  INFO: '信息',
  WARN: '警告',
  ERROR: '错误',
}

const resultTextMap = {
  SUCCESS: '成功',
  FAILURE: '失败',
}

const errorMessageTextMap = {
  LOGIN_INVALID_CREDENTIALS: '用户名、密码或角色不正确',
  LOGIN_USER_DISABLED: '账号已被禁用',
  LOGIN_USER_LOCKED: '账号暂时被锁定',
  UNAUTHORIZED: '未登录或登录状态已失效',
  FORBIDDEN: '当前账号没有访问权限',
}

function getErrorMessageText(row) {
  if (!row) {
    return '-'
  }

  if (row.errorCode && errorMessageTextMap[row.errorCode]) {
    return errorMessageTextMap[row.errorCode]
  }

  return row.errorMessage || '-'
}

function getOperationTypeText(value) {
  return operationTypeTextMap[value] || value || '-'
}

function getLevelText(value) {
  return levelTextMap[value] || value || '-'
}

function getResultText(value) {
  return resultTextMap[value] || value || '-'
}

function getLevelTagType(level) {
  const tagTypeMap = {
    INFO: 'success',
    WARN: 'warning',
    ERROR: 'danger',
  }

  return tagTypeMap[level] || 'info'
}

async function loadAuditLogs() {
  loading.value = true
  errorMessage.value = ''

  try {
    const params = {
      pageNum: 1,
      pageSize: 10,
    }

    if (queryForm.value.operationType) {
      params.operationType = queryForm.value.operationType
    }

    if (queryForm.value.module) {
      params.module = queryForm.value.module
    }

    if (queryForm.value.level) {
      params.level = queryForm.value.level
    }

    if (queryForm.value.operatorUsername) {
      params.operatorUsername = queryForm.value.operatorUsername
    }

    if (
      Array.isArray(queryForm.value.timeRange) &&
      queryForm.value.timeRange.length === 2
    ) {
      params.startTime =
        new Date(queryForm.value.timeRange[0]).toISOString()

      params.endTime =
        new Date(queryForm.value.timeRange[1]).toISOString()
    }

    const result = await getAuditLogs(params)

    if (!result.success) {
      records.value = []
      total.value = 0
      errorMessage.value = result.message || '审计日志查询失败'
      return
    }

    records.value = result.data?.records || []
    total.value = result.data?.total || 0
  } catch (error) {
    records.value = []
    total.value = 0
    errorMessage.value = '加载审计日志时发生异常'
    console.error('加载审计日志失败：', error)
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  queryForm.value = {
    operationType: '',
    module: '',
    level: '',
    operatorUsername: '',
    timeRange: [],
  }

  loadAuditLogs()
}

function openDetail(row) {
  selectedLog.value = row
  detailVisible.value = true
}

function formatMetadata(metadata) {
  if (!metadata) {
    return '-'
  }

  try {
    const parsed =
      typeof metadata === 'string'
        ? JSON.parse(metadata)
        : metadata

    return JSON.stringify(parsed, null, 2)
  } catch (error) {
    return String(metadata)
  }
}

onMounted(() => {
  loadAuditLogs()
})
</script>

<style scoped>
.audit-log-page {
  min-height: 100vh;
  padding: 32px;
  background: #f5f7fa;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.page-header h1 {
  margin: 0 0 8px;
  color: #303133;
}

.page-header p {
  margin: 0;
  color: #909399;
}

.error-alert {
  margin-bottom: 20px;
}

.page-summary {
  margin-top: 16px;
  text-align: right;
  color: #606266;
}

.filter-card {
  margin-bottom: 20px;
}

.filter-card :deep(.el-form-item) {
  margin-bottom: 0;
}

.metadata-content {
  max-height: 260px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  background: #f5f7fa;
  border-radius: 4px;
  font-family: Consolas, Monaco, monospace;
  line-height: 1.6;
}
</style>