$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$RepoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$TruForRoot = Join-Path $RepoRoot ".trufor"
$VenvRoot = Join-Path $RepoRoot ".trufor-venv"
$PythonExe = Join-Path $VenvRoot "Scripts\python.exe"
$TempRoot = Join-Path $env:TEMP ("forensight-trufor-" + [guid]::NewGuid().ToString("N"))
$OfficialCommit = "ae54475df6f41a491d7615100feb19263dec13f7"
$SourceZipUrl = "https://codeload.github.com/grip-unina/TruFor/zip/$OfficialCommit"
$WeightsUrl = "https://www.grip.unina.it/download/prog/TruFor/TruFor_weights.zip"
$WeightsMd5 = "7bee48f3476c75616c3c5721ab256ff8"

function Read-LocalSecret([string]$Prompt) {
    $secure = Read-Host -Prompt $Prompt -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}

function Require-Command([string]$Name, [string]$InstallHint) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "$Name is required. Install it first: $InstallHint"
    }
}

function Invoke-External([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Program failed with exit code $LASTEXITCODE."
    }
}

Write-Host ""
Write-Host "ForenSight ZERO-COST local TruFor setup" -ForegroundColor Cyan
Write-Host "The Render Free backend remains lightweight. Your computer runs the model."
Write-Host "TruFor is licensed for informational/nonprofit use only."
$license = Read-Host "Confirm you will use TruFor only for permitted informational/nonprofit purposes (type YES)"
if ($license -cne "YES") {
    throw "License confirmation not provided. No model source or weights were downloaded."
}

Require-Command "node" "https://nodejs.org/"
Require-Command "npm" "Install Node.js 22 or newer from https://nodejs.org/"
Require-Command "java" "Install JDK 25 (Eclipse Temurin) from https://adoptium.net/"
Require-Command "curl.exe" "Use Windows 10/11 with curl.exe available."
Require-Command "py" "Install Python 3.10 from https://www.python.org/downloads/release/python-31011/"

$javaVersion = (& java -version 2>&1 | Select-Object -First 1)
if ($javaVersion -notmatch '"25') {
    throw "JDK 25 is required by this project. Detected: $javaVersion"
}
$nodeVersion = (& node --version)
if ($nodeVersion -notmatch '^v(2[2-9]|[3-9]\d)\.') {
    throw "Node.js 22 or newer is required. Detected: $nodeVersion"
}
& py -3.10 -c "import sys; print(sys.version)"
if ($LASTEXITCODE -ne 0) {
    throw "Python 3.10 is required. Install it with the Python 3.10 Windows installer."
}

$MongoUri = Read-LocalSecret "Paste your MongoDB Atlas URI (private; it will not be written to Git)"
if ([string]::IsNullOrWhiteSpace($MongoUri) -or $MongoUri -notmatch '^mongodb(\+srv)?://') {
    throw "A valid MongoDB Atlas URI is required."
}
$AppPassword = Read-LocalSecret "Set the ForenSight investigator login password"
if ([string]::IsNullOrWhiteSpace($AppPassword)) {
    throw "A non-empty investigator password is required."
}

