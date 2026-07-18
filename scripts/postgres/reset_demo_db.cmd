@echo off
setlocal EnableExtensions
chcp 65001 >nul
title FoodAdvisor Demo Database Reset

rem ============================================================
rem FoodAdvisor: clear and rebuild the local demo database.
rem
rem Usage:
rem   1. Stop the local Spring Boot backend first.
rem   2. Run this file from anywhere.
rem   3. Type RESET when prompted.
rem
rem Optional unattended mode:
rem   reset_demo_db.cmd --yes
rem ============================================================

pushd "%~dp0..\.." >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Cannot locate the FoodAdvisor project root.
    exit /b 1
)

set "POSTGRES_SERVICE=postgres"
set "DB_USER=postgres"
set "DB_NAME=foodadvisor"
set "TMP_DIR=/tmp/foodadvisor-db-reset"

echo.
echo ============================================================
echo   FoodAdvisor Demo Database Reset
echo ============================================================
echo.
echo This operation will permanently delete the current database:
echo   %DB_NAME%
echo.
echo It will then recreate:
echo   - canonical schema
echo   - indexes
echo   - fictional demo seed data
echo.
echo Please stop the Spring Boot backend before continuing.
echo.

if /I "%~1"=="--yes" goto confirmed

set /p "CONFIRM=Type RESET to continue: "
if /I not "%CONFIRM%"=="RESET" (
    echo.
    echo [CANCELLED] No database changes were made.
    popd
    exit /b 0
)

:confirmed
echo.
echo [1/8] Checking Docker...
where docker >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker was not found in PATH.
    goto failed
)

docker compose version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker Compose is unavailable.
    goto failed
)

echo [2/8] Starting PostgreSQL...
docker compose up -d %POSTGRES_SERVICE%
if errorlevel 1 goto failed

echo [3/8] Waiting for PostgreSQL to become ready...
for /L %%i in (1,1,30) do (
    docker compose exec -T %POSTGRES_SERVICE% pg_isready -U %DB_USER% -d postgres >nul 2>&1 && goto postgres_ready
    timeout /t 1 /nobreak >nul
)

echo [ERROR] PostgreSQL did not become ready within 30 seconds.
goto failed

:postgres_ready
echo [4/8] Copying database scripts into the container...
docker compose exec -T %POSTGRES_SERVICE% sh -c "rm -rf %TMP_DIR% && mkdir -p %TMP_DIR%"
if errorlevel 1 goto failed

docker compose cp "scripts/postgres/." %POSTGRES_SERVICE%:%TMP_DIR%
if errorlevel 1 goto failed

echo [5/8] Dropping and recreating database "%DB_NAME%"...
docker compose exec -T %POSTGRES_SERVICE% psql -U %DB_USER% -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS %DB_NAME% WITH (FORCE);"
if errorlevel 1 goto failed

docker compose exec -T %POSTGRES_SERVICE% psql -U %DB_USER% -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE %DB_NAME%;"
if errorlevel 1 goto failed

echo [6/8] Creating canonical schema and indexes...
docker compose exec -T %POSTGRES_SERVICE% psql -U %DB_USER% -d %DB_NAME% -v ON_ERROR_STOP=1 -f %TMP_DIR%/init/01_schema.sql
if errorlevel 1 goto failed

docker compose exec -T %POSTGRES_SERVICE% psql -U %DB_USER% -d %DB_NAME% -v ON_ERROR_STOP=1 -f %TMP_DIR%/init/02_indexes.sql
if errorlevel 1 goto failed

docker compose exec -T %POSTGRES_SERVICE% psql -U %DB_USER% -d %DB_NAME% -v ON_ERROR_STOP=1 -f %TMP_DIR%/validation/validate_schema.sql
if errorlevel 1 goto failed

echo [7/8] Importing fictional demo data...
docker compose exec -T %POSTGRES_SERVICE% psql -U %DB_USER% -d %DB_NAME% -v ON_ERROR_STOP=1 -f %TMP_DIR%/seed/demo/00_demo_seed.sql
if errorlevel 1 goto failed

docker compose exec -T %POSTGRES_SERVICE% psql -U %DB_USER% -d %DB_NAME% -v ON_ERROR_STOP=1 -f %TMP_DIR%/validation/validate_demo_seed.sql
if errorlevel 1 goto failed

echo [8/8] Running final summary check...
docker compose exec -T %POSTGRES_SERVICE% psql -U %DB_USER% -d %DB_NAME% -v ON_ERROR_STOP=1 -c "SELECT (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE') AS tables, (SELECT COUNT(*) FROM pg_indexes WHERE schemaname='public') AS indexes, (SELECT COUNT(*) FROM users) AS users, (SELECT COUNT(*) FROM merchants) AS merchants, (SELECT COUNT(*) FROM dishes) AS dishes, (SELECT COUNT(*) FROM reviews) AS reviews;"
if errorlevel 1 goto failed

docker compose exec -T %POSTGRES_SERVICE% sh -c "rm -rf %TMP_DIR%" >nul 2>&1

echo.
echo ============================================================
echo [SUCCESS] FoodAdvisor demo database was rebuilt successfully.
echo ============================================================
echo.
echo Demo accounts:
echo   Admin:    demo_admin
echo   Diner:    demo_diner_1
echo   Merchant: demo_merchant_1
echo   Password: Demo@123456
echo.
popd
exit /b 0

:failed
echo.
echo ============================================================
echo [FAILED] Database reset stopped because a command failed.
echo Check the error output above. Do not rerun individual SQL
echo files manually until the failure cause is identified.
echo ============================================================
echo.
popd
exit /b 1
