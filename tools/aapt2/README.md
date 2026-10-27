# ARM64 AAPT2 for On-Device Android Builds

This directory documents the native ARM64 (`aarch64`) AAPT2 (Android Asset Packaging Tool 2) binary packaged with CodeAgent for on-device compilation.

## Background & Problem

When running Gradle builds (`./gradlew assembleDebug`) on an Android device via PRoot Linux (such as Ubuntu 24.04 LTS), Android Gradle Plugin (AGP) normally downloads `aapt2` from Google Maven (`com.android.tools.build:aapt2:<version>-linux.jar`).

However, Google Maven only distributes Linux binaries for **x86_64** architecture. When executed on ARM64 (`aarch64`) hardware, the x86_64 binary fails with:
```
Exec format error
```

Since the rest of the Android build pipeline—Gradle, `kotlinc`, KSP, D8, R8, and the Java compiler—runs entirely on the JVM (Java bytecode), **AAPT2 is the only native binary in the entire Android compilation toolchain.** Providing a native ARM64 AAPT2 executable completely eliminates the architecture mismatch.

## Binary Details

- **Location:** `tools/bin/arm64-v8a/aapt2`
- **Architecture:** `ELF 64-bit LSB pie executable, ARM aarch64, version 1 (GNU/Linux)`
- **Target SDK Compatibility:** Android Platform Tools 37.0.0 (compatible with `compileSdk = 37`)
- **Linkage:** Dynamically linked against standard GNU/Linux glibc:
  - `libz.so.1` (zlib)
  - `libstdc++.so.6` (standard C++ library)
  - `libm.so.6` (math library)
  - `libgcc_s.so.1` (GCC runtime)
  - `libc.so.6` (glibc 2.31+)

These libraries are pre-installed in CodeAgent's Ubuntu 24.04 LTS userland (`libc6`, `libstdc++6`, `zlib1g`).

## How Gradle Uses This Binary

AGP accepts the property `android.aapt2FromMavenOverride` pointing to a local AAPT2 binary:

1. **Automatic Detection in `settings.gradle.kts`:**
   When Gradle runs on an `aarch64` or `arm64` JVM, `settings.gradle.kts` automatically sets:
   ```kotlin
   gradle.startParameter.projectProperties["android.aapt2FromMavenOverride"] = bundledAapt2.absolutePath
   ```
2. **Helper Script (`./build.sh`):**
   When `./build.sh` detects `uname -m` as `aarch64` or `arm64`, it automatically passes:
   ```bash
   -Pandroid.aapt2FromMavenOverride=tools/bin/arm64-v8a/aapt2
   ```
3. **Manual Override:**
   You can also specify it explicitly in `gradle.properties`:
   ```properties
   android.aapt2FromMavenOverride=/path/to/code_agent/tools/bin/arm64-v8a/aapt2
   ```

## Compiling From Source

If you wish to recompile AAPT2 from AOSP source or update to a newer version:
Run the compilation script:
```bash
./tools/build-aapt2-arm64.sh
```
See `tools/build-aapt2-arm64.sh` for build instructions using CMake.
