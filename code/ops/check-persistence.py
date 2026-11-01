"""A disposable live fixture verifies database persistence across redeployment."""
from pathlib import Path
import json,re,sys,uuid
import requests
root=Path(__file__).resolve().parents[1]
state=json.loads((root/'target/embarko-release-state.json').read_text())
base=state['links']['app']
fixture=root/'target/persistence-fixture.json'
session=requests.Session()
def form(path):
    response=session.get(base+path,timeout=40)
    if response.status_code!=200:raise RuntimeError('Form unavailable')
    match=re.search(r'name="_csrf"[^>]*value="([^"]+)"',response.text)
    if not match:raise RuntimeError('Missing CSRF token')
    return match.group(1),response.text
def post(path,data,redirect=True):
    return session.post(base+path,data=data,timeout=40,allow_redirects=redirect)
if sys.argv[1]=='begin':
    if fixture.exists():raise SystemExit('A persistence fixture already exists; verify it before creating another.')
    data={'email':'persistence-'+uuid.uuid4().hex+'@example.invalid','password':uuid.uuid4().hex}
    csrf,_=form('/register')
    response=post('/register',dict(_csrf=csrf,name='Persistence verification',business='Release persistence check',**data))
    if response.status_code!=200 or 'Save your recovery code' not in response.text:raise RuntimeError('Could not create fixture account')
    fixture.write_text(json.dumps(data))
else:data=json.loads(fixture.read_text())
csrf,_=form('/login')
response=post('/login',dict(_csrf=csrf,username=data['email'],password=data['password']))
if not response.url.endswith('/app'):raise RuntimeError('Fixture login failed')
if sys.argv[1]=='begin':
    csrf,_=form('/products')
    response=post('/products',dict(_csrf=csrf,name='Persistence-check item',sku='PERSISTENCE-CHECK',unit='pieces',location='Synthetic shelf',recorded='20'))
    if 'PERSISTENCE-CHECK' not in response.text:raise RuntimeError('Fixture product failed')
    csrf,html=form('/counts/new')
    product=re.search(r'<option\s+value="(\d+)"',html).group(1)
    key=re.search(r'name="requestId"\s+value="([^"]+)"',html).group(1)
    response=post('/counts',dict(_csrf=csrf,requestId=key,productId=product,recorded='20',physical='21',note='Synthetic persistence verification'),False)
    if response.status_code!=303:raise RuntimeError('Fixture count failed')
    data['detail']=response.headers['Location'];fixture.write_text(json.dumps(data))
    print('PASS: synthetic persistence fixture saved before redeploy; credentials remain local and ignored.')
else:
    response=session.get(base+data['detail'],timeout=40)
    if response.status_code!=200 or 'PERSISTENCE-CHECK' not in response.text or '+1' not in response.text:raise RuntimeError('Saved record did not survive redeploy')
    csrf,_=form('/settings')
    response=post('/settings/delete',dict(_csrf=csrf,password=data['password']),False)
    if response.status_code!=302:raise RuntimeError('Fixture cleanup failed')
    fixture.unlink()
    with (root.parent/'documents/live-verification.md').open('a',encoding='utf-8') as report:
        report.write('\n- PASS: saved product/count remained available after redeployment and a fresh sign-in. Synthetic persistence workspace removed.\n')
    print('PASS: saved product/count survived redeploy; synthetic workspace and local fixture removed.')