New-Item -ItemType Directory -Force -Path $TempRoot | Out-Null
try {
    if (-not (Test-Path $PythonExe)) {
        Write-Host "Creating local Python 3.10 virtual environment..."
        Invoke-External "py" @("-3.10", "-m", "venv", $VenvRoot)
    }

    Write-Host "Installing pinned TruFor/PyTorch dependencies (download occurs once)..."
    Invoke-External $PythonExe @("-m", "pip", "install", "--upgrade", "pip")

    $UseCuda = [bool](Get-Command "nvidia-smi.exe" -ErrorAction SilentlyContinue)
    if ($UseCuda) {
        try {
            Invoke-External $PythonExe @("-m", "pip", "install", "--no-cache-dir",
                "torch==1.11.0+cu113", "torchvision==0.12.0+cu113",
                "--extra-index-url", "https://download.pytorch.org/whl/cu113")
        }
        catch {
            Write-Warning "CUDA PyTorch installation failed; installing the CPU build instead."
            Invoke-External $PythonExe @("-m", "pip", "install", "--no-cache-dir",
                "torch==1.11.0+cpu", "torchvision==0.12.0+cpu",
                "--extra-index-url", "https://download.pytorch.org/whl/cpu")
        }
    }
    else {
        Write-Host "NVIDIA driver utility not detected; installing CPU PyTorch."
        Invoke-External $PythonExe @("-m", "pip", "install", "--no-cache-dir",
            "torch==1.11.0+cpu", "torchvision==0.12.0+cpu",
            "--extra-index-url", "https://download.pytorch.org/whl/cpu")
    }

    Invoke-External $PythonExe @("-m", "pip", "install", "--no-cache-dir",
        "numpy==1.21.5", "timm==0.5.4", "yacs==0.1.8", "tqdm==4.64.1",
        "Pillow==9.3.0", "matplotlib==3.5.3")

    $TorchCheck = & $PythonExe -c "import torch; print('CUDA=' + str(torch.cuda.is_available())); print('DEVICE=' + (torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'CPU'))"
    if ($LASTEXITCODE -ne 0) { throw "PyTorch verification failed." }
    Write-Host $TorchCheck
    $GpuIndex = "-1"
    if ($TorchCheck -match 'CUDA=True') { $GpuIndex = "0" }

    $ModelFile = Join-Path $TruForRoot "weights\trufor.pth.tar"
    $ScriptFile = Join-Path $TruForRoot "src\trufor_test.py"
    if (-not (Test-Path $ScriptFile)) {
        Write-Host "Downloading official GRIP-UNINA TruFor inference source..."
        $SourceZip = Join-Path $TempRoot "TruFor-source.zip"
        Invoke-External "curl.exe" @("--fail", "--location", "--retry", "3", "--output", $SourceZip, $SourceZipUrl)
        $SourceExtract = Join-Path $TempRoot "source"
        Expand-Archive -LiteralPath $SourceZip -DestinationPath $SourceExtract -Force
        $SourceFolder = Get-ChildItem $SourceExtract -Directory | Select-Object -First 1
        if (-not $SourceFolder) { throw "The official TruFor source archive was empty." }
        New-Item -ItemType Directory -Force -Path (Join-Path $TruForRoot "src"), (Join-Path $TruForRoot "weights") | Out-Null
        Copy-Item (Join-Path $SourceFolder.FullName "test_docker\src\*") (Join-Path $TruForRoot "src") -Recurse -Force
        Copy-Item (Join-Path $SourceFolder.FullName "test_docker\LICENSE.txt") (Join-Path $TruForRoot "LICENSE.txt") -Force
        Copy-Item (Join-Path $SourceFolder.FullName "test_docker\LICENSE_CMX.txt") (Join-Path $TruForRoot "LICENSE_CMX.txt") -Force
    }

    if (-not (Test-Path $ModelFile)) {
        Write-Host "Downloading official 248 MB TruFor checkpoint..."
        $WeightZip = Join-Path $TempRoot "TruFor_weights.zip"
        Invoke-External "curl.exe" @("--fail", "--location", "--retry", "3", "--output", $WeightZip, $WeightsUrl)
        $actualMd5 = (Get-FileHash -Algorithm MD5 -LiteralPath $WeightZip).Hash.ToLowerInvariant()
        if ($actualMd5 -ne $WeightsMd5) {
            throw "TruFor weight checksum mismatch. Expected $WeightsMd5 but got $actualMd5."
        }
        $WeightsExtract = Join-Path $TempRoot "weights"
        Expand-Archive -LiteralPath $WeightZip -DestinationPath $WeightsExtract -Force
        $Checkpoint = Get-ChildItem -Path $WeightsExtract -Filter "trufor.pth.tar" -Recurse -File | Select-Object -First 1
        if (-not $Checkpoint) { throw "The verified official archive did not contain trufor.pth.tar." }
        New-Item -ItemType Directory -Force -Path (Split-Path $ModelFile -Parent) | Out-Null
        Copy-Item $Checkpoint.FullName $ModelFile -Force
    }

    if (-not (Test-Path $ModelFile) -or (Get-Item $ModelFile).Length -lt 100MB) {
        throw "The TruFor checkpoint is missing or unexpectedly small."
    }

    $env:MONGODB_URI = $MongoUri
    $env:MONGODB_DATABASE = "forensight_db"
    $env:APP_USERNAME = "investigator"
    $env:APP_PASSWORD = $AppPassword
    $env:TRUFOR_ENABLED = "true"
    $env:TRUFOR_ROOT = $TruForRoot
    $env:TRUFOR_PYTHON = $PythonExe
    $env:TRUFOR_BRIDGE = Join-Path $RepoRoot "backend\trufor\trufor_bridge.py"
    $env:TRUFOR_OUTPUT_DIR = Join-Path $RepoRoot "backend\uploads\trufor"
    $env:TRUFOR_GPU = $GpuIndex
    $env:OMP_NUM_THREADS = "2"
    $env:MKL_NUM_THREADS = "2"
    $env:OPENBLAS_NUM_THREADS = "2"

    Write-Host "Installing frontend packages if needed..."
    Push-Location (Join-Path $RepoRoot "frontend")
    try {
        if (-not (Test-Path "node_modules")) { Invoke-External "npm.cmd" @("ci") }
    }
    finally { Pop-Location }

    Write-Host "Starting local Spring Boot backend and TruFor..."
    $BackendDir = Join-Path $RepoRoot "backend"
    Start-Process -FilePath "powershell.exe" -WorkingDirectory $BackendDir -ArgumentList @(
        "-NoExit", "-ExecutionPolicy", "Bypass", "-Command", "& .\mvnw.cmd spring-boot:run"
    ) | Out-Null

    Write-Host "Waiting for the local API and MongoDB connection..."
    $Ready = $false
    for ($i = 0; $i -lt 90; $i++) {
        try {
            $health = Invoke-RestMethod -Uri "http://localhost:8080/api/health" -TimeoutSec 3
            if ($health.status -eq "UP" -and $health.database -eq "UP") { $Ready = $true; break }
        }
        catch { Start-Sleep -Seconds 2 }
    }
    if (-not $Ready) {
        Write-Warning "The API did not report MongoDB UP yet. Check the backend terminal, Atlas IP allowlist, and connection URI."
    }

    $FrontendDir = Join-Path $RepoRoot "frontend"
    $env:VITE_API_BASE_URL = "http://localhost:8080"
    Start-Process -FilePath "powershell.exe" -WorkingDirectory $FrontendDir -ArgumentList @(
        "-NoExit", "-ExecutionPolicy", "Bypass", "-Command", "npm.cmd run dev -- --host 127.0.0.1"
    ) | Out-Null

    Start-Sleep -Seconds 3
    Start-Process "http://localhost:5173/"
    Write-Host ""
    Write-Host "ForenSight local mode started." -ForegroundColor Green
    Write-Host "Open: http://localhost:5173/"
    Write-Host "Sign in with username investigator and the password you entered."
    Write-Host "GPU index: $GpuIndex (-1 means CPU)."
    Write-Host "Re-upload evidence to this local instance; old Render file paths are not on this computer."
    Write-Host "Keep the backend and frontend PowerShell windows open while using ForenSight."
    Write-Host "The model, weights, virtual environment, and evidence stay on this computer."
    Write-Host "Nothing above changes your GitHub or Render plan."
}
finally {
    $env:MONGODB_URI = $null
    $env:APP_PASSWORD = $null
    $env:TRUFOR_PYTHON = $null
    Remove-Item -LiteralPath $TempRoot -Recurse -Force -ErrorAction SilentlyContinue
}
