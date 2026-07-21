<template>
  <AdminLayout
    title="推荐评测"
    subtitle="管理标准测试集、执行推荐评测并查看历史运行结果"
  >
    <div class="evaluation-page">
      <div v-if="errorMessage" class="message error-message">
        {{ errorMessage }}
      </div>

      <div v-if="successMessage" class="message success-message">
        {{ successMessage }}
      </div>

      <section class="toolbar-card">
        <div>
          <h2>推荐系统标准评测</h2>
          <p>
            通过标准测试集验证自然语言条件提取和推荐排序效果。
          </p>
        </div>

        <div class="run-actions">
          <label>
            Top K
            <input
              v-model.number="topK"
              type="number"
              min="1"
              max="20"
            />
          </label>

          <button
            class="primary-button"
            :disabled="!selectedDatasetId || executing"
            @click="handleExecuteRun"
          >
            {{ executing ? '评测执行中...' : '执行评测' }}
          </button>
        </div>
      </section>

      <div class="workspace">
        <aside class="dataset-panel">
          <div class="panel-header">
            <div>
              <h3>标准测试集</h3>
              <span>共 {{ datasets.length }} 个</span>
            </div>

            <div class="panel-actions">
              <button
                class="text-button"
                :disabled="loadingDatasets"
                @click="loadDatasets"
              >
                刷新
              </button>

              <button
                class="small-primary-button"
                @click="openCreateDataset"
              >
                新建
              </button>
            </div>
          </div>

          <div v-if="loadingDatasets" class="state-panel">
            正在加载测试集...
          </div>

          <div
            v-else-if="datasets.length === 0"
            class="state-panel"
          >
            暂无测试集
          </div>

          <div v-else class="dataset-list">
            <button
              v-for="dataset in datasets"
              :key="dataset.id"
              :class="[
                'dataset-item',
                {
                  active:
                    Number(selectedDatasetId) ===
                    Number(dataset.id),
                },
              ]"
              @click="selectDataset(dataset.id)"
            >
              <div class="dataset-name">
                {{ dataset.name }}
              </div>

              <div class="dataset-meta">
                <span
                  :class="[
                    'status-badge',
                    statusClass(dataset.status),
                  ]"
                >
                  {{ statusText(dataset.status) }}
                </span>

                <span>
                  {{ dataset.dataVersion || '未设置版本' }}
                </span>
              </div>
            </button>
          </div>
        </aside>

        <main class="content-panel">
          <div v-if="!selectedDataset" class="empty-content">
            请选择一个标准测试集
          </div>

          <template v-else>
            <section class="dataset-summary">
              <div>
                <div class="section-label">当前测试集</div>
                <h2>{{ selectedDataset.name }}</h2>
                <p>
                  {{ selectedDataset.description || '暂无描述' }}
                </p>

                <button
                  class="edit-dataset-button"
                  @click="openEditDataset"
                >
                  编辑测试集
                </button>
              </div>

              <div class="summary-meta">
                <div>
                  <span>数据版本</span>
                  <strong>
                    {{ selectedDataset.dataVersion || '-' }}
                  </strong>
                </div>

                <div>
                  <span>状态</span>
                  <strong>
                    {{ statusText(selectedDataset.status) }}
                  </strong>
                </div>

                <div>
                  <span>案例数</span>
                  <strong>{{ cases.length }}</strong>
                </div>

                <div>
                  <span>运行数</span>
                  <strong>{{ runs.length }}</strong>
                </div>
              </div>
            </section>

            <section class="section-card">
              <div class="section-header">
                <div>
                  <h3>测试案例</h3>
                  <p>
                    用于验证条件提取和推荐结果是否符合预期。
                  </p>
                </div>

                <div class="section-actions">
                  <button
                    class="secondary-button"
                    :disabled="loadingCases"
                    @click="loadCases"
                  >
                    刷新案例
                  </button>

                  <button
                    class="primary-button"
                    @click="openCreateCase"
                  >
                    新增案例
                  </button>
                </div>
              </div>

              <div v-if="loadingCases" class="state-panel">
                正在加载测试案例...
              </div>

              <div
                v-else-if="cases.length === 0"
                class="state-panel"
              >
                当前测试集暂无案例
              </div>

              <div v-else class="table-wrapper">
                <table>
                  <thead>
                    <tr>
                      <th>序号</th>
                      <th>案例编号</th>
                      <th>案例名称</th>
                      <th>测试输入</th>
                      <th>期望条件</th>
                      <th>状态</th>
                      <th>操作</th>
                    </tr>
                  </thead>

                  <tbody>
                    <tr
                      v-for="item in cases"
                      :key="item.id"
                    >
                      <td>{{ item.sequenceNo }}</td>

                      <td>
                        <code>{{ item.caseCode }}</code>
                      </td>

                      <td>
                        {{ item.caseName || '-' }}
                      </td>

                      <td class="input-cell">
                        {{ item.inputText }}
                      </td>

                      <td>
                        <pre>{{
                          prettyJson(item.expectedConstraints)
                        }}</pre>
                      </td>

                      <td>
                        <span
                          :class="[
                            'status-badge',
                            item.enabled
                              ? 'success'
                              : 'muted',
                          ]"
                        >
                          {{ item.enabled ? '启用' : '停用' }}
                        </span>
                      </td>

                      <td>
                        <div class="table-actions">
                          <button
                            class="table-edit-button"
                            @click="openEditCase(item)"
                          >
                            编辑
                          </button>

                          <button
                            class="table-delete-button"
                            :disabled="caseDeletingId === item.id"
                            @click="removeCase(item)"
                          >
                            {{
                              caseDeletingId === item.id
                                ? '删除中'
                                : '删除'
                            }}
                          </button>
                        </div>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>

            <section class="section-card">
              <div class="section-header">
                <div>
                  <h3>历史评测运行</h3>
                  <p>
                    查看模型版本、条件准确率和执行情况。
                  </p>
                </div>

                <button
                  class="secondary-button"
                  :disabled="loadingRuns"
                  @click="loadRuns"
                >
                  刷新运行
                </button>
              </div>

              <div v-if="loadingRuns" class="state-panel">
                正在加载评测运行...
              </div>

              <div
                v-else-if="runs.length === 0"
                class="state-panel"
              >
                暂无评测运行，请点击“执行评测”
              </div>

              <div v-else class="run-grid">
                <article
                  v-for="run in runs"
                  :key="run.id"
                  :class="[
                    'run-card',
                    {
                      active:
                        Number(selectedRunId) === Number(run.id),
                    },
                  ]"
                  role="button"
                  tabindex="0"
                  @click="selectRun(run.id)"
                  @keydown.enter="selectRun(run.id)"
                >
                  <div class="run-card-header">
                    <strong>运行 #{{ run.id }}</strong>

                    <span
                      :class="[
                        'status-badge',
                        statusClass(run.status),
                      ]"
                    >
                      {{ statusText(run.status) }}
                    </span>
                  </div>

                  <div class="run-model">
                    {{ run.modelName || '-' }}
                    ·
                    {{ run.modelVersion || '-' }}
                  </div>

                  <div class="run-metrics">
                    <div>
                      <span>准确率</span>
                      <strong>
                        {{
                          formatPercent(
                            runMetrics(run)
                              .overallConstraintAccuracy
                          )
                        }}
                      </strong>
                    </div>

                    <div>
                      <span>案例成功</span>
                      <strong>
                        {{ run.successCount || 0 }}/{{
                          run.requestedCount || 0
                        }}
                      </strong>
                    </div>

                    <div>
                      <span>失败案例</span>
                      <strong>
                        {{ run.failedCount || 0 }}
                      </strong>
                    </div>

                    <div>
                      <span>唯一商家</span>
                      <strong>
                        {{ run.uniqueMerchantCount || 0 }}
                      </strong>
                    </div>
                  </div>

                  <div class="run-extra">
                    <span>
                      完全匹配：
                      {{
                        runMetrics(run)
                          .exactConstraintMatchCaseCount || 0
                      }}
                    </span>

                    <span>
                      无结果：
                      {{
                        runMetrics(run)
                          .noResultCaseCount || 0
                      }}
                    </span>

                    <span>
                      返回推荐：
                      {{
                        runMetrics(run)
                          .totalReturnedRecommendations || 0
                      }}
                    </span>
                  </div>

                  <small>
                    {{ formatDate(run.createdAt) }}
                  </small>

                  <div class="run-view-hint">
                    查看案例结果 →
                  </div>
                </article>
              </div>
            </section>

            <section
              v-if="runs.length >= 2"
              class="section-card"
            >
              <div class="section-header">
                <div>
                  <h3>运行对比</h3>
                  <p>
                    比较两次评测运行的总体指标和案例变化。
                  </p>
                </div>
              </div>

              <div class="compare-controls">
                <label>
                  基准运行
                  <select
                    v-model.number="baselineRunId"
                    @change="comparison = null"
                  >
                    <option
                      v-for="run in runs"
                      :key="`baseline-${run.id}`"
                      :value="run.id"
                    >
                      运行 #{{ run.id }} ·
                      {{ formatPercent(
                        runMetrics(run).overallConstraintAccuracy
                      ) }}
                    </option>
                  </select>
                </label>

                <div class="compare-arrow">→</div>

                <label>
                  候选运行
                  <select
                    v-model.number="candidateRunId"
                    @change="comparison = null"
                  >
                    <option
                      v-for="run in runs"
                      :key="`candidate-${run.id}`"
                      :value="run.id"
                    >
                      运行 #{{ run.id }} ·
                      {{ formatPercent(
                        runMetrics(run).overallConstraintAccuracy
                      ) }}
                    </option>
                  </select>
                </label>

                <button
                  class="primary-button"
                  :disabled="
                    comparing ||
                    !baselineRunId ||
                    !candidateRunId ||
                    Number(baselineRunId) === Number(candidateRunId)
                  "
                  @click="compareRuns"
                >
                  {{ comparing ? '对比中...' : '开始对比' }}
                </button>
              </div>

              <div
                v-if="
                  baselineRunId &&
                  candidateRunId &&
                  Number(baselineRunId) ===
                    Number(candidateRunId)
                "
                class="compare-warning"
              >
                请选择两次不同的评测运行。
              </div>

              <template v-if="comparison">
                <div class="comparison-title">
                  <div>
                    <span>基准运行</span>
                    <strong>#{{ comparison.baselineRunId }}</strong>
                  </div>

                  <div class="comparison-direction">→</div>

                  <div>
                    <span>候选运行</span>
                    <strong>#{{ comparison.candidateRunId }}</strong>
                  </div>
                </div>

                <div class="comparison-metrics">
                  <article class="comparison-metric">
                    <span>条件准确率</span>

                    <div class="metric-values">
                      <strong>
                        {{
                          formatPercent(
                            comparison.metrics
                              .candidateConstraintAccuracy
                          )
                        }}
                      </strong>

                      <small
                        :class="
                          positiveChangeClass(
                            comparison.metrics
                              .constraintAccuracyChange
                          )
                        "
                      >
                        {{
                          formatPercentChange(
                            comparison.metrics
                              .constraintAccuracyChange
                          )
                        }}
                      </small>
                    </div>

                    <p>
                      {{
                        formatPercent(
                          comparison.metrics
                            .baselineConstraintAccuracy
                        )
                      }}
                      →
                      {{
                        formatPercent(
                          comparison.metrics
                            .candidateConstraintAccuracy
                        )
                      }}
                    </p>
                  </article>

                  <article class="comparison-metric">
                    <span>完全匹配案例</span>

                    <div class="metric-values">
                      <strong>
                        {{
                          comparison.metrics
                            .candidateExactMatchCaseCount
                        }}
                      </strong>

                      <small
                        :class="
                          positiveChangeClass(
                            comparison.metrics
                              .exactMatchCaseCountChange
                          )
                        "
                      >
                        {{
                          formatNumberChange(
                            comparison.metrics
                              .exactMatchCaseCountChange
                          )
                        }}
                      </small>
                    </div>

                    <p>
                      {{
                        comparison.metrics
                          .baselineExactMatchCaseCount
                      }}
                      →
                      {{
                        comparison.metrics
                          .candidateExactMatchCaseCount
                      }}
                    </p>
                  </article>

                  <article class="comparison-metric">
                    <span>失败案例</span>

                    <div class="metric-values">
                      <strong>
                        {{
                          comparison.metrics
                            .candidateFailedCaseCount
                        }}
                      </strong>

                      <small
                        :class="
                          failureChangeClass(
                            comparison.metrics
                              .failedCaseCountChange
                          )
                        "
                      >
                        {{
                          formatNumberChange(
                            comparison.metrics
                              .failedCaseCountChange
                          )
                        }}
                      </small>
                    </div>

                    <p>
                      {{
                        comparison.metrics
                          .baselineFailedCaseCount
                      }}
                      →
                      {{
                        comparison.metrics
                          .candidateFailedCaseCount
                      }}
                    </p>
                  </article>

                  <article class="comparison-metric">
                    <span>无结果案例</span>

                    <div class="metric-values">
                      <strong>
                        {{
                          comparison.metrics
                            .candidateNoResultCaseCount
                        }}
                      </strong>

                      <small class="change-neutral">
                        {{
                          formatNumberChange(
                            comparison.metrics
                              .noResultCaseCountChange
                          )
                        }}
                      </small>
                    </div>

                    <p>
                      {{
                        comparison.metrics
                          .baselineNoResultCaseCount
                      }}
                      →
                      {{
                        comparison.metrics
                          .candidateNoResultCaseCount
                      }}
                    </p>
                  </article>

                  <article class="comparison-metric">
                    <span>返回推荐数量</span>

                    <div class="metric-values">
                      <strong>
                        {{
                          comparison.metrics
                            .candidateReturnedRecommendationCount
                        }}
                      </strong>

                      <small class="change-neutral">
                        {{
                          formatNumberChange(
                            comparison.metrics
                              .returnedRecommendationCountChange
                          )
                        }}
                      </small>
                    </div>

                    <p>
                      {{
                        comparison.metrics
                          .baselineReturnedRecommendationCount
                      }}
                      →
                      {{
                        comparison.metrics
                          .candidateReturnedRecommendationCount
                      }}
                    </p>
                  </article>

                  <article class="comparison-metric">
                    <span>唯一推荐商家</span>

                    <div class="metric-values">
                      <strong>
                        {{
                          comparison.metrics
                            .candidateUniqueMerchantCount
                        }}
                      </strong>

                      <small class="change-neutral">
                        {{
                          formatNumberChange(
                            comparison.metrics
                              .uniqueMerchantCountChange
                          )
                        }}
                      </small>
                    </div>

                    <p>
                      {{
                        comparison.metrics
                          .baselineUniqueMerchantCount
                      }}
                      →
                      {{
                        comparison.metrics
                          .candidateUniqueMerchantCount
                      }}
                    </p>
                  </article>
                </div>

                <div class="comparison-summary">
                  <div class="summary-item improved-summary">
                    <strong>
                      {{ comparison.improvedCaseIds.length }}
                    </strong>
                    <span>提升案例</span>
                  </div>

                  <div class="summary-item regressed-summary">
                    <strong>
                      {{ comparison.regressedCaseIds.length }}
                    </strong>
                    <span>退步案例</span>
                  </div>

                  <div class="summary-item unchanged-summary">
                    <strong>
                      {{ comparison.unchangedCaseIds.length }}
                    </strong>
                    <span>不变案例</span>
                  </div>

                  <div class="summary-item">
                    <strong>
                      {{ comparison.addedCaseIds.length }}
                    </strong>
                    <span>新增案例</span>
                  </div>

                  <div class="summary-item">
                    <strong>
                      {{ comparison.removedCaseIds.length }}
                    </strong>
                    <span>移除案例</span>
                  </div>
                </div>

                <div class="comparison-case-section">
                  <h4>案例变化明细</h4>

                  <div class="comparison-case-list">
                    <article
                      v-for="item in comparison.cases"
                      :key="item.caseId"
                      class="comparison-case-card"
                    >
                      <div class="comparison-case-header">
                        <div>
                          <strong>
                            {{ caseTitle(item.caseId) }}
                          </strong>

                          <span>
                            案例 ID：{{ item.caseId }}
                          </span>
                        </div>

                        <span
                          :class="[
                            'status-badge',
                            caseChangeClass(item.changeType),
                          ]"
                        >
                          {{ caseChangeText(item.changeType) }}
                        </span>
                      </div>

                      <div class="case-comparison-grid">
                        <div>
                          <span>条件准确率</span>

                          <strong>
                            {{
                              formatPercent(
                                item.baselineConstraintAccuracy
                              )
                            }}
                            →
                            {{
                              formatPercent(
                                item.candidateConstraintAccuracy
                              )
                            }}
                          </strong>

                          <small
                            :class="
                              positiveChangeClass(
                                item.constraintAccuracyChange
                              )
                            "
                          >
                            {{
                              formatPercentChange(
                                item.constraintAccuracyChange
                              )
                            }}
                          </small>
                        </div>

                        <div>
                          <span>推荐数量</span>

                          <strong>
                            {{ item.baselineResultCount }}
                            →
                            {{ item.candidateResultCount }}
                          </strong>

                          <small class="change-neutral">
                            {{
                              formatNumberChange(
                                item.resultCountChange
                              )
                            }}
                          </small>
                        </div>

                        <div>
                          <span>运行状态</span>

                          <strong>
                            {{ statusText(item.baselineStatus) }}
                            →
                            {{ statusText(item.candidateStatus) }}
                          </strong>
                        </div>
                      </div>
                    </article>
                  </div>
                </div>
              </template>
            </section>

            <section
              v-if="selectedRunId"
              class="section-card"
            >
              <div class="section-header">
                <div>
                  <h3>
                    运行 #{{ selectedRunId }} 案例结果
                  </h3>
                  <p>
                    查看条件提取、推荐商家和人工相关性标注。
                  </p>
                </div>

                <button
                  class="secondary-button"
                  :disabled="loadingResults"
                  @click="loadRunResults(selectedRunId)"
                >
                  刷新结果
                </button>
              </div>

              <div v-if="loadingResults" class="state-panel">
                正在加载案例结果...
              </div>

              <div
                v-else-if="runResults.length === 0"
                class="state-panel"
              >
                当前运行暂无案例结果
              </div>

              <div v-else class="result-list">
                <article
                  v-for="result in runResults"
                  :key="result.id"
                  class="result-card"
                >
                  <div class="result-header">
                    <div>
                      <h4>{{ caseTitle(result.caseId) }}</h4>
                      <p>{{ result.inputSnapshot }}</p>
                    </div>

                    <span
                      :class="[
                        'status-badge',
                        statusClass(result.status),
                      ]"
                    >
                      {{ statusText(result.status) }}
                    </span>
                  </div>

                  <div class="result-overview">
                    <div>
                      <span>结果编号</span>
                      <strong>#{{ result.id }}</strong>
                    </div>

                    <div>
                      <span>推荐数量</span>
                      <strong>{{ result.resultCount || 0 }}</strong>
                    </div>

                    <div>
                      <span>执行耗时</span>
                      <strong>{{ result.durationMs || 0 }} ms</strong>
                    </div>

                    <div>
                      <span>条件准确率</span>
                      <strong>
                        {{
                          formatPercent(
                            hardMetricsOf(result).constraintAccuracy
                          )
                        }}
                      </strong>
                    </div>

                    <div>
                      <span>硬条件过滤</span>
                      <strong>
                        {{
                          hardMetricsOf(result)
                            .hardFilterRejectedCount || 0
                        }}
                      </strong>
                    </div>

                    <div>
                      <span>人工标注</span>
                      <strong>
                        {{ relevanceText(result.relevanceLabel) }}
                      </strong>
                    </div>
                  </div>

                  <div class="constraint-grid">
                    <div class="constraint-card">
                      <h5>期望条件</h5>
                      <pre>{{
                        prettyJson(result.expectedConstraints)
                      }}</pre>
                    </div>

                    <div class="constraint-card">
                      <h5>实际提取条件</h5>
                      <pre>{{
                        prettyJson(result.extractedConstraints)
                      }}</pre>
                    </div>
                  </div>

                  <div
                    v-if="recommendationsOf(result).length > 0"
                    class="recommendation-section"
                  >
                    <h5>
                      推荐商家
                      <span>
                        共 {{ recommendationsOf(result).length }} 家
                      </span>
                    </h5>

                    <div class="recommendation-list">
                      <article
                        v-for="merchant in recommendationsOf(result)"
                        :key="merchant.merchantId"
                        class="recommendation-item"
                      >
                        <div class="recommendation-header">
                          <div>
                            <span class="rank-number">
                              #{{ merchant.rankNo }}
                            </span>

                            <strong>
                              {{ merchant.merchantName }}
                            </strong>
                          </div>

                          <strong class="score-value">
                            {{ merchant.finalScore }} 分
                          </strong>
                        </div>

                        <div class="merchant-meta">
                          <span>
                            菜系：{{ merchant.cuisine || '-' }}
                          </span>

                          <span>
                            评分：{{ merchant.merchantRating || '-' }}
                          </span>

                          <span>
                            人均：¥{{ merchant.averagePrice || '-' }}
                          </span>

                          <span>
                            距离：{{ merchant.distanceKm || '-' }} km
                          </span>
                        </div>

                        <p class="recommendation-reason">
                          {{ merchant.reason }}
                        </p>
                      </article>
                    </div>
                  </div>

                  <div v-else class="no-result-panel">
                    <strong>当前案例未返回推荐商家</strong>

                    <p
                      v-for="reason in failureReasonsOf(result)"
                      :key="`${reason.type}-${reason.message}`"
                    >
                      {{ reason.message }}
                    </p>

                    <p
                      v-if="failureReasonsOf(result).length === 0"
                    >
                      没有商家满足当前硬性条件。
                    </p>
                  </div>

                  <div class="annotation-panel">
                    <label>
                      人工相关性
                      <select
                        v-model="result.annotationDraftLabel"
                      >
                        <option value="">请选择</option>
                        <option value="RELEVANT">
                          相关
                        </option>
                        <option value="PARTIALLY_RELEVANT">
                          部分相关
                        </option>
                        <option value="IRRELEVANT">
                          不相关
                        </option>
                      </select>
                    </label>

                    <label class="annotation-note">
                      标注备注
                      <input
                        v-model="result.annotationDraftNote"
                        maxlength="1000"
                        placeholder="填写判断依据或问题说明"
                      />
                    </label>

                    <button
                      class="primary-button"
                      :disabled="
                        annotationSavingId === result.id ||
                        !result.annotationDraftLabel
                      "
                      @click="saveAnnotation(result)"
                    >
                      {{
                        annotationSavingId === result.id
                          ? '保存中...'
                          : '保存标注'
                      }}
                    </button>
                  </div>
                </article>
              </div>
            </section>
          </template>
        </main>
      </div>

      <div
        v-if="datasetDialogVisible"
        class="dialog-mask"
        @click.self="closeDatasetDialog"
      >
        <section class="dialog-card">
          <div class="dialog-header">
            <div>
              <h3>
                {{
                  datasetDialogMode === 'create'
                    ? '新建测试集'
                    : '编辑测试集'
                }}
              </h3>

              <p>
                设置测试集的名称、数据版本和使用状态。
              </p>
            </div>

            <button
              class="dialog-close"
              :disabled="datasetSaving"
              @click="closeDatasetDialog"
            >
              ×
            </button>
          </div>

          <div
            v-if="datasetFormError"
            class="dialog-error"
          >
            {{ datasetFormError }}
          </div>

          <div class="dialog-form">
            <label>
              测试集名称
              <input
                v-model="datasetForm.name"
                maxlength="200"
                placeholder="例如：推荐功能基础评测集"
              />
            </label>

            <label>
              测试集描述
              <textarea
                v-model="datasetForm.description"
                maxlength="2000"
                rows="4"
                placeholder="说明该测试集验证的功能和范围"
              />
            </label>

            <label>
              数据版本
              <input
                v-model="datasetForm.dataVersion"
                maxlength="100"
                placeholder="例如：MERCHANT_DATA_20260721"
              />
            </label>

            <label>
              测试集状态
              <select v-model="datasetForm.status">
                <option value="DRAFT">草稿</option>
                <option value="ACTIVE">启用</option>
                <option value="ARCHIVED">已归档</option>
              </select>
            </label>
          </div>

          <div class="dialog-footer">
            <button
              class="secondary-button"
              :disabled="datasetSaving"
              @click="closeDatasetDialog"
            >
              取消
            </button>

            <button
              class="primary-button"
              :disabled="datasetSaving"
              @click="saveDataset"
            >
              {{
                datasetSaving
                  ? '保存中...'
                  : '保存测试集'
              }}
            </button>
          </div>
        </section>
      </div>

      <div
        v-if="caseDialogVisible"
        class="dialog-mask"
        @click.self="closeCaseDialog"
      >
        <section class="dialog-card case-dialog-card">
          <div class="dialog-header">
            <div>
              <h3>
                {{
                  caseDialogMode === 'create'
                    ? '新增测试案例'
                    : '编辑测试案例'
                }}
              </h3>

              <p>
                配置自然语言输入、期望条件和评测标签。
              </p>
            </div>

            <button
              class="dialog-close"
              :disabled="caseSaving"
              @click="closeCaseDialog"
            >
              ×
            </button>
          </div>

          <div
            v-if="caseFormError"
            class="dialog-error"
          >
            {{ caseFormError }}
          </div>

          <div class="dialog-form">
            <div class="form-row">
              <label>
                案例编号
                <input
                  v-model="caseForm.caseCode"
                  maxlength="100"
                  placeholder="例如：PARTY_BUDGET_001"
                />
              </label>

              <label>
                案例名称
                <input
                  v-model="caseForm.caseName"
                  maxlength="200"
                  placeholder="例如：四人聚餐预算测试"
                />
              </label>
            </div>

            <label>
              测试输入
              <textarea
                v-model="caseForm.inputText"
                maxlength="2000"
                rows="3"
                placeholder="例如：四个人聚餐，人均不超过150元"
              />
            </label>

            <label>
              期望条件 JSON
              <textarea
                v-model="caseForm.expectedConstraints"
                maxlength="20000"
                rows="7"
                class="json-editor"
                placeholder='{"partySize":4,"perCapitaBudget":150}'
              />
            </label>

            <label>
              位置快照 JSON
              <textarea
                v-model="caseForm.locationSnapshot"
                maxlength="10000"
                rows="4"
                class="json-editor"
                placeholder='{"latitude":30.5723,"longitude":104.0665}'
              />
            </label>

            <label>
              案例标签 JSON 数组
              <textarea
                v-model="caseForm.tags"
                maxlength="10000"
                rows="3"
                class="json-editor"
                placeholder='["人数","预算"]'
              />
            </label>

            <div class="form-row">
              <label>
                排序序号
                <input
                  v-model.number="caseForm.sequenceNo"
                  type="number"
                  min="0"
                />
              </label>

              <label class="checkbox-label">
                <input
                  v-model="caseForm.enabled"
                  type="checkbox"
                />
                启用该测试案例
              </label>
            </div>
          </div>

          <div class="dialog-footer">
            <button
              class="secondary-button"
              :disabled="caseSaving"
              @click="closeCaseDialog"
            >
              取消
            </button>

            <button
              class="primary-button"
              :disabled="caseSaving"
              @click="saveCase"
            >
              {{
                caseSaving
                  ? '保存中...'
                  : '保存案例'
              }}
            </button>
          </div>
        </section>
      </div>
    </div>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import AdminLayout from '../../components/AdminLayout.vue'
