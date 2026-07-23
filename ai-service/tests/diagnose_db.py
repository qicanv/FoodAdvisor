"""快速诊断数据库内容"""
import psycopg2
from dotenv import load_dotenv
import os

load_dotenv(os.path.join(os.path.dirname(__file__), "..", ".env"))

conn = psycopg2.connect(
    host=os.getenv("POSTGRES_HOST", "localhost"),
    port=int(os.getenv("POSTGRES_PORT", "5432")),
    dbname=os.getenv("POSTGRES_DB", "foodadvisor"),
    user=os.getenv("POSTGRES_USER", "postgres"),
    password=os.getenv("POSTGRES_PASSWORD", "postgres"),
)
cur = conn.cursor()

# 评论状态分布
cur.execute("SELECT status, COUNT(*) FROM reviews WHERE deleted_at IS NULL GROUP BY status")
print("=== 评论状态分布 ===")
for row in cur.fetchall():
    print(f"  {row[0]}: {row[1]} 条")

# 前 5 条评论
cur.execute("SELECT id, merchant_id, status, LEFT(content, 60) FROM reviews LIMIT 5")
print("\n=== 前 5 条评论 ===")
for row in cur.fetchall():
    print(f"  id={row[0]} merchant={row[1]} status={row[2]} content={row[3]}")

# 菜品统计
cur.execute("SELECT status, COUNT(*) FROM dishes WHERE deleted_at IS NULL GROUP BY status")
print("\n=== 菜品状态分布 ===")
for row in cur.fetchall():
    print(f"  {row[0]}: {row[1]} 道")

# 前 5 道菜品
cur.execute("SELECT name, category, price, status, LEFT(description, 60) FROM dishes WHERE deleted_at IS NULL LIMIT 5")
print("\n=== 前 5 道菜品 ===")
for row in cur.fetchall():
    print(f"  {row[0]} | {row[1]} | ¥{row[2]} | status={row[3]} | {row[4]}")

cur.close()
conn.close()
