-- ============================================
-- 修复 opensearch_sync_tasks 表结构
--
-- 背景：add_content_lifecycle.sql 中的 CREATE TABLE 语句漏掉了
-- payload (JSONB) 和 content_hash (VARCHAR) 两个字段，
-- 但 Java 实体 OpenSearchSyncTask 中包含这些字段。
-- 如果 add_content_lifecycle 迁移先于 01_core_schema.inc 执行，
-- 表会在缺少这两个字段的情况下被创建，导致后续 MyBatis-Plus
-- 查询抛出 BadSqlGrammarException，出箱调度器永久卡死。
--
-- 同时修复 source_type CHECK 约束，使其与核心 schema 和代码
-- 中使用的类型（MERCHANT/MERCHANT_INTRO/MENU/REVIEW）保持一致，
-- 并将旧的 DISH 数据统一修正为 MENU。
--
-- 使用方法（在宿主机终端执行）：
--   docker exec -i foodadvisor-postgres-1 psql -U postgres -d foodadvisor < scripts/postgres/migrations/fix_opensearch_sync_tasks_schema.sql
-- ============================================

BEGIN;

-- 1. 添加缺失的列（如果尚不存在）
ALTER TABLE opensearch_sync_tasks
    ADD COLUMN IF NOT EXISTS payload JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE opensearch_sync_tasks
    ADD COLUMN IF NOT EXISTS content_hash VARCHAR(128);

-- 2. 先删除旧 CHECK 约束（它不允许 MENU，会阻止后续 UPDATE）
--    旧约束可能叫 ck_ost_source_type 或 ck_opensearch_sync_source，
--    取决于表是哪个脚本创建的。
DO $$
BEGIN
    BEGIN
        ALTER TABLE opensearch_sync_tasks
            DROP CONSTRAINT IF EXISTS ck_ost_source_type;
    EXCEPTION WHEN OTHERS THEN
        NULL;
    END;

    BEGIN
        ALTER TABLE opensearch_sync_tasks
            DROP CONSTRAINT IF EXISTS ck_opensearch_sync_source;
    EXCEPTION WHEN OTHERS THEN
        NULL;
    END;
END $$;

-- 3. 修正旧的 DISH 数据 —— 代码中 createSyncTask() 已将 DISH 规范化为 MENU
UPDATE opensearch_sync_tasks
SET source_type = 'MENU'
WHERE source_type = 'DISH';

-- 4. 添加与 01_core_schema.inc 一致的新约束
ALTER TABLE opensearch_sync_tasks
    ADD CONSTRAINT ck_opensearch_sync_source
        CHECK (source_type IN ('MERCHANT', 'MERCHANT_INTRO', 'MENU', 'REVIEW'));

COMMIT;