import {
  annotateEvaluationResult,
  compareEvaluationRuns,
  createEvaluationCase,
  createEvaluationDataset,
  deleteEvaluationCase,
  executeEvaluationRun,
  getEvaluationCases,
  getEvaluationDatasets,
  getEvaluationRunResults,
  getEvaluationRuns,
  updateEvaluationCase,
  updateEvaluationDataset,
} from '../../api/recommendationEvaluation'

const datasets = ref([])
const cases = ref([])
const runs = ref([])
const runResults = ref([])
const comparison = ref(null)

const selectedDatasetId = ref(null)
const selectedRunId = ref(null)
const baselineRunId = ref(null)
const candidateRunId = ref(null)
const topK = ref(5)

const loadingDatasets = ref(false)
const loadingCases = ref(false)
const loadingRuns = ref(false)
const loadingResults = ref(false)
const executing = ref(false)
const comparing = ref(false)
const annotationSavingId = ref(null)
const errorMessage = ref('')
const successMessage = ref('')

const datasetDialogVisible = ref(false)
const datasetDialogMode = ref('create')
const datasetSaving = ref(false)
const datasetFormError = ref('')

const datasetForm = ref({
  id: null,
  name: '',
  description: '',
  dataVersion: '',
  status: 'DRAFT',
})

