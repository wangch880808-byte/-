$ErrorActionPreference = 'Stop'
$configPath = Join-Path $PSScriptRoot '.env'
$jarPath = Join-Path $PSScriptRoot 'ruoyi-admin/target/ruoyi-admin.jar'

if (-not (Test-Path -LiteralPath $configPath -PathType Leaf)) {
    throw '本地配置不存在。请复制 backend/.env.example 为 backend/.env 并填写数据库连接与令牌密钥。'
}
if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
    throw '后端 JAR 不存在。请先在 backend/ 执行 mvn -B -pl ruoyi-admin -am package。'
}

$allowed = @(
    'RUOYI_DB_URL', 'RUOYI_DB_USERNAME', 'RUOYI_DB_PASSWORD', 'RUOYI_TOKEN_SECRET',
    'RUOYI_REDIS_HOST', 'RUOYI_REDIS_PORT', 'RUOYI_REDIS_DATABASE', 'RUOYI_REDIS_PASSWORD',
    'RUOYI_JAVA_HOME', 'RUOYI_JAVA_UNIXDOMAIN_TMPDIR'
)
$values = @{}
foreach ($line in Get-Content -LiteralPath $configPath -Encoding UTF8) {
    if ($line.Trim().Length -eq 0 -or $line.TrimStart().StartsWith('#')) { continue }
    if ($line -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$') {
        throw '本地配置格式不正确，应为 KEY=value，每行一项。'
    }
    $key = $Matches[1]
    if ($key -notin $allowed) { throw "本地配置含不支持的键：$key" }
    $values[$key] = $Matches[2]
}

foreach ($key in @('RUOYI_DB_URL', 'RUOYI_DB_USERNAME', 'RUOYI_DB_PASSWORD', 'RUOYI_TOKEN_SECRET')) {
    if (-not $values.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($values[$key])) {
        throw "本地配置缺少必填项：$key"
    }
}

$javaExe = 'java'
if ($values.ContainsKey('RUOYI_JAVA_HOME') -and $values['RUOYI_JAVA_HOME']) {
    $javaExe = Join-Path $values['RUOYI_JAVA_HOME'] 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $javaExe -PathType Leaf)) {
        throw "RUOYI_JAVA_HOME 指向的目录不是有效 JDK：$($values['RUOYI_JAVA_HOME'])"
    }
}

$previous = @{}
try {
    foreach ($key in $values.Keys) {
        $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
        [Environment]::SetEnvironmentVariable($key, $values[$key], 'Process')
    }
    $javaArgs = @()
    if ($values.ContainsKey('RUOYI_JAVA_UNIXDOMAIN_TMPDIR') -and $values['RUOYI_JAVA_UNIXDOMAIN_TMPDIR']) {
        $udsPath = $values['RUOYI_JAVA_UNIXDOMAIN_TMPDIR']
        if (-not (Test-Path -LiteralPath $udsPath -PathType Container)) {
            throw 'RUOYI_JAVA_UNIXDOMAIN_TMPDIR 指向的目录不存在。'
        }
        $javaArgs += "-Djdk.net.unixdomain.tmpdir=$udsPath"
    }
    $javaArgs += @('-jar', $jarPath)
    & $javaExe @javaArgs
    if ($LASTEXITCODE -ne 0) { throw "后端进程退出，代码：$LASTEXITCODE" }
}
finally {
    foreach ($key in $values.Keys) {
        [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process')
    }
}
