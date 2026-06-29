<h1 align="center">AFFT-Android</h1>

<p align="center">
  <strong>Android Firmware Full Toolkit</strong><br>
  <em>Aplikasi Android native untuk memodifikasi firmware — tanpa PC, tanpa Termux.</em>
</p>

<p align="center">
  <a href="https://github.com/soe1hom-arch/AFFT-Android/releases">
    <img src="https://img.shields.io/github/v/release/soe1hom-arch/AFFT-Android?style=for-the-badge&label=Release&color=blue" alt="Release">
  </a>
  <a href="https://github.com/soe1hom-arch/AFFT-Android/releases">
    <img src="https://img.shields.io/github/downloads/soe1hom-arch/AFFT-Android/total?style=for-the-badge&label=Downloads&color=success" alt="Downloads">
  </a>
  <br>
  <img src="https://img.shields.io/badge/Android-8.0%2B-brightgreen?style=flat-square&logo=android" alt="Android">
  <img src="https://img.shields.io/badge/ARM64-only-red?style=flat-square" alt="ARM64">
  <img src="https://img.shields.io/badge/Kotlin-Jetpack_Compose-purple?style=flat-square&logo=kotlin" alt="Kotlin">
</p>

---

## 📥 Download

> ⚠️ **Beta Release** — AFFT saat ini dalam tahap **beta**. Beberapa fitur mungkin masih mengandung bug atau belum stabil. Laporkan issue jika menemukan masalah.

