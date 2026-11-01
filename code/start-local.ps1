$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$data = Join-Path $PSScriptRoot 'target/local-postgres'
New-Item -ItemType Directory -Force -Path (Join-Path $PSScriptRoot 'target') | Out-Null
if (-not (Test-Path -LiteralPath (Join-Path $data 'PG_VERSION'))) {
    initdb -D $data -U countcheck -A trust --encoding=UTF8 --locale=C
    if ($LASTEXITCODE -ne 0) { throw 'Database initialization failed' }
}
pg_ctl -D $data status | Out-Null
if ($LASTEXITCODE -ne 0) {
    pg_ctl -D $data -l (Join-Path $PSScriptRoot 'target/postgres.log') -o '-p 55432 -h 127.0.0.1' -w start
    if ($LASTEXITCODE -ne 0) { throw 'Database startup failed' }
}
$exists = psql -h 127.0.0.1 -p 55432 -U countcheck -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='countcheck'"
if ($exists -ne '1') { createdb -h 127.0.0.1 -p 55432 -U countcheck countcheck }
$env:DB_JDBC_URL = 'jdbc:postgresql://127.0.0.1:55432/countcheck'
$env:DB_USERNAME = 'countcheck'
$env:DB_PASSWORD = ''
./mvnw.cmd spring-boot:run
