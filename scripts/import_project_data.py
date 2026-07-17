from __future__ import annotations

import argparse
import csv
import json
import re
import shutil
import subprocess
import sys
from collections import Counter
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[1]
DATA_DIR = ROOT / "data" / "final"
FALLBACK_DATA_DIR = ROOT / "data" / "processed"
GENERATED_DIR = ROOT / "data" / "generated"
BACKUP_DIR = ROOT / "data" / "backups"

USER_OFFSET = 1000
MERCHANT_OFFSET = 1000
REVIEW_OFFSET = 10000

PASSWORD_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"

MAX_AUDIT_ERROR_LENGTH = 500


@dataclass
class ImportAuditContext:
    import_batch_id: str = field(default_factory=lambda: f"import-{uuid4().hex}")
    trace_id: str = field(default_factory=lambda: f"data-import-{uuid4().hex}")
    started_at: datetime = field(default_factory=datetime.now)
    audit_recorded: bool = False


def data_file(name: str) -> Path:
    preferred = DATA_DIR / name
    if preferred.exists():
        return preferred
    return FALLBACK_DATA_DIR / name


def read_csv(path: Path) -> list[dict[str, str]]:
    if not path.exists():
        raise FileNotFoundError(f"File not found: {path}")
    with path.open("r", encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def require_columns(rows: list[dict[str, str]], required: set[str], filename: str) -> None:
    if not rows:
        raise ValueError(f"{filename} has no data")
    missing = required - set(rows[0].keys())
    if missing:
        raise ValueError(f"{filename} missing columns: {', '.join(sorted(missing))}")


def sql_text(value: Any) -> str:
    if value is None:
        return "NULL"
    text = str(value)
    return "'" + text.replace("'", "''") + "'"


def sql_nullable_text(value: Any) -> str:
    if value is None or str(value).strip() == "":
        return "NULL"
    return sql_text(str(value).strip())


def sql_int(value: Any) -> str:
    return str(int(float(str(value).strip())))


def sql_decimal(value: Any) -> str:
    return str(float(str(value).strip()))


def sql_timestamp(value: Any) -> str:
    text = str(value or "").strip()
    if not text:
        return "CURRENT_TIMESTAMP"
    return f"{sql_text(text)}::timestamptz"


def sql_json(value: Any) -> str:
    return f"{sql_text(json.dumps(value, ensure_ascii=False, separators=(',', ':')))}::jsonb"


def chunks(items: list[str], size: int = 250) -> list[list[str]]:
    return [items[index:index + size] for index in range(0, len(items), size)]


def sanitize_log_text(value: Any, max_length: int = MAX_AUDIT_ERROR_LENGTH) -> str:
    text = "" if value is None else str(value)
    text = text.replace("\r", " ").replace("\n", " ")
    text = re.sub(r"[\x00-\x08\x0b\x0c\x0e-\x1f]", " ", text)
    text = re.sub(
        r"(?i)(postgres(?:ql)?://)[^\s]+",
        r"\1****",
        text,
    )
    text = re.sub(
        r"(?i)(Authorization\s*:\s*)Bearer\s+[^\s,;\"']+",
        r"\1Bearer ****",
        text,
    )
    text = re.sub(
        r"(?i)(Cookie\s*:\s*)[^\s,;\"']+",
        r"\1****",
        text,
    )
    text = re.sub(
        r"(?is)((?:content|review_content)\s*[=:]\s*)'[^']*'",
        r"\1'****'",
        text,
    )
    text = re.sub(
        r"(?i)([\"'](?:password|passwd|pwd|token|api[_-]?key|authorization|cookie|secret)[\"']\s*:\s*[\"'])(.*?)([\"'])",
        r"\1****\3",
        text,
    )
    text = re.sub(
        r"(?i)((?:password|passwd|pwd|token|api[_-]?key|authorization|cookie|secret)\s*[=:]\s*)([\"']?)([^\s,;\"']+)([\"']?)",
        r"\1\2****\4",
        text,
    )
    text = re.sub(r"(?i)(Bearer\s+)[^\s,;]+", r"\1****", text)
    text = re.sub(r"sk-[A-Za-z0-9_-]{8,}", "sk-****", text)
    text = text.replace(str(ROOT), "<project-root>")
    text = " ".join(text.split())
    if len(text) > max_length:
        return text[:max_length - 3] + "..."
    return text


def audit_error_code(error: BaseException) -> str:
    if isinstance(error, FileNotFoundError):
        return "DATA_IMPORT_FILE_NOT_FOUND"
    if isinstance(error, ValueError):
        return "DATA_IMPORT_VALIDATION_ERROR"
    if isinstance(error, RuntimeError):
        return "DATA_IMPORT_RUNTIME_ERROR"
    return "DATA_IMPORT_UNEXPECTED_ERROR"


def audit_source_files(paths: list[Path]) -> list[str]:
    return [path.name for path in paths]


def audit_counts(
    users: list[dict[str, str]] | None,
    merchants: list[dict[str, str]] | None,
    reviews: list[dict[str, str]] | None,
    aspects: list[dict[str, str]] | None,
) -> dict[str, int]:
    users_count = len(users or [])
    merchants_count = len(merchants or [])
    reviews_count = len(reviews or [])
    return {
        "usersCount": users_count,
        "merchantsCount": merchants_count,
        "reviewsCount": reviews_count,
        "reviewAnalysisCount": len(aspects or []) if aspects is not None else reviews_count,
        "businessHoursCount": merchants_count * 7,
    }


def build_audit_metadata(
    context: ImportAuditContext,
    *,
    import_mode: str,
    backup_created: bool,
    backup_file_name: str | None,
    source_files: list[Path],
    users: list[dict[str, str]] | None,
    merchants: list[dict[str, str]] | None,
    reviews: list[dict[str, str]] | None,
    aspects: list[dict[str, str]] | None,
    finished_at: datetime,
) -> dict[str, Any]:
    metadata: dict[str, Any] = {
        "importBatchId": context.import_batch_id,
        "traceId": context.trace_id,
        "startedAt": context.started_at.isoformat(),
        "finishedAt": finished_at.isoformat(),
        "durationMs": int((finished_at - context.started_at).total_seconds() * 1000),
        "importMode": import_mode,
        "backupCreated": backup_created,
        "backupFileName": backup_file_name,
        "sourceFiles": audit_source_files(source_files),
    }
    metadata.update(audit_counts(users, merchants, reviews, aspects))
    return metadata


def build_audit_insert_sql(
    *,
    context: ImportAuditContext,
    level: str,
    result: str,
    metadata: dict[str, Any],
    error_code: str | None = None,
    error_message: str | None = None,
) -> str:
    values = [
        "'DATA_IMPORT'",
        "'SYSTEM'",
        "'PROJECT_DATA_IMPORT'",
        sql_text(level),
        sql_text(result),
        "'IMPORT_BATCH'",
        sql_text(context.import_batch_id),
        sql_nullable_text(error_code),
        sql_nullable_text(error_message),
        sql_text(context.trace_id),
        sql_json(metadata),
    ]
    return (
        "INSERT INTO audit_logs ("
        "operation_type, operator_role, module, level, result, object_type, "
        "object_id, error_code, error_message, business_trace_id, metadata"
        ") VALUES ("
        + ", ".join(values)
        + ");\n"
    )


def sentiment_to_rating(value: str, overall_rating: int) -> str:
    mapping = {
        "POSITIVE": max(4, overall_rating),
        "NEUTRAL": 3,
        "NEGATIVE": min(2, overall_rating),
        "NOT_MENTIONED": None,
    }
    rating = mapping.get(value)
    return "NULL" if rating is None else str(rating)


def overall_sentiment(rating: int) -> str:
    if rating >= 4:
        return "POSITIVE"
    if rating == 3:
        return "NEUTRAL"
    return "NEGATIVE"


def parse_business_hours(value: str) -> tuple[str, str, bool]:
    text = str(value or "").strip()
    if "-" not in text:
        return "10:00", "22:00", False
    open_time, close_time = [part.strip() for part in text.split("-", 1)]
    crosses_midnight = close_time <= open_time
    return open_time, close_time, crosses_midnight


def validate(
    users: list[dict[str, str]],
    merchants: list[dict[str, str]],
    reviews: list[dict[str, str]],
    aspects: list[dict[str, str]],
) -> None:
    require_columns(users, {"id", "username", "nickname", "created_at", "updated_at"}, "users.csv")
    require_columns(
        merchants,
        {
            "id", "name", "category", "cuisine", "avg_price", "rating",
            "review_count", "address", "longitude", "latitude",
            "business_hours", "tags",
        },
        "merchants.csv",
    )
    require_columns(
        reviews,
        {"id", "user_id", "merchant_id", "rating", "content", "status", "created_at", "updated_at", "source"},
        "reviews.csv",
    )
    require_columns(aspects, {"review_id", "food", "service", "ambience", "price"}, "review_aspects.csv")

    user_ids = {int(row["id"]) for row in users}
    merchant_ids = {int(row["id"]) for row in merchants}
    review_ids = {int(row["id"]) for row in reviews}
    aspect_ids = {int(row["review_id"]) for row in aspects}

    if len(user_ids) != len(users):
        raise ValueError("users.csv contains duplicate IDs")
    if len(merchant_ids) != len(merchants):
        raise ValueError("merchants.csv contains duplicate IDs")
    if len(review_ids) != len(reviews):
        raise ValueError("reviews.csv contains duplicate IDs")
    if review_ids != aspect_ids:
        raise ValueError("review_aspects.csv review_id does not match reviews.csv")

    pairs: set[tuple[int, int]] = set()
    for row in reviews:
        user_id = int(row["user_id"])
        merchant_id = int(row["merchant_id"])
        rating = int(float(row["rating"]))
        content = row["content"].strip()
        if user_id not in user_ids:
            raise ValueError(f"review references missing user: {user_id}")
        if merchant_id not in merchant_ids:
            raise ValueError(f"review references missing merchant: {merchant_id}")
        if not 1 <= rating <= 5:
            raise ValueError(f"invalid rating: {rating}")
        if not 10 <= len(content) <= 2000:
            raise ValueError(f"review {row['id']} content length violates database constraint")
        pair = (user_id, merchant_id)
        if pair in pairs:
            raise ValueError(f"duplicate review pair: user={user_id}, merchant={merchant_id}")
        pairs.add(pair)

    valid_aspects = {"POSITIVE", "NEUTRAL", "NEGATIVE", "NOT_MENTIONED"}
    for row in aspects:
        for key in ("food", "service", "ambience", "price"):
            if row[key] not in valid_aspects:
                raise ValueError(f"invalid aspect value: review={row['review_id']}, {key}={row[key]}")


def build_sql(
    users: list[dict[str, str]],
    merchants: list[dict[str, str]],
    reviews: list[dict[str, str]],
    aspects: list[dict[str, str]],
) -> str:
    aspect_by_review = {int(row["review_id"]): row for row in aspects}
    merchant_by_id = {int(row["id"]): row for row in merchants}

    lines: list[str] = [
        "\\set ON_ERROR_STOP on",
        "BEGIN;",
        "SET TIME ZONE 'Asia/Shanghai';",
        "",
        "-- Preserve baseline system accounts; imported course users start at 1001.",
        "INSERT INTO users (id, username, password_hash, nickname, email, phone, role, status) VALUES",
        f"(1, 'admin', {sql_text(PASSWORD_HASH)}, 'Admin', 'admin@foodadvisor.com', '13800000001', 'ADMIN', 'ACTIVE'),",
        f"(2, 'merchant', {sql_text(PASSWORD_HASH)}, 'Merchant User', 'merchant@foodadvisor.com', '13800000002', 'MERCHANT', 'ACTIVE'),",
        f"(3, 'demo', {sql_text(PASSWORD_HASH)}, 'Demo User', 'demo@foodadvisor.com', '13800000003', 'USER', 'ACTIVE')",
        "ON CONFLICT (id) DO UPDATE SET",
        "username = EXCLUDED.username, password_hash = EXCLUDED.password_hash, nickname = EXCLUDED.nickname,",
        "email = EXCLUDED.email, phone = EXCLUDED.phone, role = EXCLUDED.role, status = EXCLUDED.status,",
        "updated_at = CURRENT_TIMESTAMP;",
        "",
        "-- Archive legacy demo merchants that should not participate in normal recommendations.",
        "UPDATE merchants SET platform_status = 'ARCHIVED', operation_status = 'CLOSED_PERMANENTLY', updated_at = CURRENT_TIMESTAMP",
        "WHERE id BETWEEN 1 AND 5 AND merchant_code IN ('M000001','M000002','M000003','M000004','M000005');",
        "",
    ]

    user_values: list[str] = []
    for row in users:
        user_values.append(
            "(" + ", ".join(
                [
                    str(int(row["id"]) + USER_OFFSET),
                    sql_text(row["username"].strip()),
                    sql_text(PASSWORD_HASH),
                    sql_nullable_text(row.get("nickname")),
                    "NULL",
                    "NULL",
                    "'USER'",
                    "'ACTIVE'",
                    sql_timestamp(row.get("created_at")),
                    sql_timestamp(row.get("updated_at")),
                ]
            ) + ")"
        )

    for batch in chunks(user_values):
        lines.extend(
            [
                "INSERT INTO users (id, username, password_hash, nickname, email, phone, role, status, created_at, updated_at) VALUES",
                ",\n".join(batch),
                "ON CONFLICT (id) DO UPDATE SET",
                "username = EXCLUDED.username, password_hash = EXCLUDED.password_hash, nickname = EXCLUDED.nickname,",
                "role = EXCLUDED.role, status = EXCLUDED.status, updated_at = EXCLUDED.updated_at;",
                "",
            ]
        )

    merchant_values: list[str] = []
    for row in merchants:
        source_id = int(row["id"])
        tags = [tag.strip() for tag in row.get("tags", "").split("|") if tag.strip()]
        description = "Course project demo merchant; name and coordinates come from OpenStreetMap, other attributes are synthetic."
        merchant_values.append(
            "(" + ", ".join(
                [
                    str(source_id + MERCHANT_OFFSET),
                    sql_text(f"OSM{source_id:06d}"),
                    sql_text(row["name"].strip()),
                    sql_text(row["category"].strip()),
                    sql_nullable_text(row.get("cuisine")),
                    sql_decimal(row["rating"]),
                    sql_decimal(row["avg_price"]),
                    sql_int(row["review_count"]),
                    sql_text(row["address"].strip()),
                    "'CD'",
                    sql_decimal(row["longitude"]),
                    sql_decimal(row["latitude"]),
                    sql_text(description),
                    sql_json(tags),
                    "'ACTIVE'",
                    "'OPERATING'",
                    "CURRENT_TIMESTAMP",
                    "CURRENT_TIMESTAMP",
                ]
            ) + ")"
        )

    for batch in chunks(merchant_values, 150):
        lines.extend(
            [
                "INSERT INTO merchants (id, merchant_code, name, category, cuisine, rating, average_price, review_count, address, region_code, longitude, latitude, description, environment_tags, platform_status, operation_status, created_at, updated_at) VALUES",
                ",\n".join(batch),
                "ON CONFLICT (id) DO UPDATE SET",
                "merchant_code = EXCLUDED.merchant_code, name = EXCLUDED.name, category = EXCLUDED.category,",
                "cuisine = EXCLUDED.cuisine, rating = EXCLUDED.rating, average_price = EXCLUDED.average_price,",
                "review_count = EXCLUDED.review_count, address = EXCLUDED.address, region_code = EXCLUDED.region_code,",
                "longitude = EXCLUDED.longitude, latitude = EXCLUDED.latitude, description = EXCLUDED.description,",
                "environment_tags = EXCLUDED.environment_tags, platform_status = EXCLUDED.platform_status,",
                "operation_status = EXCLUDED.operation_status, updated_at = CURRENT_TIMESTAMP;",
                "",
            ]
        )

    imported_merchant_ids = ",".join(str(int(row["id"]) + MERCHANT_OFFSET) for row in merchants)
    lines.append(f"DELETE FROM merchant_business_hours WHERE merchant_id IN ({imported_merchant_ids});")

    hour_values: list[str] = []
    for row in merchants:
        merchant_id = int(row["id"]) + MERCHANT_OFFSET
        open_time, close_time, crosses = parse_business_hours(row.get("business_hours", ""))
        for day in range(1, 8):
            hour_values.append(
                f"({merchant_id}, {day}, {sql_text(open_time)}::time, {sql_text(close_time)}::time, FALSE, {'TRUE' if crosses else 'FALSE'})"
            )
    for batch in chunks(hour_values, 500):
        lines.extend(
            [
                "INSERT INTO merchant_business_hours (merchant_id, day_of_week, open_time, close_time, is_closed, crosses_midnight) VALUES",
                ",\n".join(batch) + ";",
                "",
            ]
        )

    review_values: list[str] = []
    analysis_values: list[str] = []
    for row in reviews:
        source_review_id = int(row["id"])
        review_id = source_review_id + REVIEW_OFFSET
        source_merchant_id = int(row["merchant_id"])
        merchant_id = source_merchant_id + MERCHANT_OFFSET
        user_id = int(row["user_id"]) + USER_OFFSET
        rating = int(float(row["rating"]))
        created_at = row.get("created_at", "")
        updated_at = row.get("updated_at", "")
        aspect = aspect_by_review[source_review_id]
        avg_spend = merchant_by_id[source_merchant_id].get("avg_price", "")
        consumption_date = created_at[:10] if len(created_at) >= 10 else None

        review_values.append(
            "(" + ", ".join(
                [
                    str(review_id),
                    str(merchant_id),
                    str(user_id),
                    "'ORIGINAL'",
                    str(rating),
                    sentiment_to_rating(aspect["food"], rating),
                    sentiment_to_rating(aspect["ambience"], rating),
                    sentiment_to_rating(aspect["service"], rating),
                    sql_decimal(avg_spend),
                    f"{sql_text(consumption_date)}::date" if consumption_date else "NULL",
                    sql_text(row["content"].strip()),
                    "'COURSE_SYNTHETIC'",
                    sql_text(f"course-review-{source_review_id}"),
                    sql_text(f"course-import-{source_review_id}"),
                    "1",
                    "'PUBLISHED'",
                    "'APPROVED'",
                    "'LOW'",
                    sql_timestamp(created_at),
                    sql_timestamp(created_at),
                    sql_timestamp(updated_at),
                ]
            ) + ")"
        )

        aspect_json = [
            {"aspect": "FOOD", "sentiment": aspect["food"]},
            {"aspect": "SERVICE", "sentiment": aspect["service"]},
            {"aspect": "AMBIENCE", "sentiment": aspect["ambience"]},
            {"aspect": "PRICE", "sentiment": aspect["price"]},
        ]
        analysis_values.append(
            "(" + ", ".join(
                [
                    str(review_id),
                    "1",
                    "1",
                    sql_text(overall_sentiment(rating)),
                    "0.9500",
                    "FALSE",
                    "'[]'::jsonb",
                    sql_json(aspect_json),
                    "NULL",
                    "'COURSE_SYNTHETIC_GENERATOR'",
                    "'1.0'",
                    sql_text(f"course-import-{source_review_id}"),
                    "'SUCCESS'",
                    sql_timestamp(created_at),
                    sql_timestamp(created_at),
                    sql_timestamp(created_at),
                    sql_timestamp(updated_at),
                ]
            ) + ")"
        )

    for batch in chunks(review_values, 200):
        lines.extend(
            [
                "INSERT INTO reviews (id, merchant_id, user_id, review_type, rating, taste_rating, environment_rating, service_rating, average_spend, consumption_date, content, source, external_id, idempotency_key, current_version, status, moderation_status, risk_level, published_at, created_at, updated_at) VALUES",
                ",\n".join(batch),
                "ON CONFLICT (id) DO UPDATE SET",
                "merchant_id = EXCLUDED.merchant_id, user_id = EXCLUDED.user_id, review_type = EXCLUDED.review_type,",
                "rating = EXCLUDED.rating, taste_rating = EXCLUDED.taste_rating, environment_rating = EXCLUDED.environment_rating,",
                "service_rating = EXCLUDED.service_rating, average_spend = EXCLUDED.average_spend,",
                "consumption_date = EXCLUDED.consumption_date, content = EXCLUDED.content, source = EXCLUDED.source,",
                "external_id = EXCLUDED.external_id, idempotency_key = EXCLUDED.idempotency_key,",
                "current_version = EXCLUDED.current_version, status = EXCLUDED.status,",
                "moderation_status = EXCLUDED.moderation_status, risk_level = EXCLUDED.risk_level,",
                "published_at = EXCLUDED.published_at, updated_at = EXCLUDED.updated_at;",
                "",
            ]
        )

    for batch in chunks(analysis_values, 200):
        lines.extend(
            [
                "INSERT INTO review_analysis (review_id, review_version, analysis_version, sentiment, confidence, low_confidence, keywords, aspects, negative_reason, model_name, model_version, business_trace_id, status, started_at, completed_at, created_at, updated_at) VALUES",
                ",\n".join(batch),
                "ON CONFLICT (review_id, review_version, analysis_version) DO UPDATE SET",
                "sentiment = EXCLUDED.sentiment, confidence = EXCLUDED.confidence, low_confidence = EXCLUDED.low_confidence,",
                "keywords = EXCLUDED.keywords, aspects = EXCLUDED.aspects, model_name = EXCLUDED.model_name,",
                "model_version = EXCLUDED.model_version, business_trace_id = EXCLUDED.business_trace_id,",
                "status = EXCLUDED.status, completed_at = EXCLUDED.completed_at, updated_at = EXCLUDED.updated_at;",
                "",
            ]
        )

    expected_users = len(users)
    expected_merchants = len(merchants)
    expected_reviews = len(reviews)
    expected_hours = len(merchants) * 7

    lines.extend(
        [
            "-- Recalculate merchant rating and review counts from imported reviews.",
            "UPDATE merchants m SET",
            "rating = summary.average_rating,",
            "review_count = summary.review_count,",
            "updated_at = CURRENT_TIMESTAMP",
            "FROM (",
            "    SELECT merchant_id, ROUND(AVG(rating)::numeric, 2) AS average_rating, COUNT(*)::integer AS review_count",
            "    FROM reviews",
            f"    WHERE id BETWEEN {REVIEW_OFFSET + 1} AND {REVIEW_OFFSET + expected_reviews} AND source = 'COURSE_SYNTHETIC' AND status = 'PUBLISHED'",
            "    GROUP BY merchant_id",
            ") summary",
            "WHERE m.id = summary.merchant_id;",
            "",
            "SELECT setval(pg_get_serial_sequence('users', 'id'), (SELECT MAX(id) FROM users), true);",
            "SELECT setval(pg_get_serial_sequence('merchants', 'id'), (SELECT MAX(id) FROM merchants), true);",
            "SELECT setval(pg_get_serial_sequence('merchant_business_hours', 'id'), (SELECT MAX(id) FROM merchant_business_hours), true);",
            "SELECT setval(pg_get_serial_sequence('reviews', 'id'), (SELECT MAX(id) FROM reviews), true);",
            "SELECT setval(pg_get_serial_sequence('review_analysis', 'id'), (SELECT MAX(id) FROM review_analysis), true);",
            "",
            "DO $$",
            "DECLARE",
            "    actual_users integer;",
            "    actual_merchants integer;",
            "    actual_reviews integer;",
            "    actual_analysis integer;",
            "    actual_hours integer;",
            "BEGIN",
            f"    SELECT COUNT(*) INTO actual_users FROM users WHERE id BETWEEN {USER_OFFSET + 1} AND {USER_OFFSET + expected_users};",
            f"    SELECT COUNT(*) INTO actual_merchants FROM merchants WHERE id BETWEEN {MERCHANT_OFFSET + 1} AND {MERCHANT_OFFSET + expected_merchants};",
            f"    SELECT COUNT(*) INTO actual_reviews FROM reviews WHERE id BETWEEN {REVIEW_OFFSET + 1} AND {REVIEW_OFFSET + expected_reviews} AND source = 'COURSE_SYNTHETIC';",
            f"    SELECT COUNT(*) INTO actual_analysis FROM review_analysis ra JOIN reviews r ON r.id = ra.review_id WHERE r.id BETWEEN {REVIEW_OFFSET + 1} AND {REVIEW_OFFSET + expected_reviews} AND r.source = 'COURSE_SYNTHETIC';",
            f"    SELECT COUNT(*) INTO actual_hours FROM merchant_business_hours WHERE merchant_id BETWEEN {MERCHANT_OFFSET + 1} AND {MERCHANT_OFFSET + expected_merchants};",
            f"    IF actual_users <> {expected_users} THEN RAISE EXCEPTION 'users import count mismatch: %', actual_users; END IF;",
            f"    IF actual_merchants <> {expected_merchants} THEN RAISE EXCEPTION 'merchants import count mismatch: %', actual_merchants; END IF;",
            f"    IF actual_reviews <> {expected_reviews} THEN RAISE EXCEPTION 'reviews import count mismatch: %', actual_reviews; END IF;",
            f"    IF actual_analysis <> {expected_reviews} THEN RAISE EXCEPTION 'review_analysis import count mismatch: %', actual_analysis; END IF;",
            f"    IF actual_hours <> {expected_hours} THEN RAISE EXCEPTION 'business_hours import count mismatch: %', actual_hours; END IF;",
            "END $$;",
            "",
            "COMMIT;",
            "",
            "SELECT 'users' AS item, COUNT(*) AS count FROM users WHERE id >= 1001 AND id < 2000",
            "UNION ALL SELECT 'merchants', COUNT(*) FROM merchants WHERE id >= 1001 AND id < 2000",
            f"UNION ALL SELECT 'reviews', COUNT(*) FROM reviews WHERE id BETWEEN {REVIEW_OFFSET + 1} AND {REVIEW_OFFSET + expected_reviews} AND source = 'COURSE_SYNTHETIC'",
            f"UNION ALL SELECT 'review_analysis', COUNT(*) FROM review_analysis ra JOIN reviews r ON r.id = ra.review_id WHERE r.id BETWEEN {REVIEW_OFFSET + 1} AND {REVIEW_OFFSET + expected_reviews} AND r.source = 'COURSE_SYNTHETIC'",
            "UNION ALL SELECT 'business_hours', COUNT(*) FROM merchant_business_hours WHERE merchant_id >= 1001 AND merchant_id < 2000;",
        ]
    )

    return "\n".join(lines) + "\n"


def run_command(command: list[str], *, input_text: str | None = None) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        command,
        input=input_text,
        text=True,
        encoding="utf-8",
        errors="replace",
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        cwd=ROOT,
        check=False,
    )


