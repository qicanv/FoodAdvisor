package com.foodadvisor.config;

import com.foodadvisor.service.RegionHotWordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 区域热词定时任务配置
 *
 * 负责定时自动触发区域热词生成，确保前端始终展示最新的热词数据。
 *
 * 调度策略：
 * - 每日热词：每天凌晨 2:00 生成（统计前 1 天）
 * - 每周热词：每周一凌晨 3:00 生成（统计前 7 天）
 * - 每月热词：每月 1 号凌晨 4:00 生成（统计前 30 天）
 *
 * 可通过配置 foodadvisor.hot-words.scheduled.enabled=false 禁用定时任务，
 * 禁用后只能通过管理接口手动触发生成。
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        name = "foodadvisor.hot-words.scheduled.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class HotWordScheduler {

    private static final Logger log = LoggerFactory.getLogger(HotWordScheduler.class);

    private final RegionHotWordService hotWordService;

    public HotWordScheduler(RegionHotWordService hotWordService) {
        this.hotWordService = hotWordService;
    }

    /**
     * 每日热词生成 —— 每天凌晨 2:00 执行。
     * 统计前一天的评价数据生成当日热词。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void generateDailyHotWords() {
        log.info("[定时任务] 开始生成每日热词...");
        try {
            int count = hotWordService.regenerateAll("DAILY", 1);
            log.info("[定时任务] 每日热词生成完成，共 {} 个热词", count);
        } catch (Exception e) {
            log.error("[定时任务] 每日热词生成失败", e);
        }
    }

    /**
     * 每周热词生成 —— 每周一凌晨 3:00 执行。
     * 统计前 7 天的评价数据生成周热词。
     */
    @Scheduled(cron = "0 0 3 * * MON")
    public void generateWeeklyHotWords() {
        log.info("[定时任务] 开始生成每周热词...");
        try {
            int count = hotWordService.regenerateAll("WEEKLY", 7);
            log.info("[定时任务] 每周热词生成完成，共 {} 个热词", count);
        } catch (Exception e) {
            log.error("[定时任务] 每周热词生成失败", e);
        }
    }

    /**
     * 每月热词生成 —— 每月 1 号凌晨 4:00 执行。
     * 统计前 30 天的评价数据生成月热词。
     */
    @Scheduled(cron = "0 0 4 1 * ?")
    public void generateMonthlyHotWords() {
        log.info("[定时任务] 开始生成每月热词...");
        try {
            int count = hotWordService.regenerateAll("MONTHLY", 30);
            log.info("[定时任务] 每月热词生成完成，共 {} 个热词", count);
        } catch (Exception e) {
            log.error("[定时任务] 每月热词生成失败", e);
        }
    }
}
