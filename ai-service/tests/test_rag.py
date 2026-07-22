"""
端到端 RAG 测试：用户问题 → 向量化 → OpenSearch 语义检索 → 输出结果

运行方式:
    cd ai-service
    python -m pytest tests/test_rag.py -v -s

仅测试 Embedding 模型（不需要 OpenSearch）:
    python -m pytest tests/test_rag.py -v -s -k "embedding"

仅测试完整检索（需要 OpenSearch 且有数据）:
    python -m pytest tests/test_rag.py -v -s -k "search"
"""
import pytest

from app.clients.opensearch_client import check_opensearch_connection
from app.core.config import settings
from app.schemas.search import SearchRequest
from app.services.embedding_service import get_embedding_service
from app.services.search_service import get_search_service


# ================================================================
# 用真实的语义化查询来测试
# ================================================================

TEST_QUERIES = [
    "适合拍照的复古风小店",
    "深夜还营业的烧烤店",
    "安静适合谈事情的餐厅",
    "适合朋友聚会的川菜馆",
    "一个人吃饭的安静地方",
]


# ================================================================
# 1. Embedding 模型可用性
# ================================================================


def test_embedding_model_loads():
    """验证 BGE Embedding 模型能否正常加载"""
    embedding = get_embedding_service()
    assert embedding is not None
    assert embedding.dimension == 768, f"期望 768 维，实际 {embedding.dimension} 维"
    assert embedding.check_health(), "Embedding 模型健康检查失败"
    print(f"\n   ✓ Embedding 模型加载成功: {embedding.model_identifier}, "
          f"维度={embedding.dimension}, 设备={embedding._device}")


def test_embedding_encode_single():
    """验证单个文本向量化"""
    embedding = get_embedding_service()
    vec = embedding.encode_query("安静适合谈事情的餐厅")
    assert len(vec) == 768
    assert all(isinstance(v, float) for v in vec)
    # 已 L2 归一化
    norm = sum(v * v for v in vec)
    assert abs(norm - 1.0) < 1e-5, f"向量未归一化，L2 范数={norm}"
    print(f"\n   ✓ 查询向量化成功: 维度={len(vec)}, L2范数≈{norm:.6f}")


def test_embedding_encode_batch():
    """验证批量文本向量化"""
    embedding = get_embedding_service()
    texts = TEST_QUERIES
    vectors = embedding.encode(texts)
    assert len(vectors) == len(texts)
    for i, vec in enumerate(vectors):
        assert len(vec) == 768
        norm = sum(v * v for v in vec)
        assert abs(norm - 1.0) < 1e-5, f"第{i}个向量未归一化"
    print(f"\n   ✓ 批量向量化成功: {len(texts)} 条文本 → {len(vectors)} 个向量")


# ================================================================
# 2. OpenSearch 连接
# ================================================================


def test_opensearch_connection():
    """验证 OpenSearch 能否连通"""
    connected = check_opensearch_connection()
    print(f"\n   OpenSearch 连接: {'✓ 成功' if connected else '✗ 失败'} "
          f"(host={settings.opensearch_host}:{settings.opensearch_port})")
    if not connected:
        pytest.skip("OpenSearch 不可用，跳过后续检索测试")


# ================================================================
# 3. 完整的 RAG 检索流程
# ================================================================


def test_rag_full_pipeline():
    """
    完整的 RAG 检索流程：
    用户自然语言问题 → Embedding 向量化 → OpenSearch k-NN 检索 → 输出结果
    """
    # 先确保 OpenSearch 可用
    if not check_opensearch_connection():
        pytest.skip("OpenSearch 不可用")

    search_service = get_search_service()
    embedding = get_embedding_service()

    all_queries_empty = True

    for query_text in TEST_QUERIES:
        print(f"\n{'=' * 60}")
        print(f"  查询: \"{query_text}\"")
        print(f"{'=' * 60}")

        # Step 1: 向量化
        query_vector = embedding.encode_query(query_text)
        print(f"  向量维度: {len(query_vector)}")

        # Step 2: 语义检索
        request = SearchRequest(
            query=query_text,
            topK=5,
            filters=None,  # 不加过滤，看全局能检索到什么
        )
        response = search_service.search(request)

        print(f"  检索模式: {response.data.searchMode}")
        print(f"  返回结果数: {len(response.data.results)}")

        if not response.data.results:
            print("  ⚠ 无结果 — 向量数据库可能为空，请先执行知识入库")
            continue

        all_queries_empty = False

        # Step 3: 输出结果
        for rank, item in enumerate(response.data.results, 1):
            source_label = _source_label(item.sourceType)
            text_preview = item.text[:120].replace("\n", " ")
            print(f"\n  --- Top {rank} ---")
            print(f"  商家ID:    {item.merchantId}")
            print(f"  来源:      {source_label} (id={item.sourceId})")
            print(f"  相似度:    {item.score:.4f}")
            print(f"  内容片段:  \"{text_preview}...\"")
            print(f"  文档ID:    {item.documentId}")
            print(f"  ChunkID:   {item.chunkId}")

    if all_queries_empty:
        print("\n  ⚠ 所有查询均无结果 — 请确认 OpenSearch 索引中有数据。")


