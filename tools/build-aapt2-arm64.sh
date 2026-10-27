#!/usr/bin/env bash
#
# build-aapt2-arm64.sh
#
# Guide and build script to compile AAPT2 for ARM64 (aarch64) Linux.
# Can be run natively on an aarch64 machine / Ubuntu PRoot, or cross-compiled on x86_64.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TARGET_DIR="$REPO_ROOT/tools/bin/arm64-v8a"
BUILD_DIR="/tmp/aapt2-arm64-build"

echo "========================================================"
echo " AAPT2 ARM64 (aarch64) Linux Builder"
echo "========================================================"

ARCH="$(uname -m)"
echo "Current host architecture: $ARCH"

# Determine build mode
if [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then
    echo "Building natively on ARM64 Linux..."
    NATIVE_BUILD=true
else
    echo "Cross-compiling for ARM64 from $ARCH..."
    NATIVE_BUILD=false
fi

show_deps() {
    cat << 'EOF'
Required packages on Ubuntu/Debian:
  sudo apt update
  sudo apt install -y build-essential cmake ninja-build git \
       protobuf-compiler libprotobuf-dev libexpat1-dev libpng-dev \
       zlib1g-dev libzopfli-dev pkg-config

For cross-compiling from x86_64:
  sudo apt install -y gcc-aarch64-linux-gnu g++-aarch64-linux-gnu
EOF
}

if [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
    echo "Usage: ./tools/build-aapt2-arm64.sh [--deps]"
    echo ""
    echo "Options:"
    echo "  --deps       Show required apt build dependencies"
    echo "  --help, -h   Show this help message"
    exit 0
fi

if [ "${1:-}" = "--deps" ]; then
    show_deps
    exit 0
fi

mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

echo "Step 1: Checking build tools..."
if ! command -v cmake &>/dev/null; then
    echo "Error: cmake is required."
    show_deps
    exit 1
fi

echo "Step 2: Fetching standalone AOSP build-tools repository..."
if [ ! -d "android-build-tools" ]; then
    git clone --recurse-submodules --depth 1 https://github.com/termux/android-build-tools.git android-build-tools
else
    echo "Using existing repository at $BUILD_DIR/android-build-tools"
fi

cd android-build-tools

echo "Step 3: Configuring CMake build..."
CMAKE_ARGS=(
    "-B" "build"
    "-DCMAKE_BUILD_TYPE=Release"
)

if [ "$NATIVE_BUILD" = false ]; then
    if command -v aarch64-linux-gnu-gcc &>/dev/null; then
        CMAKE_ARGS+=(
            "-DCMAKE_C_COMPILER=aarch64-linux-gnu-gcc"
            "-DCMAKE_CXX_COMPILER=aarch64-linux-gnu-g++"
            "-DCMAKE_SYSTEM_NAME=Linux"
            "-DCMAKE_SYSTEM_PROCESSOR=aarch64"
        )
    else
        echo "Error: Cross-compiler aarch64-linux-gnu-gcc not found."
        show_deps
        exit 1
    fi
fi

cmake "${CMAKE_ARGS[@]}"

echo "Step 4: Compiling aapt2..."
cmake --build build --target aapt2 -j"$(nproc)"

if [ -f "build/aapt2" ] || [ -f "build/tools/aapt2/aapt2" ]; then
    OUTPUT_BIN="$(find build -type f -name aapt2 -perm /111 | head -n 1)"
    mkdir -p "$TARGET_DIR"
    cp "$OUTPUT_BIN" "$TARGET_DIR/aapt2"
    chmod +x "$TARGET_DIR/aapt2"
    echo "========================================================"
    echo "Successfully built ARM64 aapt2:"
    file "$TARGET_DIR/aapt2"
    echo "Installed to: $TARGET_DIR/aapt2"
    echo "========================================================"
else
    echo "Error: Build succeeded but aapt2 binary was not found in build directory."
    exit 1
fi
