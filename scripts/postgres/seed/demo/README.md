# FoodAdvisor 全虚构演示数据

仅用于本地开发和答辩演示，严禁在生产环境执行。

前提：已执行 canonical `init/01_schema.sql` 和 `init/02_indexes.sql`，
且用户、商家、评价和推荐等业务表为空。唯一入口：

```powershell
psql -v ON_ERROR_STOP=1 -d foodadvisor_seed_validation `
  -f scripts/postgres/seed/demo/00_demo_seed.sql
```

所有演示账号密码均为 `Demo@123456`，数据库只保存 BCrypt 哈希：

- `demo_admin`：管理员
- `demo_diner_1`～`demo_diner_4`：普通用户
- `demo_merchant_1`～`demo_merchant_4`：商家用户
- `demo_disabled`：禁用用户

数据包括 24 家完全虚构的成都演示商家、96 个菜品、120 条评价，
以及评价分析、回复通知、六区域热词和三条完整 AI 探店推荐链路。
商家名称、地址、联系方式均为明显虚构内容；图片使用本地相对路径。

本阶段不提供自动 reset。seed 只允许空业务库执行，第二次执行会以
`Demo seed requires an empty business database` 明确失败，这是预期行为。

验证：

```powershell
psql -v ON_ERROR_STOP=1 -d foodadvisor_seed_validation `
  -f scripts/postgres/validation/validate_demo_seed.sql
```