# ================================================================
# 4. 带过滤条件的检索 (模拟 RecommendationRankingService 的调用)
# ================================================================


def test_rag_with_merchant_filter():
    """
    模拟 RecommendationRankingService.performSemanticSearch() 的调用方式:
    限定候选商家列表 → 向量检索 → 按 merchantId 聚合分数
    """
    if not check_opensearch_connection():
        pytest.skip("OpenSearch 不可用")

    search_service = get_search_service()

    # 先做一次无过滤检索，看看有哪些商家
    probe = SearchRequest(query="川菜 朋友聚会", topK=20)
    probe_response = search_service.search(probe)

    if not probe_response.data.results:
        pytest.skip("索引中无数据，无法测试过滤检索")

    # 取出前几个 merchantId 作为候选
    candidate_ids = list(set(
        item.merchantId for item in probe_response.data.results
    ))[:5]

    print(f"\n  候选商家ID: {candidate_ids}")

    # 用原始自然语言查询 + 候选商家过滤
    from app.schemas.search import SearchFilter

    request = SearchRequest(
        query="适合朋友聚会的川菜馆",
        topK=10,
        filters=SearchFilter(
            merchantIds=candidate_ids,
            sourceTypes=["REVIEW", "MERCHANT_INTRO", "MENU"],
        ),
    )
    response = search_service.search(request)

    print(f"  检索模式: {response.data.searchMode}")
    print(f"  返回结果数: {len(response.data.results)}")

    # 验证所有结果都在候选列表中
    for item in response.data.results:
        assert item.merchantId in candidate_ids, \
            f"结果 merchantId={item.merchantId} 不在候选列表 {candidate_ids} 中"
        print(f"  merchantId={item.merchantId}, "
              f"source={item.sourceType}, "
              f"score={item.score:.4f}, "
              f"text=\"{item.text[:80]}...\"")

    print(f"\n  ✓ 过滤检索通过: 全部 {len(response.data.results)} 条结果均在候选商家内")


# ================================================================
# 5. 三路分源检索 + 加权聚合（核心验证）
# ================================================================


# 三路检索配置: (sourceTypes, topK, weight, 标签)
_THREE_WAY_CONFIG = [
    (["MERCHANT_INTRO"], 5, 0.50, "商家介绍"),
    (["MENU"],           5, 0.30, "菜品"),
    (["REVIEW"],        10, 0.20, "用户评价"),
]


