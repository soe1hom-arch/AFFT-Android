<h1 align="center">AFFT-Android</h1>

<p align="center">
  <strong>Android Firmware Full Toolkit</strong><br>
  <em>A native Android app for firmware modification — no PC, no Termux required.</em>
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
  <img src="https://img.shields.io/badge/License-MIT-yellow?style=flat-square" alt="License">
</p>

---

## 📥 Download

> ⚠️ **Beta Release** — AFFT is currently in **beta**. Some features may still contain bugs or be unstable. Please report issues on GitHub.

| Variant | Link | Size |
|---------|------|------|
| **Latest Release** (signed) | [⬇️ Download APK](https://github.com/soe1hom-arch/AFFT-Android/releases/latest) | ~17 MB |
| **Latest Beta** (auto-build) | [⬇️ Download APK](https://github.com/soe1hom-arch/AFFT-Android/releases/download/beta-20260630-124/AFFT-2.0.9-beta.20260630.124.apk) | ~17 MB |

**Installation:**
1. Download the APK from the latest release above
2. Open the APK on your device (enable "Install from Unknown Sources")
3. If Google Play Protect shows a warning, tap **"Install Anyway"**
4. Grant **"Manage All Files"** permission when first launching the app

---

## ✨ Features

### 📦 Payload Dumper
- **Extract** `payload.bin` — unpack OTA firmware partitions (system, product, vendor, etc.)
- **Repack** extracted contents back into `payload.bin`
- ⚡ Multi-core extraction via **payload-dumper-go** (CGO, statically linked)
- 📊 Real-time battery-style progress bar
- 🔄 Auto-detect input files from workspace folder

### 💾 Super Image
- **Unpack** `super.img` — extract logical partitions (sparse & raw support)
- **Repack** unpacked partitions back into `super.img`

### 🗂 Filesystem
- **Extract** EROFS & ext4 partitions from `.img` files
- **Repack** extracted contents back into `.img` files
- Auto-detect filesystem type (EROFS / ext4)

### 👢 Boot Image
Unpack & Repack **7 boot image types:**
| Image | Description |
|-------|-------------|
| `boot.img` | Main kernel + ramdisk |
| `vendor_boot.img` | Vendor ramdisk |
| `init_boot.img` | Separate init ramdisk |
| `dtbo.img` | Device Tree Blob Overlay |
| `recovery.img` | Recovery ramdisk |
| `vbmeta.img` | Verified Boot metadata |
| `vendor_kernel_boot.img` | Vendor kernel + ramdisk |

### 📁 File Manager
- Browse workspace folders: `input/`, `temp/`, `Downloads/AFFT/`
- View extracted file structure with stable layout
- Toggle file size display

### 🖥 Console Output
- **Sidebar drawer** — swipe from left or tap ☰ icon
- Real-time colored log output (`[INFO]`, `[OK]`, `[ERROR]`, `[WARN]`)
- Auto-scroll to latest line
- Log viewer for browsing previous session logs
- Copy or save logs to clipboard / file

### 🧹 Clean & Export
- **Clean** — delete workspace folders with safety checks (canonical path, anti-symlink traversal)
- **Instant Export** — move results to `Downloads/AFFT/` via `renameTo` (like `mv` in Termux)
- ✅ Safe: will never delete files outside the workspace directory

### 📂 File Source Selector
- Choose files from **device storage** (system file picker) or **app workspace** (built-in browser)
- Navigate workspace folders directly — no need to copy files from storage first

---

## 📋 Requirements

| Requirement | Details |
|-------------|---------|
| **Android** | 8.0 (API 26) or higher |
| **Architecture** | **ARM64** (aarch64) only |
| **RAM** | Minimum 4 GB (6 GB+ recommended) |
| **Storage** | At least 10 GB free for large firmware files |
| **Permissions** | `MANAGE_EXTERNAL_STORAGE` (full file management) |

---

## 🛠 Technology Stack

| Component | Technology |
|-----------|------------|
| **Language** | Kotlin |
| **UI** | Jetpack Compose + Material 3 |
| **Min SDK** | 26 (Android 8.0) |
| **Target SDK** | 35 (Android 15) |
| **Background** | Foreground Service + WakeLock |
| **CI/CD** | GitHub Actions |
| **Signing** | JKS keystore via GitHub Secrets |

---

## 🧪 Development Status

**Current: Beta v2.0.9**

| Feature | Status |
|---------|--------|
| Payload extraction (`payload.bin`) | ✅ Stable |
| Super image unpack/repack (`super.img`) | ✅ Working |
| Filesystem extract/repack (EROFS/ext4) | ✅ Stable |
| Boot image unpack/repack (7 types) | ✅ Working (minor edge cases) |
| File manager | ✅ Stable |
| Console log sidebar | ✅ Stable |
| About dialog (EN/ID bilingual) | ✅ Stable |
| Clean & Export with safety checks | ✅ Stable |
| Workspace file picker | ✅ Stable |
| Beta QA & edge case testing | 🔄 In progress |
| GUI-based partition editor | 📋 Planned |
| OTA firmware changelog viewer | 📋 Planned |

> **Beta Disclaimer:** This application is under active development. Some edge cases may not be handled yet. Always back up important data before modifying firmware.

---

## 🏗 Building from Source

### GitHub Actions (automatic)
Push to `main` branch — the `Build AFFT APK` workflow will automatically build a debug APK.

```bash
git push origin main
```

### Local Build
```bash
git clone https://github.com/soe1hom-arch/AFFT-Android.git
cd AFFT-Android

# Prerequisites: Android SDK, NDK, Go 1.22+

# Build native binary
chmod +x tools/build_payload_dumper.sh
./tools/build_payload_dumper.sh

# Build debug APK
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

### Release Build (signed)
```bash
export KEYSTORE_PATH="release-key.jks"
export KEYSTORE_PASSWORD="your_password"
export KEY_ALIAS="my-release-key"
export KEY_PASSWORD="your_password"

./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

---

## 📁 Directory Structure

```
/storage/emulated/0/Android/data/com.afft.app/files/
├── input/                ← Input files (payload.bin, super.img, etc.)
└── temp/
    ├── Payload/          ← Payload extraction output
    ├── img/              ← Repacked image output
    ├── contents/         ← Filesystem extraction output
    ├── super_out/        ← Super image unpack output
    ├── boot_out/         ← Boot image unpack output
    ├── repacked/         ← Repack output
    └── logs/             ← Operation session logs

/storage/emulated/0/Download/AFFT/  ← Export destination
```

---

## 🙏 Credits & Third-Party Binaries

AFFT bundles several open-source binaries. Many thanks to their maintainers:

| Binary | Source | Description |
|--------|--------|-------------|
| **payload-dumper-go** | [ssut/payload-dumper-go](https://github.com/ssut/payload-dumper-go) | OTA payload.bin extraction tool |
| **magiskboot** | [topjohnwu/Magisk](https://github.com/topjohnwu/Magisk) | Boot image unpacking/repacking |
| **lpmake / lpunpack** | [AOSP](https://android.googlesource.com/platform/system/core/+/refs/heads/main/fs_mgr/liblp/) | Logical partition manager (super.img) |
| **mkfs.erofs** | [erofs-utils](https://git.kernel.org/pub/scm/linux/kernel/git/xiang/erofs-utils.git) | EROFS filesystem creation |
| **extract.erofs** | [erofs-utils](https://git.kernel.org/pub/scm/linux/kernel/git/xiang/erofs-utils.git) | EROFS filesystem extraction |
| **make_ext4fs** | [AOSP](https://android.googlesource.com/platform/system/core/) | ext4 filesystem creation |
| **debugfs** | [AOSP](https://android.googlesource.com/platform/system/core/) | ext2/ext3/ext4 filesystem debugger |
| **simg2img** | [AOSP](https://android.googlesource.com/platform/system/core/) | Sparse image to raw image converter |

All binaries are compiled for **ARM64** and statically linked where possible for maximum compatibility.

---

## 🐛 Reporting Issues

Found a bug or have a feature request?
1. Check [Issues](https://github.com/soe1hom-arch/AFFT-Android/issues) — see if it's already reported
2. Open a new issue with:
   - **Device model** & **Android version**
   - **Steps to reproduce**
   - **Screenshots / logs** if available
   - Log files from `temp/logs/`

---

## 👨‍💻 Developer

**Wandi / soe1hom-arch** — _Android Firmware Enthusiast_

> Open-source project built from personal need for a portable firmware modification tool that runs directly on Android without a PC.

<p align="center">
  <a href="https://github.com/soe1hom-arch/AFFT-Android/issues">Report Issue</a>
  ·
  <a href="https://github.com/soe1hom-arch/AFFT-Android/discussions">Discussions</a>
</p>

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

The included third-party binaries are subject to their respective licenses (GPL, Apache 2.0, BSD).

---

<p align="center">
  <sub>© 2026 Wandi · Built for the Android modding community</sub>
</p>