const caseDialogVisible = ref(false)
const caseDialogMode = ref('create')
const caseSaving = ref(false)
const caseDeletingId = ref(null)
const caseFormError = ref('')

const caseForm = ref({
  id: null,
  caseCode: '',
  caseName: '',
  inputText: '',
  expectedConstraints: '{}',
  locationSnapshot: '',
  tags: '[]',
  sequenceNo: 0,
  enabled: true,
})

const selectedDataset = computed(() =>
  datasets.value.find(
    item =>
      Number(item.id) ===
      Number(selectedDatasetId.value)
  )
)

const caseMap = computed(() => {
  const map = new Map()

  cases.value.forEach(item => {
    map.set(Number(item.id), item)
  })

  return map
})

const showError = message => {
  errorMessage.value = message || '操作失败'
  successMessage.value = ''
}

const showSuccess = message => {
  successMessage.value = message
  errorMessage.value = ''
}

const resetDatasetForm = () => {
  datasetForm.value = {
    id: null,
    name: '',
    description: '',
    dataVersion: '',
    status: 'DRAFT',
  }

  datasetFormError.value = ''
}

const openCreateDataset = () => {
  resetDatasetForm()
  datasetDialogMode.value = 'create'
  datasetDialogVisible.value = true
}

const openEditDataset = () => {
  if (!selectedDataset.value) {
    showError('请先选择测试集')
    return
  }

  datasetDialogMode.value = 'edit'
  datasetFormError.value = ''

  datasetForm.value = {
    id: selectedDataset.value.id,
    name: selectedDataset.value.name || '',
    description:
      selectedDataset.value.description || '',
    dataVersion:
      selectedDataset.value.dataVersion || '',
    status:
      selectedDataset.value.status || 'DRAFT',
  }

  datasetDialogVisible.value = true
}