def test_rag_three_way_search_and_aggregate():
    """
    模拟 RecommendationRankingService 的三路分源检索 + 加权聚合：

    对同一原始查询，分别在 MERCHANT_INTRO、MENU、REVIEW 上检索，
    然后按 merchantId 聚合，输出每个商家的分路分数、加权总分和可信度。
    """
    if not check_opensearch_connection():
        pytest.skip("OpenSearch 不可用")

    from collections import defaultdict
    from app.schemas.search import SearchFilter

    search_service = get_search_service()

    query = "安静适合谈事情的餐厅"
    print(f"\n{'=' * 60}")
    print(f"  三路分源检索: \"{query}\"")
    print(f"{'=' * 60}")

    # 先无过滤确定有哪些候选商家
    all_merchant_ids: set[int] = set()
    for source_types, top_k, _, label in _THREE_WAY_CONFIG:
        req = SearchRequest(
            query=query, topK=top_k,
            filters=SearchFilter(sourceTypes=source_types),
        )
        resp = search_service.search(req)
        for item in resp.data.results:
            all_merchant_ids.add(item.merchantId)
        print(f"\n  [{label}] 检索返回 {len(resp.data.results)} 条 "
              f"(searchMode={resp.data.searchMode})")

    if not all_merchant_ids:
        print("  ⚠ 所有来源均无结果")
        return

    candidate_ids = list(all_merchant_ids)
    print(f"\n  候选商家共 {len(candidate_ids)} 家: {candidate_ids[:10]}...")

    # ---- 三路分别检索 ----
    # merchantId → {sourceType: max_score}
    merchant_intro_scores: dict[int, float] = {}
    merchant_menu_scores: dict[int, float] = {}
    merchant_review_scores: dict[int, float] = {}
    # merchantId → [(sourceType, text, score), ...]
    merchant_evidence: dict[int, list[tuple[str, str, float]]] = defaultdict(list)

    source_configs = [
        (["MERCHANT_INTRO"], 5, merchant_intro_scores, 2),
        (["MENU"],           5, merchant_menu_scores,   2),
        (["REVIEW"],        10, merchant_review_scores, 3),
    ]

    for source_types, top_k, score_map, max_evidence in source_configs:
        req = SearchRequest(
            query=query, topK=top_k,
            filters=SearchFilter(
                merchantIds=candidate_ids,
                sourceTypes=source_types,
            ),
        )
        resp = search_service.search(req)

        label = _source_label(source_types[0])
        print(f"\n  --- [{label}] topK={top_k}, 返回 {len(resp.data.results)} 条 ---")

        for item in resp.data.results:
            m_id = item.merchantId
            # 保留最高分
            current = score_map.get(m_id, 0.0)
            if item.score > current:
                score_map[m_id] = item.score
            # 收集 evidence
            evidence_list = merchant_evidence[m_id]
            if len([e for e in evidence_list if e[0] == item.sourceType]) < max_evidence:
                text_preview = item.text[:120].replace("\n", " ")
                evidence_list.append((item.sourceType, text_preview, item.score))

    # ---- 加权聚合 ----
    print(f"\n{'=' * 60}")
    print(f"  按 merchantId 加权聚合 (intro×0.5 + menu×0.3 + review×0.2)")
    print(f"{'=' * 60}")

    aggregated: list[dict] = []
    for m_id in candidate_ids:
        intro  = merchant_intro_scores.get(m_id, 0.0)
        menu   = merchant_menu_scores.get(m_id, 0.0)
        review = merchant_review_scores.get(m_id, 0.0)

        weighted = 0.50 * intro + 0.30 * menu + 0.20 * review

        # 只保留至少有一路命中的商家
        if weighted == 0.0:
            continue

        source_diversity = (1 if intro > 0 else 0) \
                         + (1 if menu  > 0 else 0) \
                         + (1 if review > 0 else 0)
        hits = len(merchant_evidence.get(m_id, []))

        # 可信度 = 0.4×覆盖率 + 0.3×命中密度 + 0.3×加权分数
        coverage = source_diversity / 3.0
        density = min(hits, 5) / 5.0
        confidence = 0.40 * coverage + 0.30 * density + 0.30 * weighted

        aggregated.append({
            "merchantId": m_id,
            "intro": intro,
            "menu": menu,
            "review": review,
            "weighted": round(weighted, 4),
            "diversity": source_diversity,
            "hits": hits,
            "confidence": round(confidence, 4),
        })

    # 按加权分降序排列
    aggregated.sort(key=lambda x: x["weighted"], reverse=True)

    # ---- 输出 ----
    print(f"\n  共 {len(aggregated)} 家商家有语义匹配")
    print(f"  {'排名':<5} {'商家ID':<8} {'加权分':<8} {'可信度':<8} "
          f"{'intro':<8} {'menu':<8} {'review':<8} {'来源':<6} {'命中'}")
    print(f"  {'-' * 80}")

    for rank, item in enumerate(aggregated[:15], 1):
        print(f"  {rank:<5} {item['merchantId']:<8} "
              f"{item['weighted']:<8.4f} {item['confidence']:<8.4f} "
              f"{item['intro']:<8.4f} {item['menu']:<8.4f} "
              f"{item['review']:<8.4f} {item['diversity']}/3{'':<3} "
              f"{item['hits']}")

        # 展示每个商家的 top evidence
        for src_type, text, score in merchant_evidence.get(
                item["merchantId"], [])[:3]:
            print(f"         [{_source_label(src_type)}] "
                  f"(score={score:.4f}) \"{text[:80]}...\"")

    print(f"\n  ✓ 三路分源检索 + 加权聚合验证完成")

    # 基础断言
    if aggregated:
        top = aggregated[0]
        assert 0 <= top["weighted"] <= 1, f"加权分超出范围: {top['weighted']}"
        assert 0 <= top["confidence"] <= 1, f"可信度超出范围: {top['confidence']}"
        assert 1 <= top["diversity"] <= 3, f"来源多样性异常: {top['diversity']}"


# ================================================================
# 辅助
# ================================================================

_SOURCE_LABELS = {
    "MERCHANT_INTRO": "商家介绍",
    "MENU": "菜单",
    "REVIEW": "用户评价",
    "HIGHLIGHT": "商家亮点",
}


def _source_label(source_type: str) -> str:
    return _SOURCE_LABELS.get(source_type, source_type)
