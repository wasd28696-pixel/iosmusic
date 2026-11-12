param(
    [string]$Jdk,
    [string]$McHome,
    [string]$McVersion = "1.21.1",
    [string]$ForgeVersion = "1.21.1-52.1.19",
    [string]$Instance,
    [switch]$DeployForge
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $root "build-common.ps1")

# 本机路径覆盖（可选，加入 .gitignore，不会提交）
$localConfig = Join-Path $root "build.local.ps1"
if (Test-Path $localConfig) { . $localConfig }

$Jdk = Resolve-Jdk21 -Requested $Jdk
$McHome = Resolve-McHome -Requested $McHome -RequiredSubPath "libraries\net\minecraftforge\forge"
$ForgeVersion = Resolve-LoaderVersion `
    -Directory (Join-Path $McHome "libraries\net\minecraftforge\forge") `
    -Pattern '^1\.21\.1-52\.' -Preferred $ForgeVersion -Label "Forge 1.21.1-52.x"

Write-Host "JDK        : $Jdk"
Write-Host "Minecraft  : $McHome"
Write-Host "Forge      : $ForgeVersion"
Write-Host ""

$javac = Join-Path $Jdk "bin\javac.exe"
$jarTool = Join-Path $Jdk "bin\jar.exe"
if (!(Test-Path $javac)) { throw "javac not found: $javac" }

$build = Join-Path $root "build\forge"
$classes = Join-Path $build "classes"
$shade = Join-Path $build "shade"
$libsOut = Join-Path $build "libs"
Remove-Item $classes, $shade, $libsOut -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $classes, $shade, $libsOut | Out-Null

$forgeDir = Join-Path $McHome "libraries\net\minecraftforge\forge\$ForgeVersion"
$forgeClient = Join-Path $forgeDir "forge-$ForgeVersion-client.jar"
$forgeUniversal = Join-Path $forgeDir "forge-$ForgeVersion-universal.jar"
$official = Resolve-MinecraftClientJar -McHome $McHome -McVersion $McVersion
foreach ($required in @($forgeClient, $forgeUniversal, $official)) {
    if (!(Test-Path $required)) {
        throw "缺少编译依赖：$required`n请先用启动器启动一次 Forge $ForgeVersion 的 $McVersion 实例，让依赖下载完整。"
    }
}

$classpath = New-Object System.Collections.Generic.List[string]
$classpath.Add($forgeClient)
$classpath.Add($forgeUniversal)
$classpath.Add($official)
Get-ChildItem (Join-Path $McHome "libraries") -Recurse -File -Filter "*.jar" |
    Where-Object {
        $path = $_.FullName
        $path -notmatch '\\natives-|\\natives\\|sources|javadoc' -and
        $path -notmatch '\\net\\minecraft\\client\\' -and
        $path -notmatch '\\net\\neoforged\\' -and
        $path -notmatch '\\net\\minecraftforge\\forge\\' -and
        !($path -match '\\net\\minecraftforge\\' -and
          $path -match '\\1\.20\.1-|\\1\.19\.|\\1\.18\.|\\1\.17\.|\\1\.16\.|\\1\.12\.2-|\\1\.8\.9-|\\1\.7\.10-')
    } |
    ForEach-Object { $classpath.Add($_.FullName) }
$classpath.Add((Join-Path $root "libs\jlayer-1.0.1.jar"))
$classpath.Add((Join-Path $root "libs\zxing-core-3.5.3.jar"))

$excluded = @(
    "IosMusic.java", "ClientSetup.java",
    "GlassShader.java", "GlassRefraction.java", "GlassBlur.java",
    "TogetherClient.java", "TogetherServer.java", "TogetherNetworking.java",
    "TogetherPayload.java", "TogetherClientBridge.java"
)
$sharedSources = Get-ChildItem (Join-Path $root "src\main\java") -Recurse -File -Filter "*.java" |
    Where-Object { $excluded -notcontains $_.Name -and $_.Name -ne "WeApiProbe.java" }
$forgeSources = Get-ChildItem (Join-Path $root "src\forge\java") -Recurse -File -Filter "*.java"
$sources = @($sharedSources) + @($forgeSources)
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

Write-Host "Compiling $($sources.Count) sources against Forge $ForgeVersion / Minecraft $McVersion..."
& $javac "@$javacFile"
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }

Write-Host "Copying resources..."
Copy-Item (Join-Path $root "src\main\resources\*") $classes -Recurse -Force
Remove-Item (Join-Path $classes "META-INF\neoforge.mods.toml") -Force -ErrorAction SilentlyContinue
Copy-Item (Join-Path $root "src\forge\resources\*") $classes -Recurse -Force

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

$manifest = Join-Path $build "MANIFEST.MF"
@"
Manifest-Version: 1.0
Implementation-Title: iOS Music Forge
Implementation-Version: 1.0.0
Specification-Title: iosmusic
Specification-Version: 1
"@ | Set-Content $manifest -Encoding ASCII

$output = Join-Path $libsOut "iosmusic-1.0.0-forge.jar"
Write-Host "Packaging $output..."
& $jarTool --create --file $output --manifest $manifest -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar failed with exit code $LASTEXITCODE" }

$size = (Get-Item $output).Length
Write-Host ("Built {0} ({1:N1} KiB)" -f $output, ($size / 1024.0))

if ($DeployForge) {
    if ($Instance) {
        $mods = Join-Path $McHome "versions\$Instance\mods"
    } else {
        $mods = Join-Path $McHome "mods"
    }
    if (!(Test-Path $mods)) {
        throw "找不到 mods 目录：$mods`n请确认 -Instance 指定的实例名，或先手动创建该目录。"
    }
    $target = Join-Path $mods "iosmusic-1.0.0-forge.jar"
    Copy-Item $output $target -Force
    Write-Host "Deployed Forge build to $target"
}