const closeDatasetDialog = () => {
  if (datasetSaving.value) {
    return
  }

  datasetDialogVisible.value = false
  datasetFormError.value = ''
}

const saveDataset = async () => {
  const name = datasetForm.value.name.trim()
  const description =
    datasetForm.value.description.trim()
  const dataVersion =
    datasetForm.value.dataVersion.trim()
  const status = datasetForm.value.status

  if (!name) {
    datasetFormError.value = '测试集名称不能为空'
    return
  }

  if (name.length > 200) {
    datasetFormError.value =
      '测试集名称不能超过 200 个字符'
    return
  }

  if (description.length > 2000) {
    datasetFormError.value =
      '测试集描述不能超过 2000 个字符'
    return
  }

  if (dataVersion.length > 100) {
    datasetFormError.value =
      '数据版本不能超过 100 个字符'
    return
  }

  datasetSaving.value = true
  datasetFormError.value = ''

  const payload = {
    name,
    description: description || null,
    dataVersion: dataVersion || null,
    status,
  }

  const result =
    datasetDialogMode.value === 'create'
      ? await createEvaluationDataset(payload)
      : await updateEvaluationDataset(
          datasetForm.value.id,
          payload
        )

  datasetSaving.value = false

  if (!result.success) {
    datasetFormError.value =
      result.message || '保存测试集失败'
    return
  }

  const savedDatasetId = result.data.id

  datasetDialogVisible.value = false

  showSuccess(
    datasetDialogMode.value === 'create'
      ? '测试集创建成功'
      : '测试集修改成功'
  )

  await loadDatasets()

  if (
    Number(selectedDatasetId.value) !==
    Number(savedDatasetId)
  ) {
    await selectDataset(savedDatasetId)
  }
}

