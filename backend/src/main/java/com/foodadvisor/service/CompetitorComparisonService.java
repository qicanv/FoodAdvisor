package com.foodadvisor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.competitor.CompetitorComparisonRequest;
import com.foodadvisor.dto.competitor.CompetitorComparisonResponse;
import com.foodadvisor.dto.competitor.CompetitorMerchantVO;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.mapper.CompetitorComparisonMapper;
import com.foodadvisor.mapper.MerchantMapper;
import com.foodadvisor.trace.AiTraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 周边竞品对比服务（EPIC-02 Story 6）
 *
 * 负责：
 * 1. 查询本店与竞品的统计数据（评分、好评率、差评问题等）
 * 2. 校验竞品的区域和品类合法性
 * 3. 调用 AI 服务生成对比分析文字
 * 4. 组装数据库统计 + AI 分析为前端可用的响应
 *
 * 验收准则对齐：
 * - AC-1: 用户从相近区域和相同/相似品类中选择 2~3 家竞品
 * - AC-2: 对比至少包含价格、评分、好评率和评价数量
 * - AC-3: AI 分析突出优势或短板，无明显差异时明确说明
 * - AC-4: 固定数据下对比数值与数据库一致
 * - AC-5: 不符合区域/品类限制的商家默认不可选，强制请求时拒绝
 * - AC-7: 商家只能发起与自己店铺相关的竞品对比
 */
@Service
public class CompetitorComparisonService {

    private static final Logger log =
            LoggerFactory.getLogger(CompetitorComparisonService.class);

    /** 候选竞品查询上限 */
    private static final int MAX_CANDIDATE_COMPETITORS = 20;

    private final CompetitorComparisonMapper comparisonMapper;
    private final MerchantMapper merchantMapper;
    private final AIClientService aiClientService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AiRequestTraceService traceService;

    public CompetitorComparisonService(
            CompetitorComparisonMapper comparisonMapper,
            MerchantMapper merchantMapper,
            AIClientService aiClientService,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this(comparisonMapper, merchantMapper, aiClientService,
                jdbcTemplate, objectMapper, null);
    }

    @Autowired
    public CompetitorComparisonService(
            CompetitorComparisonMapper comparisonMapper,
            MerchantMapper merchantMapper,
            AIClientService aiClientService,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            AiRequestTraceService traceService
    ) {
        this.comparisonMapper = comparisonMapper;
        this.merchantMapper = merchantMapper;
        this.aiClientService = aiClientService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.traceService = traceService;
    }

    // ============================================
    // 商家端 API
    // ============================================

    /**
     * 获取本店周边可选的候选竞品列表。
     *
     * 筛选条件：同区域 + 同品类（或同菜系），营业中的活跃商家，
     * 排除本店自身。按评分降序排列，最多返回 20 家。
     *
     * 前端可据此渲染竞品选择器（验收准则 AC-1/AC-5）。
     *
     * @param merchantId 本店 ID
     * @return 候选竞品的基础信息列表
     */
    public List<CompetitorMerchantVO> getCandidateCompetitors(Long merchantId) {
        // 1. 获取本店基础信息
        Merchant self = merchantMapper.selectById(merchantId);
        if (self == null) {
                                    throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "商家不存在");
        }

        // 2. 查询同区域同品类的候选竞品
        List<Map<String, Object>> rows = comparisonMapper.findNearbyCompetitors(
                merchantId,
                self.getRegionCode(),
                self.getCategory(),
                self.getCuisine(),
                MAX_CANDIDATE_COMPETITORS
        );

