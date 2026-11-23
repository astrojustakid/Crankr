param(
    [string]$Message = ""
)

# 1. Baca versi dari version.properties
$versionFile = "version.properties"
$major = "1"
$minor = "65"
$code = "66"

if (Test-Path $versionFile) {
    Get-Content $versionFile | ForEach-Object {
        if ($_ -match "VERSION_MAJOR=(\d+)") { $major = $matches[1] }
        if ($_ -match "VERSION_MINOR=(\d+)") { $minor = $matches[1] }
        if ($_ -match "VERSION_CODE=(\d+)")  { $code = $matches[1] }
    }
}

$versionName = "$major.$minor"
Write-Host "==============================================" -ForegroundColor Red
Write-Host "  CRANKR AUTO-PUSH & RELEASE (v$versionName - Build $code)" -ForegroundColor White
Write-Host "==============================================" -ForegroundColor Red

# 2. Sinkronisasi version.json
$jsonFile = "version.json"
if (Test-Path $jsonFile) {
    try {
        $json = Get-Content $jsonFile -Raw | ConvertFrom-Json
        $json.versionCode = [int]$code
        $json.versionName = $versionName
        if ($Message -ne "") {
            $json.changelog = $Message
        }
        $json | ConvertTo-Json -Depth 5 | Set-Content $jsonFile -Encoding UTF8
        Write-Host "[OK] version.json berhasil disinkronkan ke v$versionName" -ForegroundColor Green
    } catch {
        Write-Host "[WARN] Gagal update version.json: $_" -ForegroundColor Yellow
    }
}

# 3. Cek Remote Git
$remoteUrl = git remote get-url origin 2>$null
if (-not $remoteUrl) {
    Write-Host "[PERINGATAN] Remote 'origin' belum disetel!" -ForegroundColor Yellow
    Write-Host "Silakan jalankan perintah berikut sekali:" -ForegroundColor Cyan
    Write-Host "  git remote add origin <LINK_REPO_GITHUB_ANDA>" -ForegroundColor White
    Write-Host "Contoh:" -ForegroundColor Gray
    Write-Host "  git remote add origin https://github.com/username/crankr.git" -ForegroundColor Gray
    exit 1
}

# 4. Git Add, Commit & Tag
$commitMsg = if ($Message -ne "") { "Release Crankr v$versionName: $Message" } else { "Release Crankr v$versionName (Build $code)" }

Write-Host "-> Menambahkan file ke Git..." -ForegroundColor Cyan
git add .

Write-Host "-> Melakukan Git Commit: '$commitMsg'..." -ForegroundColor Cyan
git commit -m "$commitMsg"

Write-Host "-> Membuat Git Tag: 'v$versionName'..." -ForegroundColor Cyan
git tag -f "v$versionName"

# 5. Git Push ke Remote
Write-Host "-> Mendorong (Push) ke $remoteUrl..." -ForegroundColor Cyan
git push origin main --tags

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "[BERHASIL] Seluruh pembaruan Crankr v$versionName telah sukses dipush ke Git!" -ForegroundColor Green
    Write-Host "File version.json di repositori Anda sekarang aktif untuk OTA Auto-Updater." -ForegroundColor White
} else {
    Write-Host ""
    Write-Host "[GAGAL] Terjadi kendala saat git push. Pastikan Anda sudah login atau memiliki izin push ke repo tersebut." -ForegroundColor Red
}
