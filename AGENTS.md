### ZERO-COLLATERAL-DAMAGE POLICY
1. Setiap kali menerima instruksi perbaikan atau penambahan fitur, HANYA modifikasi file yang secara eksplisit diminta oleh pengguna.
2. DILARANG MEROMBAK modul lain yang sudah stabil (seperti Diskografi, Player Engine, Search, Database Cache, Theme) tanpa instruksi tertulis.
3. DILARANG menambahkan filter data kaku, sanitasi teks berlebih, atau paginasi otomatis pada API response yang sudah berjalan normal.
4. Setiap refactor atau modifikasi wajib lolos verifikasi tanpa merusak fitur di layar lain.

### PROTECTED MODULES (LOCKED)
Modul-modul inti yang telah diverifikasi stabil dan dilarang diubah tanpa izin tertulis:
- Modul Diskografi & Artis (`ArtistViewModel.kt`, `ArtistRepository.kt`, `ArtistDetailScreen.kt`, `ArtistProfileScreen.kt`)
- Modul Pemutar Musik & Audio Stream Resolver (`PlayerViewModel.kt`, `YouTubeAudioEngine.kt`, `MusicStreamResolver.kt`, `AudioStreamResolver.kt`)
- Modul Basis Data & Cache Lokal (`ListeningHistoryDatabaseHelper.kt`, `PlaylistStorageManager.kt`, `LikedSongsManager.kt`, `DownloadManagerHelper.kt`)
- Modul Estetika & Desain Punk Rock (`Theme.kt`, `Color.kt`, `Type.kt`)

# Crankr Agent Rules & Engineering Guidelines

## 1. Target Output Build (Release APK Mandate)
- **Signed Release APK Mandatory**: Setiap kali selesai memperbaiki bug, memperbarui UI, atau menambahkan fitur baru, agen **WAJIB** menjalankan build APK versi Signed Release menggunakan perintah:
  ```powershell
  $env:JAVA_HOME = "C:\Users\AstroKido\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2"; .\gradlew assembleRelease
  ```
  atau pada lingkungan dengan JAVA_HOME terkonfigurasi:
  ```bash
  ./gradlew assembleRelease
  ```
- **Larangan Debug APK**: Jangan pernah menghasilkan, merekomendasikan, atau mengandalkan APK varian debug (`assembleDebug`).
- **Lokasi & Nama Output Build**: Pastikan output APK rilis selalu bernama **crankr.apk** (bukan `app-release.apk` atau `apk-release`) dan tersimpan di direktori:
  `app/build/outputs/apk/release/crankr.apk`
  Verifikasi selalu keberadaan dan integritas file `crankr.apk` ini setelah build selesai.

## 2. Keystore Permanen & Package Identity
- **Keystore Rilis Permanen**: Kunci penggunaan keystore rilis permanen yang tersimpan di konfigurasi proyek. Jangan pernah membuat keystore baru secara acak yang dapat merusak tanda tangan rilis (signature mismatch).
- **Package Identity Konsisten**: Pertahankan identitas unik package name produksi. Jangan pernah mengembalikannya ke format default `com.example` atau mengubah `applicationId` / `namespace` secara sembarangan.

## 3. Standar UI dan Gaya Desain Crankr
- **Identitas Visual Punk Rock Minimalis**:
  - Pertahankan tema punk rock modern minimalis:
    - Dominan warna hitam gelap pekat: `PitchBlack` (`#0D0D0D`), `DarkCharcoal` (`#121212`), `CardBackground` (`#1A1A1A`).
    - Aksen merah neon khas Crankr: `CrimsonRed` (`#E50914`), `CrimsonRedDark` (`#B81D24`), `CrimsonRedSoft` (`#33E50914`).
    - Tipografi industrial berbobot: Oswald Bold / Anton untuk display, heading, dan brand, serta Inter untuk teks isi dan sekunder.
- **Anti-Bloat & Anti-Filler**:
  - Hindari penambahan teks filler generik ("Lorem ipsum" atau teks dummy tanpa konteks).
  - Hindari visualizer berlebihan yang mengganggu performa rendering dan mengotori estetika layout.
  - Hindari komponen AI atau fitur gimmick yang tidak diminta secara eksplisit oleh pengguna.
- **Kerapian & Kualitas Produksi**:
  - Semua elemen interaktif harus responsif, memiliki padding ergonomis, kontras tinggi, dan animasi mikro yang halus (*snappy*).

## 4. Kepatuhan Otomatis Setiap Sesi
- Agen **wajib** membaca, memahami, dan mematuhi seluruh instruksi dalam aturan ini secara otomatis di setiap sesi kerja baru tanpa perlu diminta ulang oleh pengguna.

## 5. Sistem Kenaikan Versi Aplikasi Otomatis
- **Berkas Konfigurasi**: Nilai versi dikelola melalui `version.properties` di root folder (`VERSION_MAJOR`, `VERSION_MINOR`, `VERSION_CODE`).
- **Pemicu Build**: Setiap kali perintah `assembleDebug` atau `assembleRelease` dieksekusi, skrip pada `app/build.gradle.kts` otomatis menambah `VERSION_CODE += 1` dan `VERSION_MINOR += 1`.
- **Rollover Versi**: Jika `VERSION_MINOR > 99`, maka `VERSION_MINOR` kembali ke 0 dan `VERSION_MAJOR += 1`.
- **Penerapan**: `versionCode` menggunakan `VERSION_CODE` dan `versionName` menggunakan format `"$VERSION_MAJOR.$VERSION_MINOR"` (misal 1.0, 1.1, ..., 1.99 -> 2.0).

## 6. Aktivasi Skill Desain (Mode Hybrid)
- **Otomatis untuk Tugas UI/Layout Baru**: Setiap kali menerima instruksi merombak UI, membuat layar baru, atau menyusun sistem komponen visual, agen **wajib** membaca `.agents/skills/ui-ux-pro-max/SKILL.md` dan `.agents/skills/design-system/SKILL.md` terlebih dahulu sebelum mengeksekusi kode.
- **Jangan Muat untuk Tugas Non-UI**: Untuk perbaikan bug logika, audio engine, database, atau skrip build, lewati skill desain agar hemat konteks.
- **Sesuai Penyebutan (On-Demand)**: Skill `.agents/skills/` lainnya (`banner-design`, `brand`, `design`, `slides`, `ui-styling`) hanya diaktifkan ketika pengguna memintanya secara eksplisit atau tugas memang berkaitan langsung (misal membuat banner, presentasi, atau web UI).


