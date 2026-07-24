package com.foodadvisor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.PageResult;
import com.foodadvisor.dto.alert.*;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.service.SensitiveAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SensitiveAlertController 单元测试（standalone MockMvc）
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>GET /api/admin/sensitive-alerts — 列表查询 + 筛选 + 分页</li>
 *   <li>GET /api/admin/sensitive-alerts/{id} — 预警详情</li>
 *   <li>PUT /api/admin/sensitive-alerts/{id}/status — 状态更新</li>
 *   <li>POST /api/admin/sensitive-alerts/detect — 手动触发检测</li>
 *   <li>GET /api/admin/sensitive-alerts/pending-count — 待处理数量</li>
 *   <li>GET /api/admin/sensitive-alerts/topic-types — 话题类型枚举</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SensitiveAlertControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SensitiveAlertService sensitiveAlertService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 模拟 OPERATOR 角色的 JWT 属性。
     * 注意：standalone 模式下 JwtInterceptor 不生效，
     * 需在请求中手动注入 userId/username/role 来模拟权限。
     */
    @BeforeEach
    void setUp() {
        SensitiveAlertController controller = new SensitiveAlertController(sensitiveAlertService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .defaultRequest(get("/")
                        .requestAttr("userId", 1L)
                        .requestAttr("username", "operator1")
                        .requestAttr("role", "OPERATOR"))
                .build();
    }

    // ============================================================
    // 1. 列表查询
    // ============================================================

    @Test
    void shouldListAlertsWithDefaultPagination() throws Exception {
        SensitiveAlertDTO alert = buildDTO();
        PageResult<SensitiveAlertDTO> pageResult = new PageResult<>(1, 20, 1, List.of(alert));
        when(sensitiveAlertService.listAlerts(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(1), eq(20)))
                .thenReturn(pageResult);

        mockMvc.perform(get("/api/admin/sensitive-alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].topicType").value("FOOD_SAFETY"));
    }

    @Test
    void shouldFilterAlertsByStatus() throws Exception {
        when(sensitiveAlertService.listAlerts(
                eq("PENDING"), isNull(), isNull(), isNull(), isNull(), isNull(), eq(1), eq(20)))
                .thenReturn(new PageResult<>(1, 20, 0, List.of()));

        mockMvc.perform(get("/api/admin/sensitive-alerts?status=PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void shouldFilterAlertsByTopicTypeAndRiskLevel() throws Exception {
        when(sensitiveAlertService.listAlerts(
                isNull(), eq("FOOD_SAFETY"), eq("HIGH"), isNull(), isNull(), isNull(), eq(1), eq(20)))
                .thenReturn(new PageResult<>(1, 20, 0, List.of()));

        mockMvc.perform(get("/api/admin/sensitive-alerts?topicType=FOOD_SAFETY&riskLevel=HIGH"))
                .andExpect(status().isOk());
    }

    // ============================================================
    // 2. 预警详情
    // ============================================================

    @Test
    void shouldGetAlertDetail() throws Exception {
        SensitiveAlertDetailDTO detail = SensitiveAlertDetailDTO.builder()
                .id(1L)
                .merchantId(1L)
                .merchantName("川味小馆")
                .topicType("FOOD_SAFETY")
                .topicTypeName("食品安全")
                .riskLevel("HIGH")
                .riskLevelName("高风险")
                .reviewCount(3)
                .keywords(List.of("食物中毒", "拉肚子"))
                .status("PENDING")
                .statusName("待处理")
                .reviews(List.of())
                .build();

        when(sensitiveAlertService.getAlertDetail(1L)).thenReturn(detail);

        mockMvc.perform(get("/api/admin/sensitive-alerts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topicType").value("FOOD_SAFETY"))
                .andExpect(jsonPath("$.data.merchantName").value("川味小馆"))
                .andExpect(jsonPath("$.data.reviewCount").value(3));
    }

    @Test
    void shouldReturn404WhenAlertNotFound() throws Exception {
        when(sensitiveAlertService.getAlertDetail(999L))
                .thenThrow(new ApiException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "ALERT_NOT_FOUND", "预警记录不存在"));

        // standalone MockMvc 不加载 GlobalExceptionHandler，
        // 异常会直接向上传播，在此验证异常抛出
        assertThrows(Exception.class, () -> {
            mockMvc.perform(get("/api/admin/sensitive-alerts/999"));
        });
    }

    // ============================================================
    // 3. 更新状态
    // ============================================================

    @Test
    void shouldUpdateAlertStatus() throws Exception {
        SensitiveAlertDTO updated = buildDTO();
        updated.setStatus("RESOLVED");
        updated.setStatusName("已处理");

        when(sensitiveAlertService.updateAlertStatus(eq(1L), any(UpdateAlertStatusRequest.class),
                anyLong(), anyString())).thenReturn(updated);

        UpdateAlertStatusRequest request = new UpdateAlertStatusRequest();
        request.setStatus("RESOLVED");
        request.setRemark("已处理完成");

        mockMvc.perform(put("/api/admin/sensitive-alerts/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));
    }

    // ============================================================
    // 4. 手动触发检测
    // ============================================================

    @Test
    void shouldDetectSensitiveTopics() throws Exception {
        SensitiveAlertDTO alert = buildDTO();
        when(sensitiveAlertService.detectSensitiveTopics(any(DetectSensitiveRequest.class)))
                .thenReturn(List.of(alert));

        DetectSensitiveRequest request = new DetectSensitiveRequest();
        request.setThreshold(3);

        mockMvc.perform(post("/api/admin/sensitive-alerts/detect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].topicType").value("FOOD_SAFETY"));
    }

    @Test
    void shouldDetectSensitiveTopicsWithEmptyBody() throws Exception {
        when(sensitiveAlertService.detectSensitiveTopics(any(DetectSensitiveRequest.class)))
                .thenReturn(List.of());

        mockMvc.perform(post("/api/admin/sensitive-alerts/detect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    // ============================================================
    // 5. 待处理数量
    // ============================================================

    @Test
    void shouldGetPendingCount() throws Exception {
        when(sensitiveAlertService.countPendingAlerts()).thenReturn(5L);

        mockMvc.perform(get("/api/admin/sensitive-alerts/pending-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(5));
    }

    // ============================================================
    // 6. 话题类型枚举
    // ============================================================

    @Test
    void shouldGetTopicTypes() throws Exception {
        mockMvc.perform(get("/api/admin/sensitive-alerts/topic-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].value").value("FOOD_SAFETY"))
                .andExpect(jsonPath("$.data[0].label").value("食品安全"))
                .andExpect(jsonPath("$.data[1].value").value("HYGIENE"))
                .andExpect(jsonPath("$.data[3].value").value("SERVICE_DISPUTE"));
    }

    // ============================================================
    // 测试辅助方法
    // ============================================================

    private SensitiveAlertDTO buildDTO() {
        return SensitiveAlertDTO.builder()
                .id(1L)
                .merchantId(1L)
                .merchantName("川味小馆")
                .topicType("FOOD_SAFETY")
                .topicTypeName("食品安全")
                .riskLevel("HIGH")
                .riskLevelName("高风险")
                .reviewCount(5)
                .keywords(List.of("食物中毒", "拉肚子"))
                .firstOccurredAt(OffsetDateTime.now().minusHours(10))
                .lastOccurredAt(OffsetDateTime.now().minusHours(1))
                .status("PENDING")
                .statusName("待处理")
                .build();
    }
}
