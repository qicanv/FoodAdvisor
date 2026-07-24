import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";

import AiDiningView from "./AiDiningView.vue";

const api = vi.hoisted(() => ({
  createDiningSession: vi.fn(),
  deleteDiningSession: vi.fn(),
  getDiningMessages: vi.fn(),
  getDiningSessions: vi.fn(),
  getRecommendationEvidences: vi.fn(),
  sendDiningMessage: vi.fn(),
  adjustDiningRecommendation: vi.fn(),
}));

const routerMock = vi.hoisted(() => ({
  push: vi.fn(),
  replace: vi.fn(() => Promise.resolve()),
}));

const routeMock = vi.hoisted(() => ({
  path: "/diner/ai-dining",
  query: {},
}));

vi.mock("../../api/aiDining", () => api);
vi.mock("../../api/behavior", () => ({
  logMerchantClick: vi.fn(() => Promise.resolve()),
  logSearch: vi.fn(() => Promise.resolve()),
}));
vi.mock("vue-router", () => ({
  useRouter: () => routerMock,
  useRoute: () => routeMock,
}));

const success = (overrides) => ({
  success: true,
  data: {
    userMessageId: 10,
    assistantMessageId: 11,
    assistantText: "已完成",
    responseType: "RECOMMENDATION",
    currentConstraints: { cuisines: ["川菜"] },
    recommendation: {
      recommendationId: 20,
      semanticStatus: "FULL",
      results: [],
      adjustmentSuggestions: [],
      limitingConditions: [],
    },
    ...overrides,
  },
});

async function mounted(history = []) {
  localStorage.setItem("foodadvisor.aiDining.session.anonymous", "1");
  api.getDiningMessages.mockResolvedValue({
    success: true,
    data: { messages: history },
  });
  const wrapper = mount(AiDiningView);
  await flushPromises();
  return wrapper;
}

beforeEach(() => {
  localStorage.clear();
  vi.clearAllMocks();

  routeMock.path = "/diner/ai-dining";
  routeMock.query = {};

  api.getRecommendationEvidences.mockResolvedValue({
    success: true,
    data: [],
  });
  api.getDiningSessions.mockResolvedValue({
    success: true,
    data: { sessions: [] },
  });
});

