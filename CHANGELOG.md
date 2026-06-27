# Changelog AFFT-Android

## [2.0.2] — 2026-06-27

### ✅ Fixed
- **Export error** — Perbaiki `getWorkDir()` menggunakan `getExternalFilesDir(null)` agar `renameTo` berhasil (satu partisi dengan Downloads)
- **Export lambat** — Ganti copy file-by-file dengan `renameTo` (mv) → instan, fallback `copyRecursively` jika beda partisi
- **Clean hapus semua data** ([CRITICAL]) — Safety check `cleanSelected()` sekarang menggunakan `workDir` (parent dari tempDir) bukan `context.filesDir`. Mencegah penghapusan di luar folder kerja
- **Layout File Manager tidak stabil** — Hapus `heightIn(min=400.dp)`, gunakan `weight(1f)` + placeholder minimum 5 baris agar tinggi konsisten walaupun folder kosong
- **TerminalView di setiap screen** — Pindahkan console log dari masing-masing screen ke **sidebar drawer** yang bisa di-swipe dari kiri atau melalui ikon ☰
- **About dialog** — diperbarui dengan informasi profesional: tentang aplikasi, pengembang, status binary, dan daftar fitur
- **Nama file extraction** — URI wrapper sekarang preserve nama file asli (tidak hardcoded `filesystem_src.img`)
- **Safety path** — Semua operasi file menggunakan `canonicalPath` untuk cegah symlink traversal

### 🔧 Changed
- **AFFTService.kt** — `clearLogs()` diubah dari `private` ke `public` untuk tombol Clear di sidebar
- **Directory structure** — Kerja pindah dari internal `filesDir` ke external `getExternalFilesDir(null)` untuk kompatibilitas export

### 🚀 Added
- **Sidebar drawer** — Menu navigasi cepat + console log terpusat
- **About dialog** — Tampilan profesional dengan info app & developer
- **Collapsible log** — Tombol Log/Hide Log di File Manager (sekarang terpusat di sidebar)

### 🧪 Status Pengujian
- [x] **Extract filesystem (img)** — Terbukti berfungsi
- [ ] Payload — Belum diuji
- [ ] Super — Belum diuji
- [ ] Boot family — Belum diuji
- [ ] Repack — Belum diuji

## [2.0.1] — 2026-06-26

### ✅ Fixed
- **Export via MediaStore** — Tambah `RELATIVE_PATH` tanpa prefix `Download/`
- **FileManager** — Tinggi minimal 400dp untuk konsistensi tampilan
- **ERoFS extraction** — Perbaiki command args (`-i -x -o`)
- **ELF detection** — Deteksi dynamic/static untuk fallback linker64
- **make_ext4fs** — Ganti dari GLIBC ke NDK (Bionic libc)

### 🔧 Changed
- **Auto-detect file** — Kembalikan fungsi auto-detect dari folder `input/`
- **UI screens** — Konsistensi layout di Payload, Super, Filesystem, Boot

## [2.0.0] — 2026-06-25

### ✨ Initial Release
- Ekstraksi & repack payload.bin
- Unpack & repack super.img (sparse)
- Ekstraksi & repack filesystem (EROFS/ext4)
- Unpack & repack boot images (7 jenis)
- File manager
- Ekspor hasil ke Downloads
- Terminal console real-time
- Binary native untuk ARM64