const loadDatasets = async () => {
  loadingDatasets.value = true
  errorMessage.value = ''

  const result = await getEvaluationDatasets({
    pageNum: 1,
    pageSize: 100,
  })

  loadingDatasets.value = false

  if (!result.success) {
    showError(result.message)
    return
  }

  datasets.value = result.data?.records || []

  if (datasets.value.length === 0) {
    selectedDatasetId.value = null
    cases.value = []
    runs.value = []
    return
  }

  const currentStillExists = datasets.value.some(
    item =>
      Number(item.id) ===
      Number(selectedDatasetId.value)
  )

  if (!currentStillExists) {
    await selectDataset(datasets.value[0].id)
  }
}

const selectDataset = async datasetId => {
  selectedDatasetId.value = datasetId
  selectedRunId.value = null
  baselineRunId.value = null
  candidateRunId.value = null
  runResults.value = []
  comparison.value = null
  errorMessage.value = ''
  successMessage.value = ''

  await Promise.all([
    loadCases(),
    loadRuns(),
  ])
}

const loadCases = async () => {
  if (!selectedDatasetId.value) {
    cases.value = []
    return
  }

  loadingCases.value = true

  const result = await getEvaluationCases(
    selectedDatasetId.value
  )

  loadingCases.value = false

  if (!result.success) {
    showError(result.message)
    return
  }

  cases.value = Array.isArray(result.data)
    ? result.data
    : []
}

const loadRuns = async () => {
  if (!selectedDatasetId.value) {
    runs.value = []
    selectedRunId.value = null
    baselineRunId.value = null
    candidateRunId.value = null
    runResults.value = []
    comparison.value = null
    return
  }

  loadingRuns.value = true

  const result = await getEvaluationRuns(
    selectedDatasetId.value
  )

  loadingRuns.value = false

  if (!result.success) {
    showError(result.message)
    return
  }

  runs.value = Array.isArray(result.data)
    ? result.data
    : []

  if (runs.value.length === 0) {
    selectedRunId.value = null
    baselineRunId.value = null
    candidateRunId.value = null
    runResults.value = []
    comparison.value = null
    return
  }

  if (runs.value.length >= 2) {
    const validRunIds = new Set(
      runs.value.map(run => Number(run.id))
    )

    const comparisonSelectionValid =
      validRunIds.has(Number(baselineRunId.value)) &&
      validRunIds.has(Number(candidateRunId.value)) &&
      Number(baselineRunId.value) !==
        Number(candidateRunId.value)

    if (!comparisonSelectionValid) {
      candidateRunId.value = runs.value[0].id
      baselineRunId.value =
        runs.value[runs.value.length - 1].id
      comparison.value = null
    }
  } else {
    candidateRunId.value = runs.value[0].id
    baselineRunId.value = null
    comparison.value = null
  }

  const selectedStillExists = runs.value.some(
    run =>
      Number(run.id) ===
      Number(selectedRunId.value)
  )

  const nextRunId = selectedStillExists
    ? selectedRunId.value
    : runs.value[0].id

  await selectRun(nextRunId)
}

const selectRun = async runId => {
  selectedRunId.value = runId
  await loadRunResults(runId)
}

const loadRunResults = async runId => {
  if (!runId) {
    runResults.value = []
    return
  }

  loadingResults.value = true

  const result = await getEvaluationRunResults(runId)

  loadingResults.value = false

  if (!result.success) {
    showError(result.message)
    return
  }

  runResults.value = (result.data || []).map(item => ({
    ...item,
    annotationDraftLabel:
      item.relevanceLabel || '',
    annotationDraftNote:
      item.annotationNote || '',
  }))
}

const saveAnnotation = async resultItem => {
  if (!resultItem.annotationDraftLabel) {
    showError('请选择人工相关性标签')
    return
  }

  annotationSavingId.value = resultItem.id

  const result = await annotateEvaluationResult(
    selectedRunId.value,
    resultItem.id,
    {
      relevanceLabel:
        resultItem.annotationDraftLabel,
      annotationNote:
        resultItem.annotationDraftNote?.trim() || null,
    }
  )

  annotationSavingId.value = null

  if (!result.success) {
    showError(result.message)
    return
  }

  const index = runResults.value.findIndex(
    item => Number(item.id) === Number(resultItem.id)
  )

  if (index >= 0) {
    runResults.value[index] = {
      ...result.data,
      annotationDraftLabel:
        result.data.relevanceLabel || '',
      annotationDraftNote:
        result.data.annotationNote || '',
    }
  }

  showSuccess(
    `结果 #${resultItem.id} 的人工标注已保存`
  )
}

const compareRuns = async () => {
  if (!baselineRunId.value || !candidateRunId.value) {
    showError('请选择基准运行和候选运行')
    return
  }

  if (
    Number(baselineRunId.value) ===
    Number(candidateRunId.value)
  ) {
    showError('基准运行和候选运行不能相同')
    return
  }

  comparing.value = true
  comparison.value = null
  errorMessage.value = ''

  const result = await compareEvaluationRuns(
    baselineRunId.value,
    candidateRunId.value
  )

  comparing.value = false

  if (!result.success) {
    showError(result.message)
    return
  }

  comparison.value = result.data
  showSuccess(
    `运行 #${baselineRunId.value} 与运行 #${candidateRunId.value} 对比完成`
  )
}

const handleExecuteRun = async () => {
  if (!selectedDatasetId.value) {
    showError('请先选择测试集')
    return
  }

  const normalizedTopK = Number(topK.value)

  if (
    !Number.isInteger(normalizedTopK) ||
    normalizedTopK < 1 ||
    normalizedTopK > 20
  ) {
    showError('Top K 必须是 1 到 20 之间的整数')
    return
  }

  executing.value = true
  errorMessage.value = ''
  successMessage.value = ''

  const result = await executeEvaluationRun(
    selectedDatasetId.value,
    {
      topK: normalizedTopK,
    }
  )

  executing.value = false

  if (!result.success) {
    showError(result.message)
    return
  }

  showSuccess(
    `评测运行 #${result.data.id} 已执行完成`
  )

  selectedRunId.value = result.data.id
  await loadRuns()
}

const parseJson = value => {
  if (!value) {
    return {}
  }

  if (typeof value === 'object') {
    return value
  }

  try {
    return JSON.parse(value)
  } catch {
    return value
  }
}

const prettyJson = value => {
  const parsed = parseJson(value)

  if (typeof parsed === 'string') {
    return parsed
  }

  return JSON.stringify(parsed, null, 2)
}

