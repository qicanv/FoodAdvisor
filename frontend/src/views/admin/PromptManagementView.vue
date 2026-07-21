<template>
  <AdminLayout
    title="提示词管理"
    subtitle="管理 AI 业务场景的提示词版本、启用状态与变更记录"
  >
    <template #sidebar>
      <div class="page-sidebar-nav">
        <span class="page-sidebar-title">提示词场景</span>

        <div class="page-sidebar-items-wrapper">
          <div
            v-for="definition in definitions"
            :key="definition.sceneCode"
            :class="[
              'page-sidebar-item',
              {
                active:
                  selectedSceneCode === definition.sceneCode,
              },
            ]"
            @click="selectScene(definition.sceneCode)"
          >
            <span class="menu-icon">
              {{ sceneIcon(definition.sceneCode) }}
            </span>

            <span class="scene-menu-text">
              <strong>{{ definition.sceneName }}</strong>
              <small>
                {{
                  definition.activeVersionTag ||
                  '暂无启用版本'
                }}
              </small>
            </span>
          </div>
        </div>
      </div>
    </template>

    <div class="prompt-page">
      <el-alert
        v-if="errorMessage"
        class="notice"
        type="error"
        :title="errorMessage"
        show-icon
        closable
        @close="errorMessage = ''"
      />

      <section class="toolbar">
        <div>
          <h1>
            {{
              currentDefinition?.sceneName ||
              '提示词管理'
            }}
          </h1>

          <p>
            {{
              currentDefinition?.description ||
              '请选择需要管理的提示词场景。'
            }}
          </p>
        </div>

        <div class="toolbar-actions">
          <el-button
            :loading="loading"
            @click="refreshCurrentScene"
          >
            刷新
          </el-button>

          <el-button
            type="primary"
            :disabled="!selectedSceneCode"
            @click="openCreateDialog"
          >
            创建新版本
          </el-button>
        </div>
      </section>

      <div
        v-loading="loading"
        class="content-container"
      >
        <template v-if="currentDefinition">
          <section class="summary-grid">
            <article class="summary-card">
              <span class="summary-label">场景编码</span>
              <strong>
                {{ currentDefinition.sceneCode }}
              </strong>
            </article>

            <article class="summary-card">
              <span class="summary-label">场景状态</span>

              <el-tag
                :type="
                  currentDefinition.status === 'ACTIVE'
                    ? 'success'
                    : 'info'
                "
              >
                {{ formatStatus(currentDefinition.status) }}
              </el-tag>
            </article>

            <article class="summary-card">
              <span class="summary-label">
                当前启用版本
              </span>

              <strong>
                {{
                  currentDefinition.activeVersionTag ||
                  '未启用'
                }}
              </strong>
            </article>

            <article class="summary-card">
              <span class="summary-label">
                历史版本数量
              </span>

              <strong>{{ versions.length }}</strong>
            </article>
          </section>

          <el-tabs v-model="activeTab">
            <el-tab-pane
              label="当前版本"
              name="current"
            >
              <section class="panel">
                <div class="panel-header">
                  <div>
                    <h2>当前启用提示词</h2>
                    <p>
                      新的 AI 请求会使用当前启用版本；
                      已保存的历史结果不会被修改。
                    </p>
                  </div>

                  <el-tag
                    v-if="
                      currentDefinition.activeVersionTag
                    "
                    type="success"
                  >
                    {{
                      currentDefinition.activeVersionTag
                    }}
                  </el-tag>
                </div>

                <div
                  v-if="
                    currentDefinition.activeVersionContent
                  "
                  class="prompt-content"
                >
                  <pre>{{
                    currentDefinition.activeVersionContent
                  }}</pre>
                </div>

                <el-empty
                  v-else
                  description="该场景暂时没有启用版本"
                />
              </section>
            </el-tab-pane>

            <el-tab-pane
              label="历史版本"
              name="versions"
            >
              <section class="panel">
                <div class="panel-header">
                  <div>
                    <h2>版本历史</h2>
                    <p>
                      每次修改都会创建新版本，已有版本不会被覆盖。
                    </p>
                  </div>
                </div>

                <el-table
                  :data="versions"
                  empty-text="暂无提示词版本"
                  row-key="id"
                >
                  <el-table-column
                    prop="versionNo"
                    label="版本号"
                    width="90"
                  >
                    <template #default="{ row }">
                      v{{ row.versionNo }}
                    </template>
                  </el-table-column>

                  <el-table-column
                    prop="versionTag"
                    label="版本标识"
                    min-width="170"
                  />

                  <el-table-column
                    prop="changeNote"
                    label="变更说明"
                    min-width="240"
                    show-overflow-tooltip
                  />

                  <el-table-column
                    prop="createdBy"
                    label="创建人"
                    width="100"
                  >
                    <template #default="{ row }">
                      {{ row.createdBy ?? '-' }}
                    </template>
                  </el-table-column>

                  <el-table-column
                    prop="createdAt"
                    label="创建时间"
                    min-width="180"
                  >
                    <template #default="{ row }">
                      {{ formatDateTime(row.createdAt) }}
                    </template>
                  </el-table-column>

                  <el-table-column
                    label="状态"
                    width="100"
                  >
                    <template #default="{ row }">
                      <el-tag
                        v-if="row.active"
                        type="success"
                      >
                        当前启用
                      </el-tag>

                      <el-tag v-else type="info">
                        历史版本
                      </el-tag>
                    </template>
                  </el-table-column>

                  <el-table-column
                    label="操作"
                    width="260"
                    fixed="right"
                  >
                    <template #default="{ row }">
                      <el-button
                        size="small"
                        @click="openVersionDetail(row)"
                      >
                        查看 / 对比
                      </el-button>

                      <el-button
                        v-if="
                          !row.active &&
                          isOlderThanActive(row)
                        "
                        size="small"
                        type="warning"
                        @click="confirmRollback(row)"
                      >
                        回滚
                      </el-button>

                      <el-button
                        v-else-if="!row.active"
                        size="small"
                        type="primary"
                        @click="confirmActivate(row)"
                      >
                        启用
                      </el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </section>
            </el-tab-pane>

            <el-tab-pane
              label="操作记录"
              name="logs"
            >
              <section class="panel">
                <div class="panel-header">
                  <div>
                    <h2>启用与回滚记录</h2>
                    <p>
                      展示版本切换的操作者、时间和备注。
                    </p>
                  </div>
                </div>

                <el-table
                  :data="activationLogs"
                  empty-text="暂无启用或回滚记录"
                  row-key="id"
                >
                  <el-table-column
                    prop="operationType"
                    label="操作类型"
                    width="120"
                  >
                    <template #default="{ row }">
                      <el-tag
                        :type="
                          row.operationType === 'ROLLBACK'
                            ? 'warning'
                            : 'success'
                        "
                      >
                        {{
                          formatOperationType(
                            row.operationType
                          )
                        }}
                      </el-tag>
                    </template>
                  </el-table-column>

                  <el-table-column
                    label="原版本"
                    min-width="150"
                  >
                    <template #default="{ row }">
                      {{ row.fromVersionTag || '无' }}
                    </template>
                  </el-table-column>

                  <el-table-column
                    label="目标版本"
                    min-width="150"
                  >
                    <template #default="{ row }">
                      {{ row.toVersionTag || '无' }}
                    </template>
                  </el-table-column>

                  <el-table-column
                    prop="operationNote"
                    label="操作备注"
                    min-width="240"
                  >
                    <template #default="{ row }">
                      {{ row.operationNote || '-' }}
                    </template>
                  </el-table-column>

                  <el-table-column
                    prop="operatedBy"
                    label="操作人"
                    width="100"
                  >
                    <template #default="{ row }">
                      {{ row.operatedBy ?? '-' }}
                    </template>
                  </el-table-column>

                  <el-table-column
                    prop="operatedAt"
                    label="操作时间"
                    min-width="180"
                  >
                    <template #default="{ row }">
                      {{ formatDateTime(row.operatedAt) }}
                    </template>
                  </el-table-column>
                </el-table>
              </section>
            </el-tab-pane>
          </el-tabs>
        </template>

        <el-empty
          v-else-if="!loading"
          description="暂无提示词场景"
        />
      </div>
    </div>

    <el-dialog
      v-model="createDialogVisible"
      title="创建提示词新版本"
      width="760px"
      destroy-on-close
    >
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-position="top"
      >
        <el-form-item label="业务场景">
          <el-input
            :model-value="
              currentDefinition?.sceneName || ''
            "
            disabled
          />
        </el-form-item>

        <el-form-item
          label="提示词内容"
          prop="content"
        >
          <el-input
            v-model="createForm.content"
            type="textarea"
            :rows="14"
            maxlength="50000"
            show-word-limit
            placeholder="请输入新的提示词内容"
          />
        </el-form-item>

        <el-form-item
          label="变更说明"
          prop="changeNote"
        >
          <el-input
            v-model="createForm.changeNote"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="说明本次修改的目的和主要变化"
          />
        </el-form-item>

        <el-form-item label="版本启用">
          <el-switch
            v-model="createForm.activate"
            active-text="创建后立即启用"
            inactive-text="仅保存为历史版本"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button
          :disabled="submitting"
          @click="createDialogVisible = false"
        >
          取消
        </el-button>

        <el-button
          type="primary"
          :loading="submitting"
          @click="submitCreateVersion"
        >
          创建版本
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="detailDialogVisible"
      title="提示词版本详情"
      width="900px"
      destroy-on-close
    >
      <template v-if="selectedVersion">
        <el-descriptions
          :column="2"
          border
          class="version-descriptions"
        >
          <el-descriptions-item label="版本标识">
            {{ selectedVersion.versionTag }}
          </el-descriptions-item>

          <el-descriptions-item label="版本号">
            v{{ selectedVersion.versionNo }}
          </el-descriptions-item>

          <el-descriptions-item label="创建人">
            {{ selectedVersion.createdBy ?? '-' }}
          </el-descriptions-item>

          <el-descriptions-item label="创建时间">
            {{
              formatDateTime(
                selectedVersion.createdAt
              )
            }}
          </el-descriptions-item>

          <el-descriptions-item
            label="变更说明"
            :span="2"
          >
            {{ selectedVersion.changeNote }}
          </el-descriptions-item>
        </el-descriptions>

        <el-tabs
          v-model="detailTab"
          class="detail-tabs"
        >
          <el-tab-pane
            label="版本内容"
            name="content"
          >
            <div class="prompt-content">
              <pre>{{ selectedVersion.content }}</pre>
            </div>
          </el-tab-pane>

          <el-tab-pane
            label="与当前版本对比"
            name="diff"
          >
            <el-alert
              v-if="selectedVersion.active"
              type="info"
              title="该版本就是当前启用版本，没有差异。"
              show-icon
              :closable="false"
            />

            <div
              v-else-if="diffRows.length"
              class="diff-container"
            >
              <div class="diff-header">
                <span>当前启用版本</span>
                <span>所选历史版本</span>
              </div>

              <div
                v-for="row in diffRows"
                :key="row.line"
                class="diff-row"
                :class="{ changed: row.changed }"
              >
                <div class="diff-cell">
                  <span class="line-number">
                    {{ row.line }}
                  </span>
                  <code>{{ row.current }}</code>
                </div>

                <div class="diff-cell">
                  <span class="line-number">
                    {{ row.line }}
                  </span>
                  <code>{{ row.selected }}</code>
                </div>
              </div>
            </div>

            <el-empty
              v-else
              description="当前没有可对比的启用版本"
            />
          </el-tab-pane>
        </el-tabs>
      </template>

      <template #footer>
        <el-button @click="detailDialogVisible = false">
          关闭
        </el-button>
      </template>
    </el-dialog>
  </AdminLayout>
