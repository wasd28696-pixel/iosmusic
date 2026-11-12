param(
    [string]$Jdk = "E:\java\java21",
    [string]$McHome = "E:\.minecraft",
    [switch]$Deploy
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
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

$neoClient = Join-Path $McHome "libraries\net\neoforged\neoforge\21.1.233\neoforge-21.1.233-client.jar"
$neoUniversal = Join-Path $McHome "libraries\net\neoforged\neoforge\21.1.233\neoforge-21.1.233-universal.jar"
$official = Join-Path $McHome "libraries\net\minecraft\client\1.21.1\client-1.21.1-official.jar"
foreach ($required in @($neoClient, $neoUniversal, $official)) {
    if (!(Test-Path $required)) { throw "Missing compile dependency: $required" }
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

Write-Host "Compiling $($sources.Count) sources against NeoForge 21.1.233 / Minecraft 1.21.1..."
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
    $mods = Join-Path $McHome "versions\Create+\mods"
    if (!(Test-Path $mods)) { throw "Test instance mods folder not found: $mods" }
    $target = Join-Path $mods "iosmusic-1.0.0.jar"
    Copy-Item $output $target -Force
    Write-Host "Deployed to $target"
}
