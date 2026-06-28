#!/bin/bash
# Build script: cross-compile payload-dumper-go with CGO + liblzma for Android ARM64
#
# Usage:
#   export NDK_DIR=/path/to/android-ndk-r27
#   export SRC_DIR=/path/to/this/repo
#   bash build/build_payload_dumper.sh
#
# Dependencies:
#   - Android NDK r27+
#   - Go 1.22+
#   - curl, make, cmake, etc.

set -euo pipefail

# ─── Configuration ────────────────────────────────────────────────────────
: "${NDK_DIR:?Must set NDK_DIR to Android NDK path}"
: "${SRC_DIR:?Must set SRC_DIR to repo root}"
: "${WORK_DIR:=/tmp/payload-dumper-build}"

TOOLCHAIN="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64"
CC="$TOOLCHAIN/bin/aarch64-linux-android21-clang"
AR="$TOOLCHAIN/bin/llvm-ar"
GO_VER="1.22"
PAYLOAD_REPO="https://github.com/ssut/payload-dumper-go.git"

OUTPUT_DIR="${SRC_DIR}/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$OUTPUT_DIR" "$WORK_DIR"

echo "=== [1/6] Building liblzma.a (static) ==="
cd "$WORK_DIR"
LZMA_VER="5.4.6"
if [ ! -f "xz-${LZMA_VER}.tar.xz" ]; then
    curl -sLo "xz-${LZMA_VER}.tar.xz" \
        "https://github.com/tukaani-project/xz/releases/download/v${LZMA_VER}/xz-${LZMA_VER}.tar.xz"
fi
rm -rf "xz-${LZMA_VER}"
tar -xf "xz-${LZMA_VER}.tar.xz"
cd "xz-${LZMA_VER}"
./configure --host=aarch64-linux-android \
    --prefix="$WORK_DIR/xz-install" \
    CC="$CC" \
    AR="$AR" \
    --enable-static --disable-shared \
    --disable-xz --disable-xzdec \
    --disable-lzmadec --disable-lzmainfo \
    --disable-lzma-links --disable-scripts --disable-doc \
    --with-pic
make -j$(nproc)
make install
echo "=== liblzma.a built! ==="

echo "=== [2/6] Fetching payload-dumper-go source ==="
cd "$WORK_DIR"
if [ -d "payload-dumper-go" ]; then
    cd payload-dumper-go && git pull && cd ..
else
    git clone --depth=1 "$PAYLOAD_REPO"
fi

echo "=== [3/6] Applying CGO patch ==="
cd payload-dumper-go

# Remove pure-Go xz import and replace with CGO-based call
# Line 18: remove the import line for ulikunitz/xz
sed -i '/"github.com\/ulikunitz\/xz"/d' payload.go

# Replace xz.NewReader(teeReader) with newXzReader(teeReader)
sed -i 's/xz\.NewReader(teeReader)/newXzReader(teeReader)/g' payload.go

# Remove xz references from go.mod if present
go mod edit -droprequire github.com/ulikunitz/xz 2>/dev/null || true

# Copy our CGO wrapper into the source tree
cp "$SRC_DIR/build/cgo_lzma.go" ./

echo "=== [4/6] Setting up Go module ==="
# Ensure go.mod uses a Go version that supports our Go code
go mod tidy 2>/dev/null || true

echo "=== [5/6] Compiling with CGO + static liblzma ==="
export GO111MODULE=on
export CGO_ENABLED=1
export GOOS=android
export GOARCH=arm64
export CC="$CC"
export CGO_CFLAGS="-I$WORK_DIR/xz-install/include -O2"
export CGO_LDFLAGS="-L$WORK_DIR/xz-install/lib -llzma -static"

go build \
    -ldflags="-linkmode=external -extldflags='-static -L$WORK_DIR/xz-install/lib -llzma' -s -w" \
    -o libpayload-dumper-go.so \
    -tags nolzma \
    .

echo "=== [6/6] Copying binary to jniLibs ==="
file libpayload-dumper-go.so
cp libpayload-dumper-go.so "$OUTPUT_DIR/libpayload-dumper-go.so"
chmod 755 "$OUTPUT_DIR/libpayload-dumper-go.so"

echo ""
echo "=== DONE ==="
echo "Output: $OUTPUT_DIR/libpayload-dumper-go.so"
ls -lh "$OUTPUT_DIR/libpayload-dumper-go.so"
