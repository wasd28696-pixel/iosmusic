# build-common.ps1 —— build.ps1 / build-forge.ps1 共用的环境探测
#
# 这两个脚本用 javac 直接编译，编译依赖（Minecraft / NeoForge / Forge 的 jar）
# 直接取自本机 Minecraft 启动器已经下载好的 libraries 目录，所以必须先有一个
# 装好对应加载器的实例。本文件负责把这些路径自动找出来，避免写死某一台机器。

function Get-JdkMajorVersion {
    param([string]$JdkPath)
    $release = Join-Path $JdkPath "release"
    if (Test-Path $release) {
        $line = Get-Content $release -ErrorAction SilentlyContinue |
            Where-Object { $_ -match '^JAVA_VERSION="(\d+)' } | Select-Object -First 1
        if ($line -and $line -match 'JAVA_VERSION="(\d+)') { return [int]$Matches[1] }
    }
    return 0
}

function Resolve-Jdk21 {
    param([string]$Requested)

    if ($Requested) {
        $javac = Join-Path $Requested "bin\javac.exe"
        if (!(Test-Path $javac)) { throw "-Jdk 指定的路径里找不到 javac.exe：$Requested" }
        $major = Get-JdkMajorVersion $Requested
        if ($major -and $major -ne 21) { throw "-Jdk 指定的 JDK 版本是 $major，本项目需要 21：$Requested" }
        return (Resolve-Path $Requested).Path
    }

    $candidates = New-Object System.Collections.Generic.List[string]
    if ($env:JDK21) { $candidates.Add($env:JDK21) }
    if ($env:JAVA_HOME) { $candidates.Add($env:JAVA_HOME) }

    $vendors = @("Java", "Eclipse Adoptium", "Eclipse Foundation", "Microsoft", "Zulu",
                 "Amazon Corretto", "BellSoft", "Semeru", "IBM", "GraalVM", "RedHat", "JetBrains")
    $roots = @($env:ProgramFiles, ${env:ProgramFiles(x86)}) | Where-Object { $_ -and (Test-Path $_) }
    foreach ($root in $roots) {
        foreach ($vendor in $vendors) {
            $vendorDir = Join-Path $root $vendor
            if (Test-Path $vendorDir) {
                Get-ChildItem $vendorDir -Directory -ErrorAction SilentlyContinue |
                    ForEach-Object { $candidates.Add($_.FullName) }
            }
        }
    }

    # 兜底：PATH 里的 javac 如果正好是 21
    $onPath = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($onPath) { $candidates.Add((Split-Path (Split-Path $onPath.Source -Parent) -Parent)) }

    $rejected = @()
    $seen = @{}
    foreach ($candidate in $candidates) {
        if (!$candidate) { continue }
        $normalized = $candidate.TrimEnd('\')
        if ($seen.ContainsKey($normalized)) { continue }
        $seen[$normalized] = $true
        if (!(Test-Path (Join-Path $normalized "bin\javac.exe"))) { continue }
        $major = Get-JdkMajorVersion $normalized
        if ($major -eq 21) { return (Resolve-Path $normalized).Path }
        if ($major) { $rejected += "$normalized (Java $major)" }
    }

    $hint = if ($rejected.Count) { "`n已检测到但版本不符：" + ($rejected -join "、") } else { "" }
    throw @"
找不到 JDK 21。请安装 JDK 21 后重试，或用 -Jdk 指定路径：
  powershell -File .\build.ps1 -Jdk "C:\Program Files\Java\jdk-21"$hint

下载地址：https://adoptium.net/temurin/releases/?version=21
"@
}

function Resolve-McHome {
    param([string]$Requested, [string]$RequiredSubPath)

    if ($Requested) {
        if (!(Test-Path (Join-Path $Requested "libraries"))) {
            throw "-McHome 指定的目录不像 Minecraft 主目录（缺少 libraries）：$Requested"
        }
        return (Resolve-Path $Requested).Path
    }

    $candidates = New-Object System.Collections.Generic.List[string]
    if ($env:MC_HOME) { $candidates.Add($env:MC_HOME) }
    if ($env:APPDATA) { $candidates.Add((Join-Path $env:APPDATA ".minecraft")) }
    if ($env:USERPROFILE) { $candidates.Add((Join-Path $env:USERPROFILE ".minecraft")) }
    Get-PSDrive -PSProvider FileSystem -ErrorAction SilentlyContinue | Where-Object { $_.Root } |
        ForEach-Object { $candidates.Add((Join-Path $_.Root ".minecraft")) }

    $seen = @{}
    $fallback = $null
    foreach ($candidate in $candidates) {
        if (!$candidate -or $seen.ContainsKey($candidate)) { continue }
        $seen[$candidate] = $true
        if (!(Test-Path (Join-Path $candidate "libraries"))) { continue }
        $resolved = (Resolve-Path $candidate).Path
        if ($RequiredSubPath -and (Test-Path (Join-Path $resolved $RequiredSubPath))) { return $resolved }
        if (!$fallback) { $fallback = $resolved }
    }
    if ($fallback) { return $fallback }

    throw @"
找不到 Minecraft 主目录（.minecraft）。请先用启动器安装好一个对应加载器的 1.21.1 实例，
或用 -McHome 指定路径：
  powershell -File .\build.ps1 -McHome "C:\Users\<你>\AppData\Roaming\.minecraft"
"@
}

# 在 libraries 下挑版本号最高的匹配项，找不到就用默认值
function Resolve-LoaderVersion {
    param([string]$Directory, [string]$Pattern, [string]$Preferred, [string]$Label)

    $available = @()
    if (Test-Path $Directory) {
        $available = Get-ChildItem $Directory -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match $Pattern } | Select-Object -ExpandProperty Name
    }

    if ($Preferred -and ($available -contains $Preferred)) { return $Preferred }

    if ($available.Count) {
        $best = $available |
            Sort-Object -Property @{ Expression = {
                if ($_ -match '(\d+(?:\.\d+)+)$') {
                    try { [version]$Matches[1] } catch { [version]"0.0" }
                } else { [version]"0.0" }
            } } -Descending |
            Select-Object -First 1
        if ($Preferred) {
            Write-Host "提示：未找到 $Label $Preferred，改用 $best" -ForegroundColor Yellow
        }
        return $best
    }

    throw @"
Minecraft 主目录里没有 $Label 的依赖库。
请先用启动器安装一个 Minecraft 1.21.1 + $Label 的实例，让启动器把依赖下载完整。
"@
}

function Resolve-MinecraftClientJar {
    param([string]$McHome, [string]$McVersion)

    $candidates = @(
        (Join-Path $McHome "libraries\net\minecraft\client\$McVersion\client-$McVersion-official.jar"),
        (Join-Path $McHome "libraries\net\minecraft\client\$McVersion\client-$McVersion.jar"),
        (Join-Path $McHome "versions\$McVersion\$McVersion.jar")
    )
    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) { return $candidate }
    }

    throw @"
找不到 Minecraft $McVersion 客户端 jar。已尝试：
  $($candidates -join "`n  ")

请先用启动器启动一次该版本的实例，让它把客户端下载完整。
"@
}
