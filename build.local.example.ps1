# 本机构建配置示例
#
# 用法：复制此文件为 build.local.ps1，按自己机器修改。
# build.local.ps1 已加入 .gitignore，不会被提交到仓库。
#
# 两个脚本都会自动探测 JDK 21 和 .minecraft 的位置；
# 只有探测不到时，才需要在 build.local.ps1 里写死下面这些变量。

# JDK 21 的安装目录（里面要有 bin\javac.exe）
$env:JDK21 = "C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot"

# Minecraft 主目录（里面要有 libraries 文件夹）
$env:MC_HOME = "$env:APPDATA\.minecraft"
