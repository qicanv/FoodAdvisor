package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodadvisor.entity.OpenSearchSyncTask;
import com.foodadvisor.mapper.OpenSearchSyncTaskMapper;
import com.foodadvisor.mapper.DishMapper;
import com.foodadvisor.mapper.ReviewMapper;
import com.foodadvisor.entity.Dish;
import com.foodadvisor.entity.Review;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * OpenSearch 同步任务服务
 * 管理内容状态变更后的 OpenSearch 同步任务，支持失败重试。
 */
@Service
public class OpenSearchSyncService {

    private static final Logger log =
            LoggerFactory.getLogger(OpenSearchSyncService.class);

    /** 最大重试次数 */
    private static final int MAX_RETRY_COUNT = 5;

    private final OpenSearchSyncTaskMapper mapper;
    private final AIClientService aiClientService;
    private final KnowledgeEnrichmentService knowledgeEnrichmentService;
    @Autowired(required = false)
    private DishMapper dishMapper;
    @Autowired(required = false)
    private ReviewMapper reviewMapper;

    public OpenSearchSyncService(
            OpenSearchSyncTaskMapper mapper,
            AIClientService aiClientService,
            KnowledgeEnrichmentService knowledgeEnrichmentService
    ) {
        this.mapper = mapper;
        this.aiClientService = aiClientService;
        this.knowledgeEnrichmentService = knowledgeEnrichmentService;
    }

    /**
     * 创建一条同步任务。
     *
     * @param sourceType    来源类型（MERCHANT / DISH / REVIEW / TOPIC）
     * @param sourceId      来源主键ID
     * @param operationType 操作类型（UPSERT / DISABLE / DELETE / REINDEX）
     * @return 创建的任务
     */
    @Transactional
    public OpenSearchSyncTask createSyncTask(
            String sourceType,
            Long sourceId,
            String operationType
    ) {
        String normalizedSource = "DISH".equals(sourceType)
                ? "MENU" : sourceType;
        OpenSearchSyncTask existing = mapper.selectOne(
                new LambdaQueryWrapper<OpenSearchSyncTask>()
                        .eq(OpenSearchSyncTask::getSourceType, normalizedSource)
                        .eq(OpenSearchSyncTask::getSourceId, sourceId)
                        .eq(OpenSearchSyncTask::getOperationType, operationType)
                        .in(OpenSearchSyncTask::getStatus,
                                OpenSearchSyncTask.STATUS_PENDING,
                                OpenSearchSyncTask.STATUS_PROCESSING)
                        .last("LIMIT 1"));
        if (existing != null) return existing;

        OpenSearchSyncTask task = new OpenSearchSyncTask();
        task.setSourceType(normalizedSource);
        task.setSourceId(sourceId);
        task.setOperationType(operationType);
        task.setContentVersion(1);
        task.setStatus(OpenSearchSyncTask.STATUS_PENDING);
        task.setRetryCount(0);

        mapper.insert(task);

        log.info("创建同步任务: sourceType={}, sourceId={}, operation={}, taskId={}",
                sourceType, sourceId, operationType, task.getId());

        return task;
    }

    /**
     * 处理所有待同步任务。
     * 建议通过定时任务或管理接口手动触发。
     *
     * @return 处理的任务数量
     */
    public int processPendingTasks() {
        LambdaQueryWrapper<OpenSearchSyncTask> wrapper =
                new LambdaQueryWrapper<>();
        wrapper.eq(OpenSearchSyncTask::getStatus, OpenSearchSyncTask.STATUS_PENDING)
                .and(w -> w
                .isNull(OpenSearchSyncTask::getNextRetryAt)
                .or()
                .le(OpenSearchSyncTask::getNextRetryAt, OffsetDateTime.now())
                )
                .orderByAsc(OpenSearchSyncTask::getId)
                .last("LIMIT 100");

        List<OpenSearchSyncTask> tasks = mapper.selectList(wrapper);
        int processedCount = 0;

        for (OpenSearchSyncTask task : tasks) {
            try {
                processTask(task);
                processedCount++;
            } catch (Exception e) {
                log.error("处理同步任务失败: taskId={}, error={}",
                        task.getId(), e.getMessage(), e);
            }
        }

        return processedCount;
    }

    /**
     * 重试失败的同步任务。
     *
     * @return 重试的任务数量
     */
    public int retryFailed() {
        LambdaQueryWrapper<OpenSearchSyncTask> wrapper =
                new LambdaQueryWrapper<>();
        wrapper.eq(OpenSearchSyncTask::getStatus, OpenSearchSyncTask.STATUS_FAILED)
                .lt(OpenSearchSyncTask::getRetryCount, MAX_RETRY_COUNT)
                .le(OpenSearchSyncTask::getNextRetryAt, OffsetDateTime.now());

        List<OpenSearchSyncTask> tasks = mapper.selectList(wrapper);
        int retriedCount = 0;

        for (OpenSearchSyncTask task : tasks) {
            try {
                processTask(task);
                retriedCount++;
            } catch (Exception e) {
                log.error("重试同步任务失败: taskId={}, error={}",
                        task.getId(), e.getMessage(), e);
            }
        }

        return retriedCount;
    }