| Variant | Link | Ukuran |
|---------|------|--------|
| **Latest Release** (signed) | [⬇️ Download APK](https://github.com/soe1hom-arch/AFFT-Android/releases/latest) | ~17 MB |
| Debug Build (unsigned) | [⬇️ Download Artifact](https://github.com/soe1hom-arch/AFFT-Android/actions/workflows/build-apk.yml) | ~25 MB |

**Cara Install:**
1. Download APK dari rilis terbaru di atas
2. Buka file APK di HP (aktifkan "Install from Unknown Sources")
3. Jika muncul peringatan Play Protect, pilih **"Install Anyway"**
4. Berikan izin **"Manage All Files"** saat pertama membuka aplikasi

---

## ✨ Fitur

### 📦 Payload Dumper
- **Ekstrak** `payload.bin` — bongkar OTA firmware Android (system, product, vendor, dll.)
- **Repack** dari folder hasil ekstraksi kembali ke `payload.bin`
- ⚡ Ekstraksi multi-core dengan binary **payload-dumper-go** (CGO, statically linked)
- 📊 Real-time progress bar dengan gaya terminal
- 🔄 Auto-detect file input dari folder kerja

### 💾 Super Image
- **Unpack** `super.img` — bongkar logical partitions (sparse & raw support)
- **Repack** dari folder hasil unpack kembali ke `super.img`

### 🗂 Filesystem
- **Ekstrak** partisi EROFS & ext4 dari file `.img`
- **Repack** folder hasil ekstraksi kembali ke file `.img`
- Auto-detect tipe filesystem (EROFS / ext4)

### 👢 Boot Image
Unpack & Repack **7 jenis** boot image:
| Image | Deskripsi |
|-------|-----------|
| `boot.img` | Kernel + ramdisk utama |
| `vendor_boot.img` | Vendor ramdisk |
| `init_boot.img` | Init ramdisk terpisah |
| `dtbo.img` | Device Tree Blob Overlay |
| `recovery.img` | Recovery ramdisk |
| `vbmeta.img` | Verified Boot metadata |
| `vendor_kernel_boot.img` | Vendor kernel + ramdisk |

### 📁 File Manager
- Jelajahi folder kerja: `input/`, `temp/`, `Downloads/AFFT/`
- Lihat struktur hasil ekstraksi dengan layout stabil
- Toggle tampilan ukuran file

### 🖥 Console Output
- **Sidebar drawer** — geser dari kiri layar atau tap ☰
- Output real-time semua operasi dengan log berwarna (`[INFO]`, `[OK]`, `[ERROR]`, `[WARN]`)
- Auto-scroll ke baris terbaru
- Log viewer untuk membaca file log session sebelumnya
- Copy / simpan log ke clipboard atau file

### 🧹 Clean & Export
- **Clean** — hapus folder kerja dengan safety check (canonical path, anti symlink traversal)
- **Export instan** — pindahkan hasil ke `Downloads/AFFT/` dengan `renameTo` (seperti `mv` di Termux)
- ✅ Aman: tidak akan hapus di luar folder kerja

---

## 📋 Persyaratan

| Persyaratan | Detail |
|-------------|--------|
| **Android** | 8.0 (API 26) atau lebih baru |
| **Arsitektur** | **ARM64** (aarch64) — hanya |
| **RAM** | Minimal 4 GB (disarankan 6 GB+) |
| **Penyimpanan** | Minimal 10 GB free untuk firmware besar |
| **Izin** | `MANAGE_EXTERNAL_STORAGE` (manajemen file penuh) |

---

## 🏗 Cara Build

### GitHub Actions (otomatis)
Push ke branch `main` → workflow akan otomatis build APK. Download dari tab **Actions**.

```bash
git push origin main
```

### Build Lokal
```bash
git clone https://github.com/soe1hom-arch/AFFT-Android.git
cd AFFT-Android

# Prasyarat: Android SDK, NDK, Go 1.22+

# Build binary native
chmod +x tools/build_payload_dumper.sh
./tools/build_payload_dumper.sh

# Build APK debug
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

### Release Build
```bash
export KEYSTORE_PATH="release-key.jks"
export KEYSTORE_PASSWORD="your_password"
export KEY_ALIAS="my-release-key"
export KEY_PASSWORD="your_password"

./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

---

## 📁 Struktur Direktori

```
/storage/emulated/0/Android/data/com.afft.app/files/
├── input/                ← File input (payload.bin, super.img, dll)
├── temp/
│   ├── Payload/          ← Hasil ekstraksi payload.bin
│   ├── img/              ← Image hasil repack
│   ├── contents/         ← Hasil ekstraksi filesystem
│   ├── super_out/        ← Hasil unpack super.img
│   ├── boot_out/         ← Hasil unpack boot images
│   ├── repacked/         ← Hasil repack
│   └── logs/             ← Log session operasi

/storage/emulated/0/Download/AFFT/  ← Hasil export
```

---

## 🛠 Teknologi

| Komponen | Teknologi |
|----------|-----------|
| **Bahasa** | Kotlin |
| **UI** | Jetpack Compose + Material 3 |
| **Min SDK** | 26 (Android 8.0) |
| **Target SDK** | 35 (Android 15) |
| **Background** | Foreground Service + WakeLock |
| **Native Binary** | payload-dumper-go (Go + CGO, static) |
| **CI/CD** | GitHub Actions |
| **Signing** | JKS keystore via GitHub Secrets |

---

## 🧪 Status Pengembangan

**Saat ini: Beta v2.0.4**

- [x] Ekstrak payload.bin (✅ stabil)
- [x] Unpack/Repack super.img
- [x] Ekstrak/Repack filesystem (EROFS/ext4)
- [x] Unpack/Repack 7 jenis boot image
- [x] File manager
- [x] Console log sidebar (real-time, log viewer)
- [x] About dialog (EN/ID bilingual)
- [x] Clean & Export dengan safety check
- [ ] Uji coba Payload & Super (QA)
- [ ] Uji coba Boot images (QA)
- [ ] Backup & restore
- [ ] GUI-based partition editor
- [ ] Update OTA firmware checker

> **Beta Disclaimer:** Aplikasi ini masih dalam tahap pengembangan aktif. Beberapa edge case mungkin belum tertangani. Selalu backup data penting sebelum memodifikasi firmware.

---

## 🐛 Melaporkan Masalah

Jika menemukan bug atau memiliki saran:
1. Cek [Issues](https://github.com/soe1hom-arch/AFFT-Android/issues) — apakah sudah ada laporan serupa
2. Buka issue baru dengan format:
   - **Device** & **Android version**
   - **Langkah reproduksi**
   - **Screenshot / log** jika ada
   - File log dari `temp/logs/`

---

## 👨‍💻 Pengembang

**Wandi / soe1hom-arch** — _Android Firmware Enthusiast_

> Proyek open-source untuk alat modifikasi firmware yang portabel dan jalan langsung di HP tanpa PC.

<p align="center">
  <a href="https://github.com/soe1hom-arch/AFFT-Android/issues">Report Issue</a>
  ·
  <a href="https://github.com/soe1hom-arch/AFFT-Android/discussions">Discussions</a>
</p>

---

<p align="center">
  <sub>© 2026 Wandi · Dibangun dengan ❤️ untuk komunitas Android Indonesia</sub>
</p>