def ensure_docker() -> None:
    if shutil.which("docker") is None:
        raise RuntimeError("Docker command not found; please start Docker Desktop")
    result = run_command(["docker", "compose", "ps", "postgres"])
    if result.returncode != 0:
        raise RuntimeError("Unable to access docker compose: " + sanitize_log_text(result.stdout))
    ready = run_command([
        "docker", "compose", "exec", "-T", "postgres", "sh", "-lc",
        'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"',
    ])
    if ready.returncode != 0:
        raise RuntimeError("PostgreSQL is not ready: " + sanitize_log_text(ready.stdout))


def backup_database() -> Path:
    BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    backup_path = BACKUP_DIR / f"foodadvisor_before_import_{datetime.now():%Y%m%d_%H%M%S}.sql"
    command = [
        "docker", "compose", "exec", "-T", "postgres", "sh", "-lc",
        'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --no-privileges',
    ]
    with backup_path.open("wb") as output:
        result = subprocess.run(command, stdout=output, stderr=subprocess.PIPE, cwd=ROOT, check=False)
    if result.returncode != 0:
        backup_path.unlink(missing_ok=True)
        message = result.stderr.decode("utf-8", errors="replace")
        raise RuntimeError("Database backup failed:\n" + message)
    return backup_path


def execute_sql(sql: str) -> str:
    command = [
        "docker", "compose", "exec", "-T", "postgres", "sh", "-lc",
        'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"',
    ]
    result = run_command(command, input_text=sql)
    if result.returncode != 0:
        raise RuntimeError(
            "DATABASE_IMPORT_FAILED: transaction rolled back; "
            f"psql return code={result.returncode}"
        )
    return result.stdout