</template>

<script setup>
import {
  computed,
  nextTick,
  onMounted,
  reactive,
  ref,
} from 'vue'
import {
  ElMessage,
  ElMessageBox,
} from 'element-plus'
import AdminLayout from '../../components/AdminLayout.vue'
import {
  activatePromptVersion,
  createPromptVersion,
  getPromptActivationLogs,
  getPromptDefinition,
  getPromptDefinitions,
  getPromptVersions,
  rollbackPromptVersion,
} from '../../api/promptManagement'

const definitions = ref([])
const currentDefinition = ref(null)
const versions = ref([])
const activationLogs = ref([])

const selectedSceneCode = ref('')
const selectedVersion = ref(null)

const loading = ref(false)
const submitting = ref(false)
const errorMessage = ref('')

const activeTab = ref('current')
const detailTab = ref('content')

const createDialogVisible = ref(false)
const detailDialogVisible = ref(false)
const createFormRef = ref()

const createForm = reactive({
  content: '',
  changeNote: '',
  activate: true,
})

const createRules = {
  content: [
    {
      required: true,
      message: '请输入提示词内容',
      trigger: 'blur',
    },
    {
      max: 50000,
      message: '提示词内容不能超过 50000 个字符',
      trigger: 'blur',
    },
  ],
  changeNote: [
    {
      required: true,
      message: '请输入变更说明',
      trigger: 'blur',
    },
    {
      max: 500,
      message: '变更说明不能超过 500 个字符',
      trigger: 'blur',
    },
  ],
}

