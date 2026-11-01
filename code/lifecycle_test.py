import requests,re,uuid
from pathlib import Path
base='http://127.0.0.1:8080';passed=[]
def check(name,v):
 assert v,name
 passed.append(name);print('PASS',name)
def token(s,path):
 r=s.get(base+path);assert r.status_code==200,(path,r.status_code)
 return re.search(r'name="_csrf"[^>]*value="([^"]+)"',r.text).group(1),r
s=requests.Session();email='account-'+uuid.uuid4().hex+'@example.invalid';password=uuid.uuid4().hex
c,_=token(s,'/register');r=s.post(base+'/register',data=dict(_csrf=c,name='Account QA',business='Lifecycle QA',email=email,password=password));recovery=re.search(r'<p class="notice">([^<]+)',r.text).group(1)
check('recovery code provided once',len(recovery)==72)
c,_=token(s,'/login');s.post(base+'/login',data=dict(_csrf=c,username=email,password=password))
c,_=token(s,'/products');s.post(base+'/products',data=dict(_csrf=c,name='Pagination product',sku='PAGE',unit='pieces',location='Shelf',recorded='5'))
c,r=token(s,'/counts/new');pid=re.search(r'<option\s+value="(\d+)"',r.text).group(1)
for i in range(22):
 r=s.post(base+'/counts',data=dict(_csrf=c,requestId=str(uuid.uuid4()),productId=pid,recorded='5',physical='5' if i%2 else '3'),allow_redirects=False);assert r.status_code==303,r.status_code
r=s.get(base+'/counts');ids=re.findall(r'aria-label="View count"',r.text);check('history page capped at 20',len(ids)==20)
link=re.search(r'href="([^"]+)"[^>]*>\s*Next page',r.text).group(1).replace('&amp;','&');r=s.get(base+link);check('next page has remaining 2 rows',len(re.findall('aria-label="View count"',r.text))==2)
r=s.get(base+'/counts?mismatch=true');check('mismatch filtering selects 11 differences',len(re.findall('aria-label="View count"',r.text))==11)
fresh=requests.Session();c,_=token(fresh,'/recover');newpassword=uuid.uuid4().hex;r=fresh.post(base+'/recover',data=dict(_csrf=c,email=email,code=recovery,password=newpassword));check('account recovery succeeds','Save your recovery code' in r.text)
check('recovery revokes previous session',s.get(base+'/app',allow_redirects=False).status_code==302)
c,_=token(fresh,'/recover');r=fresh.post(base+'/recover',data=dict(_csrf=c,email=email,code=recovery,password=newpassword));check('old recovery code cannot be reused','Recovery details are invalid' in r.text)
c,_=token(fresh,'/login');r=fresh.post(base+'/login',data=dict(_csrf=c,username=email,password=newpassword));check('new password signs in',r.url.endswith('/app'))
c,_=token(fresh,'/settings');r=fresh.post(base+'/settings/delete',data=dict(_csrf=c,password='wrong'));check('delete rejects wrong password',r.status_code==400)
c,_=token(fresh,'/settings');r=fresh.post(base+'/settings/delete',data=dict(_csrf=c,password=newpassword),allow_redirects=False);check('password-confirmed deletion succeeds',r.status_code==302)
check('deleted workspace session is revoked',fresh.get(base+'/app',allow_redirects=False).status_code==302)
p=Path('../documents/verification-results.md');p.write_text(p.read_text(encoding='utf-8')+'\n## Account and pagination checks\n\n'+'\n'.join('- PASS: '+n for n in passed)+'\n',encoding='utf-8')
print('TOTAL',len(passed))
