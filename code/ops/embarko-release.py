"""Embarko release commands. Credentials remain in memory and server-side settings."""
from pathlib import Path
import json, os, re, subprocess, sys
import requests

root=Path(__file__).resolve().parents[1]
os.chdir(root)
api='https://ship.embarko.ai'
token=os.environ.get('DEPLOY_TOKEN')
if not token:
    raw=(Path.home()/'.embarko/credentials').read_text().strip()
    if raw.startswith('{'):
        obj=json.loads(raw);token=obj.get('token') or obj.get('deployToken')
    else:
        token=raw.removeprefix('DEPLOY_TOKEN=').strip().strip('"').strip("'")
if not token or any(c.isspace() for c in token):raise SystemExit('A valid Embarko deployment credential is required.')
session=requests.Session()
session.headers.update({'Authorization':'Bearer '+token,'X-Agent-Name':'codex'})
state_file=root/'target/embarko-release-state.json'
action=sys.argv[1]
def safe_reply(response):
    try:data=response.json()
    except ValueError:raise SystemExit('Embarko returned a non-JSON response; HTTP '+str(response.status_code))
    if response.status_code>=400:
        print('Embarko request failed; HTTP',response.status_code,'code',data.get('code','unspecified'))
        raise SystemExit(1)
    return data
def save(data):
    app=data.get('app',{})
    if app.get('id'):
        (root/'embarko.json').write_text(json.dumps({'appId':app['id']},indent=2)+'\n')
    state={k:data[k] for k in ['status','action','links'] if k in data}
    state['app']={k:app[k] for k in ['id','name','claimed','expiresAt'] if k in app}
    state_file.write_text(json.dumps(state,indent=2)+'\n')
    print(json.dumps(state))
if action=='deploy':
    if state_file.exists():name=json.loads(state_file.read_text())['app']['name']
    else:
        name=None
        for candidate in ['countcheck','veshwik-countcheck','countcheck-veshwik-2026']:
            result=session.get(api+'/api/apps/'+candidate,timeout=30)
            if result.status_code==404:name=candidate;break
            if result.status_code!=200:safe_reply(result)
        if not name:raise SystemExit('No unused suggested app name is available.')
    subprocess.run([sys.executable,'package-embarko.py'],check=True)
    with (root/'target/countcheck-embarko.tar.gz').open('rb') as archive:
        response=session.post(api+'/apps',headers={'X-App-Name':name,'X-App-Type':'inventory-counting'},files={'source':('countcheck.tar.gz',archive,'application/gzip')},timeout=180)
    save(safe_reply(response))
elif action=='settings':
    state=json.loads(state_file.read_text());name=state['app']['name']
    settings={}
    for line in (root/'.env.runtime').read_text().splitlines():
        if '=' in line and not line.startswith('#'):
            key,value=line.split('=',1);settings[key]=value
    values=[{'key':key,'value':settings[key]} for key in ['DB_JDBC_URL','DB_USERNAME','DB_PASSWORD']]
    values.extend([{'key':'SPRING_PROFILES_ACTIVE','value':'production'},{'key':'RUN_MIGRATIONS','value':'false'}])
    response=session.put(api+'/api/apps/'+name+'/env-vars',json={'vars':values,'apply':False},timeout=30)
    # An app without a completed deployment can still save its server settings.
    if response.status_code==422 and response.json().get('code')=='no_current_deployment':
        print('Server settings saved; deploy will apply them.')
    else:safe_reply(response);print('Server-side database settings saved. Values were not printed.')
elif action=='status':
    state=json.loads(state_file.read_text());name=state['app']['name']
    save(safe_reply(session.get(api+'/apps/'+name+'/status',timeout=30)))
elif action=='logs':
    state=json.loads(state_file.read_text());name=state['app']['name']
    data=safe_reply(session.get(api+'/apps/'+name+'/logs',params={'stream':'stderr'},timeout=30))
    logs=data.get('logs','')
    # The application uses URL-without-password + separate properties. Additional redaction is defensive.
    logs=re.sub(r'(?i)(password|token|secret)([=:]\s*)[^\s,;]+',r'\1\2[REDACTED]',logs)
    (root/'target/embarko-diagnostics.log').write_text(logs,encoding='utf-8')
    print(logs[-14000:])
else:raise SystemExit('Unknown release command')
