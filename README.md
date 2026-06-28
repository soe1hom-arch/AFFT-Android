# AFFT-Android

**Android Firmware Full Toolkit** — Aplikasi Android untuk memodifikasi firmware Android.  
Dibangun dengan **Kotlin + Jetpack Compose**.

> **Developer:** soe1hom-arch / Wandi  
> **Version:** 2.0.4  
> **Lisensi:** Open-source

---

## Fitur

### 📦 Payload
- **Ekstrak** `payload.bin` — Bongkar OTA firmware Android
- **Repack** dari folder hasil ekstraksi
- ✅ Terbukti bekerja

### 💾 Super
- **Unpack** `super.img` — Bongkar logical partitions (sparse support)
- **Repack** dari folder hasil unpack

### 🗂 Filesystem
- **Ekstrak** filesystem (EROFS & ext4) dari file `.img`
- **Repack** filesystem dari folder hasil ekstraksi
- Auto-detect tipe filesystem
- ✅ Terbukti bekerja

### 👢 Boot Family
Unpack & Repack 7 jenis boot image:
- `boot.img` · `vendor_boot.img` · `init_boot.img`
- `dtbo.img` · `recovery.img` · `vbmeta.img`
- `vendor_kernel_boot.img`

### 📁 File Manager
- Browse folder `temp/`, `work/`, `input/`, `Downloads/AFFT/`
- Lihat struktur file hasil ekstraksi
- Layout stabil: selalu tampilkan minimal 5 item
- Tampilkan/sembunyikan ukuran file

### 🖥 Console Log (Sidebar)
- **Sidebar drawer** — Geser dari kiri layar atau tap ikon ☰
- Output real-time semua operasi
- Tombol **Clear** untuk membersihkan log
- Log sudah dipindahkan dari semua screen ke satu tempat terpusat

### 🧹 Clean & Export
- **Clean** — Hapus folder kerja (img, contents, payload, boot_out, dll.)
  - ✅ Safety check dengan `canonicalPath`, anti symlink traversal
  - ✅ Tidak akan hapus di luar folder kerja
- **Export** — Pindahkan hasil kerja ke `Downloads/AFFT/`
  - ✅ `renameTo` = instan (seperti `mv` di Termux)
  - ✅ Fallback `copyRecursively` jika rename gagal
  - ✅ Aman: error hapus sumber tidak gagalkan export

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
/storage/emulated/0/Android/data/com.afft.app/files/afft_work/
├── input/              ← Tempat file input (payload.bin, super.img, dll)
└── temp/
    ├── img/            ← Image hasil repack / output
    ├── contents/       ← Hasil ekstraksi filesystem
    ├── Payload/        ← Hasil ekstraksi payload.bin
    ├── boot_out/       ← Hasil unpack boot images
    ├── repacked/       ← Hasil repack
    └── logs/           ← Log operasi

/storage/emulated/0/Download/AFFT/   ← Hasil export
```

---

## Rencana Pengembangan

- [x] Ekstrak & Repack payload.bin
- [x] Unpack & Repack super.img
- [x] Ekstrak & Repack filesystem (EROFS/ext4)
- [x] Unpack & Repack boot images
- [x] File Manager
- [x] Export instan (renameTo)
- [x] Safety check Clean (anti hapus sembarangan)
- [x] Sidebar drawer untuk console log
- [x] About dialog profesional
- [ ] Uji coba Payload & Super
- [ ] Uji coba Boot images
- [ ] Backup & restore

---

## Credits

Developer/Dikembangkan oleh **soe1hom-arch / Wandi**  
— _AFFT: Alat Modifikasi Firmware Android sederhana yang langsung bisa di install di hp android anda,tanpa perlu terminal linux

Project open-source, gunakan dengan bijak.
