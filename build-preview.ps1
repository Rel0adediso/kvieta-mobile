param(
    [string]$ToolsRoot = 'C:\Users\Public\KvietaAndroidTools'
)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$buildPath = Join-Path $ToolsRoot 'project'
$link = Get-Item -LiteralPath $buildPath -ErrorAction Stop
if ($link.LinkType -ne 'Junction' -or
    [IO.Path]::GetFullPath([string]$link.Target).TrimEnd('\') -ne [IO.Path]::GetFullPath($projectRoot).TrimEnd('\')) {
    throw 'The build junction must point to this Android project. See README.md.'
}
$jdk = Get-ChildItem -LiteralPath (Join-Path $ToolsRoot 'java') -Directory |
    Where-Object { $_.Name -like 'jdk-17*' } | Select-Object -First 1
if (!$jdk -or !(Test-Path -LiteralPath (Join-Path $jdk.FullName 'bin/java.exe'))) {
    throw 'JDK 17 was not found in the tools directory.'
}
$previousJava = $env:JAVA_HOME
$previousSdk = $env:ANDROID_HOME
$previousGradle = $env:GRADLE_USER_HOME
try {
    $env:JAVA_HOME = $jdk.FullName
    $env:ANDROID_HOME = Join-Path $ToolsRoot 'sdk'
    $env:GRADLE_USER_HOME = Join-Path $ToolsRoot 'gradle-cache'
    Push-Location -LiteralPath $buildPath
    try {
        & .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
        if ($LASTEXITCODE -ne 0) { throw "Android build failed (exit $LASTEXITCODE)." }
    } finally { Pop-Location }
    $apk = Join-Path $projectRoot 'app/build/outputs/apk/debug/app-debug.apk'
    if (!(Test-Path -LiteralPath $apk)) { throw 'APK was not produced.' }
    Write-Output "APK: $apk"
    Get-FileHash -LiteralPath $apk -Algorithm SHA256
} finally {
    $env:JAVA_HOME = $previousJava
    $env:ANDROID_HOME = $previousSdk
    $env:GRADLE_USER_HOME = $previousGradle
}
