from pathlib import Path
import os, subprocess
root=Path(__file__).resolve().parents[1]
os.chdir(root)
env=os.environ.copy()
for line in (root/'.env.runtime.validation').read_text().splitlines():
    if '=' in line and not line.startswith('#'):
        k,v=line.split('=',1);env[k]=v
env.update(SPRING_FLYWAY_ENABLED='false',SERVER_ADDRESS='127.0.0.1',PORT='8080')
with (root/'target/validation-server.log').open('w',encoding='utf-8') as log:
    process=subprocess.Popen(['java','-Xmx96m','-XX:+UseSerialGC','-jar','target/countcheck-1.0.0.jar'],env=env,stdout=log,stderr=subprocess.STDOUT)
    (root/'target/validation-server.pid').write_text(str(process.pid))
    print('Validation server process:',process.pid,flush=True)
    raise SystemExit(process.wait())