def execute_audit_sql(sql: str) -> None:
    command = [
        "docker", "compose", "exec", "-T", "postgres", "sh", "-lc",
        'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"',
    ]
    result = run_command(command, input_text=sql)
    if result.returncode != 0:
        raise RuntimeError(
            "DATA_IMPORT_AUDIT_WRITE_FAILED: "
            f"psql return code={result.returncode}"
        )


def record_data_import_audit_safely(
    context: ImportAuditContext,
    *,
    level: str,
    result: str,
    import_mode: str,
    backup_created: bool,
    backup_file_name: str | None,
    source_files: list[Path],
    users: list[dict[str, str]] | None,
    merchants: list[dict[str, str]] | None,
    reviews: list[dict[str, str]] | None,
    aspects: list[dict[str, str]] | None,
    error: BaseException | None = None,
) -> None:
    if context.audit_recorded:
        return

    try:
        finished_at = datetime.now()
        error_code = audit_error_code(error) if error is not None else None
        error_message = sanitize_log_text(error) if error is not None else None
        metadata = build_audit_metadata(
            context,
            import_mode=import_mode,
            backup_created=backup_created,
            backup_file_name=backup_file_name,
            source_files=source_files,
            users=users,
            merchants=merchants,
            reviews=reviews,
            aspects=aspects,
            finished_at=finished_at,
        )
        sql = build_audit_insert_sql(
            context=context,
            level=level,
            result=result,
            metadata=metadata,
            error_code=error_code,
            error_message=error_message,
        )
        execute_audit_sql(sql)
        context.audit_recorded = True
    except Exception as audit_error:
        try:
            warning = sanitize_log_text(
                type(audit_error).__name__ + ": " + str(audit_error)
            )
        except Exception:
            warning = "audit error unavailable"
        print(
            "DATA_IMPORT audit warning: " + warning,
            file=sys.stderr,
        )


