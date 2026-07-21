-- ============================================================
-- 评价举报表（EPIC-08 故事5）
-- 用户可对不实、违规或不当评价进行举报
-- ============================================================

CREATE TABLE IF NOT EXISTS review_reports (
    id BIGSERIAL PRIMARY KEY,
    reporter_user_id BIGINT NOT NULL,
    reported_review_id BIGINT NOT NULL,
    merchant_id BIGINT NOT NULL,
    reason VARCHAR(30) NOT NULL CHECK (reason IN ('ADVERTISING','FALSE_REVIEW','MALICIOUS_ATTACK','SEXUAL_OR_VULGAR','PRIVACY_LEAK','OTHER')),
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RESOLVED','REJECTED')),
    handled_by BIGINT,
    handled_at TIMESTAMPTZ,
    resolution TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 同一用户对同一评价只能有一条未处理举报
CREATE UNIQUE INDEX IF NOT EXISTS uk_review_reports_pending
ON review_reports(reporter_user_id, reported_review_id)
WHERE status = 'PENDING';

-- 按举报人查询的索引（"我的举报"页面）
CREATE INDEX IF NOT EXISTS idx_review_reports_reporter
ON review_reports(reporter_user_id, created_at DESC);

-- 按状态查询的索引（审核工作台）
CREATE INDEX IF NOT EXISTS idx_review_reports_status
ON review_reports(status, created_at DESC);