const diffRows = computed(() => {
  if (
    !selectedVersion.value ||
    selectedVersion.value.active ||
    !currentDefinition.value?.activeVersionContent
  ) {
    return []
  }

  const currentLines =
    currentDefinition.value.activeVersionContent.split(
      '\n'
    )
  const selectedLines =
    selectedVersion.value.content.split('\n')

  const rowCount = Math.max(
    currentLines.length,
    selectedLines.length
  )

  return Array.from(
    { length: rowCount },
    (_, index) => {
      const current = currentLines[index] ?? ''
      const selected = selectedLines[index] ?? ''

      return {
        line: index + 1,
        current,
        selected,
        changed: current !== selected,
      }
    }
  )
})

onMounted(() => {
  loadDefinitions()
})

async function loadDefinitions() {
  loading.value = true
  errorMessage.value = ''

  try {
    const response = await getPromptDefinitions()

    if (!response.success) {
      errorMessage.value =
        response.message || '查询提示词场景失败'
      return
    }

    definitions.value = Array.isArray(response.data)
      ? response.data
      : []

    if (!definitions.value.length) {
      currentDefinition.value = null
      return
    }

    const existingScene = definitions.value.some(
      item =>
        item.sceneCode === selectedSceneCode.value
    )

    const sceneCode = existingScene
      ? selectedSceneCode.value
      : definitions.value[0].sceneCode

    await selectScene(sceneCode)
  } catch (error) {
    errorMessage.value =
      error?.message || '查询提示词场景失败'
  } finally {
    loading.value = false
  }
}

