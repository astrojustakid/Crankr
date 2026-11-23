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

    # 7. Otomatisasi GitHub Release & Upload APK
    $apkPath = "app/build/outputs/apk/release/crankr.apk"
    if (Test-Path $apkPath) {
        Write-Host ""
        Write-Host "-> Memeriksa kredensial GitHub untuk rilis asset APK..." -ForegroundColor Cyan
        $credInput = @"
protocol=https
host=github.com
"@
        $credOutput = $credInput | git credential fill 2>$null
        $ghToken = ""
        foreach ($line in ($credOutput -split "`n")) {
            if ($line.Trim().StartsWith("password=")) {
                $ghToken = $line.Trim().Substring("password=".Length).Trim()
            }
        }

        if ($ghToken -ne "") {
            Write-Host "-> Menyinkronkan GitHub Release untuk tag 'v$versionName'..." -ForegroundColor Cyan
            try {
                $ghHeaders = @{
                    "Authorization" = "Bearer $ghToken"
                    "User-Agent"    = "Crankr-Release-Bot"
                    "Accept"        = "application/vnd.github.v3+json"
                }
                
                $releaseUrl = "https://api.github.com/repos/astrojustakid/Crankr/releases/tags/v$versionName"
                $existingRelease = $null
                try {
                    $existingRelease = Invoke-RestMethod -Uri $releaseUrl -Headers $ghHeaders -Method Get -ErrorAction Stop
                } catch {}

                if (-not $existingRelease) {
                    $createBody = @{
                        tag_name   = "v$versionName"
                        name       = "Release Crankr v$versionName (Build $code)"
                        body       = $formattedChangelog
                        draft      = $false
                        prerelease = $false
                    } | ConvertTo-Json
                    
                    $existingRelease = Invoke-RestMethod -Uri "https://api.github.com/repos/astrojustakid/Crankr/releases" `
                        -Headers $ghHeaders -Method Post -Body $createBody -ContentType "application/json; charset=utf-8"
                    Write-Host "[OK] GitHub Release 'v$versionName' berhasil dibuat." -ForegroundColor Green
                }

                if ($existingRelease -and $existingRelease.id) {
                    $releaseId = $existingRelease.id
                    
                    # Cek jika ada asset crankr.apk sebelumnya, hapus dahulu
                    if ($existingRelease.assets) {
                        foreach ($asset in $existingRelease.assets) {
                            if ($asset.name -eq "crankr.apk") {
                                Write-Host "-> Menghapus asset crankr.apk lama (ID: $($asset.id))..." -ForegroundColor Yellow
                                Invoke-RestMethod -Uri "https://api.github.com/repos/astrojustakid/Crankr/releases/assets/$($asset.id)" `
                                    -Headers $ghHeaders -Method Delete
                            }
                        }
                    }

                    # Unggah file APK ke asset release
                    $uploadUri = "https://uploads.github.com/repos/astrojustakid/Crankr/releases/$releaseId/assets?name=crankr.apk"
                    Write-Host "-> Mengunggah berkas crankr.apk ke GitHub Release..." -ForegroundColor Cyan
                    curl.exe -s -X POST -H "Authorization: Bearer $ghToken" `
                        -H "Content-Type: application/vnd.android.package-archive" `
                        --data-binary "@$apkPath" `
                        $uploadUri | Out-Null
                        
                    Write-Host "[BERHASIL] crankr.apk sukses diunggah ke GitHub Release 'v$versionName'!" -ForegroundColor Green
                }
            } catch {
                Write-Host "[WARN] Gagal menyinkronkan GitHub Release asset: $_" -ForegroundColor Yellow
            }
        } else {
            Write-Host "[INFO] Token GitHub tidak ditemukan. Melewati upload release asset." -ForegroundColor Gray
        }
    }
} else {
    Write-Host ""
    Write-Host "[GAGAL] Kendala saat git push. Pastikan koneksi atau kredensial Git aktif." -ForegroundColor Red
}
