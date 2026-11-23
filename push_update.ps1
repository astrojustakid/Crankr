param(
    [string]$Features = "",
    [string]$Fixes = "",
    [string]$Notes = "",
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
Write-Host "==========================================================" -ForegroundColor Red
Write-Host "   CRANKR AUTO-RELEASE & PUSH (v$versionName - Build $code)" -ForegroundColor White
Write-Host "==========================================================" -ForegroundColor Red

# 2. Susun Catatan Rilis Terstruktur
$changelogParts = @()

if ($Features -ne "") {
    $changelogParts += "[FITUR BARU]`n" + ($Features -split "`n" | ForEach-Object { if ($_ -match "^\s*-\s*") { $_ } else { "- $_" } } | Out-String).Trim()
}

if ($Fixes -ne "") {
    $changelogParts += "[PERBAIKAN BUG]`n" + ($Fixes -split "`n" | ForEach-Object { if ($_ -match "^\s*-\s*") { $_ } else { "- $_" } } | Out-String).Trim()
}

if ($Notes -ne "") {
    $changelogParts += "[CATATAN]`n" + ($Notes -split "`n" | ForEach-Object { if ($_ -match "^\s*-\s*") { $_ } else { "- $_" } } | Out-String).Trim()
}

if ($changelogParts.Count -eq 0 -and $Message -ne "") {
    $changelogParts += $Message
}

$formattedChangelog = if ($changelogParts.Count -gt 0) {
    $changelogParts -join "`n`n"
} else {
    "[RILIS v$versionName]`n- Pembaruan rutin stabilitas & audio streaming Crankr"
}

# 3. Sinkronisasi version.json
$jsonFile = "version.json"
if (Test-Path $jsonFile) {
    try {
        $json = Get-Content $jsonFile -Raw | ConvertFrom-Json
        $json.versionCode = [int]$code
        $json.versionName = $versionName
        $json.downloadUrl = "https://github.com/astrojustakid/Crankr/releases/latest/download/crankr.apk"
        $json.changelog = $formattedChangelog
        $json | ConvertTo-Json -Depth 5 | Set-Content $jsonFile -Encoding UTF8
        Write-Host "[OK] version.json disinkronkan ke v$versionName dengan catatan rilis terbaru." -ForegroundColor Green
    } catch {
        Write-Host "[WARN] Gagal update version.json: $_" -ForegroundColor Yellow
    }
}

# 4. Validasi Remote Git
$remoteUrl = git remote get-url origin 2>$null
if (-not $remoteUrl) {
    Write-Host "[ERROR] Remote 'origin' belum disetel!" -ForegroundColor Red
    exit 1
}

# 5. Git Commit Message Terstruktur
$commitTitle = "Release Crankr v$versionName (Build $code)"
$fullCommitMsg = "$commitTitle`n`n$formattedChangelog"

Write-Host "-> Menambahkan berkas ke Git..." -ForegroundColor Cyan
git add .

Write-Host "-> Melakukan Git Commit..." -ForegroundColor Cyan
git commit -m "$fullCommitMsg"

Write-Host "-> Membuat Git Tag: 'v$versionName'..." -ForegroundColor Cyan
git tag -f "v$versionName"

# 6. Git Push ke Remote
Write-Host "-> Mendorong (Push) ke $remoteUrl..." -ForegroundColor Cyan
git push -u origin main
git push origin --tags --force

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "[BERHASIL] Seluruh pembaruan Crankr v$versionName sukses dipush ke Git!" -ForegroundColor Green
    Write-Host "Catatan Rilis:" -ForegroundColor Yellow
    Write-Host $formattedChangelog -ForegroundColor White
} else {
    Write-Host ""
    Write-Host "[GAGAL] Kendala saat git push. Pastikan koneksi atau kredensial Git aktif." -ForegroundColor Red
}
