-- ============================================================
-- Prompt version management
-- ============================================================

-- 1. Prompt definitions for each AI business scene
CREATE TABLE IF NOT EXISTS prompt_definitions (
    id                  BIGSERIAL PRIMARY KEY,
    scene_code          VARCHAR(80) NOT NULL,
    scene_name          VARCHAR(100) NOT NULL,
    description         TEXT,
    active_version_id   BIGINT,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_prompt_definitions_scene_code
        UNIQUE (scene_code),

    CONSTRAINT ck_prompt_definitions_scene_code
        CHECK (
            scene_code IN (
                'DINING_RECOMMENDATION',
                'CONSTRAINT_EXTRACTION',
                'REVIEW_SUMMARY',
                'SENTIMENT_ANALYSIS',
                'REVIEW_REPLY',
                'BUSINESS_ADVICE'
            )
        ),

    CONSTRAINT ck_prompt_definitions_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

-- 2. Immutable prompt versions
CREATE TABLE IF NOT EXISTS prompt_versions (
    id                      BIGSERIAL PRIMARY KEY,
    prompt_definition_id    BIGINT NOT NULL,
    version_no              INTEGER NOT NULL,
    version_tag             VARCHAR(150) NOT NULL,
    content                 TEXT NOT NULL,
    change_note             VARCHAR(500),
    created_by              BIGINT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_prompt_versions_definition
        FOREIGN KEY (prompt_definition_id)
        REFERENCES prompt_definitions(id)
        ON DELETE RESTRICT,

    CONSTRAINT uk_prompt_versions_definition_no
        UNIQUE (prompt_definition_id, version_no),

    CONSTRAINT uk_prompt_versions_tag
        UNIQUE (version_tag),

    CONSTRAINT ck_prompt_versions_version_no
        CHECK (version_no >= 1),

    CONSTRAINT ck_prompt_versions_content
        CHECK (length(btrim(content)) > 0)
);

-- Add active-version foreign key after both tables exist
ALTER TABLE prompt_definitions
    ADD CONSTRAINT fk_prompt_definitions_active_version
    FOREIGN KEY (active_version_id)
    REFERENCES prompt_versions(id)
    ON DELETE RESTRICT;

-- 3. Prompt activation and rollback history
CREATE TABLE IF NOT EXISTS prompt_activation_logs (
    id                      BIGSERIAL PRIMARY KEY,
    prompt_definition_id    BIGINT NOT NULL,
    from_version_id         BIGINT,
    to_version_id           BIGINT NOT NULL,
    operation_type          VARCHAR(20) NOT NULL,
    operation_note          VARCHAR(500),
    operated_by             BIGINT,
    operated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_prompt_activation_definition
        FOREIGN KEY (prompt_definition_id)
        REFERENCES prompt_definitions(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_prompt_activation_from_version
        FOREIGN KEY (from_version_id)
        REFERENCES prompt_versions(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_prompt_activation_to_version
        FOREIGN KEY (to_version_id)
        REFERENCES prompt_versions(id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_prompt_activation_operation
        CHECK (operation_type IN ('ACTIVATE', 'ROLLBACK'))
);

-- 4. Initialize the six supported AI scenes
INSERT INTO prompt_definitions (
    scene_code,
    scene_name,
    description,
    status
)
VALUES
    (
        'DINING_RECOMMENDATION',
        '探店推荐',
        'Controls AI-generated restaurant recommendations and recommendation reasons.',
        'ACTIVE'
    ),
    (
        'CONSTRAINT_EXTRACTION',
        '需求提取',
        'Extracts structured dining constraints from natural-language dialogue.',
        'ACTIVE'
    ),
    (
        'REVIEW_SUMMARY',
        '评价摘要',
        'Generates structured summaries from merchant review data.',
        'ACTIVE'
    ),
    (
        'SENTIMENT_ANALYSIS',
        '情感分析',
        'Analyzes review sentiment, topics and risk signals.',
        'ACTIVE'
    ),
    (
        'REVIEW_REPLY',
        '评价回复',
        'Generates merchant reply suggestions for customer reviews.',
        'ACTIVE'
    ),
    (
        'BUSINESS_ADVICE',
        '经营建议',
        'Generates business insights and improvement suggestions.',
        'ACTIVE'
    )
ON CONFLICT (scene_code) DO NOTHING;