async function selectScene(sceneCode) {
  if (!sceneCode) {
    return
  }

  selectedSceneCode.value = sceneCode
  activeTab.value = 'current'
  await refreshCurrentScene()
}

async function refreshCurrentScene() {
  if (!selectedSceneCode.value) {
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    const [
      definitionResponse,
      versionsResponse,
      logsResponse,
    ] = await Promise.all([
      getPromptDefinition(
        selectedSceneCode.value
      ),
      getPromptVersions(selectedSceneCode.value),
      getPromptActivationLogs(
        selectedSceneCode.value
      ),
    ])

    if (!definitionResponse.success) {
      throw new Error(
        definitionResponse.message ||
          '查询提示词场景失败'
      )
    }

    if (!versionsResponse.success) {
      throw new Error(
        versionsResponse.message ||
          '查询提示词版本失败'
      )
    }

    if (!logsResponse.success) {
      throw new Error(
        logsResponse.message ||
          '查询操作记录失败'
      )
    }

    currentDefinition.value =
      definitionResponse.data

    versions.value = Array.isArray(
      versionsResponse.data
    )
      ? versionsResponse.data
      : []

    activationLogs.value = Array.isArray(
      logsResponse.data
    )
      ? logsResponse.data
      : []

    updateDefinitionCache(
      definitionResponse.data
    )
  } catch (error) {
    errorMessage.value =
      error?.message || '加载提示词信息失败'
  } finally {
    loading.value = false
  }
}

