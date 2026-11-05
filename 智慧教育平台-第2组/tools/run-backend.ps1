param(
  [int]$Port = 8080,
  [string]$LogPath = ""
)
$ErrorActionPreference = 'Stop'

# 端口被占用时直接给出可操作提示，不要让它抛一堆 Tomcat 异常
$busy = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($busy) {
  $owner = ($busy | Select-Object -First 1).OwningProcess
  Write-Host ""
  Write-Host "端口 $Port 已被进程 $owner 占用。" -ForegroundColor Yellow
  Write-Host "如果是之前启动的旧后端，先结束它：" -ForegroundColor Yellow
  Write-Host "    taskkill /PID $owner /F" -ForegroundColor Yellow
  Write-Host "或者用管理员 PowerShell，或在任务管理器里结束 java.exe。" -ForegroundColor Yellow
  Write-Host ""
  Write-Host "也可以换端口启动： tools\run-backend.ps1 -Port 8081" -ForegroundColor Yellow
  exit 1
}

$build = Join-Path $env:TEMP 'zhijiao-build'
if (-not (Test-Path (Join-Path $build 'classpath.txt'))) {
  throw '没有找到编译产物，请先运行 tools\build-backend.ps1'
}
$cp = [System.IO.File]::ReadAllText((Join-Path $build 'classpath.txt')).Trim()
if (-not $LogPath) { $LogPath = Join-Path $env:TEMP 'zhijiao-logs' }
New-Item -ItemType Directory -Force -Path $LogPath | Out-Null

$full = "$build\classes;$build\resources;$cp"
Write-Host ""
Write-Host "后端启动中，约 10 秒后可用： http://127.0.0.1:$Port/login.html" -ForegroundColor Green
Write-Host "日志目录： $LogPath"
Write-Host "按 Ctrl+C 停止" -ForegroundColor DarkGray
Write-Host ""
& java "-Dfile.encoding=UTF-8" "-Duser.timezone=Asia/Shanghai" "-Dserver.port=$Port" "-Druoyi.log.path=$LogPath" -cp $full com.ruoyi.RuoYiApplication
