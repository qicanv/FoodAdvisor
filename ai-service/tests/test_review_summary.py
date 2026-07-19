from app.models.schemas import ReviewSummaryRequest, SummaryReviewItem
from app.services import review_summary_service as service_module


def request_with_reviews() -> ReviewSummaryRequest:
    return ReviewSummaryRequest(
        merchantId=10,
        version=1,
        minimumReviewCount=1,
        reviews=[
            SummaryReviewItem(
                reviewId=101,
                rating=5,
                content="环境安静，适合朋友聚餐。",
                reviewTime="2026-07-01T10:00:00+08:00",
            )
        ],
    )


async def run_with_result(monkeypatch, result: dict):
    async def fake_chat_json(**_kwargs):
        return result

    monkeypatch.setattr(
        service_module.llm_service,
        "chat_json",
        fake_chat_json,
    )
    return await service_module.review_summary_service.summarize(
        request_with_reviews()
    )


async def test_out_of_candidate_review_ids_are_discarded(monkeypatch):
    response = await run_with_result(
        monkeypatch,
        {
            "advantages": [
                {
                    "name": "环境好",
                    "mentionCount": 1,
                    "reviewIds": [999],
                }
            ],
            "evidences": [
                {
                    "reviewId": 999,
                    "evidenceType": "ADVANTAGE",
                    "evidenceExcerpt": "伪造内容",
                }
            ],
        },
    )

    assert response.advantages == []
    assert response.evidences == []


async def test_non_verbatim_excerpt_is_replaced_by_controlled_text(
    monkeypatch,
):
    response = await run_with_result(
        monkeypatch,
        {
            "advantages": [
                {
                    "name": "环境安静",
                    "mentionCount": 1,
                    "reviewIds": [101],
                }
            ],
            "evidences": [
                {
                    "reviewId": 101,
                    "evidenceType": "ADVANTAGE",
                    "evidenceExcerpt": "模型编造的片段",
                }
            ],
        },
    )

    assert response.evidences[0].reviewId == 101
    assert response.evidences[0].evidenceExcerpt == "环境安静，适合朋友聚餐。"


async def test_invalid_evidence_type_is_filtered(monkeypatch):
    response = await run_with_result(
        monkeypatch,
        {
            "evidences": [
                {
                    "reviewId": 101,
                    "evidenceType": "AI",
                    "evidenceExcerpt": "环境安静",
                }
            ]
        },
    )

    assert response.evidences == []


async def test_valid_review_id_and_type_are_preserved(monkeypatch):
    response = await run_with_result(
        monkeypatch,
        {
            "evidences": [
                {
                    "reviewId": 101,
                    "evidenceType": "ENVIRONMENT",
                    "evidenceExcerpt": "环境安静",
                }
            ]
        },
    )

    assert response.evidences[0].reviewId == 101
    assert response.evidences[0].evidenceType.value == "ENVIRONMENT"
