param(
    [string]$Maven = 'mvn',
    [string]$LocalRepository = '',
    [switch]$InstallDependencies,
    [switch]$SkipBackend
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
function Invoke-Checked([string]$Command, [string[]]$CommandArgs) {
    & $Command @CommandArgs
    if ($LASTEXITCODE -ne 0) { throw "检查失败：$Command，退出代码 $LASTEXITCODE" }
}
Push-Location $projectRoot
try {
    Invoke-Checked 'node' @('--test','tests/frontend-regressions.test.cjs','tests/context-permissions.test.cjs','tests/shell-integration.test.cjs','tests/vue-security.test.mjs')
    Push-Location 'frontend/vue'
    try {
        if ($InstallDependencies) { Invoke-Checked 'pnpm' @('install','--frozen-lockfile') }
        Invoke-Checked 'pnpm' @('build')
    } finally { Pop-Location }
    if (-not $SkipBackend) {
        Push-Location 'backend'
        try {
            $mavenArguments = @('-B','-pl','ruoyi-admin','-am','package')
            if ($LocalRepository) { $mavenArguments = @("-Dmaven.repo.local=$LocalRepository") + $mavenArguments }
            Invoke-Checked $Maven $mavenArguments
        } finally { Pop-Location }
    }
} finally { Pop-Location }
Write-Output '第一组构建和回归检查通过。真实业务联调另按验收清单执行。'
