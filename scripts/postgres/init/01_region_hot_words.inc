-- ============================================
-- FoodAdvisor 鍖哄煙鐑瘝妯″潡寤鸿〃鑴氭湰
-- 鍔熻兘锛氬熀浜庡悇鍖哄煙鍟嗗璇勪环鍐呭锛岃嚜鍔ㄦ彁鍙栭珮棰戠儹璇?
-- ============================================

-- ============================================
-- 8.1 鍖哄煙鐑瘝琛?region_hot_words
-- ============================================
CREATE TABLE IF NOT EXISTS region_hot_words (
    id BIGSERIAL PRIMARY KEY,

    -- 鍖哄煙缂栫爜锛屽叧鑱?merchants.region_code锛屽 "310100"锛堜笂娴峰競锛?
    region_code VARCHAR(50) NOT NULL,

    -- 鐑瘝鏂囨湰锛屽 "楹昏荆椴滈"銆?鎺掗槦涔?銆?鎬т环姣旈珮"
    word VARCHAR(100) NOT NULL,

    -- 鐑瘝鍒嗙被锛歍ASTE(鍙ｅ懗) / SERVICE(鏈嶅姟) / ENVIRONMENT(鐜) /
    --          PRICE(浠锋牸) / SPEED(閫熷害) / GENERAL(缁煎悎)
    category VARCHAR(30) NOT NULL DEFAULT 'GENERAL',

    -- 鎯呮劅鍊惧悜锛歅OSITIVE / NEUTRAL / NEGATIVE
    sentiment VARCHAR(20),

    -- 鐑害鍒嗘暟 = 鎻愬強棰戞鏉冮噸 + 鏃堕棿琛板噺鍥犲瓙 + 鎯呮劅鏉冮噸
    -- 鑼冨洿 0.00 ~ 100.00锛岃秺澶ц秺"鐑?
    heat_score NUMERIC(6, 2) NOT NULL DEFAULT 0,

    -- 璇ョ儹璇嶅湪缁熻鍛ㄦ湡鍐呯殑鎻愬強娆℃暟
    mention_count INTEGER NOT NULL DEFAULT 0,

    -- 璇ョ儹璇嶅叧鑱旂殑璇勪环鏁伴噺锛堝幓閲嶏級
    review_count INTEGER NOT NULL DEFAULT 0,

    -- 璇ョ儹璇嶅叧鑱旂殑鍟嗗鏁伴噺锛堝幓閲嶏級
    merchant_count INTEGER NOT NULL DEFAULT 0,

    -- 姝ｉ潰璇勪环鍗犳瘮 0.00 ~ 1.00
    positive_ratio NUMERIC(5, 4),

    -- 鏁版嵁鏉ユ簮绫诲瀷锛欰I_TAG(鍩轰簬AI鏍囩) / KEYWORD_EXTRACT(鍩轰簬鍏抽敭璇嶆彁鍙?
    source_type VARCHAR(30) NOT NULL DEFAULT 'AI_TAG',

    -- 缁熻鍛ㄦ湡绫诲瀷锛欴AILY / WEEKLY / MONTHLY
    period_type VARCHAR(10) NOT NULL DEFAULT 'WEEKLY',

    -- 缁熻璧峰鏃ユ湡
    period_start DATE NOT NULL,

    -- 缁熻鎴鏃ユ湡
    period_end DATE NOT NULL,

    -- 鐑瘝鐢熸垚鐨勫揩鐓х増鏈彿锛堢敤浜庡尯鍒嗕笉鍚屾壒娆＄殑鐢熸垚缁撴灉锛?
    version INTEGER NOT NULL DEFAULT 1,

    -- 鐘舵€侊細ACTIVE / OUTDATED
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 鍚屼竴鍖哄煙 + 鍚屼竴鐑瘝 + 鍚屼竴鍛ㄦ湡 + 鍚屼竴鐗堟湰鐨勫敮涓€绾︽潫
    CONSTRAINT uk_region_hot_word_period
        UNIQUE (region_code, word, period_type, period_start, period_end, version),

    CONSTRAINT ck_hot_word_category
        CHECK (category IN (
            'TASTE', 'SERVICE', 'ENVIRONMENT', 'PRICE',
            'SPEED', 'GENERAL'
        )),

    CONSTRAINT ck_hot_word_sentiment
        CHECK (sentiment IS NULL OR sentiment IN ('POSITIVE', 'NEUTRAL', 'NEGATIVE', 'MIXED')),

    CONSTRAINT ck_hot_word_heat_score
        CHECK (heat_score >= 0 AND heat_score <= 100),

    CONSTRAINT ck_hot_word_mention_count
        CHECK (mention_count >= 0),

    CONSTRAINT ck_hot_word_review_count
        CHECK (review_count >= 0),

    CONSTRAINT ck_hot_word_merchant_count
        CHECK (merchant_count >= 0),

    CONSTRAINT ck_hot_word_positive_ratio
        CHECK (positive_ratio IS NULL OR positive_ratio BETWEEN 0 AND 1),

    CONSTRAINT ck_hot_word_source_type
        CHECK (source_type IN ('AI_TAG', 'KEYWORD_EXTRACT')),

    CONSTRAINT ck_hot_word_period_type
        CHECK (period_type IN ('DAILY', 'WEEKLY', 'MONTHLY')),

    CONSTRAINT ck_hot_word_status
        CHECK (status IN ('ACTIVE', 'OUTDATED'))
);

-- ============================================
-- 8.2 鍖哄煙鐑瘝绱㈠紩
-- ============================================
-- 鎸夊尯鍩?+ 鐑害鏌ヨ锛堟渶甯哥敤锛?
-- 鎸夊尯鍩?+ 鍒嗙被鏌ヨ
-- 鎸夊懆鏈熸煡璇紙鐢ㄤ簬娓呯悊杩囨湡鏁版嵁锛?
-- 鎸夌増鏈繃婊?
-- 鎸夊垱寤烘椂闂存帓搴?
