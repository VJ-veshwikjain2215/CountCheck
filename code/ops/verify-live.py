"""Synthetic live release smoke test. Test workspaces are removed after verification."""
from pathlib import Path
import json, os, re, sys, uuid
import requests

root=Path(__file__).resolve().parents[1]
state=json.loads((root/'target/embarko-release-state.json').read_text())
base=state['links'].get('app')
if not base or state.get('status')!='live':raise SystemExit('Wait until Embarko reports a live app URL.')
passed=[]
accounts=[]
def check(name,condition):
    if not condition:raise AssertionError(name)
    passed.append(name);print('PASS:',name,flush=True)
def get(session,path):return session.get(base+path,timeout=40)
def token(session,path):
    response=get(session,path)
    check('page renders '+path,response.status_code==200)
    match=re.search(r'name="_csrf"[^>]*value="([^"]+)"',response.text)
    if not match:raise AssertionError('CSRF form token missing')
    return match.group(1),response
def post(session,path,data,redirects=True):return session.post(base+path,data=data,allow_redirects=redirects,timeout=40)
def create(label):
    session=requests.Session();email='release-'+uuid.uuid4().hex+'@example.invalid';password=uuid.uuid4().hex
    csrf,_=token(session,'/register')
    response=post(session,'/register',dict(_csrf=csrf,name='Release verification',business='Release check '+label,email=email,password=password))
    check('workspace registration '+label,response.status_code==200 and 'Save your recovery code' in response.text)
    csrf,_=token(session,'/login')
    response=post(session,'/login',dict(_csrf=csrf,username=email,password=password))
    check('workspace login '+label,response.status_code==200 and response.url.endswith('/app'))
    accounts.append((session,email,password))
    return session
def cleanup():
    failures=[]
    for session,email,password in accounts:
        try:
            csrf,_=token(session,'/settings')
            response=post(session,'/settings/delete',dict(_csrf=csrf,password=password),False)
            if response.status_code!=302:failures.append('Workspace cleanup did not return its expected redirect')
        except Exception as error:failures.append(type(error).__name__)
    return failures
try:
    check('public HTTPS landing page',requests.get(base,timeout=40).status_code==200)
    landing=requests.get(base,timeout=40)
    asset=re.search(r'href="([^"]*/css/app-[a-f0-9]{32}\.css)"',landing.text)
    check('content-versioned static assets',asset is not None)
    if asset:
        resource=requests.get(base+asset.group(1),timeout=40)
        check('static assets have long public caching',resource.status_code==200 and 'max-age=31536000' in resource.headers.get('Cache-Control',''))
    first=create('A');second=create('B')
    check('private pages remain no-store','no-store' in get(first,'/app').headers.get('Cache-Control',''))
    for page in ['/app','/products','/counts','/counts/new','/settings','/privacy']:
        check('authenticated page '+page,get(first,page).status_code==200)
    csrf,_=token(first,'/products')
    response=post(first,'/products',dict(_csrf=csrf,name='Release-check item',sku='RELEASE-CHECK',unit='pieces',location='Synthetic shelf',recorded='10'))
    check('product saved',response.status_code==200 and 'RELEASE-CHECK' in response.text)
    csrf,response=token(first,'/counts/new')
    product=re.search(r'<option\s+value="(\d+)"',response.text).group(1)
    key=re.search(r'name="requestId"\s+value="([^"]+)"',response.text).group(1)
    payload=dict(_csrf=csrf,requestId=key,productId=product,recorded='10',physical='8',note='Synthetic release verification')
    response=post(first,'/counts',payload,False)
    check('save returns committed detail redirect',response.status_code==303)
    detail=response.headers['Location']
    response=get(first,detail)
    check('detail shows signed difference',response.status_code==200 and '−2' in response.text)
    response=post(first,'/counts',payload,False)
    check('same-key retry returns same record',response.status_code==303 and response.headers['Location']==detail)
    check('other workspace cannot read record',get(second,detail).status_code==404)
    check('other workspace history isolated','RELEASE-CHECK' not in get(second,'/counts').text)
    check('history and filter show observation','RELEASE-CHECK' in get(first,'/counts?mismatch=true').text)
    response=get(first,'/counts/export.csv')
    check('CSV export works',response.status_code==200 and 'RELEASE-CHECK' in response.text)
    check('missing CSRF is rejected',post(first,'/counts',{'physical':'8'},False).status_code==403)
    check('CSP header present',"default-src 'self'" in get(first,'/app').headers.get('Content-Security-Policy',''))
    check('secure session cookie',any(cookie.secure for cookie in first.cookies if cookie.name=='JSESSIONID'))
finally:
    failures=cleanup()
    report=root.parent/'documents/live-verification.md'
    report.write_text('# Live CountCheck verification\n\nURL: '+base+'\n\n'+'\n'.join('- PASS: '+name for name in passed)+'\n\nSynthetic test workspace cleanup: '+('completed' if not failures else 'NEEDS ATTENTION: '+', '.join(failures))+'\n',encoding='utf-8')
    if failures:raise SystemExit('Test workspace cleanup needs attention; details saved without credentials.')
print('TOTAL:',len(passed),'live checks; synthetic workspaces removed.')
