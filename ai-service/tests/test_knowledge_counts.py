from unittest.mock import Mock

from app.services.knowledge_service import KnowledgeService


def test_active_source_counts_uses_distinct_sources():
    service = object.__new__(KnowledgeService)
    service._index_name = "knowledge-test"
    service._client = Mock()
    service._client.search.return_value = {
        "hits": {"total": {"value": 7}},
        "aggregations": {
            "by_source_type": {
                "buckets": [
                    {
                        "key": "MENU",
                        "doc_count": 5,
                        "distinct_sources": {"value": 2},
                    },
                    {
                        "key": "REVIEW",
                        "doc_count": 2,
                        "distinct_sources": {"value": 2},
                    },
                ]
            }
        },
    }

    result = service.active_source_counts()

    assert result == {
        "activeDocumentCount": 7,
        "activeDistinctSourceCounts": {"MENU": 2, "REVIEW": 2},
    }
    body = service._client.search.call_args.kwargs["body"]
    assert body["query"] == {"term": {"isActive": True}}
