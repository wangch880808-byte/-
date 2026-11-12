$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
foreach ($toolName in @('java', 'mvn', 'node', 'pnpm')) {
    if (-not (Get-Command $toolName -ErrorAction SilentlyContinue)) {
        throw "Missing command: $toolName. See the running guide in the project root."
    }
}
Push-Location (Join-Path $projectRoot 'frontend')
try {
    & pnpm install --frozen-lockfile
    if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
    & pnpm run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
}
finally { Pop-Location }
Push-Location $projectRoot
try {
    & mvn -B -f backend/pom.xml -pl ruoyi-admin -am -DskipTests clean package
    if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
    Write-Host 'Build complete. Configure backend/.env, then run backend/run-local.ps1.'
}
finally { Pop-Location }
