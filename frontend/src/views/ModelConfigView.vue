<template>
  <main class="model-config-page">
    <section class="toolbar">
      <div>
        <h1>模型配置</h1>
        <p>管理不同业务场景使用的大模型服务。</p>
      </div>
      <el-button type="primary" @click="openCreateDialog">新增配置</el-button>
    </section>

    <el-alert
      v-if="errorMessage"
      class="notice"
      type="error"
      :title="errorMessage"
      show-icon
    />

    <el-table
      v-loading="loading"
      :data="configs"
      class="config-table"
      empty-text="暂无模型配置"
    >
      <el-table-column prop="configName" label="配置名称" min-width="140" />
      <el-table-column prop="provider" label="服务商" width="150" />
      <el-table-column prop="modelName" label="模型" min-width="150" />
      <el-table-column prop="baseUrl" label="接口地址" min-width="220" />
      <el-table-column prop="maskedApiKey" label="访问密钥" width="150" />
      <el-table-column prop="timeoutMs" label="超时(ms)" width="110" />
      <el-table-column prop="temperature" label="温度" width="90" />
      <el-table-column prop="maxOutputTokens" label="最大输出" width="110" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="连接测试" min-width="180">
        <template #default="{ row }">
          <el-tag
            v-if="row.lastTestStatus"
            :type="row.lastTestStatus === 'SUCCESS' ? 'success' : 'danger'"
          >
            {{ row.lastTestStatus }}
          </el-tag>
          <span class="test-message">{{ row.lastTestMessage }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
          <el-button size="small" type="primary" @click="runTest(row)">
            测试
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <section class="scene-section">
      <h2>场景绑定</h2>
      <div class="scene-grid">
        <div
          v-for="scene in scenes"
          :key="scene.value"
          class="scene-row"
        >
          <div>
            <strong>{{ scene.label }}</strong>
            <span>{{ getBindingName(scene.value) }}</span>
          </div>
          <el-select
            v-model="sceneSelections[scene.value]"
            placeholder="选择已测试成功的配置"
            @change="value => saveSceneBinding(scene.value, value)"
          >
            <el-option
              v-for="config in testedActiveConfigs"
              :key="config.id"
              :label="`${config.configName} / ${config.modelName}`"
              :value="config.id"
            />
          </el-select>
        </div>
      </div>
    </section>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑模型配置' : '新增模型配置'"
      width="640px"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="120px"
      >
        <el-form-item label="配置名称" prop="configName">
          <el-input v-model="form.configName" />
        </el-form-item>
        <el-form-item label="服务商" prop="provider">
          <el-input v-model="form.provider" />
        </el-form-item>
        <el-form-item label="模型名称" prop="modelName">
          <el-input v-model="form.modelName" />
        </el-form-item>
        <el-form-item label="接口地址" prop="baseUrl">
          <el-input v-model="form.baseUrl" placeholder="https://api.example.com/v1" />
        </el-form-item>
        <el-form-item label="访问密钥" prop="apiKey">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            :placeholder="editingId ? '留空表示不修改密钥' : '请输入访问密钥'"
          />
        </el-form-item>
        <el-form-item label="超时时间" prop="timeoutMs">
          <el-input-number v-model="form.timeoutMs" :min="1000" :max="120000" />
        </el-form-item>
        <el-form-item label="温度参数" prop="temperature">
          <el-input-number
            v-model="form.temperature"
            :min="0"
            :max="2"
            :step="0.1"
          />
        </el-form-item>
        <el-form-item label="最大输出" prop="maxOutputTokens">
          <el-input-number
            v-model="form.maxOutputTokens"
            :min="1"
            :max="32000"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveConfig">保存</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  bindScene,
  createModelConfig,
  getModelConfigs,
  getSceneBindings,
  testModelConfig,
  updateModelConfig,
} from '../api/modelConfig'

const scenes = [
  { label: '探店推荐', value: 'STORE_RECOMMENDATION' },
  { label: '评价摘要', value: 'REVIEW_SUMMARY' },
  { label: '评价回复', value: 'REVIEW_REPLY' },
]

const configs = ref([])
const bindings = ref([])
const sceneSelections = reactive({})
const loading = ref(false)
const errorMessage = ref('')
const dialogVisible = ref(false)
const editingId = ref(null)
const formRef = ref()

const form = reactive({
  configName: '',
  provider: 'OPENAI_COMPATIBLE',
  modelName: '',
  baseUrl: '',
  apiKey: '',
  timeoutMs: 30000,
  temperature: 0.7,
  maxOutputTokens: 1024,
  status: 'ACTIVE',
})

