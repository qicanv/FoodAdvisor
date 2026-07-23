# 数据库脚本执行指南

## 前置条件

1. 已安装 PostgreSQL 数据库
2. 已创建数据库 `foodadvisor`

## 执行顺序

### 1. 创建核心表结构

```bash
cd scripts/postgres/init
psql -d foodadvisor -f 01_schema.sql
psql -d foodadvisor -f 02_indexes.sql
```

### 2. 创建专题管理和食客管理表结构及种子数据

```bash
cd scripts/postgres/migrations
psql -d foodadvisor -f 02_topic_and_diner_schema.sql
```

### 3. 创建演示数据（可选）

```bash
cd scripts/postgres/seed/demo
psql -d foodadvisor -f 00_demo_seed.sql
```

## 脚本说明

### 01_schema.sql（核心表结构）
- 用户表（users）
- 商家表（merchants）
- 审核日志表（audit_logs）
- 其他核心业务表

### 02_topic_and_diner_schema.sql（专题管理和食客管理）
包含以下表结构和种子数据：

#### 表结构
| 表名 | 说明 |
|------|------|
| content_tags | 内容标签表（分类、菜系、场景、环境、价格） |
| merchant_tag_relations | 标签与商家关联表 |
| topics | 专题表 |
| topic_merchants | 专题与商家关联表 |

#### 种子数据
- **27个内容标签**：
  - 7个餐饮类型（烧烤、火锅、快餐、海鲜、甜品、面食、米饭）
  - 9个菜系（川菜、粤菜、鲁菜、苏菜、浙菜、闽菜、湘菜、徽菜、日料、韩式、西餐）
  - 5个消费场景（夜宵、约会、家庭聚餐、商务宴请、朋友聚会）
  - 4个环境特点（环境优雅、网红、私密、热闹）
  - 4个价格区间（人均50以下、50-100、100-200、200以上）

- **标签与商家自动关联**：根据商家的 category 和 cuisine 字段自动匹配

- **5个示例专题**：
  - 夜宵好去处（已发布）
  - 网红打卡餐厅（已发布）
  - 浪漫约会餐厅（草稿）
  - 商务宴请精选（下线）
  - 高性价比美食（已发布）

- **10个食客用户**：
  - 6个活跃用户（role=USER, status=ACTIVE）
  - 2个禁用用户（role=USER, status=DISABLED）
  - 2个锁定用户（role=USER, status=LOCKED）

## API对接说明

### 食客管理
- **API路径**：`/api/admin/users`
- **前端文件**：`frontend/src/views/admin/AdminDinerView.vue`
- **后端控制器**：`AdminUserController.java`

### 专题管理
- **API路径**：`/api/admin/topics`
- **前端文件**：`frontend/src/views/admin/TopicManagement.vue`
- **后端控制器**：`TopicController.java`

### 标签管理
- **API路径**：`/api/admin/topics/tags`
- **标签关联商家**：`/api/admin/topics/tags/{id}/merchants`

## 登录账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | demo_admin | Demo@123456 |
| 食客 | demo_diner_1 | Demo@123456 |
| 食客 | demo_diner_2 | Demo@123456 |
| ... | ... | Demo@123456 |

## 字段映射说明

前端与数据库字段对应关系：

| 前端字段 | 数据库字段 | 说明 |
|----------|------------|------|
| tag.type | content_tags.category | 标签类型 |
| topic.coverImage | topics.cover_url | 专题封面图 |
| diner.lastLoginTime | users.last_login_at | 最后登录时间 |
| diner.status | users.status | 用户状态 |

## 注意事项

1. 执行脚本前确保已创建 `foodadvisor` 数据库
2. 脚本使用 `IF NOT EXISTS` 和 `ON CONFLICT DO NOTHING`，可重复执行
3. 所有食客用户密码统一为 `Demo@123456`
4. 标签关联逻辑依赖商家表的 `category` 和 `cuisine` 字段

## 查看数据库内容（终端方式）

### 使用 Docker 进入数据库（推荐）

如果你使用 Docker 部署数据库，可以用类似的命令进入：

```bash
# 进入数据库容器（查看容器名称）
docker ps | findstr postgres

# 进入数据库（容器名可能是 foodadvisor-main-postgres-1 或 foodadvisor-postgres）
docker exec -it foodadvisor-main-postgres-1 psql -U postgres -d foodadvisor

# 查看所有表
\dt

# 查看食客统计相关表数据
SELECT * FROM user_follows;
SELECT * FROM review_likes;
SELECT * FROM user_activities;

# 查看食客用户
SELECT id, username, nickname, status FROM users WHERE role = 'USER';

# 退出
\q
```

### 使用 PowerShell（Windows）

#### 登录并保存Token
```powershell
$token = (Invoke-WebRequest -Uri http://localhost:8080/api/auth/login -Method POST -ContentType "application/json" -Body '{"username":"demo_admin","password":"Demo@123456"}' -UseBasicParsing | Select-Object -ExpandProperty Content | ConvertFrom-Json).data.token
```

#### 查看标签列表
```powershell
Invoke-WebRequest -Uri "http://localhost:8080/api/admin/tags" -Headers @{Authorization="Bearer $token"} -UseBasicParsing | Select-Object -ExpandProperty Content
```

#### 查看专题列表
```powershell
Invoke-WebRequest -Uri "http://localhost:8080/api/admin/topics" -Headers @{Authorization="Bearer $token"} -UseBasicParsing | Select-Object -ExpandProperty Content
```

#### 查看商家列表
```powershell
Invoke-WebRequest -Uri "http://localhost:8080/api/admin/merchants" -Headers @{Authorization="Bearer $token"} -UseBasicParsing | Select-Object -ExpandProperty Content
```

#### 查看食客列表（带统计数据）
```powershell
Invoke-WebRequest -Uri "http://localhost:8080/api/admin/users?role=USER" -Headers @{Authorization="Bearer $token"} -UseBasicParsing | Select-Object -ExpandProperty Content
```

#### 查看食客详情（含评论数、关注数、获赞数、评分统计）
```powershell
Invoke-WebRequest -Uri "http://localhost:8080/api/admin/users/2" -Headers @{Authorization="Bearer $token"} -UseBasicParsing | Select-Object -ExpandProperty Content
```

#### 查看关注商家统计
```powershell
$dinerId = 2
$result = Invoke-WebRequest -Uri "http://localhost:8080/api/admin/users/$dinerId" -Headers @{Authorization="Bearer $token"} -UseBasicParsing | Select-Object -ExpandProperty Content | ConvertFrom-Json
Write-Host "食客ID $dinerId 关注商家数: $($result.data.followCount)"
Write-Host "评论数: $($result.data.reviewCount)"
Write-Host "获赞数: $($result.data.likeCount)"
Write-Host "平均评分: $($result.data.avgRating)"
```

### 数据字段说明

| 字段 | 说明 | 示例值 |
|------|------|--------|
| reviewCount | 评论数 | 48 |
| followCount | 关注商家数 | 3 |
| likeCount | 获赞数 | 156 |
| avgRating | 平均评分 | 3.5 |
| recentActivities | 近期活动列表 | [{type:'REVIEW', content:'发布了评价'}] |
| ratingDistribution | 评分分布 | {5:12, 4:13, 3:13, 2:5, 1:5} |