function updateDefinitionCache(definition) {
  const index = definitions.value.findIndex(
    item =>
      item.sceneCode === definition.sceneCode
  )

  if (index >= 0) {
    definitions.value[index] = definition
  }
}

function openCreateDialog() {
  if (!currentDefinition.value) {
    return
  }

  createForm.content =
    currentDefinition.value.activeVersionContent ||
    ''
  createForm.changeNote = ''
  createForm.activate = true
  createDialogVisible.value = true

  nextTick(() => {
    createFormRef.value?.clearValidate()
  })
}

async function submitCreateVersion() {
  if (!createFormRef.value) {
    return
  }

  const valid =
    await createFormRef.value
      .validate()
      .catch(() => false)

  if (!valid) {
    return
  }

  submitting.value = true

  try {
    const response = await createPromptVersion(
      selectedSceneCode.value,
      {
        content: createForm.content.trim(),
        changeNote:
          createForm.changeNote.trim(),
        activate: createForm.activate,
      }
    )

    if (!response.success) {
      throw new Error(
        response.message || '创建版本失败'
      )
    }

    createDialogVisible.value = false
    ElMessage.success('提示词版本创建成功')
    await refreshCurrentScene()
    activeTab.value = 'versions'
  } catch (error) {
    ElMessage.error(
      error?.message || '创建版本失败'
    )
  } finally {
    submitting.value = false
  }
}

function openVersionDetail(version) {
  selectedVersion.value = version
  detailTab.value = 'content'
  detailDialogVisible.value = true
}

async function confirmActivate(version) {
  try {
    const { value } = await ElMessageBox.prompt(
      `确认启用 ${version.versionTag} 吗？`,
      '启用提示词版本',
      {
        confirmButtonText: '确认启用',
        cancelButtonText: '取消',
        inputPlaceholder: '请输入操作备注（可选）',
        inputValue: '',
      }
    )

    const response = await activatePromptVersion(
      selectedSceneCode.value,
      version.id,
      {
        operationNote: value?.trim() || null,
      }
    )

    if (!response.success) {
      throw new Error(
        response.message || '启用版本失败'
      )
    }

    ElMessage.success('提示词版本已启用')
    await refreshCurrentScene()
  } catch (error) {
    if (
      error === 'cancel' ||
      error === 'close'
    ) {
      return
    }

    ElMessage.error(
      error?.message || '启用版本失败'
    )
  }
}

async function confirmRollback(version) {
  try {
    const { value } = await ElMessageBox.prompt(
      `确认将当前提示词回滚到 ${version.versionTag} 吗？`,
      '回滚提示词版本',
      {
        type: 'warning',
        confirmButtonText: '确认回滚',
        cancelButtonText: '取消',
        inputPlaceholder: '请输入回滚原因',
        inputValidator: input =>
          Boolean(input?.trim()) ||
          '请输入回滚原因',
      }
    )

    const response = await rollbackPromptVersion(
      selectedSceneCode.value,
      version.id,
      {
        operationNote: value.trim(),
      }
    )

    if (!response.success) {
      throw new Error(
        response.message || '回滚版本失败'
      )
    }

    ElMessage.success('提示词版本已回滚')
    await refreshCurrentScene()
  } catch (error) {
    if (
      error === 'cancel' ||
      error === 'close'
    ) {
      return
    }

    ElMessage.error(
      error?.message || '回滚版本失败'
    )
  }
}

function isOlderThanActive(version) {
  const activeVersionNo =
    currentDefinition.value?.activeVersionNo

  return (
    Number.isFinite(activeVersionNo) &&
    version.versionNo < activeVersionNo
  )
}

function formatDateTime(value) {
  if (!value) {
    return '-'
  }

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value
  }

  return date.toLocaleString('zh-CN', {
    hour12: false,
  })
}