    @Scheduled(fixedDelayString = "${opensearch.sync.fixed-delay-ms:15000}")
    public void consumeOutbox() {
        try {
            processPendingTasks();
            retryFailed();
        } catch (RuntimeException exception) {
            // Keep application startup healthy while a rolling deployment is
            // waiting for the idempotent outbox migration to be applied.
            log.warn("OpenSearch outbox consumer skipped this cycle: {}",
                    exception.getClass().getSimpleName());
        }
    }

    /**
     * 标记任务为成功。
     */
    public void markSuccess(Long taskId) {
        OpenSearchSyncTask task = mapper.selectById(taskId);
        if (task != null) {
            task.setStatus(OpenSearchSyncTask.STATUS_SUCCESS);
            mapper.updateById(task);
            log.info("同步任务成功: taskId={}", taskId);
        }
    }

    /**
     * 标记任务为失败，并设置下次重试时间（指数退避）。
     */
    public void markFailed(Long taskId, String errorMessage) {
        OpenSearchSyncTask task = mapper.selectById(taskId);
        if (task != null) {
            task.setStatus(OpenSearchSyncTask.STATUS_FAILED);
            task.setErrorMessage(
                    errorMessage != null && errorMessage.length() > 500
                            ? errorMessage.substring(0, 500)
                            : errorMessage
            );

            int retryCount = task.getRetryCount() != null ? task.getRetryCount() : 0;
            if (retryCount < MAX_RETRY_COUNT) {
                task.setRetryCount(retryCount + 1);
                // 指数退避：2^retryCount 分钟
                long delayMinutes = (long) Math.pow(2, retryCount + 1);
                task.setNextRetryAt(OffsetDateTime.now().plusMinutes(delayMinutes));
            }

            mapper.updateById(task);
            log.warn("同步任务失败: taskId={}, retry={}/{}, error={}",
                    taskId, task.getRetryCount(), MAX_RETRY_COUNT, errorMessage);
        }
    }

    // ============================================
    // 内部处理逻辑
    // ============================================

    private void processTask(OpenSearchSyncTask task) {
        // 标记为处理中
        task.setStatus(OpenSearchSyncTask.STATUS_PROCESSING);
        mapper.updateById(task);

        try {
            if (OpenSearchSyncTask.OP_DISABLE.equals(task.getOperationType())
                    || OpenSearchSyncTask.OP_DELETE.equals(task.getOperationType())) {
                // 停用操作：调用 AI 服务 deactivate 接口
                aiClientService.deactivateKnowledge(
                        task.getSourceType(),
                        List.of(task.getSourceId())
                );
            } else if (OpenSearchSyncTask.OP_UPSERT.equals(task.getOperationType())) {
                // UPSERT：根据来源类型构造增强文本并同步
                Long sourceId = task.getSourceId();
                switch (task.getSourceType()) {
                    case "MERCHANT" -> {
                        knowledgeEnrichmentService.syncMerchantIntro(sourceId);
                        knowledgeEnrichmentService.syncMerchantMenu(sourceId);
                        knowledgeEnrichmentService.syncMerchantReviews(sourceId);
                    }
                    case "MERCHANT_INTRO" ->
                            knowledgeEnrichmentService.syncMerchantIntro(sourceId);
                    case "MENU" -> {
                        Dish dish = dishMapper == null
                                ? null : dishMapper.selectById(sourceId);
                        if (dish == null || dish.getMerchantId() == null) {
                            aiClientService.deactivateKnowledge(
                                    "MENU", List.of(sourceId));
                        } else {
                            knowledgeEnrichmentService.syncMerchantMenu(
                                    dish.getMerchantId());
                        }
                    }
                    case "REVIEW" -> {
                        Review review = reviewMapper == null
                                ? null : reviewMapper.selectById(sourceId);
                        boolean visible = review != null
                                && "PUBLISHED".equals(review.getStatus())
                                && "APPROVED".equals(
                                review.getModerationStatus())
                                && review.getDeletedAt() == null;
                        if (visible) {
                            knowledgeEnrichmentService.syncMerchantReviews(
                                    review.getMerchantId());
                        } else {
                            aiClientService.deactivateKnowledge(
                                    "REVIEW", List.of(sourceId));
                        }
                    }
                    default ->
                            log.warn("未知同步来源类型: {}", task.getSourceType());
                }
            }

            markSuccess(task.getId());
        } catch (Exception e) {
            markFailed(task.getId(), e.getMessage());
        }
    }
}
