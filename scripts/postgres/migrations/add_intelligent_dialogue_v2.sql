\set ON_ERROR_STOP on

BEGIN;

-- ============================================================
-- 1. Constraint extraction V2 history fields
-- ============================================================

ALTER TABLE constraint_extractions
    ADD COLUMN IF NOT EXISTS constraint_patch JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS extractor VARCHAR(30),
    ADD COLUMN IF NOT EXISTS degraded BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS prompt_version VARCHAR(150),
    ADD COLUMN IF NOT EXISTS degradation_reason VARCHAR(100);


-- ============================================================
-- 2. Transactional outbox for OpenSearch synchronization
-- ============================================================

CREATE TABLE IF NOT EXISTS opensearch_sync_tasks (
    id BIGSERIAL PRIMARY KEY,
    source_type VARCHAR(30) NOT NULL,
    source_id BIGINT NOT NULL,
    operation_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ,
    error_message VARCHAR(500),
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    content_version INTEGER NOT NULL DEFAULT 1,
    content_hash VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_opensearch_sync_source
        CHECK (
            source_type IN (
                'MERCHANT',
                'MERCHANT_INTRO',
                'MENU',
                'REVIEW'
            )
        ),

    CONSTRAINT ck_opensearch_sync_operation
        CHECK (
            operation_type IN (
                'UPSERT',
                'DISABLE',
                'DELETE',
                'REINDEX'
            )
        ),

    CONSTRAINT ck_opensearch_sync_status
        CHECK (
            status IN (
                'PENDING',
                'PROCESSING',
                'SUCCESS',
                'FAILED'
            )
        ),

    CONSTRAINT ck_opensearch_sync_retry
        CHECK (retry_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_opensearch_sync_due
    ON opensearch_sync_tasks (
        status,
        next_retry_at,
        id
    );

CREATE UNIQUE INDEX IF NOT EXISTS uk_opensearch_sync_active_task
    ON opensearch_sync_tasks (
        source_type,
        source_id,
        operation_type
    )
    WHERE status IN ('PENDING', 'PROCESSING');


-- ============================================================
-- 3. CONSTRAINT_EXTRACTION V2 prompt
-- ============================================================

-- Update the V2 version when it already exists.
UPDATE prompt_versions pv
SET content = $constraint_prompt$
You are FoodAdvisor's dining constraint extraction component.

Return ONLY valid JSON matching the ConstraintPatch output schema supplied below.
Do not return Markdown, explanations, or a full constraint-state snapshot.

The Java backend is authoritative. You only propose changes through operations.

Rules:
1. Extract every dining condition explicitly stated in the current user message.
2. Scalar fields must be placed in operations.set.
3. Positive list values must be placed in operations.add.
4. Explicitly removed fields must be placed in operations.clear.
5. Negative preferences must use operations.exclude.
6. Never invent merchants, reviews, IDs, locations, ratings, or constraints.
7. confidence must be an object mapping each changed field to a number from 0 to 1.
8. Set directRecommend=true when the user is asking to find or recommend a restaurant, including phrases such as:
   想吃、帮我找、推荐一家、去哪吃、有没有适合的店.

Field mappings:
- Number of diners -> partySize
- Per-person budget -> perCapitaBudget
- Total budget -> totalBudget
- 烧烤、火锅、自助餐、咖啡店等店铺类型 -> merchantTypes
- 川菜、粤菜、湘菜等菜系 -> cuisines
- 朋友聚餐、约会、家庭聚餐、商务宴请 -> scenes
- 适合拍照、安静、有包间、有氛围 -> environmentRequirements
- Explicit time such as 晚上十一点 -> businessTargetTime using HH:mm
- When the time refers to the same evening, businessTargetNextDay=false

For this user message:
四个人，人均100元，想吃烧烤，适合朋友聚餐，晚上十一点还营业，最好适合拍照。

A correct patch should contain values equivalent to:
{
  "intent": "MERCHANT_RECOMMENDATION",
  "directRecommend": true,
  "operations": {
    "set": {
      "partySize": 4,
      "perCapitaBudget": 100,
      "businessTargetTime": "23:00",
      "businessTargetNextDay": false
    },
    "add": {
      "merchantTypes": ["烧烤"],
      "scenes": ["朋友聚餐"],
      "environmentRequirements": ["适合拍照"]
    },
    "remove": {},
    "clear": [],
    "exclude": {},
    "unexclude": {}
  },
  "conflicts": [],
  "followUpHints": [],
  "confidence": {
    "partySize": 0.99,
    "perCapitaBudget": 0.99,
    "merchantTypes": 0.95,
    "scenes": 0.95,
    "environmentRequirements": 0.90,
    "businessTargetTime": 0.98,
    "businessTargetNextDay": 0.90
  }
}
$constraint_prompt$,
    change_note = $note$Intelligent dining dialogue V2$note$
FROM prompt_definitions pd
WHERE pv.prompt_definition_id = pd.id
  AND pd.scene_code = 'CONSTRAINT_EXTRACTION'
  AND pv.version_tag = 'constraint-extraction:v2';


-- Insert the V2 version when it does not exist.
INSERT INTO prompt_versions (
    prompt_definition_id,
    version_no,
    version_tag,
    content,
    change_note
)
SELECT
    pd.id,

    COALESCE(
        (
            SELECT MAX(existing.version_no)
            FROM prompt_versions existing
            WHERE existing.prompt_definition_id = pd.id
        ),
        0
    ) + 1,

    'constraint-extraction:v2',

    $constraint_prompt$
You are FoodAdvisor's dining constraint extraction component.

Return ONLY valid JSON matching the ConstraintPatch output schema supplied below.
Do not return Markdown, explanations, or a full constraint-state snapshot.

The Java backend is authoritative. You only propose changes through operations.

Rules:
1. Extract every dining condition explicitly stated in the current user message.
2. Scalar fields must be placed in operations.set.
3. Positive list values must be placed in operations.add.
4. Explicitly removed fields must be placed in operations.clear.
5. Negative preferences must use operations.exclude.
6. Never invent merchants, reviews, IDs, locations, ratings, or constraints.
7. confidence must be an object mapping each changed field to a number from 0 to 1.
8. Set directRecommend=true when the user is asking to find or recommend a restaurant, including phrases such as:
   想吃、帮我找、推荐一家、去哪吃、有没有适合的店.

Field mappings:
- Number of diners -> partySize
- Per-person budget -> perCapitaBudget
- Total budget -> totalBudget
- 烧烤、火锅、自助餐、咖啡店等店铺类型 -> merchantTypes
- 川菜、粤菜、湘菜等菜系 -> cuisines
- 朋友聚餐、约会、家庭聚餐、商务宴请 -> scenes
- 适合拍照、安静、有包间、有氛围 -> environmentRequirements
- Explicit time such as 晚上十一点 -> businessTargetTime using HH:mm
- When the time refers to the same evening, businessTargetNextDay=false

For this user message:
四个人，人均100元，想吃烧烤，适合朋友聚餐，晚上十一点还营业，最好适合拍照。

A correct patch should contain values equivalent to:
{
  "intent": "MERCHANT_RECOMMENDATION",
  "directRecommend": true,
  "operations": {
    "set": {
      "partySize": 4,
      "perCapitaBudget": 100,
      "businessTargetTime": "23:00",
      "businessTargetNextDay": false
    },
    "add": {
      "merchantTypes": ["烧烤"],
      "scenes": ["朋友聚餐"],
      "environmentRequirements": ["适合拍照"]
    },
    "remove": {},
    "clear": [],
    "exclude": {},
    "unexclude": {}
  },
  "conflicts": [],
  "followUpHints": [],
  "confidence": {
    "partySize": 0.99,
    "perCapitaBudget": 0.99,
    "merchantTypes": 0.95,
    "scenes": 0.95,
    "environmentRequirements": 0.90,
    "businessTargetTime": 0.98,
    "businessTargetNextDay": 0.90
  }
}
$constraint_prompt$,

    $note$Intelligent dining dialogue V2$note$

FROM prompt_definitions pd
WHERE pd.scene_code = 'CONSTRAINT_EXTRACTION'
  AND NOT EXISTS (
      SELECT 1
      FROM prompt_versions existing
      WHERE existing.prompt_definition_id = pd.id
        AND existing.version_tag = 'constraint-extraction:v2'
  );


-- Activate the CONSTRAINT_EXTRACTION V2 version.
UPDATE prompt_definitions pd
SET active_version_id = pv.id,
    updated_at = CURRENT_TIMESTAMP
FROM prompt_versions pv
WHERE pv.prompt_definition_id = pd.id
  AND pd.scene_code = 'CONSTRAINT_EXTRACTION'
  AND pv.version_tag = 'constraint-extraction:v2';


-- ============================================================
-- 4. DINING_RECOMMENDATION V2 prompt
-- ============================================================

-- Update the V2 version when it already exists.
UPDATE prompt_versions pv
SET content = $reply_prompt$
You are FoodAdvisor's grounded dining reply component.

Generate natural-language replies using only the trusted data supplied by Java,
including CandidateFacts, GapFacts and EvidenceFacts.

Rules:
1. Do not invent merchants, reviews, prices, ratings, distances, opening status,
   IDs, evidence, scores, addresses or recommendation reasons.
2. Do not change the order of the supplied candidates.
3. Only use factId and evidenceId values that appear in the trusted input.
4. When a fact is absent, omit the conclusion instead of guessing.
5. The assistant text and merchant reasons must remain consistent with the
   supplied facts.
6. Return only valid JSON matching the DiningReply output schema.
7. Do not return Markdown or explanatory text outside the JSON object.
8. Generate no more than the requested maximum number of follow-up questions.
9. Set replyGenerator to AI_MODEL and degraded to false for a successful result.

Java remains authoritative for filtering, ranking, merchant identity,
constraint state and evidence access.
$reply_prompt$,
    change_note = $note$Grounded dining reply V2$note$
FROM prompt_definitions pd
WHERE pv.prompt_definition_id = pd.id
  AND pd.scene_code = 'DINING_RECOMMENDATION'
  AND pv.version_tag = 'dining-recommendation:v2';


-- Insert the V2 version when it does not exist.
INSERT INTO prompt_versions (
    prompt_definition_id,
    version_no,
    version_tag,
    content,
    change_note
)
SELECT
    pd.id,

    COALESCE(
        (
            SELECT MAX(existing.version_no)
            FROM prompt_versions existing
            WHERE existing.prompt_definition_id = pd.id
        ),
        0
    ) + 1,

    'dining-recommendation:v2',

    $reply_prompt$
You are FoodAdvisor's grounded dining reply component.

Generate natural-language replies using only the trusted data supplied by Java,
including CandidateFacts, GapFacts and EvidenceFacts.

Rules:
1. Do not invent merchants, reviews, prices, ratings, distances, opening status,
   IDs, evidence, scores, addresses or recommendation reasons.
2. Do not change the order of the supplied candidates.
3. Only use factId and evidenceId values that appear in the trusted input.
4. When a fact is absent, omit the conclusion instead of guessing.
5. The assistant text and merchant reasons must remain consistent with the
   supplied facts.
6. Return only valid JSON matching the DiningReply output schema.
7. Do not return Markdown or explanatory text outside the JSON object.
8. Generate no more than the requested maximum number of follow-up questions.
9. Set replyGenerator to AI_MODEL and degraded to false for a successful result.

Java remains authoritative for filtering, ranking, merchant identity,
constraint state and evidence access.
$reply_prompt$,

    $note$Grounded dining reply V2$note$

FROM prompt_definitions pd
WHERE pd.scene_code = 'DINING_RECOMMENDATION'
  AND NOT EXISTS (
      SELECT 1
      FROM prompt_versions existing
      WHERE existing.prompt_definition_id = pd.id
        AND existing.version_tag = 'dining-recommendation:v2'
  );


-- Activate the DINING_RECOMMENDATION V2 version.
UPDATE prompt_definitions pd
SET active_version_id = pv.id,
    updated_at = CURRENT_TIMESTAMP
FROM prompt_versions pv
WHERE pv.prompt_definition_id = pd.id
  AND pd.scene_code = 'DINING_RECOMMENDATION'
  AND pv.version_tag = 'dining-recommendation:v2';


COMMIT;