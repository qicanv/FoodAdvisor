package com.foodadvisor.dto.competitor;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 周边竞品对比请求 — 商家端发起对比。
 *
 * 商家用户选择周边2~3家同类型商家作为竞品，
 * 后端从数据库查询各商家统计数据后调用 AI 服务生成对比分析。
 *
 * 验收准则对齐：
 * - AC-1: 竞品数量 2~3 家
 * - AC-5: 只允许相近区域和相同/相似品类
 * - AC-7: 商家只能发起与自己店铺相关的对比
 */
@Data
public class CompetitorComparisonRequest {

    /**
     * 竞品商家 ID 列表（不包含本店，本店从当前登录商家身份获取）。
     * 长度限制 1~3，即选择 1~3 家竞品。
     */
    @NotNull(message = "竞品列表不能为空")
    @Size(min = 1, max = 3, message = "竞品数量必须在1~3家之间")
    private List<Long> competitorMerchantIds;
}
