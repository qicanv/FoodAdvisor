BEGIN;

CREATE TABLE IF NOT EXISTS moderation_rules (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(100) NOT NULL,
    rule_code VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    risk_level VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_moderation_rules_code ON moderation_rules(rule_code);
CREATE INDEX IF NOT EXISTS idx_moderation_rules_risk_level ON moderation_rules(risk_level, enabled);

COMMENT ON TABLE moderation_rules IS '审核规则配置表';
COMMENT ON COLUMN moderation_rules.rule_name IS '规则名称';
COMMENT ON COLUMN moderation_rules.rule_code IS '规则编码';
COMMENT ON COLUMN moderation_rules.description IS '规则描述';
COMMENT ON COLUMN moderation_rules.risk_level IS '风险等级：LOW/MEDIUM/HIGH';
COMMENT ON COLUMN moderation_rules.enabled IS '是否启用';

CREATE TABLE IF NOT EXISTS moderation_keywords (
    id BIGSERIAL PRIMARY KEY,
    rule_code VARCHAR(50) NOT NULL,
    keyword VARCHAR(100) NOT NULL,
    match_type VARCHAR(20) NOT NULL DEFAULT 'CONTAINS',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_moderation_keywords_rule FOREIGN KEY (rule_code) REFERENCES moderation_rules(rule_code) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_moderation_keywords_rule ON moderation_keywords(rule_code);
CREATE INDEX IF NOT EXISTS idx_moderation_keywords_keyword ON moderation_keywords(keyword);

COMMENT ON TABLE moderation_keywords IS '违规关键词表';
COMMENT ON COLUMN moderation_keywords.rule_code IS '关联规则编码';
COMMENT ON COLUMN moderation_keywords.keyword IS '关键词';
COMMENT ON COLUMN moderation_keywords.match_type IS '匹配类型：CONTAINS/EXACT/REGEX';

CREATE TABLE IF NOT EXISTS review_rule_matches (
    id BIGSERIAL PRIMARY KEY,
    review_id BIGINT NOT NULL,
    rule_code VARCHAR(50) NOT NULL,
    keyword VARCHAR(100),
    match_position INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_review_rule_matches_review FOREIGN KEY (review_id) REFERENCES reviews(id) ON DELETE CASCADE,
    CONSTRAINT fk_review_rule_matches_rule FOREIGN KEY (rule_code) REFERENCES moderation_rules(rule_code) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_review_rule_matches_review ON review_rule_matches(review_id);
CREATE INDEX IF NOT EXISTS idx_review_rule_matches_rule ON review_rule_matches(rule_code);

COMMENT ON TABLE review_rule_matches IS '评价规则匹配记录';
COMMENT ON COLUMN review_rule_matches.review_id IS '评价ID';
COMMENT ON COLUMN review_rule_matches.rule_code IS '触发规则编码';
COMMENT ON COLUMN review_rule_matches.keyword IS '匹配的关键词';
COMMENT ON COLUMN review_rule_matches.match_position IS '匹配位置';

INSERT INTO moderation_rules (rule_name, rule_code, description, risk_level, enabled) VALUES
('敏感词检测', 'SENSITIVE_WORD', '检测评价内容中是否包含敏感词汇', 'HIGH', true),
('广告营销检测', 'ADVERTISING', '检测评价内容中是否包含广告营销信息', 'MEDIUM', true),
('恶意攻击检测', 'MALICIOUS_ATTACK', '检测评价内容中是否包含恶意攻击、侮辱性语言', 'HIGH', true),
('色情内容检测', 'PORNOGRAPHY', '检测评价内容中是否包含色情相关内容', 'HIGH', true),
('政治敏感检测', 'POLITICAL_SENSITIVE', '检测评价内容中是否包含政治敏感内容', 'HIGH', true),
('垃圾内容检测', 'SPAM', '检测评价内容中是否包含重复、无意义的垃圾内容', 'LOW', true),
('辱骂性语言检测', 'ABUSE_LANGUAGE', '检测评价内容中是否包含辱骂性、攻击性语言', 'HIGH', true),
('价格异常检测', 'PRICE_ABUSE', '检测评价内容中是否包含异常价格描述或价格欺诈信息', 'MEDIUM', true);

INSERT INTO moderation_keywords (rule_code, keyword, match_type) VALUES
('SENSITIVE_WORD', '操', 'CONTAINS'),
('SENSITIVE_WORD', '草', 'CONTAINS'),
('SENSITIVE_WORD', '他妈的', 'CONTAINS'),
('SENSITIVE_WORD', '他妈', 'CONTAINS'),
('SENSITIVE_WORD', '傻逼', 'CONTAINS'),
('SENSITIVE_WORD', '傻B', 'CONTAINS'),
('SENSITIVE_WORD', '神经病', 'CONTAINS'),
('SENSITIVE_WORD', '去死', 'CONTAINS'),
('SENSITIVE_WORD', '滚', 'CONTAINS'),
('ADVERTISING', '淘宝', 'CONTAINS'),
('ADVERTISING', '天猫', 'CONTAINS'),
('ADVERTISING', '京东', 'CONTAINS'),
('ADVERTISING', '拼多多', 'CONTAINS'),
('ADVERTISING', '微信号', 'CONTAINS'),
('ADVERTISING', '微信', 'CONTAINS'),
('ADVERTISING', 'QQ', 'CONTAINS'),
('ADVERTISING', '优惠卷', 'CONTAINS'),
('ADVERTISING', '优惠券', 'CONTAINS'),
('ADVERTISING', '促销', 'CONTAINS'),
('ADVERTISING', '打折', 'CONTAINS'),
('MALICIOUS_ATTACK', '垃圾店', 'CONTAINS'),
('MALICIOUS_ATTACK', '黑店', 'CONTAINS'),
('MALICIOUS_ATTACK', '骗子', 'CONTAINS'),
('MALICIOUS_ATTACK', '坑人', 'CONTAINS'),
('MALICIOUS_ATTACK', '欺诈', 'CONTAINS'),
('MALICIOUS_ATTACK', '骗钱', 'CONTAINS'),
('PORNOGRAPHY', '色情', 'CONTAINS'),
('PORNOGRAPHY', '裸', 'CONTAINS'),
('PORNOGRAPHY', '性交', 'CONTAINS'),
('PORNOGRAPHY', '做爱', 'CONTAINS'),
('POLITICAL_SENSITIVE', '习近平', 'CONTAINS'),
('POLITICAL_SENSITIVE', '李克强', 'CONTAINS'),
('POLITICAL_SENSITIVE', '毛泽东', 'CONTAINS'),
('POLITICAL_SENSITIVE', '共产党', 'CONTAINS'),
('POLITICAL_SENSITIVE', '政府', 'CONTAINS'),
('POLITICAL_SENSITIVE', '台独', 'CONTAINS'),
('POLITICAL_SENSITIVE', '分裂', 'CONTAINS'),
('SPAM', '哈哈', 'CONTAINS'),
('SPAM', '呵呵', 'CONTAINS'),
('SPAM', '随便', 'CONTAINS'),
('SPAM', '路过', 'CONTAINS'),
('ABUSE_LANGUAGE', '贱人', 'CONTAINS'),
('ABUSE_LANGUAGE', '狗东西', 'CONTAINS'),
('ABUSE_LANGUAGE', '畜生', 'CONTAINS'),
('ABUSE_LANGUAGE', '王八', 'CONTAINS'),
('ABUSE_LANGUAGE', '混蛋', 'CONTAINS'),
('PRICE_ABUSE', '天价', 'CONTAINS'),
('PRICE_ABUSE', '宰客', 'CONTAINS'),
('PRICE_ABUSE', '诈骗', 'CONTAINS'),
('PRICE_ABUSE', '假的', 'CONTAINS'),
('PRICE_ABUSE', '假货', 'CONTAINS');

COMMIT;