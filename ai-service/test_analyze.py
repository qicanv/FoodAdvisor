"""快速测试情感分析接口"""
import httpx
import asyncio
import json
import os

AI_SERVICE_URL = "http://localhost:8000"
INTERNAL_TOKEN = os.getenv("INTERNAL_API_TOKEN")


async def test_review():
    """测试正面评价和负面评价"""
    if not INTERNAL_TOKEN:
        raise RuntimeError("INTERNAL_API_TOKEN is required")

    headers = {"X-Internal-Token": INTERNAL_TOKEN}

    # trust_env=False 跳过系统代理，timeout=60 给 LLM 调用留够时间
    async with httpx.AsyncClient(trust_env=False, timeout=60.0) as client:
        # 1. 健康检查
        resp = await client.get(f"{AI_SERVICE_URL}/health")
        print("=== 健康检查 ===")
        print("状态码:", resp.status_code)
        if resp.status_code == 200:
            print(resp.text)

        # 2. 正面评价测试
        print("\n=== 正面评价分析 ===")
        resp = await client.post(
            f"{AI_SERVICE_URL}/internal/reviews/analyze",
            headers=headers,
            json={
                "reviewId": 1,
                "merchantId": 1,
                "content": "味道非常正宗！麻婆豆腐特别好吃，麻辣鲜香，每次来都要点。水煮鱼的分量也很足。"
            }
        )
        print(json.dumps(resp.json(), ensure_ascii=False, indent=2))

        # 3. 负面评价测试
        print("\n=== 负面评价分析 ===")
        resp = await client.post(
            f"{AI_SERVICE_URL}/internal/reviews/analyze",
            headers=headers,
            json={
                "reviewId": 4,
                "merchantId": 1,
                "content": "上菜速度太慢了！等了半个多小时才上来第一个菜，而且服务员态度冷漠，叫了好几次都没人理。"
            }
        )
        print(json.dumps(resp.json(), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    asyncio.run(test_review())
