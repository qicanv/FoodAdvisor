from __future__ import annotations

import importlib.util
import contextlib
import io
import json
import re
import sys
import tempfile
import unittest
import subprocess
from pathlib import Path
from unittest.mock import patch


SCRIPT_PATH = Path(__file__).with_name("import_project_data.py")
SPEC = importlib.util.spec_from_file_location("import_project_data", SCRIPT_PATH)
import_project_data = importlib.util.module_from_spec(SPEC)
sys.modules["import_project_data"] = import_project_data
assert SPEC.loader is not None
SPEC.loader.exec_module(import_project_data)


class ImportProjectDataAuditTest(unittest.TestCase):

    def setUp(self) -> None:
        self.users = [
            {
                "id": "1",
                "username": "alice",
                "nickname": "Alice",
                "created_at": "2026-07-01T10:00:00+08:00",
                "updated_at": "2026-07-01T10:00:00+08:00",
            }
        ]
        self.merchants = [
            {
                "id": "1",
                "name": "Test Merchant",
                "category": "Restaurant",
                "cuisine": "Sichuan",
                "avg_price": "68",
                "rating": "4.5",
                "review_count": "1",
                "address": "Test Address",
                "longitude": "104.06",
                "latitude": "30.67",
                "business_hours": "10:00-22:00",
                "tags": "quiet|wifi",
            }
        ]
        self.reviews = [
            {
                "id": "1",
                "user_id": "1",
                "merchant_id": "1",
                "rating": "5",
                "content": "This review content is long enough",
                "status": "PUBLISHED",
                "created_at": "2026-07-01T11:00:00+08:00",
                "updated_at": "2026-07-01T11:00:00+08:00",
                "source": "COURSE_SYNTHETIC",
            }
        ]
        self.aspects = [
            {
                "review_id": "1",
                "food": "POSITIVE",
                "service": "POSITIVE",
                "ambience": "NEUTRAL",
                "price": "POSITIVE",
            }
        ]

    def test_success_writes_one_data_import_success_log_after_import(self) -> None:
        audit_sql: list[str] = []
        events: list[str] = []

        with self.patched_main_io(), \
                patch.object(sys, "argv", ["import_project_data.py"]), \
                patch.object(import_project_data, "ensure_docker", side_effect=lambda: events.append("docker")), \
                patch.object(import_project_data, "backup_database", side_effect=lambda: events.append("backup") or Path("/secret/backups/import_backup.sql")), \
                patch.object(import_project_data, "execute_sql", side_effect=lambda sql: events.append("import") or "confirmed"), \
                patch.object(import_project_data, "execute_audit_sql", side_effect=lambda sql: events.append("audit") or audit_sql.append(sql)):
            self.assertEqual(0, import_project_data.main())

        self.assertEqual(["docker", "backup", "import", "audit"], events)
        self.assertEqual(1, len(audit_sql))
        self.assertIn("'DATA_IMPORT'", audit_sql[0])
        self.assertIn("'PROJECT_DATA_IMPORT'", audit_sql[0])
        self.assertIn("'INFO'", audit_sql[0])
        self.assertIn("'SUCCESS'", audit_sql[0])
        metadata = self.extract_metadata(audit_sql[0])
        self.assertEqual("LIVE", metadata["importMode"])
        self.assertTrue(metadata["backupCreated"])
        self.assertEqual("import_backup.sql", metadata["backupFileName"])
        self.assertEqual(["users.csv", "merchants.csv", "reviews.csv", "review_aspects.csv"], metadata["sourceFiles"])
        self.assertEqual(1, metadata["usersCount"])
        self.assertEqual(1, metadata["merchantsCount"])
        self.assertEqual(1, metadata["reviewsCount"])
        self.assertEqual(1, metadata["reviewAnalysisCount"])
        self.assertEqual(7, metadata["businessHoursCount"])

    def test_import_failure_writes_failure_without_success(self) -> None:
        audit_sql: list[str] = []

        with self.patched_main_io(), \
                patch.object(sys, "argv", ["import_project_data.py", "--skip-backup"]), \
                patch.object(import_project_data, "ensure_docker"), \
                patch.object(import_project_data, "execute_sql", side_effect=RuntimeError("psql failed password=plain token=abc")), \
                patch.object(import_project_data, "execute_audit_sql", side_effect=lambda sql: audit_sql.append(sql)):
            with self.assertRaises(RuntimeError):
                import_project_data.main()

        self.assertEqual(1, len(audit_sql))
        self.assertIn("'ERROR'", audit_sql[0])
        self.assertIn("'FAILURE'", audit_sql[0])
        self.assertNotIn("'SUCCESS'", audit_sql[0])
        self.assertIn("DATA_IMPORT_RUNTIME_ERROR", audit_sql[0])
        self.assertNotIn("plain", audit_sql[0])
        self.assertNotIn("token=abc", audit_sql[0])

    def test_audit_failure_does_not_mask_original_import_error(self) -> None:
        with self.patched_main_io(), \
                patch.object(sys, "argv", ["import_project_data.py", "--skip-backup"]), \
                patch.object(import_project_data, "ensure_docker"), \
                patch.object(import_project_data, "execute_sql", side_effect=RuntimeError("original import failure")), \
                patch.object(import_project_data, "execute_audit_sql", side_effect=RuntimeError("audit down password=secret")):
            with self.assertRaisesRegex(RuntimeError, "original import failure"):
                import_project_data.main()

    def test_audit_metadata_failure_does_not_mask_original_import_error(self) -> None:
        with self.patched_main_io(), \
                patch.object(sys, "argv", ["import_project_data.py", "--skip-backup"]), \
                patch.object(import_project_data, "ensure_docker"), \
                patch.object(import_project_data, "execute_sql", side_effect=RuntimeError("original import failure")), \
                patch.object(import_project_data, "build_audit_metadata", side_effect=RuntimeError("metadata password=secret")):
            with self.assertRaisesRegex(RuntimeError, "original import failure"):
                import_project_data.main()

    def test_success_audit_construction_failure_still_returns_zero(self) -> None:
        stderr = io.StringIO()
        with self.patched_main_io(), \
                patch.object(sys, "argv", ["import_project_data.py", "--skip-backup"]), \
                patch.object(import_project_data, "ensure_docker"), \
                patch.object(import_project_data, "execute_sql", return_value="confirmed"), \
                patch.object(import_project_data, "build_audit_insert_sql", side_effect=RuntimeError("Authorization: Bearer actual-secret")), \
                contextlib.redirect_stderr(stderr):
            self.assertEqual(0, import_project_data.main())
        warning = stderr.getvalue()
        self.assertIn("DATA_IMPORT audit warning", warning)
        self.assertNotIn("actual-secret", warning)

    def test_audit_sql_sanitizes_sensitive_error_and_escapes_text(self) -> None:
        captured: list[str] = []
        context = import_project_data.ImportAuditContext()
        error = RuntimeError(
            "postgresql://user:dbpass@localhost/db token=abc "
            "Authorization: Bearer actual-secret Cookie: sessionid=cookie-secret "
            "password='quoted-secret' {\"token\":\"json-secret\"} "
            "content='REVIEW_TEXT_SECRET' "
            "quote ' double \" slash \\ newline\n"
        )

        with patch.object(import_project_data, "execute_audit_sql", side_effect=lambda sql: captured.append(sql)):
            import_project_data.record_data_import_audit_safely(
                context,
                level="ERROR",
                result="FAILURE",
                import_mode="LIVE",
                backup_created=False,
                backup_file_name=None,
                source_files=[Path("/absolute/users.csv")],
                users=self.users,
                merchants=self.merchants,
                reviews=self.reviews,
                aspects=self.aspects,
                error=error,
            )

        self.assertEqual(1, len(captured))
        audit_sql = captured[0]
        self.assertNotIn("dbpass", audit_sql)
        self.assertNotIn("token=abc", audit_sql)
        self.assertNotIn("actual-secret", audit_sql)
        self.assertNotIn("cookie-secret", audit_sql)
        self.assertNotIn("quoted-secret", audit_sql)
        self.assertNotIn("json-secret", audit_sql)
        self.assertNotIn("REVIEW_TEXT_SECRET", audit_sql)
        self.assertIn("quote ''", audit_sql)
        self.assertNotIn("\nquote", audit_sql)

    def test_outer_error_summary_sanitizes_sensitive_values(self) -> None:
        error = RuntimeError(
            "postgresql://user:dbpass@localhost/db password='quoted-secret' "
            "Authorization: Bearer actual-secret {\"token\":\"json-secret\"} "
            "content='REVIEW_TEXT_SECRET' INSERT INTO reviews VALUES ('full sql')"
        )

        summary = import_project_data.safe_error_summary(error)

        self.assertNotIn("dbpass", summary)
        self.assertNotIn("quoted-secret", summary)
        self.assertNotIn("actual-secret", summary)
        self.assertNotIn("json-secret", summary)
        self.assertNotIn("REVIEW_TEXT_SECRET", summary)

    def test_execute_sql_failure_omits_full_psql_output(self) -> None:
        result = subprocess.CompletedProcess(
            args=["psql"],
            returncode=7,
            stdout="INSERT INTO reviews VALUES ('REVIEW_TEXT_SECRET') password=plain",
            stderr="",
        )

        with patch.object(import_project_data, "run_command", return_value=result):
            with self.assertRaises(RuntimeError) as raised:
                import_project_data.execute_sql("SELECT 1")

        message = str(raised.exception)
        self.assertIn("DATABASE_IMPORT_FAILED", message)
        self.assertIn("psql return code=7", message)
        self.assertNotIn("REVIEW_TEXT_SECRET", message)
        self.assertNotIn("INSERT INTO reviews", message)
        self.assertNotIn("password=plain", message)

    def test_execute_audit_sql_failure_omits_full_stdout(self) -> None:
        result = subprocess.CompletedProcess(
            args=["psql"],
            returncode=9,
            stdout="Authorization: Bearer actual-secret INSERT INTO audit_logs ...",
            stderr="",
        )

        with patch.object(import_project_data, "run_command", return_value=result):
            with self.assertRaises(RuntimeError) as raised:
                import_project_data.execute_audit_sql("INSERT")

        message = str(raised.exception)
        self.assertIn("DATA_IMPORT_AUDIT_WRITE_FAILED", message)
        self.assertIn("psql return code=9", message)
        self.assertNotIn("actual-secret", message)
        self.assertNotIn("INSERT INTO audit_logs", message)

    def test_audit_context_allows_only_one_terminal_log(self) -> None:
        captured: list[str] = []
        context = import_project_data.ImportAuditContext()

        with patch.object(import_project_data, "execute_audit_sql", side_effect=lambda sql: captured.append(sql)):
            self.record_success(context)
            self.record_success(context)

        self.assertEqual(1, len(captured))

    def test_build_sql_preserves_offsets_and_import_order(self) -> None:
        sql = import_project_data.build_sql(
            self.users,
            self.merchants,
            self.reviews,
            self.aspects,
        )

        self.assertIn(str(1 + import_project_data.USER_OFFSET), sql)
        self.assertIn(str(1 + import_project_data.MERCHANT_OFFSET), sql)
        self.assertIn(str(1 + import_project_data.REVIEW_OFFSET), sql)
        self.assertLess(sql.index("INSERT INTO users"), sql.index("INSERT INTO merchants"))
        self.assertLess(sql.index("INSERT INTO merchants"), sql.index("INSERT INTO merchant_business_hours"))
        self.assertLess(sql.index("INSERT INTO merchant_business_hours"), sql.index("INSERT INTO reviews"))
        self.assertLess(sql.index("INSERT INTO reviews"), sql.index("INSERT INTO review_analysis"))

    def test_build_sql_contains_no_known_mojibake_marker(self) -> None:
        sql = import_project_data.build_sql(
            self.users,
            self.merchants,
            self.reviews,
            self.aspects,
        )

        self.assertNotIn("闂傚", sql)

    def test_duplicate_user_id_message_is_readable(self) -> None:
        users = [dict(self.users[0]), dict(self.users[0])]

        with self.assertRaisesRegex(ValueError, "users.csv contains duplicate IDs"):
            import_project_data.validate(
                users,
                self.merchants,
                self.reviews,
                self.aspects,
            )

    def patched_main_io(self):
        temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(temp_dir.cleanup)
        generated_dir = Path(temp_dir.name) / "generated"
        path_by_name = {
            "users.csv": Path("/safe/source/users.csv"),
            "merchants.csv": Path("/safe/source/merchants.csv"),
            "reviews.csv": Path("/safe/source/reviews.csv"),
            "review_aspects.csv": Path("/safe/source/review_aspects.csv"),
        }

        def read_csv(path: Path):
            if path.name == "users.csv":
                return self.users
            if path.name == "merchants.csv":
                return self.merchants
            if path.name == "reviews.csv":
                return self.reviews
            if path.name == "review_aspects.csv":
                return self.aspects
            raise AssertionError(path)

        stack = contextlib.ExitStack()
        stack.enter_context(patch.object(import_project_data, "GENERATED_DIR", generated_dir))
        stack.enter_context(patch.object(import_project_data, "data_file", side_effect=lambda name: path_by_name[name]))
        stack.enter_context(patch.object(import_project_data, "read_csv", side_effect=read_csv))
        self.addCleanup(stack.close)
        return stack

    def record_success(self, context) -> None:
        import_project_data.record_data_import_audit_safely(
            context,
            level="INFO",
            result="SUCCESS",
            import_mode="LIVE",
            backup_created=False,
            backup_file_name=None,
            source_files=[Path("/safe/users.csv")],
            users=self.users,
            merchants=self.merchants,
            reviews=self.reviews,
            aspects=self.aspects,
        )

    @staticmethod
    def extract_metadata(sql: str) -> dict:
        match = re.search(r"'({.*})'::jsonb", sql)
        assert match is not None, sql
        return json.loads(match.group(1).replace("''", "'"))


if __name__ == "__main__":
    unittest.main()
