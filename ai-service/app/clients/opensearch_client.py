from opensearchpy import OpenSearch

from app.core.config import settings


def create_opensearch_client() -> OpenSearch:
    authentication = None

    if settings.opensearch_username and settings.opensearch_password:
        authentication = (
            settings.opensearch_username,
            settings.opensearch_password,
        )

    client = OpenSearch(
        hosts=[
            {
                "host": settings.opensearch_host,
                "port": settings.opensearch_port,
            }
        ],
        http_auth=authentication,
        use_ssl=settings.opensearch_use_ssl,
        verify_certs=settings.opensearch_verify_certs,
        ssl_assert_hostname=False,
        ssl_show_warn=False,
        timeout=5,
    )

    return client


def check_opensearch_connection() -> bool:
    try:
        client = create_opensearch_client()
        return bool(client.ping())
    except Exception:
        return False