def safe_error_summary(error: BaseException) -> str:
    return sanitize_log_text(type(error).__name__ + ": " + str(error))


def main() -> int:
    context = ImportAuditContext()
    parser = argparse.ArgumentParser(description="Import FoodAdvisor project data")
    parser.add_argument("--generate-only", action="store_true", help="Only generate SQL without connecting to the database")
    parser.add_argument("--skip-backup", action="store_true", help="Skip database backup before import")
    args = parser.parse_args()

    users: list[dict[str, str]] | None = None
    merchants: list[dict[str, str]] | None = None
    reviews: list[dict[str, str]] | None = None
    aspects: list[dict[str, str]] | None = None
    source_paths: list[Path] = []
    backup_path: Path | None = None
    import_mode = "GENERATE_ONLY" if args.generate_only else "LIVE"

    try:
        users_path = data_file("users.csv")
        merchants_path = data_file("merchants.csv")
        reviews_path = data_file("reviews.csv")
        aspects_path = data_file("review_aspects.csv")
        source_paths = [users_path, merchants_path, reviews_path, aspects_path]

        users = read_csv(users_path)
        merchants = read_csv(merchants_path)
        reviews = read_csv(reviews_path)
        aspects = read_csv(aspects_path)
        validate(users, merchants, reviews, aspects)

        print("=" * 60)
        print("FoodAdvisor data import")
        print("=" * 60)
        print(f"users: {len(users)}")
        print(f"merchants: {len(merchants)}")
        print(f"reviews: {len(reviews)}")
        print(f"review aspects: {len(aspects)}")
        print(f"data directory: {users_path.parent}")

        sql = build_sql(users, merchants, reviews, aspects)
        GENERATED_DIR.mkdir(parents=True, exist_ok=True)
        sql_path = GENERATED_DIR / "import_project_data.sql"
        sql_path.write_text(sql, encoding="utf-8")
        print(f"SQL generated: {sql_path}")

        if args.generate_only:
            print("SQL generated only; database import was not executed.")
            return 0

        ensure_docker()

        if not args.skip_backup:
            backup_path = backup_database()
            print(f"database backup: {backup_path}")

        output = execute_sql(sql)
        print("\ndatabase output:")
        print(output.strip())
        print("\nimport completed.")

        record_data_import_audit_safely(
            context,
            level="INFO",
            result="SUCCESS",
            import_mode=import_mode,
            backup_created=backup_path is not None,
            backup_file_name=backup_path.name if backup_path is not None else None,
            source_files=source_paths,
            users=users,
            merchants=merchants,
            reviews=reviews,
            aspects=aspects,
        )
        return 0
    except Exception as error:
        record_data_import_audit_safely(
            context,
            level="ERROR",
            result="FAILURE",
            import_mode=import_mode,
            backup_created=backup_path is not None,
            backup_file_name=backup_path.name if backup_path is not None else None,
            source_files=source_paths,
            users=users,
            merchants=merchants,
            reviews=reviews,
            aspects=aspects,
            error=error,
        )
        raise

if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"\nError: {safe_error_summary(error)}", file=sys.stderr)
        raise SystemExit(1)
