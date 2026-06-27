# AFFT-Android

**Android Firmware Full Toolkit** — Aplikasi Android untuk memodifikasi firmware Android.  
Dibangun dengan Kotlin + Jetpack Compose.

> Author: **soe1hom-arch / Wandi**  
> Version: **2.0.2**

---

## Fitur

### 📦 Payload
- **Ekstrak** `payload.bin` — Bongkar OTA firmware Android
- **Repack** dari folder hasil ekstraksi

### 💾 Super
- **Unpack** `super.img` — Bongkar logical partitions (sparse support)
- **Repack** dari folder hasil unpack

### 🗂 Filesystem
- **Ekstrak** filesystem (EROFS & ext4) dari file `.img`
- **Repack** filesystem dari folder hasil ekstraksi
- Auto-detect tipe filesystem

### 👢 Boot Family
Unpack & Repack 7 jenis boot image:
- `boot.img` · `vendor_boot.img` · `init_boot.img`
- `dtbo.img` · `recovery.img` · `vbmeta.img`
- `vendor_kernel_boot.img`

### 📁 File Manager
- Browse folder `temp/`, `work/`, `input/`, `Downloads/AFFT/`
- Lihat struktur file hasil ekstraksi
- Selection & export ke Downloads

### 🖥 Console Log
- **Sidebar drawer** — Geser dari kiri atau tap ☰
- Output real-time semua operasi
- Tombol **Clear** untuk membersihkan log

### 🧹 Clean & Export
- **Clean** — Hapus folder kerja (img, contents, payload, boot_out, dll.)
- **Export** — Pindahkan hasil kerja ke `Downloads/AFFT/` secara instan
- Aman: safety check dengan canonical path, anti symlink traversal

---

## Persyaratan

| Persyaratan | Detail |
|-------------|--------|
| **Android** | 8.0 (API 26) ke atas |
| **Izin** | `MANAGE_EXTERNAL_STORAGE` (manajemen file penuh) |
| **Arch** | ARM64 / aarch64 (native binary untuk arsitektur ARM) |

---

## Cara Build

### GitHub Actions (Rekomendasi)
Push ke branch `main` atau `master`, APK akan otomatis dibuild oleh GitHub Actions.  
Download dari tab **Actions** → pilih workflow → **Artifacts** → `AFFT-APK-Debug`.

### Build Lokal
```bash
git clone https://github.com/soe1hom-arch/AFFT-Android
cd AFFT-Android
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

---

## Struktur Direktori

```
/storage/emulated/0/
├── Android/data/com.afft.app/files/afft_work/
│   ├── input/          ← Tempat file input (payload, img, dll)
│   ├── temp/
│   │   ├── payload/    ← Hasil ekstraksi payload.bin
│   │   ├── img/        ← Hasil repack / image output
│   │   ├── contents/   ← Hasil ekstraksi filesystem
│   │   ├── boot_out/   ← Hasil unpack boot images
│   │   ├── repacked/   ← Hasil repack
│   │   └── logs/       ← Log operasi
│   └── temp/           ← Temporary working directory
└── Download/AFFT/       ← Hasil export
```

---

## Credits & Lisensi

Dikembangkan oleh **soe1hom-arch / Wandi**  
— _AFFT: Alat Modifikasi Firmware Android untuk Semua_

_Project open-source, gunakan dengan bijak._