function formatStatus(status) {
  const labels = {
    ACTIVE: '启用',
    DISABLED: '停用',
  }

  return labels[status] || status || '-'
}

function formatOperationType(type) {
  const labels = {
    ACTIVATE: '启用',
    ROLLBACK: '回滚',
  }

  return labels[type] || type || '-'
}

function sceneIcon(sceneCode) {
  const icons = {
    DINING_RECOMMENDATION: '🍽️',
    CONSTRAINT_EXTRACTION: '🔎',
    REVIEW_SUMMARY: '📝',
    SENTIMENT_ANALYSIS: '📊',
    REVIEW_REPLY: '💬',
    BUSINESS_ADVICE: '💡',
  }

  return icons[sceneCode] || '🤖'
}
</script>

<style scoped>
.prompt-page {
  min-width: 0;
}

.notice {
  margin-bottom: 20px;
}

.toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
}

.toolbar h1 {
  margin: 0;
  color: #1f2d3d;
  font-size: 26px;
}

.toolbar p {
  margin: 8px 0 0;
  color: #667085;
  line-height: 1.6;
}

.toolbar-actions {
  display: flex;
  gap: 12px;
  flex-shrink: 0;
}

.content-container {
  min-height: 360px;
}

.summary-grid {
  display: grid;
  grid-template-columns:
    repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.summary-card {
  min-height: 96px;
  padding: 20px;
  background: #fff;
  border: 1px solid #e8ecf2;
  border-radius: 12px;
  box-shadow: 0 4px 16px
    rgba(31, 45, 61, 0.05);
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 10px;
}

.summary-label {
  color: #8492a6;
  font-size: 13px;
}

.summary-card strong {
  color: #1f2d3d;
  font-size: 17px;
  overflow-wrap: anywhere;
}

.panel {
  padding: 24px;
  background: #fff;
  border: 1px solid #e8ecf2;
  border-radius: 12px;
  box-shadow: 0 4px 16px
    rgba(31, 45, 61, 0.05);
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.panel-header h2 {
  margin: 0;
  color: #1f2d3d;
  font-size: 19px;
}

.panel-header p {
  margin: 8px 0 0;
  color: #667085;
  line-height: 1.6;
}

.prompt-content {
  max-height: 520px;
  overflow: auto;
  padding: 18px;
  background: #f7f9fc;
  border: 1px solid #e1e7ef;
  border-radius: 10px;
}

.prompt-content pre {
  margin: 0;
  color: #344054;
  font-family:
    Consolas, Monaco, 'Courier New', monospace;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.scene-menu-text {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.scene-menu-text strong {
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scene-menu-text small {
  opacity: 0.68;
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.version-descriptions {
  margin-bottom: 20px;
}

.detail-tabs {
  margin-top: 12px;
}

.diff-container {
  border: 1px solid #dfe5ec;
  border-radius: 10px;
  overflow: hidden;
}

.diff-header {
  display: grid;
  grid-template-columns: 1fr 1fr;
  background: #eef2f7;
  font-weight: 700;
  color: #344054;
}

.diff-header span {
  padding: 12px 16px;
}

.diff-header span + span {
  border-left: 1px solid #dfe5ec;
}

.diff-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  background: #fff;
}

.diff-row + .diff-row {
  border-top: 1px solid #edf0f4;
}

.diff-row.changed {
  background: #fff8e6;
}

.diff-cell {
  display: flex;
  min-width: 0;
  min-height: 38px;
}

.diff-cell + .diff-cell {
  border-left: 1px solid #dfe5ec;
}

.line-number {
  width: 44px;
  padding: 9px 8px;
  flex-shrink: 0;
  background: rgba(31, 45, 61, 0.04);
  color: #98a2b3;
  text-align: right;
  user-select: none;
}

.diff-cell code {
  padding: 9px 12px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  color: #344054;
}

@media (max-width: 1100px) {
  .summary-grid {
    grid-template-columns:
      repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .toolbar {
    flex-direction: column;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }

  .diff-container {
    overflow-x: auto;
  }

  .diff-header,
  .diff-row {
    min-width: 760px;
  }
}
</style>