param(
    [ValidateSet("test", "run", "package")]
    [string]$Command = "run"
)

$ErrorActionPreference = "Stop"
$mavenVersion = "3.9.9"
$toolsDir = Join-Path $PSScriptRoot ".tools"
$jdkDir = Join-Path $toolsDir "jdk-17"
$javaCmd = Join-Path $jdkDir "bin\java.exe"
$mavenDir = Join-Path $toolsDir "apache-maven-$mavenVersion"
$mavenCmd = Join-Path $mavenDir "bin\mvn.cmd"

function Import-LocalEnv {
    $envFile = Join-Path $PSScriptRoot ".env.local"
    if (-not (Test-Path $envFile)) {
        return
    }
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith("#") -or -not $trimmed.Contains("=")) {
            continue
        }
        $parts = $trimmed.Split("=", 2)
        $name = $parts[0].Trim()
        $value = $parts[1].Trim().Trim('"').Trim("'")
        if ($name) {
            [Environment]::SetEnvironmentVariable($name, $value, "Process")
        }
    }
}

function Test-Java17 {
    param([string]$CommandPath)
    if (-not (Test-Path $CommandPath)) {
        return $false
    }
    $escaped = '"' + $CommandPath + '" -version 2>&1'
    $versionOutput = cmd /c $escaped
    if ($LASTEXITCODE -ne 0 -or -not $versionOutput) {
        return $false
    }
    return ($versionOutput -join "`n") -match 'version "17\.|version "18\.|version "19\.|version "2[0-9]\.'
}

function Find-InstalledJava17Home {
    $candidates = @()
    if ($env:JAVA_HOME) {
        $candidates += $env:JAVA_HOME
    }
    $registryPaths = @(
        "HKLM:\SOFTWARE\JavaSoft\JDK\*",
        "HKLM:\SOFTWARE\Eclipse Adoptium\JDK\*\hotspot\MSI",
        "HKLM:\SOFTWARE\Microsoft\JDK\*"
    )
    foreach ($path in $registryPaths) {
        $items = Get-ItemProperty $path -ErrorAction SilentlyContinue
        foreach ($item in $items) {
            if ($item.JavaHome) {
                $candidates += $item.JavaHome
            }
            if ($item.Path) {
                $candidates += $item.Path
            }
        }
    }
    foreach ($candidate in $candidates | Select-Object -Unique) {
        $candidateJava = Join-Path $candidate "bin\java.exe"
        if (Test-Java17 $candidateJava) {
            return $candidate
        }
    }
    return $null
}

Import-LocalEnv

$installedJdk = Find-InstalledJava17Home
if ($installedJdk) {
    $jdkDir = $installedJdk
    $javaCmd = Join-Path $jdkDir "bin\java.exe"
}

if (-not (Test-Java17 $javaCmd)) {
    New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
    $jdkZip = Join-Path $toolsDir "jdk-17.zip"
    $jdkExtractDir = Join-Path $toolsDir "jdk-17-extract"
    if (-not (Test-Path $jdkZip)) {
        Write-Host "Downloading portable JDK 17..."
        Invoke-WebRequest -Uri "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk" -OutFile $jdkZip
    }
    if (Test-Path $jdkExtractDir) {
        Remove-Item -LiteralPath $jdkExtractDir -Recurse -Force
    }
    Write-Host "Extracting JDK 17..."
    Expand-Archive -Force -Path $jdkZip -DestinationPath $jdkExtractDir
    $expandedHome = Get-ChildItem -LiteralPath $jdkExtractDir -Directory | Select-Object -First 1
    if ($null -eq $expandedHome) {
        throw "JDK archive extraction failed."
    }
    if (Test-Path $jdkDir) {
        Remove-Item -LiteralPath $jdkDir -Recurse -Force
    }
    Move-Item -LiteralPath $expandedHome.FullName -Destination $jdkDir
    Remove-Item -LiteralPath $jdkExtractDir -Recurse -Force
}

$env:JAVA_HOME = $jdkDir
$env:Path = (Join-Path $jdkDir "bin") + [IO.Path]::PathSeparator + $env:Path

if (-not (Test-Path $mavenCmd)) {
    New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
    $zipPath = Join-Path $toolsDir "apache-maven-$mavenVersion-bin.zip"
    if (-not (Test-Path $zipPath)) {
        Write-Host "Downloading Maven $mavenVersion..."
        Invoke-WebRequest -Uri "https://archive.apache.org/dist/maven/maven-3/$mavenVersion/binaries/apache-maven-$mavenVersion-bin.zip" -OutFile $zipPath
    }
    Write-Host "Extracting Maven..."
    Expand-Archive -Force -Path $zipPath -DestinationPath $toolsDir
}

if ($Command -eq "test") {
    & $mavenCmd test
} elseif ($Command -eq "package") {
    & $mavenCmd package
} else {
    & $mavenCmd spring-boot:run
}