const nextCaseSequenceNo = () => {
  if (cases.value.length === 0) {
    return 1
  }

  return (
    Math.max(
      ...cases.value.map(item =>
        Number(item.sequenceNo || 0)
      )
    ) + 1
  )
}

const resetCaseForm = () => {
  caseForm.value = {
    id: null,
    caseCode: '',
    caseName: '',
    inputText: '',
    expectedConstraints: '{}',
    locationSnapshot: JSON.stringify(
      {
        latitude: 30.5723,
        longitude: 104.0665,
      },
      null,
      2
    ),
    tags: '[]',
    sequenceNo: nextCaseSequenceNo(),
    enabled: true,
  }

  caseFormError.value = ''
}

const openCreateCase = () => {
  if (!selectedDatasetId.value) {
    showError('请先选择测试集')
    return
  }

  resetCaseForm()
  caseDialogMode.value = 'create'
  caseDialogVisible.value = true
}

const openEditCase = item => {
  caseDialogMode.value = 'edit'
  caseFormError.value = ''

  caseForm.value = {
    id: item.id,
    caseCode: item.caseCode || '',
    caseName: item.caseName || '',
    inputText: item.inputText || '',
    expectedConstraints:
      prettyJson(item.expectedConstraints),
    locationSnapshot: item.locationSnapshot
      ? prettyJson(item.locationSnapshot)
      : '',
    tags: item.tags
      ? prettyJson(item.tags)
      : '[]',
    sequenceNo: Number(item.sequenceNo || 0),
    enabled: item.enabled !== false,
  }

  caseDialogVisible.value = true
}

const closeCaseDialog = () => {
  if (caseSaving.value) {
    return
  }

  caseDialogVisible.value = false
  caseFormError.value = ''
}

const saveCase = async () => {
  const caseCode = caseForm.value.caseCode.trim()
  const caseName = caseForm.value.caseName.trim()
  const inputText = caseForm.value.inputText.trim()

  if (!caseCode) {
    caseFormError.value = '案例编号不能为空'
    return
  }

  if (caseCode.length > 100) {
    caseFormError.value =
      '案例编号不能超过 100 个字符'
    return
  }

  if (caseName.length > 200) {
    caseFormError.value =
      '案例名称不能超过 200 个字符'
    return
  }

  if (!inputText) {
    caseFormError.value = '测试输入不能为空'
    return
  }

  if (inputText.length > 2000) {
    caseFormError.value =
      '测试输入不能超过 2000 个字符'
    return
  }

  const sequenceNo = Number(
    caseForm.value.sequenceNo
  )

  if (
    !Number.isInteger(sequenceNo) ||
    sequenceNo < 0
  ) {
    caseFormError.value =
      '排序序号必须是大于等于 0 的整数'
    return
  }

  let expectedConstraints
  let locationSnapshot
  let tags

  try {
    expectedConstraints =
      parseRequiredObjectJson(
        caseForm.value.expectedConstraints,
        '期望条件'
      )

    locationSnapshot =
      parseOptionalObjectJson(
        caseForm.value.locationSnapshot,
        '位置快照'
      )

    tags = parseOptionalArrayJson(
      caseForm.value.tags,
      '案例标签'
    )
  } catch (error) {
    caseFormError.value = error.message
    return
  }

  const payload = {
    caseCode,
    caseName: caseName || null,
    inputText,
    expectedConstraints:
      JSON.stringify(expectedConstraints),
    locationSnapshot: locationSnapshot
      ? JSON.stringify(locationSnapshot)
      : null,
    tags: JSON.stringify(tags),
    sequenceNo,
    enabled: Boolean(caseForm.value.enabled),
  }

  caseSaving.value = true
  caseFormError.value = ''

  const result =
    caseDialogMode.value === 'create'
      ? await createEvaluationCase(
          selectedDatasetId.value,
          payload
        )
      : await updateEvaluationCase(
          selectedDatasetId.value,
          caseForm.value.id,
          payload
        )

  caseSaving.value = false

  if (!result.success) {
    caseFormError.value =
      result.message || '保存测试案例失败'
    return
  }

  caseDialogVisible.value = false

  showSuccess(
    caseDialogMode.value === 'create'
      ? '测试案例创建成功'
      : '测试案例修改成功'
  )

  await loadCases()
}

const removeCase = async item => {
  const title =
    item.caseName || item.caseCode

  const confirmed = window.confirm(
    `确认删除测试案例“${title}”吗？\n删除后不能恢复。`
  )

  if (!confirmed) {
    return
  }

  caseDeletingId.value = item.id

  const result = await deleteEvaluationCase(
    selectedDatasetId.value,
    item.id
  )

  caseDeletingId.value = null

  if (!result.success) {
    showError(
      result.message || '删除测试案例失败'
    )
    return
  }

  showSuccess(`测试案例“${title}”已删除`)
  await loadCases()
}

const parseRequiredObjectJson = (
  value,
  fieldName
) => {
  const text = value?.trim()

  if (!text) {
    throw new Error(`${fieldName}不能为空`)
  }

  let parsed

  try {
    parsed = JSON.parse(text)
  } catch {
    throw new Error(`${fieldName}必须是合法 JSON`)
  }

  if (
    typeof parsed !== 'object' ||
    parsed === null ||
    Array.isArray(parsed)
  ) {
    throw new Error(`${fieldName}必须是 JSON 对象`)
  }

  return parsed
}

const parseOptionalObjectJson = (
  value,
  fieldName
) => {
  const text = value?.trim()

  if (!text) {
    return null
  }

  return parseRequiredObjectJson(text, fieldName)
}

const parseOptionalArrayJson = (
  value,
  fieldName
) => {
  const text = value?.trim()

  if (!text) {
    return []
  }

  let parsed

  try {
    parsed = JSON.parse(text)
  } catch {
    throw new Error(`${fieldName}必须是合法 JSON`)
  }

  if (!Array.isArray(parsed)) {
    throw new Error(`${fieldName}必须是 JSON 数组`)
  }

  return parsed
}

const runMetrics = run => {
  const parsed = parseJson(run?.metrics)

  return typeof parsed === 'object' && parsed
    ? parsed
    : {}
}

const recommendationsOf = result => {
  const parsed = parseJson(
    result?.recommendationSnapshot
  )

  return Array.isArray(parsed) ? parsed : []
}

const hardMetricsOf = result => {
  const parsed = parseJson(
    result?.hardConditionMetrics
  )

  return typeof parsed === 'object' &&
    parsed !== null &&
    !Array.isArray(parsed)
    ? parsed
    : {}
}

const failureReasonsOf = result => {
  const parsed = parseJson(result?.failureReasons)

  return Array.isArray(parsed) ? parsed : []
}

const caseTitle = caseId => {
  const item = caseMap.value.get(Number(caseId))

  if (!item) {
    return `测试案例 #${caseId}`
  }

  return item.caseName || item.caseCode
}

const relevanceText = value => {
  const map = {
    RELEVANT: '相关',
    PARTIALLY_RELEVANT: '部分相关',
    IRRELEVANT: '不相关',
  }

  return map[value] || '未标注'
}

const formatPercentChange = value => {
  const number = Number(value)

  if (!Number.isFinite(number)) {
    return '0.00%'
  }

  const prefix = number > 0 ? '+' : ''

  return `${prefix}${(number * 100).toFixed(2)}%`
}

const formatNumberChange = value => {
  const number = Number(value)

  if (!Number.isFinite(number)) {
    return '0'
  }

  return number > 0 ? `+${number}` : String(number)
}

const positiveChangeClass = value => {
  const number = Number(value)

  if (number > 0) {
    return 'change-positive'
  }

  if (number < 0) {
    return 'change-negative'
  }

  return 'change-neutral'
}

const failureChangeClass = value => {
  const number = Number(value)

  if (number < 0) {
    return 'change-positive'
  }

  if (number > 0) {
    return 'change-negative'
  }

  return 'change-neutral'
}

