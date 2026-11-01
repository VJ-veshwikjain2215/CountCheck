"""Run controlled database operations without putting credentials in arguments or output."""
from pathlib import Path
from urllib.parse import urlsplit, unquote
import os, secrets, subprocess, sys

root=Path(__file__).resolve().parents[1]
os.chdir(root)
action=sys.argv[1]
file=Path(sys.argv[2])
settings={}
for line in file.read_text(encoding='utf-8-sig').splitlines():
    if '=' in line and not line.lstrip().startswith('#'):
        k,v=line.split('=',1);settings[k]=v.strip().strip('"').strip("'")
env=os.environ.copy()
if 'DATABASE_URL_UNPOOLED' in settings:
    url=urlsplit(settings['DATABASE_URL_UNPOOLED'])
    host=url.hostname+':'+str(url.port or 5432)
    env.update(DB_JDBC_URL='jdbc:postgresql://'+host+url.path+'?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory',
               DB_USERNAME=unquote(url.username),DB_PASSWORD=unquote(url.password))
else:
    for key in ['DB_JDBC_URL','DB_USERNAME','DB_PASSWORD']:env[key]=settings[key]
    if action in ['migrate','runtime-role'] and 'MIGRATION_DB_JDBC_URL' in settings:
        env['DB_JDBC_URL']=settings['MIGRATION_DB_JDBC_URL']
if action=='tests':
    if file.name!='.env.validation':raise SystemExit('Tests require the isolated validation branch file')
    result=subprocess.run([str(root/('mvnw.cmd' if os.name=='nt' else 'mvnw')),'-q','test'],env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
else:
    if action=='runtime-role':env['RUNTIME_DB_PASSWORD']=secrets.token_hex(32)
    cp=(root/'target/runtime-classpath.txt').read_text().strip()
    result=subprocess.run(['java','--class-path',cp,'ops/DatabaseAdmin.java',action],env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
log=root/'target'/('database-'+action+'-'+file.name.replace('.','')+'.log')
log.write_text(result.stdout,encoding='utf-8')
for line in result.stdout.splitlines():
    if line.startswith(('PASS:','Database operation failed:','SQL state:')):print(line)
if result.returncode:
    print('Operation did not pass; details retained in the ignored local diagnostic log.')
    raise SystemExit(result.returncode)
if action=='runtime-role':
    output=Path('.env.runtime.validation' if file.name=='.env.validation' else '.env.runtime')
    runtime_url=env['DB_JDBC_URL']
    # Use a pooled endpoint for application traffic; migrations retain the direct connection.
    if 'DATABASE_URL' in settings:
        pooled=urlsplit(settings['DATABASE_URL'])
        runtime_url='jdbc:postgresql://'+pooled.hostname+':'+str(pooled.port or 5432)+pooled.path+'?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory'
    output.write_text('DB_JDBC_URL='+runtime_url+'\nDB_USERNAME=countcheck_runtime\nDB_PASSWORD='+env['RUNTIME_DB_PASSWORD']+'\n',encoding='utf-8')
    print('Runtime settings saved to an ignored local file; no credential values printed.')
if action=='tests':print('PASS: isolated hosted PostgreSQL test suite')
