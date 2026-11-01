$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$config = Join-Path $PSScriptRoot '.env.local'
if (-not (Test-Path -LiteralPath $config)) {
    throw 'Copy .env.example to .env.local and add your hosted PostgreSQL connection details.'
}
$settings = @{}
foreach ($line in Get-Content -LiteralPath $config) {
    if ($line -match '^\s*(DB_JDBC_URL|DB_USERNAME|DB_PASSWORD)=(.*)$') {
        $settings[$matches[1]] = $matches[2]
    }
}
foreach ($name in @('DB_JDBC_URL', 'DB_USERNAME', 'DB_PASSWORD')) {
    if (-not $settings.ContainsKey($name)) { throw "Missing setting: $name" }
}
$previous = @{}
try {
    foreach ($name in $settings.Keys) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $settings[$name], 'Process')
    }
    mvn -q dependency:copy '-Dartifact=org.postgresql:postgresql:42.7.10' '-DoutputDirectory=target/tools'
    if ($LASTEXITCODE -ne 0) { throw 'Could not prepare the JDBC driver.' }
    java --class-path target/tools/postgresql-42.7.10.jar ops/DatabasePreflight.java
    if ($LASTEXITCODE -ne 0) { throw 'Hosted database preflight did not pass.' }
} finally {
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
    $settings.Clear()
}
