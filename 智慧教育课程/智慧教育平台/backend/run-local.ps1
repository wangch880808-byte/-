$ErrorActionPreference = "Stop"
$configPath = Join-Path $PSScriptRoot ".env"
$jarPath = Join-Path $PSScriptRoot "ruoyi-admin/target/ruoyi-admin.jar"

if (-not (Test-Path -LiteralPath $configPath -PathType Leaf)) {
    throw "Missing backend/.env. Copy backend/.env.example to backend/.env and fill DB/token settings."
}
if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
    throw "Missing backend JAR. Run: mvn -B -pl ruoyi-admin -am package"
}

$allowed = @(
    "RUOYI_DB_URL", "RUOYI_DB_USERNAME", "RUOYI_DB_PASSWORD", "RUOYI_TOKEN_SECRET",
    "RUOYI_REDIS_HOST", "RUOYI_REDIS_PORT", "RUOYI_REDIS_DATABASE", "RUOYI_REDIS_PASSWORD",
    "RUOYI_PROFILE", "SERVER_PORT", "TOKEN_EXPIRE_MINUTES",
    "LOG_LEVEL_RUOYI", "LOG_LEVEL_EDUCATION", "SWAGGER_ENABLED",
    "RUOYI_JAVA_UNIXDOMAIN_TMPDIR", "ZHIJIAO_PREVIEW_ROOT"
)
$values = @{}
foreach ($line in Get-Content -LiteralPath $configPath -Encoding UTF8) {
    if ($line.Trim().Length -eq 0 -or $line.TrimStart().StartsWith("#")) { continue }
    if ($line -notmatch "^([A-Z][A-Z0-9_]*)=(.*)$") {
        throw "Invalid .env line. Expected KEY=value"
    }
    $key = $Matches[1]
    if ($key -notin $allowed) { throw "Unsupported .env key: $key" }
    $values[$key] = $Matches[2]
}

foreach ($key in @("RUOYI_DB_URL", "RUOYI_DB_USERNAME", "RUOYI_DB_PASSWORD", "RUOYI_TOKEN_SECRET")) {
    if (-not $values.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($values[$key])) {
        throw "Missing required .env key: $key"
    }
}

$previous = @{}
try {
    foreach ($key in $values.Keys) {
        $previous[$key] = [Environment]::GetEnvironmentVariable($key, "Process")
        [Environment]::SetEnvironmentVariable($key, $values[$key], "Process")
    }
    $javaArgs = @()
    if ($values.ContainsKey("RUOYI_JAVA_UNIXDOMAIN_TMPDIR") -and $values["RUOYI_JAVA_UNIXDOMAIN_TMPDIR"]) {
        $udsPath = $values["RUOYI_JAVA_UNIXDOMAIN_TMPDIR"]
        if (-not (Test-Path -LiteralPath $udsPath -PathType Container)) {
            throw "RUOYI_JAVA_UNIXDOMAIN_TMPDIR directory does not exist"
        }
        $javaArgs += "-Djdk.net.unixdomain.tmpdir=$udsPath"
    }
    if ($values.ContainsKey("ZHIJIAO_PREVIEW_ROOT") -and $values["ZHIJIAO_PREVIEW_ROOT"]) {
        $previewRoot = $values["ZHIJIAO_PREVIEW_ROOT"]
        if (-not (Test-Path -LiteralPath $previewRoot -PathType Container)) {
            throw "ZHIJIAO_PREVIEW_ROOT directory does not exist"
        }
        $javaArgs += "-Dzhijiao.resource.preview-root=$previewRoot"
    }
    $javaArgs += @("-jar", $jarPath)
    & java @javaArgs
    if ($LASTEXITCODE -ne 0) { throw "Backend exited with code $LASTEXITCODE" }
}
finally {
    foreach ($key in $values.Keys) {
        [Environment]::SetEnvironmentVariable($key, $previous[$key], "Process")
    }
}