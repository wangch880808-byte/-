# build-backend.ps1
# 本机没有可用的 Maven 插件仓库（离线），改用 javac + 本地 .m2 依赖直接编译。
# 用法： powershell -NoProfile -ExecutionPolicy Bypass -File tools\build-backend.ps1
param(
  [string]$Repo = (Join-Path $env:USERPROFILE '.m2\repository')
)
$ErrorActionPreference = 'Stop'

$platform = $null
$dir = $PSScriptRoot
for ($i = 0; $i -lt 4 -and $dir; $i++) {
  if (Test-Path (Join-Path $dir 'backend\pom.xml')) { $platform = $dir; break }
  $dir = Split-Path $dir -Parent
}
if (-not $platform) { throw 'backend/pom.xml not found (请把 tools 放在 智慧教育平台 目录下)' }
$backend = Join-Path $platform 'backend'

$out     = Join-Path $env:TEMP 'zhijiao-build'
$classes = Join-Path $out 'classes'
$res     = Join-Path $out 'resources'

if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Force -Path $classes, $res | Out-Null

# ---------- 1. 收集 jar，剔除 Boot2/Boot4 等并存版本，按 artifact 去重取最高版本 ----------
$exclude = @(
  '/spring-boot-devtools/',
  '/springfox/',
  '/org/springframework/boot/[^/]+/2\.',
  '/org/springframework/boot/[^/]+/4\.',
  '/org/springframework/[^/]+/5\.',
  '/org/springframework/[^/]+/7\.',
  '/org/apache/tomcat/embed/[^/]+/9\.',
  '/org/apache/tomcat/embed/[^/]+/11\.',
  '/org/springframework/data/[^/]+/2\.',
  '/org/springframework/data/[^/]+/4\.',
  '/org/springframework/security/[^/]+/5\.',
  '/org/springframework/security/[^/]+/7\.',
  '/org/springframework/session/[^/]+/2\.',
  '/pagehelper[^/]*/4\.',
  '/mybatis-spring-boot[^/]*/4\.',
  '/druid-spring-boot-4',
  '/org/springdoc/[^/]+/3\.',
  '/io/swagger/[^/]+/1\.',
  '/io/swagger/swagger-core/[^/]+/2\.1',
  '/io/swagger/swagger-models/[^/]+/2\.1',
  '/io/swagger/swagger-annotations/[^/]+/2\.1',
  '/spring-boot-maven-plugin/',
  '/com/fasterxml/jackson/[^/]+/2\.9'
)
$allJars = Get-ChildItem $Repo -Recurse -Filter '*.jar' -File |
  Where-Object { $_.Name -notmatch '-(sources|javadoc|tests)\.jar$' }

function Compare-Ver($a, $b) {
  $pa = ($a -split '[.\-]') | ForEach-Object { if ($_ -match '^\d+$') { [int]$_ } else { 0 } }
  $pb = ($b -split '[.\-]') | ForEach-Object { if ($_ -match '^\d+$') { [int]$_ } else { 0 } }
  for ($i = 0; $i -lt [Math]::Max($pa.Count, $pb.Count); $i++) {
    $x = if ($i -lt $pa.Count) { $pa[$i] } else { 0 }
    $y = if ($i -lt $pb.Count) { $pb[$i] } else { 0 }
    if ($x -ne $y) { return $x - $y }
  }
  return 0
}

$groups = @{}
foreach ($jar in $allJars) {
  $rel = '/' + $jar.FullName.Substring($Repo.Length + 1).Replace('\', '/')
  $skip = $false
  foreach ($p in $exclude) { if ($rel -match $p) { $skip = $true; break } }
  if ($skip) { continue }
  $version  = $jar.Directory.Name
  $artifact = $jar.Name.Substring(0, $jar.Name.Length - $version.Length - 5)
  $key = $jar.Directory.Parent.FullName + '|' + $artifact
  if (-not $groups.ContainsKey($key)) { $groups[$key] = @() }
  $groups[$key] += [pscustomobject]@{ Jar = $jar; Version = $version }
}

$cpJars = @()
foreach ($key in $groups.Keys) {
  $best = $groups[$key][0]
  foreach ($c in $groups[$key]) { if ((Compare-Ver $c.Version $best.Version) -gt 0) { $best = $c } }
  $cpJars += $best.Jar.FullName
}
$cpJars = $cpJars | Sort-Object

# ---------- 2. 收集源码 ----------
$modules = 'ruoyi-common','ruoyi-system','ruoyi-framework','ruoyi-quartz','ruoyi-generator','ruoyi-education','ruoyi-admin'
$sources = @()
foreach ($m in $modules) {
  $s = Join-Path $backend "$m\src\main\java"
  if (-not (Test-Path $s)) { continue }
  $sources += Get-ChildItem $s -Recurse -Filter '*.java' -File |
    ForEach-Object { $_.FullName.Substring($backend.Length + 1).Replace('\', '/') }
}
[System.IO.File]::WriteAllLines((Join-Path $out 'sources.txt'), $sources, [System.Text.Encoding]::ASCII)

# ---------- 3. 资源 ----------
foreach ($m in $modules) {
  $r = Join-Path $backend "$m\src\main\resources"
  if (Test-Path $r) { Copy-Item "$r\*" $res -Recurse -Force }
}

# 前端静态页由后端同源提供（/login.html、/admin.html、/edu-api.js 等）
$frontend = $backend.Substring(0, $backend.LastIndexOf('\')) + '\frontend'
if (Test-Path $frontend) {
  $static = Join-Path $res 'static'
  New-Item -ItemType Directory -Force -Path $static | Out-Null
  Copy-Item "$frontend\*" $static -Recurse -Force
  Write-Host "static pages copied from $frontend"
}

# ---------- 4. 编译（切到 backend 目录，argfile 里只放相对路径，避开中文路径） ----------
$cp = ($cpJars -join ';')
Set-Content (Join-Path $out 'classpath.txt') $cp -Encoding UTF8
Write-Host "jars: $($cpJars.Count)  sources: $($sources.Count)"
Push-Location $backend
$prev = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
  & javac --release 17 -encoding UTF-8 -parameters -proc:none -nowarn -d $classes -cp $cp "@$out\sources.txt" 2>&1 |
    Tee-Object -FilePath (Join-Path $out 'javac.log') | Where-Object { $_ -match 'error|错误' }
  $code = $LASTEXITCODE
} finally {
  $ErrorActionPreference = $prev
  Pop-Location
}
if ($code -ne 0) { Write-Host "COMPILE FAILED ($code)"; exit 1 }
Write-Host "COMPILE OK -> $classes"
