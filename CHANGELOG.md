# Changelog AFFT-Android

## [2.0.2] — 2026-06-27

### Ditambahkan
- Sidebar drawer untuk Console Log (geser dari kiri atau tap ☰)
- About dialog dengan info aplikasi, pengembang, status binary, dan fitur
- Placeholder rows pada file list (minimal 5 item tampil walau folder kosong)
- Tombol "Show Size" / "Hide Size" pada File Manager
- Bullet point fitur di About dialog

### Diubah
- **TerminalView dipindahkan** dari setiap screen (Payload, Super, FS, Boot, File Manager) ke sidebar drawer — satu tempat terpusat
- **Layout File Manager** distabilkan: Card file list menggunakan `weight(1f)` + `fillMaxSize()`
- **Info bar** jadi lebih rapi dengan tombol aksi yang tersusun horizontal
- Output log dibuat collapsible (sebelumnya selalu tampil)

### Diperbaiki
- **⚠️ CRITICAL: Clean hapus semua data** — Safety check `cleanSelected()` sebelumnya menggunakan `context.filesDir` (internal). Setelah work directory dipindah ke external storage, safety check tidak valid. **Sekarang menggunakan `workDir`** sebagai root validasi.
- **Safety check** menggunakan `canonicalPath` untuk cegah symlink traversal
- Export gagal karena beda partisi — sekarang `getWorkDir()` menggunakan `getExternalFilesDir(null)` (external storage, satu partisi dengan Downloads)
- Export lambat (copy file by file) — diganti `renameTo` (instan, seperti `mv` di Termux) dengan fallback `copyRecursively`
- Hapus sumber gagal tidak lagi menggagalkan export (try-catch terpisah)

### Teknis
- `AFFTService.clearLogs()` dibuat public untuk tombol Clear di drawer
- Semua import TerminalView tidak terpakai dibersihkan dari screen

---

## [2.0.1] — 2026-06-26

### Ditambahkan
- Auto-detect file dari folder input/ di semua screen
- Dukungan EROFS filesystem (extract & repack)
- ELF dynamic/static detection untuk linker64 fallback

### Diubah
- Fungsi extraction sekarang preserve nama file asli dari URI (tidak hardcoded)
- Layout File Manager menggunakan `heightIn(min = 400.dp)`

### Diperbaiki
- Perintah extract.erofs menggunakan flag `-i -x -o` yang benar
- `make_ext4fs` diganti dari GLIBC ke NDK (Bionic libc)

---

## [2.0.0] — 2026-06-25

### Initial Release
- Ekstrak & Repack payload.bin
- Unpack & Repack super.img (sparse)
- Ekstrak & Repack filesystem (ext4)
- Unpack & Repack boot images (7 jenis)
- File Manager dasar
- Export ke Downloads
- Clean folder kerja
- Binary deployment otomatis
