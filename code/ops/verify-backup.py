"""Verify a PostgreSQL backup against a new disposable database on the same server.
Uses standard PGHOST/PGPORT/PGUSER/PGPASSWORD environment variables.
No source database is modified. Never run against a customer database without an approved backup destination.
"""
import os, subprocess, uuid
from pathlib import Path
source = os.environ.get('PGDATABASE', 'countcheck')
target = 'countcheck_restore_' + uuid.uuid4().hex[:12]
root = Path(__file__).resolve().parents[1]
dump = root / 'target' / (target + '.dump')
dump.parent.mkdir(exist_ok=True)
def run(*args):
    return subprocess.run(args, check=True, capture_output=True, text=True).stdout.strip()
def counts(database):
    sql = "SELECT 'businesses',count(*) FROM businesses UNION ALL SELECT 'members',count(*) FROM members UNION ALL SELECT 'products',count(*) FROM products UNION ALL SELECT 'count_entries',count(*) FROM count_entries ORDER BY 1"
    return run('psql', '-X', '-d', database, '-tA', '-c', sql)
created = False
try:
    before = counts(source)
    run('pg_dump', '--format=custom', '--file='+str(dump), '--dbname='+source)
    run('createdb', target); created=True
    run('pg_restore', '--no-owner', '--no-privileges', '--dbname='+target, str(dump))
    after = counts(target)
    if before != after: raise RuntimeError('Restored table counts do not match. Use a quiet test database for this check.')
    migrations=run('psql','-X','-d',target,'-tA','-c','SELECT count(*) FROM flyway_schema_history WHERE success')
    print('PASS: dump restored into a disposable database; all four application table counts match; migrations:', migrations)
finally:
    if created and target.startswith('countcheck_restore_'): run('dropdb',target)