const rules = {
  configName: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  provider: [{ required: true, message: '请输入服务商', trigger: 'blur' }],
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  baseUrl: [{ required: true, message: '请输入接口地址', trigger: 'blur' }],
  apiKey: [
    {
      validator: (_rule, value, callback) => {
        if (!editingId.value && !value) {
          callback(new Error('新增配置必须填写访问密钥'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

const testedActiveConfigs = computed(() =>
  configs.value.filter(
    item => item.status === 'ACTIVE' && item.lastTestStatus === 'SUCCESS'
  )
)

const loadData = async () => {
  loading.value = true
  errorMessage.value = ''

  try {
    const [configResponse, bindingResponse] = await Promise.all([
      getModelConfigs(),
      getSceneBindings(),
    ])
    configs.value = configResponse.data
    bindings.value = bindingResponse.data
    syncSceneSelections()
  } catch (error) {
    errorMessage.value = getErrorMessage(error)
  } finally {
    loading.value = false
  }
}

const syncSceneSelections = () => {
  scenes.forEach(scene => {
    const binding = bindings.value.find(item => item.sceneType === scene.value)
    sceneSelections[scene.value] = binding?.modelConfigId ?? null
  })
}

const getBindingName = sceneType => {
  const binding = bindings.value.find(item => item.sceneType === sceneType)
  return binding
    ? `${binding.modelConfigName} / ${binding.modelName}`
    : '未绑定'
}

const resetForm = () => {
  Object.assign(form, {
    configName: '',
    provider: 'OPENAI_COMPATIBLE',
    modelName: '',
    baseUrl: '',
    apiKey: '',
    timeoutMs: 30000,
    temperature: 0.7,
    maxOutputTokens: 1024,
    status: 'ACTIVE',
  })
}

const openCreateDialog = () => {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

const openEditDialog = row => {
  editingId.value = row.id
  Object.assign(form, {
    configName: row.configName,
    provider: row.provider,
    modelName: row.modelName,
    baseUrl: row.baseUrl,
    apiKey: '',
    timeoutMs: row.timeoutMs,
    temperature: Number(row.temperature),
    maxOutputTokens: row.maxOutputTokens,
    status: row.status,
  })
  dialogVisible.value = true
}

const saveConfig = async () => {
  await formRef.value.validate()
  errorMessage.value = ''

  try {
    const payload = { ...form }
    if (editingId.value && !payload.apiKey) {
      payload.apiKey = null
    }

    if (editingId.value) {
      await updateModelConfig(editingId.value, payload)
    } else {
      await createModelConfig(payload)
    }

    dialogVisible.value = false
    await loadData()
  } catch (error) {
    errorMessage.value = getErrorMessage(error)
  }
}

const runTest = async row => {
  errorMessage.value = ''
  try {
    const response = await testModelConfig(row.id)
    if (!response.data.success) {
      errorMessage.value = response.data.message
    }
    await loadData()
  } catch (error) {
    errorMessage.value = getErrorMessage(error)
  }
}

const saveSceneBinding = async (sceneType, modelConfigId) => {
  errorMessage.value = ''
  try {
    await bindScene({ sceneType, modelConfigId })
    await loadData()
  } catch (error) {
    errorMessage.value = getErrorMessage(error)
    await loadData()
  }
}

const getErrorMessage = error => {
  return error?.response?.data?.message
    || error?.response?.data?.data
    || '操作失败，请检查后端服务和配置参数'
}

onMounted(loadData)
</script>

<style scoped>
.model-config-page {
  max-width: 1320px;
  margin: 32px auto;
  padding: 0 20px 40px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 18px;
}

.toolbar h1,
.scene-section h2 {
  margin: 0;
  color: #1f2d3d;
}

.toolbar p {
  margin: 6px 0 0;
  color: #667085;
}

.notice {
  margin-bottom: 16px;
}

.config-table {
  width: 100%;
}

.test-message {
  display: block;
  margin-top: 4px;
  color: #667085;
  font-size: 12px;
}

.scene-section {
  margin-top: 28px;
  padding-top: 22px;
  border-top: 1px solid #dcdfe6;
}

.scene-grid {
  display: grid;
  gap: 14px;
  margin-top: 14px;
}

.scene-row {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) minmax(260px, 420px);
  align-items: center;
  gap: 18px;
  padding: 14px 0;
  border-bottom: 1px solid #ebeef5;
}

.scene-row span {
  display: block;
  margin-top: 4px;
  color: #667085;
}

@media (max-width: 760px) {
  .toolbar,
  .scene-row {
    display: grid;
    grid-template-columns: 1fr;
    align-items: stretch;
  }
}
</style>
