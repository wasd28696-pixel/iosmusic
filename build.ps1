param(
    [string]$Jdk,
    [string]$McHome,
    [string]$McVersion = "1.21.1",
    [string]$NeoForgeVersion = "21.1.233",
    [string]$Instance,
    [switch]$Deploy
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $root "build-common.ps1")

# 本机路径覆盖（可选，加入 .gitignore，不会提交）
$localConfig = Join-Path $root "build.local.ps1"
if (Test-Path $localConfig) { . $localConfig }

$Jdk = Resolve-Jdk21 -Requested $Jdk
$McHome = Resolve-McHome -Requested $McHome -RequiredSubPath "libraries\net\neoforged\neoforge"
$NeoForgeVersion = Resolve-LoaderVersion `
    -Directory (Join-Path $McHome "libraries\net\neoforged\neoforge") `
    -Pattern '^21\.1\.\d+$' -Preferred $NeoForgeVersion -Label "NeoForge 21.1.x"

Write-Host "JDK        : $Jdk"
Write-Host "Minecraft  : $McHome"
Write-Host "NeoForge   : $NeoForgeVersion"
Write-Host ""

$javac = Join-Path $Jdk "bin\javac.exe"
$jarTool = Join-Path $Jdk "bin\jar.exe"
if (!(Test-Path $javac)) { throw "javac not found: $javac" }
if (!(Test-Path $jarTool)) { throw "jar not found: $jarTool" }

$build = Join-Path $root "build"
$classes = Join-Path $build "classes"
$shade = Join-Path $build "shade"
$libsOut = Join-Path $build "libs"
Remove-Item $classes, $shade, $libsOut -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $classes, $shade, $libsOut | Out-Null

$neoDir = Join-Path $McHome "libraries\net\neoforged\neoforge\$NeoForgeVersion"
$neoClient = Join-Path $neoDir "neoforge-$NeoForgeVersion-client.jar"
$neoUniversal = Join-Path $neoDir "neoforge-$NeoForgeVersion-universal.jar"
$official = Resolve-MinecraftClientJar -McHome $McHome -McVersion $McVersion
foreach ($required in @($neoClient, $neoUniversal, $official)) {
    if (!(Test-Path $required)) {
        throw "缺少编译依赖：$required`n请先用启动器启动一次 NeoForge $NeoForgeVersion 的 $McVersion 实例，让依赖下载完整。"
    }
}

$classpath = New-Object System.Collections.Generic.List[string]
$classpath.Add($neoClient)
$classpath.Add($neoUniversal)
$classpath.Add($official)
Get-ChildItem (Join-Path $McHome "libraries") -Recurse -File -Filter "*.jar" |
    Where-Object {
        $_.FullName -notmatch '\\natives-|\\natives\\|sources|javadoc' -and
        $_.FullName -notmatch '\\net\\minecraft\\client\\' -and
        $_.FullName -notmatch '\\net\\minecraftforge\\forge\\'
    } |
    ForEach-Object { $classpath.Add($_.FullName) }
$classpath.Add((Join-Path $root "libs\jlayer-1.0.1.jar"))
$classpath.Add((Join-Path $root "libs\zxing-core-3.5.3.jar"))

$sources = Get-ChildItem (Join-Path $root "src\main\java") -Recurse -File -Filter "*.java" |
    Where-Object { $_.Name -ne "WeApiProbe.java" }
$sourceArgs = $sources | ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' }
$cpLine = ($classpath | ForEach-Object { $_ -replace '\\', '/' }) -join ';'
$javacArgs = @(
    "-cp `"$cpLine`"",
    "-proc:none",
    "-encoding UTF-8",
    "-d `"$($classes -replace '\\','/')`""
) + $sourceArgs
$javacFile = Join-Path $build "javac.args"
[IO.File]::WriteAllText($javacFile, ($javacArgs -join "`r`n"), [Text.UTF8Encoding]::new($false))

Write-Host "Compiling $($sources.Count) sources against NeoForge $NeoForgeVersion / Minecraft $McVersion..."
& $javac "@$javacFile"
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }

Write-Host "Copying resources..."
Copy-Item (Join-Path $root "src\main\resources\*") $classes -Recurse -Force

Write-Host "Shading JLayer and ZXing..."
Push-Location $shade
try {
    & $jarTool xf (Join-Path $root "libs\jlayer-1.0.1.jar")
    & $jarTool xf (Join-Path $root "libs\zxing-core-3.5.3.jar")
} finally {
    Pop-Location
}
foreach ($package in @("javazoom", "com\google\zxing")) {
    $source = Join-Path $shade $package
    if (Test-Path $source) {
        $destination = Join-Path $classes $package
        New-Item -ItemType Directory -Force (Split-Path $destination) | Out-Null
        Copy-Item $source $destination -Recurse -Force
    }
}
if (!(Test-Path (Join-Path $classes "com\google\zxing\qrcode\QRCodeWriter.class"))) {
    throw "ZXing was not shaded correctly"
}
if (!(Test-Path (Join-Path $classes "javazoom\jl\decoder\Decoder.class"))) {
    throw "JLayer was not shaded correctly"
}

$manifest = Join-Path $build "MANIFEST.MF"
@"
Manifest-Version: 1.0
Implementation-Title: iOS Music
Implementation-Version: 1.0.0
Specification-Title: iosmusic
Specification-Version: 1
"@ | Set-Content $manifest -Encoding ASCII

$output = Join-Path $libsOut "iosmusic-1.0.0.jar"
Write-Host "Packaging $output..."
& $jarTool --create --file $output --manifest $manifest -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar failed with exit code $LASTEXITCODE" }

$size = (Get-Item $output).Length
Write-Host ("Built {0} ({1:N1} KiB)" -f $output, ($size / 1024.0))

if ($Deploy) {
    if ($Instance) {
        $mods = Join-Path $McHome "versions\$Instance\mods"
    } else {
        $mods = Join-Path $McHome "mods"
    }
    if (!(Test-Path $mods)) {
        throw "找不到 mods 目录：$mods`n请确认 -Instance 指定的实例名，或先手动创建该目录。"
    }
    $target = Join-Path $mods "iosmusic-1.0.0.jar"
    Copy-Item $output $target -Force
    Write-Host "Deployed to $target"
}
