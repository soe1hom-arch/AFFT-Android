# Panduan Kontribusi

Terima kasih tertarik berkontribusi ke **AFFT-Android**! 🎉

## Branch Strategy

- **`main`** — branch stabil untuk rilis.
- Buat **feature branch** dari `main` untuk setiap perubahan, lalu buat Pull Request.

## Cara Berkontribusi

1. **Fork** repo ini.
2. Buat branch baru: `git checkout -b feat/nama-fitur`.
3. Lakukan perubahan yang diperlukan.
4. Pastikan kode mengikuti standar formatting (**ktlint**):
   ```bash
   ./gradlew ktlintCheck
   ./gradlew ktlintFormat
   ```
5. Commit dengan pesan yang jelas (gunakan [Conventional Commits](https://www.conventionalcommits.org/)):
   ```
   feat: tambah fitur partition editor
   fix: perbaiki crash saat ekstraksi payload.bin
   docs: update README dengan petunjuk baru
   ```
6. Push ke branch-mu: `git push origin feat/namafitur`.
7. Buat **Pull Request** ke branch `main` repo ini.

## Panduan Kode

- Gunakan **Kotlin** dengan gaya yang konsisten dengan kode yang sudah ada.
- UI mengikuti pola yang sudah ada (Jetpack Compose + Material 3).
- Hindari magic numbers — gunakan konstanta dengan nama yang deskriptif.
- Fitur baru harus dilengkapi dokumentasi di README jika relevan.

## Melaporkan Bug

Buka [issue baru](https://github.com/soe1hom-arch/AFFT-Android/issues/new/choose) dengan template Bug Report.

## Lisensi

Dengan berkontribusi, kamu setuju bahwa kontribusimu akan dilisensikan di bawah lisensi MIT yang digunakan proyek ini.
