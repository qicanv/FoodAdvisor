# Canonical schema validation

`validate_schema.sql` is destructive only in the sense that it exits with a
non-zero status when the schema is incomplete or contains business data. It
does not modify the database.

Run it only after applying `init/01_schema.sql` and `init/02_indexes.sql` to a
new, dedicated validation database:

```powershell
psql -v ON_ERROR_STOP=1 -d foodadvisor_schema_validation `
  -f scripts/postgres/validation/validate_schema.sql
```

The canonical schema deliberately targets an empty database. A second
execution of `01_schema.sql` must fail clearly; this prevents accidental
acceptance of schema drift. `02_indexes.sql` uses `IF NOT EXISTS` and is safe
to repeat after a successful schema creation.