const caseChangeText = changeType => {
  const map = {
    IMPROVED: '已提升',
    REGRESSED: '已退步',
    UNCHANGED: '无变化',
    ADDED: '新增案例',
    REMOVED: '移除案例',
  }

  return map[changeType] || changeType || '-'
}

const caseChangeClass = changeType => {
  const map = {
    IMPROVED: 'success',
    REGRESSED: 'danger',
    UNCHANGED: 'muted',
    ADDED: 'warning',
    REMOVED: 'warning',
  }

  return map[changeType] || 'muted'
}

const formatPercent = value => {
  const number = Number(value)

  if (!Number.isFinite(number)) {
    return '0.00%'
  }

  return `${(number * 100).toFixed(2)}%`
}

const formatDate = value => {
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

const statusText = status => {
  const map = {
    DRAFT: '草稿',
    ACTIVE: '启用',
    ARCHIVED: '已归档',
    PENDING: '等待中',
    RUNNING: '执行中',
    COMPLETED: '已完成',
    SUCCESS: '成功',
    FAILED: '失败',
  }

  return map[status] || status || '-'
}

const statusClass = status => {
  if (
    ['COMPLETED', 'SUCCESS', 'ACTIVE'].includes(status)
  ) {
    return 'success'
  }

  if (
    ['FAILED', 'ARCHIVED'].includes(status)
  ) {
    return 'danger'
  }

  if (
    ['RUNNING', 'PENDING'].includes(status)
  ) {
    return 'warning'
  }

  return 'muted'
}

onMounted(loadDatasets)
</script>

<style scoped>

.section-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.table-actions {
  display: flex;
  gap: 8px;
  white-space: nowrap;
}

.table-edit-button,
.table-delete-button {
  padding: 6px 10px;
  background: #fff;
  border-radius: 7px;
  cursor: pointer;
  font: inherit;
  font-size: 12px;
}

.table-edit-button {
  color: #1570ef;
  border: 1px solid #b2ddff;
}

.table-delete-button {
  color: #b42318;
  border: 1px solid #fecdca;
}

.table-delete-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.case-dialog-card {
  width: min(760px, 100%);
}

.form-row {
  display: grid;
  grid-template-columns:
    repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.json-editor {
  font-family:
    Consolas,
    Monaco,
    monospace;
  font-size: 13px;
}

.checkbox-label {
  flex-direction: row;
  align-items: center;
  align-self: end;
  min-height: 40px;
}

.checkbox-label input {
  width: 16px;
  min-height: auto;
  margin: 0;
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.small-primary-button {
  padding: 6px 10px;
  color: #fff;
  background: #1684f8;
  border: none;
  border-radius: 7px;
  cursor: pointer;
  font: inherit;
  font-size: 12px;
}

.edit-dataset-button {
  margin-top: 14px;
  padding: 7px 12px;
  color: #1684f8;
  background: #eff8ff;
  border: 1px solid #b2ddff;
  border-radius: 8px;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
}

.dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(16, 24, 40, 0.48);
}

.dialog-card {
  width: min(580px, 100%);
  max-height: calc(100vh - 48px);
  overflow-y: auto;
  padding: 24px;
  background: #fff;
  border-radius: 14px;
  box-shadow:
    0 24px 48px rgba(16, 24, 40, 0.2);
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  gap: 20px;
}

.dialog-header h3 {
  margin: 0;
  color: #1d2939;
  font-size: 19px;
}

.dialog-header p {
  margin: 7px 0 0;
  color: #667085;
  font-size: 13px;
}

.dialog-close {
  width: 34px;
  height: 34px;
  color: #667085;
  background: #f2f4f7;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 22px;
  line-height: 1;
}

.dialog-error {
  margin-top: 18px;
  padding: 11px 13px;
  color: #b42318;
  background: #fef3f2;
  border: 1px solid #fecdca;
  border-radius: 8px;
  font-size: 13px;
}

.dialog-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 20px;
}

.dialog-form input,
.dialog-form select,
.dialog-form textarea {
  width: 100%;
  padding: 9px 11px;
  box-sizing: border-box;
  color: #344054;
  background: #fff;
  border: 1px solid #d0d5dd;
  border-radius: 8px;
  font: inherit;
}

.dialog-form input,
.dialog-form select {
  min-height: 40px;
}

.dialog-form textarea {
  resize: vertical;
  line-height: 1.6;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 24px;
  padding-top: 18px;
  border-top: 1px solid #eaecf0;
}

.compare-controls {
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 14px;
}

.compare-controls select {
  min-width: 230px;
  min-height: 38px;
  padding: 8px 11px;
  border: 1px solid #d0d5dd;
  border-radius: 8px;
  background: #fff;
}

.compare-arrow {
  padding-bottom: 10px;
  color: #98a2b3;
  font-size: 20px;
}

.compare-warning {
  margin-top: 14px;
  padding: 11px 14px;
  color: #b54708;
  background: #fffaeb;
  border: 1px solid #fedf89;
  border-radius: 8px;
}

.comparison-title {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-top: 22px;
  padding: 16px;
  background: #f8fafc;
  border-radius: 10px;
}

.comparison-title div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.comparison-title span {
  color: #667085;
  font-size: 12px;
}

.comparison-title strong {
  color: #1d2939;
  font-size: 18px;
}

.comparison-direction {
  color: #1684f8;
  font-size: 22px;
}

.comparison-metrics {
  display: grid;
  grid-template-columns:
    repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 16px;
}

.comparison-metric {
  padding: 17px;
  background: #f8fafc;
  border: 1px solid #eaecf0;
  border-radius: 10px;
}

.comparison-metric > span {
  color: #667085;
  font-size: 13px;
}

.metric-values {
  display: flex;
  align-items: baseline;
  gap: 9px;
  margin-top: 8px;
}

.metric-values strong {
  color: #1d2939;
  font-size: 25px;
}

.metric-values small {
  font-weight: 700;
}

.comparison-metric p {
  margin: 8px 0 0;
  color: #98a2b3;
  font-size: 12px;
}

.change-positive {
  color: #027a48;
}

.change-negative {
  color: #b42318;
}

.change-neutral {
  color: #667085;
}

.comparison-summary {
  display: grid;
  grid-template-columns:
    repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.summary-item {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 13px;
  color: #475467;
  background: #f8fafc;
  border-radius: 8px;
}

.summary-item strong {
  font-size: 20px;
}

.improved-summary {
  color: #027a48;
  background: #ecfdf3;
}

.regressed-summary {
  color: #b42318;
  background: #fef3f2;
}

.unchanged-summary {
  color: #475467;
  background: #f2f4f7;
}

.comparison-case-section {
  margin-top: 22px;
}

.comparison-case-section h4 {
  margin: 0 0 13px;
  color: #344054;
}

.comparison-case-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.comparison-case-card {
  padding: 16px;
  border: 1px solid #e4e7ec;
  border-radius: 10px;
}

.comparison-case-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.comparison-case-header div {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.comparison-case-header span {
  color: #98a2b3;
  font-size: 12px;
}

.case-comparison-grid {
  display: grid;
  grid-template-columns:
    repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 15px;
}

.case-comparison-grid > div {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px;
  background: #f8fafc;
  border-radius: 8px;
}

.case-comparison-grid span {
  color: #667085;
  font-size: 12px;
}

.case-comparison-grid small {
  font-weight: 700;
}

.evaluation-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.message {
  padding: 12px 16px;
  border-radius: 10px;
  font-size: 14px;
}

.error-message {
  color: #b42318;
  background: #fef3f2;
  border: 1px solid #fecdca;
}

.success-message {
  color: #027a48;
  background: #ecfdf3;
  border: 1px solid #abefc6;
}

.toolbar-card,
.section-card,
.dataset-summary,
.dataset-panel {
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 14px;
}

.toolbar-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 22px 24px;
}

.toolbar-card h2,
.dataset-summary h2,
.section-header h3,
.panel-header h3 {
  margin: 0;
  color: #1d2939;
}

.toolbar-card p,
.dataset-summary p,
.section-header p {
  margin: 6px 0 0;
  color: #667085;
}

.run-actions {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}

.run-card {
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    background-color 0.2s ease,
    box-shadow 0.2s ease;
}

.run-card.active {
  border-color: #1684f8;
  background: #eff8ff;
  box-shadow:
    0 0 0 2px rgba(22, 132, 248, 0.08);
}

.run-view-hint {
  margin-top: 12px;
  color: #1684f8;
  font-size: 12px;
  font-weight: 600;
}

.result-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.result-card {
  padding: 20px;
  border: 1px solid #e4e7ec;
  border-radius: 12px;
}

.result-header {
  display: flex;
  justify-content: space-between;
  gap: 20px;
}

.result-header h4 {
  margin: 0;
  color: #1d2939;
  font-size: 17px;
}

.result-header p {
  margin: 7px 0 0;
  color: #667085;
  font-size: 14px;
}

.result-overview {
  display: grid;
  grid-template-columns:
    repeat(6, minmax(0, 1fr));
  gap: 12px;
  margin: 18px 0;
  padding: 15px;
  background: #f8fafc;
  border-radius: 10px;
}

.result-overview div {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.result-overview span {
  color: #667085;
  font-size: 12px;
}

.constraint-grid {
  display: grid;
  grid-template-columns:
    repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.constraint-card {
  min-width: 0;
}

.constraint-card h5,
.recommendation-section h5 {
  margin: 0 0 10px;
  color: #344054;
  font-size: 14px;
}

.recommendation-section {
  margin-top: 18px;
}

.recommendation-section h5 span {
  margin-left: 6px;
  color: #98a2b3;
  font-weight: 400;
}

.recommendation-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.recommendation-item {
  padding: 14px;
  background: #f8fafc;
  border: 1px solid #eaecf0;
  border-radius: 9px;
}

.recommendation-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.rank-number {
  margin-right: 8px;
  color: #1684f8;
  font-weight: 700;
}

.score-value {
  color: #027a48;
}

.merchant-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 9px;
  color: #667085;
  font-size: 12px;
}

.recommendation-reason {
  margin: 10px 0 0;
  color: #475467;
  font-size: 13px;
  line-height: 1.65;
}

.no-result-panel {
  margin-top: 18px;
  padding: 15px;
  color: #b54708;
  background: #fffaeb;
  border: 1px solid #fedf89;
  border-radius: 9px;
}

.no-result-panel p {
  margin: 7px 0 0;
  font-size: 13px;
}

.annotation-panel {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  margin-top: 20px;
  padding-top: 18px;
  border-top: 1px solid #eaecf0;
}

.annotation-panel select,
.annotation-panel input {
  min-height: 38px;
  padding: 8px 11px;
  box-sizing: border-box;
  border: 1px solid #d0d5dd;
  border-radius: 8px;
  background: #fff;
}

.annotation-panel select {
  min-width: 145px;
}

.annotation-note {
  flex: 1;
}

.annotation-note input {
  width: 100%;
}

label {
  display: flex;
  flex-direction: column;
  gap: 7px;
  color: #475467;
  font-size: 13px;
}

input {
  width: 80px;
  min-height: 38px;
  padding: 8px 11px;
  box-sizing: border-box;
  border: 1px solid #d0d5dd;
  border-radius: 8px;
}

.primary-button,
.secondary-button,
.text-button {
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font: inherit;
}

.primary-button {
  padding: 10px 17px;
  color: #fff;
  background: #1684f8;
}

.secondary-button {
  padding: 9px 14px;
  color: #344054;
  background: #f2f4f7;
}

.text-button {
  color: #1684f8;
  background: transparent;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.workspace {
  display: grid;
  grid-template-columns: 270px minmax(0, 1fr);
  gap: 20px;
}

.dataset-panel {
  align-self: start;
  padding: 18px;
  position: sticky;
  top: 112px;
}

.panel-header,
.section-header,
.run-card-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.panel-header span {
  color: #98a2b3;
  font-size: 12px;
}

.dataset-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 16px;
}

.dataset-item {
  padding: 14px;
  text-align: left;
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 10px;
  cursor: pointer;
}

.dataset-item.active {
  border-color: #1684f8;
  background: #eff8ff;
  box-shadow:
    0 0 0 2px rgba(22, 132, 248, 0.08);
}

.dataset-name {
  margin-bottom: 10px;
  color: #1d2939;
  font-weight: 700;
}

.dataset-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  color: #667085;
  font-size: 12px;
}

.content-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
}

.empty-content,
.state-panel {
  padding: 35px;
  color: #98a2b3;
  text-align: center;
}

.dataset-summary {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 22px;
}

.section-label {
  margin-bottom: 7px;
  color: #1684f8;
  font-size: 12px;
  font-weight: 700;
}

.summary-meta {
  display: grid;
  grid-template-columns:
    repeat(4, minmax(90px, 1fr));
  gap: 14px;
}

.summary-meta div {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.summary-meta span,
.run-metrics span {
  color: #667085;
  font-size: 12px;
}

.section-card {
  padding: 22px;
}

.section-header {
  align-items: center;
  margin-bottom: 18px;
}

.table-wrapper {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  padding: 12px;
  border-bottom: 1px solid #eaecf0;
  text-align: left;
  vertical-align: top;
}

th {
  color: #475467;
  background: #f9fafb;
  font-size: 13px;
}

td {
  color: #344054;
  font-size: 13px;
}

.input-cell {
  min-width: 210px;
}

pre {
  max-width: 420px;
  margin: 0;
  padding: 10px;
  overflow: auto;
  color: #344054;
  background: #f8fafc;
  border-radius: 8px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-word;
}

.status-badge {
  display: inline-flex;
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 12px;
}

.status-badge.success {
  color: #027a48;
  background: #ecfdf3;
}

.status-badge.warning {
  color: #b54708;
  background: #fffaeb;
}

.status-badge.danger {
  color: #b42318;
  background: #fef3f2;
}

.status-badge.muted {
  color: #475467;
  background: #f2f4f7;
}

.run-grid {
  display: grid;
  grid-template-columns:
    repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.run-card {
  padding: 16px;
  border: 1px solid #e4e7ec;
  border-radius: 11px;
}

.run-model {
  margin: 12px 0;
  color: #667085;
  font-size: 12px;
}

.run-metrics {
  display: grid;
  grid-template-columns:
    repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.run-metrics div {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.run-extra {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin: 14px 0;
  color: #667085;
  font-size: 12px;
}

.run-card small {
  color: #98a2b3;
}

@media (max-width: 1200px) {
  .workspace {
    grid-template-columns: 1fr;
  }

  .dataset-panel {
    position: static;
  }

  .dataset-summary {
    flex-direction: column;
  }

  .run-grid {
    grid-template-columns: 1fr;
  }

  .result-overview {
    grid-template-columns:
      repeat(3, minmax(0, 1fr));
  }

  .comparison-metrics {
    grid-template-columns:
      repeat(2, minmax(0, 1fr));
  }

  .comparison-summary {
    grid-template-columns:
      repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .toolbar-card,
  .run-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .summary-meta,
  .run-metrics {
    grid-template-columns:
      repeat(2, minmax(0, 1fr));
  }

  .result-overview,
  .constraint-grid {
    grid-template-columns: 1fr;
  }

  .annotation-panel {
    align-items: stretch;
    flex-direction: column;
  }

  .compare-controls {
    align-items: stretch;
    flex-direction: column;
  }

  .compare-controls select {
    width: 100%;
    min-width: 0;
  }

  .compare-arrow {
    display: none;
  }

  .section-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .form-row {
    grid-template-columns: 1fr;
  }

  .comparison-metrics,
  .comparison-summary,
  .case-comparison-grid {
    grid-template-columns: 1fr;
  }
}
</style>