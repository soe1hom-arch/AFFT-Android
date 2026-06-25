# AFFT-Android

Android Firmware Full Toolkit - Aplikasi Android untuk memodifikasi firmware Android.

## Fitur

- **Extract payload.bin** - Bongkar OTA firmware Android
- **Unpack/Repack super.img** - Bongkar & rakit logical partitions
- **Extract/Repack filesystem** - EROFS & ext4
- **Boot family** - Unpack/repack boot, recovery, dtbo, vbmeta, vendor_boot, init_boot
- **Sparse image** - Deteksi & konversi otomatis
- **Terminal console** - Output real-time seperti terminal

## Build dengan GitHub Actions

Cukup push ke GitHub, APK akan otomatis dibuild.

## Build Lokal

```bash
git clone https://github.com/username/AFFT-Android
cd AFFT-Android
./gradlew assembleDebug
```

APK akan tersedia di `app/build/outputs/apk/debug/app-debug.apk`

## Persyaratan

- Android 8.0 (API 26) ke atas
- Izin penyimpanan untuk membaca/menulis file firmware
- ARM64 device (binary native untuk aarch64)

## Credits

Original AFFT by soe1hom-arch / Wandi
