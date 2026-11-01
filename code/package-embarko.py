"""Package only application sources/configuration; never local data or credentials."""
from pathlib import Path
import tarfile
root = Path(__file__).resolve().parent
out = root / 'target/countcheck-embarko.tar.gz'
out.parent.mkdir(exist_ok=True)
files = [root / p for p in ['pom.xml', 'railpack.json', 'package.json', 'mvnw', 'mvnw.cmd']]
files += list((root / 'src/main').rglob('*'))
files += list((root / '.mvn').rglob('*'))
if (root / 'embarko.json').exists(): files.append(root / 'embarko.json')
with tarfile.open(out, 'w:gz') as archive:
    for file in sorted(files):
        if file.is_file():
            info=archive.gettarinfo(str(file),arcname=file.relative_to(root).as_posix())
            if file.name=='mvnw':info.mode=0o755
            with file.open('rb') as content:archive.addfile(info,content)
print('Created source-only archive:', out.name)
