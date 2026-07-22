"""
数据库迁移自动执行工具

遍历 scripts/postgres/migrations/ 目录下的所有 SQL 文件，
通过 psql 对已存在的数据库执行幂等迁移。
通过 _migrations 表记录已执行的文件，已执行过的自动跳过。

用法: python scripts/migrate.py
"""
import subprocess
import os
import sys
from pathlib import Path

MIGRATIONS_DIR = Path(__file__).resolve().parent / 'postgres' / 'migrations'
DB_PASS = os.environ.get('POSTGRES_PASSWORD', 'password')

# 检测 psql
try:
    subprocess.run(['psql', '--version'], capture_output=True, check=True)
    LOCAL_PSQL = True
except FileNotFoundError:
    LOCAL_PSQL = False


def preprocess_sql(sql: str, base_dir: Path) -> str:
    """展开 \\ir 引用，将引用文件内容内联"""
    import re
    result = []
    for line in sql.split('\n'):
        m = re.match(r"^\\ir\s+(.+)", line.strip())
        if m:
            ref_path = (base_dir / m.group(1).strip()).resolve()
            if ref_path.exists():
                result.append(f'-- BEGIN included: {m.group(1).strip()}')
                result.append(ref_path.read_text(encoding='utf-8'))
                result.append(f'-- END included: {m.group(1).strip()}')
            else:
                print(f'  警告: 引用文件不存在 {ref_path}')
        else:
            result.append(line)
    return '\n'.join(result)


def run_sql(sql: str) -> subprocess.CompletedProcess:
    """通过 stdin 管道执行 SQL 文本"""
    env = {**os.environ}
    if LOCAL_PSQL:
        cmd = ['psql',
               '-h', os.environ.get('POSTGRES_HOST', 'localhost'),
               '-p', os.environ.get('POSTGRES_PORT', '5433'),
               '-U', os.environ.get('POSTGRES_USER', 'postgres'),
               '-d', os.environ.get('POSTGRES_DB', 'foodadvisor'),
               '-v', 'ON_ERROR_STOP=on']
        env['PGPASSWORD'] = DB_PASS
    else:
        cmd = ['docker', 'exec', '-i', 'foodadvisor-postgres-1', 'psql',
               '-U', 'postgres', '-d', 'foodadvisor',
               '-v', 'ON_ERROR_STOP=on']
    return subprocess.run(cmd, input=sql, env=env, capture_output=True, text=True)


def migration_applied(filename: str) -> bool:
    r = run_sql(f"SELECT 1 FROM _migrations WHERE filename = '{filename}'")
    return '1' in r.stdout


def run_migrations():
    # 创建迁移记录表
    run_sql('CREATE TABLE IF NOT EXISTS _migrations '
            '(filename VARCHAR(255) PRIMARY KEY, '
            'executed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP)')

    files = sorted(MIGRATIONS_DIR.glob('*.sql'))
    if not files:
        print('未找到迁移文件')
        return

    applied = 0
    for f in files:
        if migration_applied(f.name):
            continue
        print(f'执行: {f.name} ...', end=' ', flush=True)
        sql = f.read_text(encoding='utf-8')
        sql = preprocess_sql(sql, MIGRATIONS_DIR)
        r = run_sql(sql)
        if r.returncode == 0:
            run_sql(f"INSERT INTO _migrations (filename) VALUES ('{f.name}')")
            print('OK')
            applied += 1
        else:
            print('失败')
            for line in r.stderr.strip().split('\n')[-8:]:
                if line.strip():
                    print(f'  {line.strip()}')
            sys.exit(1)

    skipped = len(files) - applied
    print(f'迁移完成: 执行 {applied} 个, 跳过 {skipped} 个')


if __name__ == '__main__':
    run_migrations()