describe("AiDiningView", () => {
  it("rejects blank input without sending", async () => {
    const wrapper = await mounted();
    await wrapper.find("textarea").setValue("   ");
    await wrapper.find("form").trigger("submit");
    expect(api.sendDiningMessage).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("不能只输入空格");
  });

  it("suppresses double send while the first request is pending", async () => {
    let resolve;
    api.sendDiningMessage.mockReturnValue(
      new Promise((done) => {
        resolve = done;
      }),
    );
    const wrapper = await mounted();
    await wrapper.find("textarea").setValue("四个人吃川菜");
    wrapper.find("form").trigger("submit");
    wrapper.find("form").trigger("submit");
    await Promise.resolve();
    expect(api.sendDiningMessage).toHaveBeenCalledTimes(1);
    resolve(success());
    await flushPromises();
  });

  it("reuses requestId when retrying the same failed send", async () => {
    api.sendDiningMessage
      .mockResolvedValueOnce({ success: false, message: "暂时失败" })
      .mockResolvedValueOnce(success());
    const wrapper = await mounted();
    await wrapper.find("textarea").setValue("两个人吃烧烤");
    await wrapper.find("form").trigger("submit");
    await flushPromises();
    await wrapper.find(".retry-button").trigger("click");
    await flushPromises();
    expect(api.sendDiningMessage.mock.calls[0][2]).toBe(
      api.sendDiningMessage.mock.calls[1][2],
    );
  });

  it("clears an old error after success", async () => {
    api.sendDiningMessage.mockResolvedValue(success());
    const wrapper = await mounted();
    await wrapper.find("textarea").setValue(" ");
    await wrapper.find("form").trigger("submit");
    await wrapper.find("textarea").setValue("直接推荐");
    await wrapper.find("form").trigger("submit");
    await flushPromises();
    expect(wrapper.find(".error-banner").exists()).toBe(false);
  });

  it("shows a nonfatal degradation notice", async () => {
    api.sendDiningMessage.mockResolvedValue(
      success({
        degraded: true,
        extractor: "RULE_FALLBACK",
      }),
    );
    const wrapper = await mounted();
    await wrapper.find("textarea").setValue("直接推荐");
    await wrapper.find("form").trigger("submit");
    await flushPromises();
    expect(wrapper.find(".notice-banner").text()).toContain("规则安全降级");
  });

  it("renders recommendation cards and an evidence entry for every card", async () => {
    api.sendDiningMessage.mockResolvedValue(
      success({
        recommendation: {
          recommendationId: 20,
          semanticStatus: "FULL",
          results: [
            {
              merchantId: 12,
              merchantName: "真实测试店",
              operationStatus: "OPERATING",
              riskNotes: ["预算接近上限"],
              recommendationBases: [],
            },
          ],
          adjustmentSuggestions: [],
          limitingConditions: [],
        },
      }),
    );
    const wrapper = await mounted();
    await wrapper.find("textarea").setValue("推荐川菜");
    await wrapper.find("form").trigger("submit");
    await flushPromises();
    expect(wrapper.text()).toContain("真实测试店");
    expect(wrapper.text()).toContain("预算接近上限");
    expect(wrapper.find(".evidence-button").exists()).toBe(true);
  });

  it("shows an empty evidence state", async () => {
    const wrapper = await mounted([
      {
        id: 1,
        role: "ASSISTANT",
        content: "推荐结果",
        recommendationId: 20,
        recommendations: [{ merchantId: 12, merchantName: "店铺" }],
      },
    ]);
    await wrapper.find(".evidence-button").trigger("click");
    await flushPromises();
    expect(wrapper.text()).toContain("暂无可查看依据");
  });

  it("keeps adjustment suggestions separate from limiting conditions", async () => {
    const wrapper = await mounted([
      {
        id: 1,
        role: "ASSISTANT",
        content: "无匹配",
        adjustmentSuggestions: [{ id: "a", displayText: "预算提高12元" }],
        limitingConditions: [{ field: "budget", description: "当前预算80元" }],
      },
    ]);
    expect(wrapper.find(".suggestion-panel").text()).toContain("预算提高12元");
    expect(wrapper.find(".limiting-panel").text()).toContain("当前预算80元");
  });

  it("restores recommendations and degradation state from history", async () => {
    const wrapper = await mounted([
      {
        id: 1,
        role: "ASSISTANT",
        content: "历史推荐",
        degraded: true,
        extractor: "RULE_FALLBACK",
        recommendations: [{ merchantId: 12, merchantName: "历史店铺" }],
      },
    ]);
    expect(wrapper.text()).toContain("历史店铺");
    expect(wrapper.text()).toContain("规则安全降级");
  });

  it("restores and friendly-formats the latest history constraints", async () => {
    const wrapper = await mounted([
      {
        id: 1,
        role: "ASSISTANT",
        content: "历史条件",
        currentConstraints: {
          distanceKm: 5,
          minRating: 4,
          ratingPreference: "HIGH",
          timezone: "Asia/Shanghai",
        },
      },
    ]);

    expect(wrapper.text()).toContain("距离 5 公里以内");
    expect(wrapper.text()).toContain("评分 4 分以上");
    expect(wrapper.text()).toContain("偏好高评分");
    expect(wrapper.text()).not.toContain("timezone");
    expect(wrapper.text()).not.toContain("Asia/Shanghai");
  });

  it("keeps restored constraints after switching history sessions", async () => {
    const wrapper = await mounted([
      {
        id: 1,
        role: "ASSISTANT",
        content: "当前会话",
        currentConstraints: { distanceKm: 3 },
      },
    ]);
    api.getDiningSessions.mockResolvedValue({
      success: true,
      data: {
        sessions: [
          {
            sessionId: 2,
            title: "高评分会话",
            updatedAt: "2026-07-24T10:00:00+08:00",
          },
        ],
      },
    });
    api.getDiningMessages.mockResolvedValue({
      success: true,
      data: {
        messages: [
          {
            id: 2,
            role: "ASSISTANT",
            content: "切换后的会话",
            currentConstraints: { ratingPreference: "HIGH" },
          },
        ],
      },
    });

    await wrapper.find(".history-close-button").trigger("click");
    await wrapper.find(".history-button").trigger("click");
    await flushPromises();
    await wrapper.find(".history-session-item").trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("偏好高评分");
    expect(wrapper.text()).not.toContain("距离 3 公里以内");
  });

  it("creates a new session and clears the current conversation", async () => {
    api.createDiningSession.mockResolvedValue({
      success: true,
      data: {
        sessionId: 2,
      },
    });

    const wrapper = await mounted([
      {
        id: 1,
        role: "ASSISTANT",
        content: "旧会话消息",
        recommendations: [],
      },
    ]);

    expect(wrapper.text()).toContain("旧会话消息");
    expect(localStorage.getItem("foodadvisor.aiDining.session.anonymous")).toBe(
      "1",
    );

    await wrapper.find(".sidebar-new-session-button").trigger("click");
    await flushPromises();

    expect(api.createDiningSession).toHaveBeenCalledWith("AI探店对话");
    expect(localStorage.getItem("foodadvisor.aiDining.session.anonymous")).toBe(
      "2",
    );
    expect(wrapper.text()).not.toContain("旧会话消息");
    expect(wrapper.text()).toContain("想吃什么，直接告诉我");
  });

  it("restores the session from the URL and clears the temporary query", async () => {
    routeMock.query = {
      from: "ai-dining",
      sessionId: "9",
    };

    const wrapper = await mounted([
      {
        id: 91,
        role: "ASSISTANT",
        content: "从商家详情返回的原会话",
        recommendations: [],
      },
    ]);

    expect(api.getDiningMessages).toHaveBeenCalledWith(9);
    expect(wrapper.text()).toContain("从商家详情返回的原会话");
    expect(localStorage.getItem("foodadvisor.aiDining.session.anonymous")).toBe(
      "9",
    );

    expect(routerMock.replace).toHaveBeenCalledWith({
      path: "/diner/ai-dining",
      query: {},
    });
  });
});
