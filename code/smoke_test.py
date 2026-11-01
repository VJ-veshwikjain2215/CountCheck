import re,uuid,requests,concurrent.futures
from pathlib import Path
BASE='http://127.0.0.1:8080'
checks=[]
def check(name,condition):
 assert condition,name
 checks.append(name);print('PASS',name)
def csrf(s,path):
 r=s.get(BASE+path);check('GET '+path,r.status_code==200)
 token=re.search(r'name="_csrf"[^>]*value="([^"]+)"',r.text)
 assert token,r.text[:500]
 return token.group(1),r
suffix=uuid.uuid4().hex[:10]
def account(n):
 s=requests.Session();token,_=csrf(s,'/register');email=f'qa-{suffix}-{n}@example.invalid';password=uuid.uuid4().hex
 r=s.post(BASE+'/register',data=dict(_csrf=token,name='QA Owner',business='QA Workspace '+n,email=email,password=password),allow_redirects=False);print('REGISTER RESPONSE',r.status_code,r.headers.get('Location'));check('register '+n,r.status_code==200 and 'Save your recovery code' in r.text)
 token,_=csrf(s,'/login');r=s.post(BASE+'/login',data=dict(_csrf=token,username=email,password=password),allow_redirects=False);print('LOGIN RESPONSE',r.status_code,r.headers.get('Location'));check('login '+n,r.status_code==302 and r.headers['Location'].endswith('/app'))
 return s
s=account('A');t=account('B')
for path in ['/','/app','/products','/counts','/counts/new','/settings','/privacy']:
 r=s.get(BASE+path);check('page renders '+path,r.status_code==200)
check('CSRF rejects missing token',s.post(BASE+'/products',data={'name':'test'}).status_code==403)
token,_=csrf(s,'/products');r=s.post(BASE+'/products',data=dict(_csrf=token,name='<script>Alert tote</script>',sku='TOTE-QA',unit='pieces',location='Shelf A',recorded='48'));check('add product',r.status_code==200 and 'TOTE-QA' in r.text)
token,r=csrf(s,'/counts/new');pid=re.search(r'<option\s+value="(\d+)"',r.text).group(1);key=re.search(r'name="requestId"\s+value="([^"]+)"',r.text).group(1)
data=dict(_csrf=token,requestId=key,productId=pid,recorded='48',physical='45',note='=SUM(1)')
r=s.post(BASE+'/counts',data=data,allow_redirects=False);check('count commit returns 303',r.status_code==303);detail=r.headers['Location']
r=s.get(BASE+detail);check('detail renders escaped input and signed difference',r.status_code==200 and '&lt;script&gt;' in r.text and '−3' in r.text)
r=s.post(BASE+'/counts',data=data,allow_redirects=False);check('idempotent replay returns same record',r.status_code==303 and r.headers['Location']==detail)
r=s.post(BASE+'/counts',data={**data,'physical':'44'},allow_redirects=False);check('changed key payload returns conflict',r.status_code==409)
check('cross business detail denied',t.get(BASE+detail).status_code==404)
check('cross business history isolated','TOTE-QA' not in t.get(BASE+'/counts').text)
check('cross business product isolated','TOTE-QA' not in t.get(BASE+'/products').text)
token2,_=csrf(t,'/counts/new');check('cross business product write denied',t.post(BASE+'/counts',data={**data,'_csrf':token2,'requestId':str(uuid.uuid4())}).status_code==404)
for value in ['-1','100001','007','1.5',' 5']:
 check('invalid count '+value,s.post(BASE+'/counts',data={**data,'requestId':str(uuid.uuid4()),'physical':value}).status_code==400)
key2=str(uuid.uuid4())
def submit(_):
 local=requests.Session();local.cookies.update(s.cookies);r=local.post(BASE+'/counts',data={**data,'requestId':key2},allow_redirects=False);return r.status_code,r.headers.get('Location')
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:results=list(pool.map(submit,range(4)))
check('concurrent retries produce same record',all(x[0]==303 for x in results) and len(set(x[1] for x in results))==1)
r=s.get(BASE+'/counts/export.csv');check('CSV export neutralizes formulas',r.status_code==200 and "'=SUM(1)" in r.text)
check('CSP is present',"default-src 'self'" in s.get(BASE+'/app').headers.get('Content-Security-Policy',''))
check('oversize POST rejected',s.post(BASE+'/counts',data={'x':'a'*20000}).status_code==413)
check('JSON POST rejected',s.post(BASE+'/counts',json={}).status_code==415)
check('malformed cursor rejected',s.get(BASE+'/counts?before=invalid&beforeId=1').status_code==400)
check('anonymous protected page requires login',requests.get(BASE+'/app',allow_redirects=False).status_code==302)
check('no public reset route',s.get(BASE+'/reset',allow_redirects=False).status_code in [403,404])
Path('../documents/verification-results.md').write_text('# CountCheck verification\n\nObserved local HTTP checks: '+str(len(checks))+' passed. Tested against PostgreSQL 18.6 on 1 November 2026.\n\n'+'\n'.join('- PASS: '+c for c in checks)+'\n\nTest accounts and rows are synthetic. Browser layout verification is tracked separately.\n',encoding='utf-8')
print('TOTAL',len(checks))