        // 3. 转换为 VO
        return rows.stream()
                .map(this::mapToBasicVO)
                .collect(Collectors.toList());
    }

    /**
     * 执行竞品对比分析（不含 AI 追踪上下文，向后兼容）。
     *
     * @see #performComparison(Long, CompetitorComparisonRequest, AiTraceContext)
     */
    public CompetitorComparisonResponse performComparison(
            Long merchantId,
            CompetitorComparisonRequest request
    ) {
        AiTraceContext context = traceService == null
                ? null
                : traceService.startTrace(null, null, null, "COMPETITOR_ANALYSIS");
        try {
            return performComparison(merchantId, request, context);
        } catch (RuntimeException exception) {
            if (context != null) {
                traceService.failTraceSafely(context,
                        "COMPETITOR_ANALYSIS_FAILED", exception.getMessage());
            }
            throw exception;
        }
    }

    /**
     * 执行竞品对比分析（完整流程）。
     *
     * 流程：
     * 1. 获取本店基础信息 + 验证合法性
     * 2. 校验竞品 ID 均在合法候选列表中（区域 + 品类限制）
     * 3. 查询所有参与对比商家的详细统计数据
     * 4. 调用 AI 服务生成对比分析
     * 5. 组装最终响应
     *
     * @param merchantId 本店 ID
     * @param request    前端请求（包含竞品 ID 列表）
     * @param context    AI 追踪上下文（可为 null）
     * @return 完整对比结果（统计数据 + AI 分析）
     */
    public CompetitorComparisonResponse performComparison(
            Long merchantId,
            CompetitorComparisonRequest request,
            AiTraceContext context
    ) {
        // ---- 1. 获取本店基础信息 ----
        Merchant self = merchantMapper.selectById(merchantId);
        if (self == null) {
                                    throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "商家不存在");
        }

        // ---- 2. 校验竞品合法性（验收准则 AC-5） ----
        List<Long> competitorIds = request.getCompetitorMerchantIds();
        if (competitorIds == null || competitorIds.isEmpty()) {
                        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_INPUT",
                    "请至少选择 1 家竞品商家");
        }
        if (competitorIds.size() > 3) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_INPUT",
                    "最多选择 3 家竞品商家");
        }
        if (competitorIds.contains(merchantId)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_INPUT",
                    "不能选择本店作为竞品");
        }

        // 获取合法候选竞品列表
        List<Map<String, Object>> candidates = comparisonMapper.findNearbyCompetitors(
                merchantId,
                self.getRegionCode(),
                self.getCategory(),
                self.getCuisine(),
                MAX_CANDIDATE_COMPETITORS
        );
        Set<Long> validCompetitorIds = candidates.stream()
                .map(row -> ((Number) row.get("id")).longValue())
                .collect(Collectors.toSet());

        // 校验请求中的竞品 ID 都合法
        for (Long cid : competitorIds) {
            if (!validCompetitorIds.contains(cid)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_COMPETITOR",
                        "商家 ID=" + cid + " 不在可对比范围内"
                                + "（需要同区域、同品类且正常营业）");
            }
        }

        // ---- 3. 查询所有参与对比商家的详细统计数据 ----
        // 组装本店 + 竞品的 ID 列表
        List<Long> allMerchantIds = new ArrayList<>();
        allMerchantIds.add(merchantId);
        allMerchantIds.addAll(competitorIds);

        // 查询每家商家的完整数据
        List<CompetitorMerchantVO> merchantDataList = new ArrayList<>();
        // 本店排第一
        merchantDataList.add(buildMerchantData(merchantId, true));
        for (Long cid : competitorIds) {
            merchantDataList.add(buildMerchantData(cid, false));
        }

        // ---- 4. 调用 AI 服务生成对比分析 ----
        List<Map<String, Object>> aiRequestData = merchantDataList.stream()
                .map(this::toAiRequestMap)
                .collect(Collectors.toList());

        JsonNode aiResult;
        try {
            aiResult = context == null
                    ? aiClientService.generateCompetitorComparison(merchantId, aiRequestData)
                    : aiClientService.generateCompetitorComparison(merchantId, aiRequestData, context);
        } catch (Exception e) {
            log.error("竞品对比 AI 调用失败 merchantId={}: {}", merchantId, e.getMessage());
            // AI 调用失败时返回降级结果（只有统计数据，无 AI 分析）
            return CompetitorComparisonResponse.builder()
                    .merchantId(merchantId)
                    .comparisonStatus("FAILED")
                    .merchantData(merchantDataList)
                    .errorMessage("AI 分析服务调用失败：" + e.getMessage())
                    .build();
        }

        // ---- 5. 组装响应 ----
        List<CompetitorComparisonResponse.AiMerchantAnalysis> aiAnalyses =
                parseAiAnalyses(aiResult);

        return CompetitorComparisonResponse.builder()
                .merchantId(merchantId)
                .comparisonStatus(
                        aiResult.has("comparisonStatus")
                                ? aiResult.get("comparisonStatus").asText()
                                : "SUCCESS"
                )
                .merchantData(merchantDataList)
                .aiMerchantAnalyses(aiAnalyses)
                .aiSummaryText(
                        aiResult.has("summaryText") && !aiResult.get("summaryText").isNull()
                                ? aiResult.get("summaryText").asText()
                                : null
                )
                .aiImprovementSuggestions(parseStringList(aiResult, "improvementSuggestions"))
                .modelName(
                        aiResult.has("modelName") && !aiResult.get("modelName").isNull()
                                ? aiResult.get("modelName").asText()
                                : null
                )
                .businessTraceId(
                        aiResult.has("businessTraceId") && !aiResult.get("businessTraceId").isNull()
                                ? aiResult.get("businessTraceId").asText()
                                : null
                )
                .build();
    }

    // ============================================
    // 商家统计数据组装
    // ============================================

    /**
     * 查询单家商家的完整统计数据（供对比使用）。
     *
     * 查询内容包括：
     * - 基本信息（名称、类别、菜系、地址、人均、评分、评价数）
     * - 好评率
     * - 分项评分（口味、环境、服务）
     * - 高频正面标签
     * - 主要差评问题
     *
     * @param merchantId 商家 ID
     * @param isSelf     是否为本店
     * @return 商家对比数据 VO
     */
    private CompetitorMerchantVO buildMerchantData(Long merchantId, boolean isSelf) {
        // 基础统计
        Map<String, Object> basic = comparisonMapper.getMerchantBasicStats(merchantId);
        if (basic == null || basic.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                    "商家 ID=" + merchantId + " 不存在");
        }

        // 好评率
        BigDecimal positiveRate = comparisonMapper.getPositiveRate(merchantId);

        // 分项评分
        Map<String, Object> dimRatings = comparisonMapper.getDimensionRatings(merchantId);

        // 正面标签
        List<String> topPositiveTags = comparisonMapper.getTopPositiveTags(merchantId)
                .stream()
                .map(row -> String.valueOf(row.get("tag_name")))
                .collect(Collectors.toList());

        // 差评问题
        List<String> topNegativeIssues = comparisonMapper.getTopNegativeIssues(merchantId)
                .stream()
                .map(row -> String.valueOf(row.get("category_name")))
                .collect(Collectors.toList());

        return CompetitorMerchantVO.builder()
                .merchantId(merchantId)
                .merchantName(String.valueOf(basic.get("name")))
                .category(String.valueOf(basic.getOrDefault("category", "")))
                .cuisine(basic.get("cuisine") != null
                        ? String.valueOf(basic.get("cuisine")) : null)
                .address(basic.get("address") != null
                        ? String.valueOf(basic.get("address")) : null)
                .isSelf(isSelf)
                .averagePrice(toBigDecimal(basic.get("average_price")))
                .rating(toBigDecimal(basic.get("rating")))
                .reviewCount(basic.get("review_count") instanceof Number n
                        ? n.intValue() : 0)
                .positiveRate(positiveRate)
                .tasteRating(toBigDecimal(
                        dimRatings != null ? dimRatings.get("taste_avg") : null))
                .environmentRating(toBigDecimal(
                        dimRatings != null ? dimRatings.get("environment_avg") : null))
                .serviceRating(toBigDecimal(
                        dimRatings != null ? dimRatings.get("service_avg") : null))
                .topPositiveTags(topPositiveTags)
                .topNegativeIssues(topNegativeIssues)
                .build();
    }

    // ============================================
    // AI 结果解析
    // ============================================

    /**
     * 解析 AI 返回的 merchantAnalyses 数组。
     */
    private List<CompetitorComparisonResponse.AiMerchantAnalysis> parseAiAnalyses(
            JsonNode aiResult
    ) {
        JsonNode analysesNode = aiResult.get("merchantAnalyses");
        if (analysesNode == null || !analysesNode.isArray()) {
            return Collections.emptyList();
        }

        List<CompetitorComparisonResponse.AiMerchantAnalysis> result = new ArrayList<>();
        for (JsonNode item : analysesNode) {
            result.add(CompetitorComparisonResponse.AiMerchantAnalysis.builder()
                    .merchantId(item.has("merchantId")
                            ? item.get("merchantId").asLong() : null)
                    .merchantName(item.has("merchantName")
                            ? item.get("merchantName").asText() : null)
                    .strengths(parseStringList(item, "strengths"))
                    .weaknesses(parseStringList(item, "weaknesses"))
                    .overallAssessment(item.has("overallAssessment")
                            ? item.get("overallAssessment").asText() : "")
                    .build());
        }
        return result;
    }

    /**
     * 从 JsonNode 中解析字符串数组。
     */
    private List<String> parseStringList(JsonNode parent, String fieldName) {
        JsonNode arrayNode = parent.get(fieldName);
        if (arrayNode == null || !arrayNode.isArray()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : arrayNode) {
            if (item.isTextual()) {
                result.add(item.asText());
            }
        }
        return result;
    }

    // ============================================
    // 工具方法
    // ============================================

    /**
     * 将 CompetitorMerchantVO 转换为发送给 AI 服务的 Map 格式。
     */
    private Map<String, Object> toAiRequestMap(CompetitorMerchantVO vo) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("merchantId", vo.getMerchantId());
        map.put("merchantName", vo.getMerchantName());
        map.put("category", vo.getCategory());
        map.put("cuisine", vo.getCuisine());
        map.put("address", vo.getAddress());
        map.put("averagePrice", vo.getAveragePrice());
        map.put("rating", vo.getRating());
        map.put("reviewCount", vo.getReviewCount());
        map.put("positiveRate", vo.getPositiveRate());
        map.put("tasteRating", vo.getTasteRating());
        map.put("environmentRating", vo.getEnvironmentRating());
        map.put("serviceRating", vo.getServiceRating());
        map.put("topPositiveTags", vo.getTopPositiveTags() != null
                ? vo.getTopPositiveTags() : Collections.emptyList());
        map.put("topNegativeIssues", vo.getTopNegativeIssues() != null
                ? vo.getTopNegativeIssues() : Collections.emptyList());
        return map;
    }

    /**
     * 将竞品候选列表的 Map 行转换为基础 VO（仅基本信息，不包含详细统计）。
     */
    private CompetitorMerchantVO mapToBasicVO(Map<String, Object> row) {
        return CompetitorMerchantVO.builder()
                .merchantId(((Number) row.get("id")).longValue())
                .merchantName(String.valueOf(row.get("name")))
                .category(String.valueOf(row.getOrDefault("category", "")))
                .cuisine(row.get("cuisine") != null
                        ? String.valueOf(row.get("cuisine")) : null)
                .address(row.get("address") != null
                        ? String.valueOf(row.get("address")) : null)
                .averagePrice(toBigDecimal(row.get("average_price")))
                .rating(toBigDecimal(row.get("rating")))
                .reviewCount(row.get("review_count") instanceof Number n
                        ? n.intValue() : 0)
                .isSelf(false)
                .build();
    }

    /**
     * 将 Object 安全转为 BigDecimal。
     */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        try {
            return new BigDecimal(value.toString())
                    .setScale(1, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
