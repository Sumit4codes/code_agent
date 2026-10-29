#!/usr/bin/env bash
#
# check-env.sh
#
# Environment Diagnostic Tool for Koda Dev
# Checks prerequisites for building Koda Dev on-device (ARM64/Android) or on host.
#

set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Formatting
if [ -t 1 ]; then
    BOLD="\033[1m"
    RESET="\033[0m"
    GREEN="\033[32m"
    YELLOW="\033[33m"
    RED="\033[31m"
    CYAN="\033[36m"
else
    BOLD=""
    RESET=""
    GREEN=""
    YELLOW=""
    RED=""
    CYAN=""
fi

echo -e "${BOLD}======================================================${RESET}"
echo -e "${BOLD}      Koda Dev On-Device Build Environment Check     ${RESET}"
echo -e "${BOLD}======================================================${RESET}"
echo ""

ALL_PASS=true
WARNINGS=0
ERRORS=0

pass() {
    echo -e "  ${GREEN}✔${RESET} ${BOLD}$1${RESET}: $2"
}

warn() {
    echo -e "  ${YELLOW}⚠${RESET} ${BOLD}$1${RESET}: $2"
    WARNINGS=$((WARNINGS + 1))
}

fail() {
    echo -e "  ${RED}✖${RESET} ${BOLD}$1${RESET}: $2"
    ERRORS=$((ERRORS + 1))
    ALL_PASS=false
}

# 1. Architecture Check
echo -e "${CYAN}[1/5] Host Architecture${RESET}"
ARCH="$(uname -m)"
IS_ARM64=false
case "$ARCH" in
    aarch64|arm64)
        IS_ARM64=true
        pass "Architecture" "$ARCH (ARM 64-bit)"
        ;;
    x86_64|amd64)
        pass "Architecture" "$ARCH (Standard desktop/server)"
        ;;
    *)
        warn "Architecture" "$ARCH (Unusual architecture, verify AGP support)"
        ;;
esac
echo ""

# 2. JDK 21 Detection
echo -e "${CYAN}[2/5] Java Development Kit (JDK 21)${RESET}"
JAVA_BIN=""
JAVAC_BIN=""
JAVA_VERSION=""

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_BIN="$JAVA_HOME/bin/java"
    JAVAC_BIN="$JAVA_HOME/bin/javac"
elif command -v java &>/dev/null; then
    JAVA_BIN="$(command -v java)"
    JAVAC_BIN="$(command -v javac 2>/dev/null || true)"
else
    # Check standard install locations
    for cand in "$HOME/.local/opt/jdk-21" "/usr/lib/jvm/java-21-openjdk-amd64" "/usr/lib/jvm/java-21-openjdk-arm64" "/usr/lib/jvm/java-21-openjdk"; do
        if [ -x "$cand/bin/java" ]; then
            JAVA_BIN="$cand/bin/java"
            JAVAC_BIN="$cand/bin/javac"
            export JAVA_HOME="$cand"
            break
        fi
    done
fi

if [ -n "$JAVA_BIN" ] && [ -x "$JAVA_BIN" ]; then
    JAVA_VERSION="$("$JAVA_BIN" -version 2>&1 | awk -F '"' '/version/ {print $2}')"
    MAJOR="$(echo "$JAVA_VERSION" | cut -d'.' -f1)"
    if [ "$MAJOR" = "1" ]; then
        MAJOR="$(echo "$JAVA_VERSION" | cut -d'.' -f2)"
    fi

    if [ "$MAJOR" = "21" ]; then
        pass "JDK Version" "Java $JAVA_VERSION ($JAVA_BIN)"
    else
        warn "JDK Version" "Java $JAVA_VERSION detected. AGP 9.4 strictly requires JDK 21."
        if [ "$IS_ARM64" = true ]; then
            echo -e "      ${YELLOW}→ Install via: apt update && apt install -y openjdk-21-jdk-headless${RESET}"
        fi
    fi
else
    fail "JDK" "No Java installation found in PATH or JAVA_HOME."
    if [ "$IS_ARM64" = true ]; then
        echo -e "      ${RED}→ Install via: apt update && apt install -y openjdk-21-jdk-headless${RESET}"
    fi
fi
echo ""

# 3. Android SDK & Platform API 37
echo -e "${CYAN}[3/5] Android SDK (Platform API 37)${RESET}"
SDK_DIR=""
if [ -f "$REPO_ROOT/local.properties" ]; then
    SDK_DIR="$(grep -E '^\s*sdk\.dir\s*=' "$REPO_ROOT/local.properties" 2>/dev/null | cut -d'=' -f2- | tr -d ' \r\t' || true)"
fi

if [ -z "$SDK_DIR" ] || [ ! -d "$SDK_DIR" ]; then
    if [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME" ]; then
        SDK_DIR="$ANDROID_HOME"
    elif [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -d "$ANDROID_SDK_ROOT" ]; then
        SDK_DIR="$ANDROID_SDK_ROOT"
    fi
fi

if [ -n "$SDK_DIR" ] && [ -d "$SDK_DIR" ]; then
    pass "Android SDK Path" "$SDK_DIR"
    
    # Check for platform API 37
    PLATFORM_JAR="$SDK_DIR/platforms/android-37/android.jar"
    if [ -f "$PLATFORM_JAR" ]; then
        pass "Platform API 37" "android.jar verified ($PLATFORM_JAR)"
    else
        warn "Platform API 37" "platforms/android-37/android.jar not found in $SDK_DIR."
        echo -e "      ${YELLOW}→ Required for compileSdk = 37. Download android-37 platform via sdkmanager.${RESET}"
    fi
else
    fail "Android SDK" "Android SDK not found (checked local.properties, ANDROID_HOME, ANDROID_SDK_ROOT)."
    echo -e "      ${RED}→ Set ANDROID_HOME or configure sdk.dir in local.properties.${RESET}"
fi
echo ""

# 4. AAPT2 ARM64 Binary
echo -e "${CYAN}[4/5] AAPT2 Resource Packaging Tool${RESET}"
BUNDLED_AAPT2="$REPO_ROOT/tools/bin/arm64-v8a/aapt2"

if [ -f "$BUNDLED_AAPT2" ]; then
    if [ -x "$BUNDLED_AAPT2" ]; then
        AAPT2_ARCH="$(file "$BUNDLED_AAPT2" 2>/dev/null | grep -o 'ARM aarch64' || true)"
        if [ -n "$AAPT2_ARCH" ]; then
            pass "ARM64 AAPT2" "Bundled binary ready at $BUNDLED_AAPT2 ($AAPT2_ARCH)"
        else
            pass "AAPT2 Binary" "Bundled binary exists at $BUNDLED_AAPT2"
        fi
    else
        warn "ARM64 AAPT2" "Found $BUNDLED_AAPT2 but it lacks executable permission. Run: chmod +x $BUNDLED_AAPT2"
    fi
else
    if [ "$IS_ARM64" = true ]; then
        fail "ARM64 AAPT2" "Bundled ARM64 aapt2 not found at $BUNDLED_AAPT2."
        echo -e "      ${RED}→ Compile or restore via: ./tools/build-aapt2-arm64.sh${RESET}"
    else
        pass "AAPT2" "x86_64 host: standard Google Maven AAPT2 artifacts will be used."
    fi
fi
echo ""

# 5. System RAM & Swap (LMK Protection)
echo -e "${CYAN}[5/5] Memory & Low Memory Killer (LMK) Protection${RESET}"
if [ -f "/proc/meminfo" ]; then
    TOTAL_MEM_KB="$(awk '/MemTotal/ {print $2}' /proc/meminfo)"
    AVAIL_MEM_KB="$(awk '/MemAvailable/ {print $2}' /proc/meminfo || true)"
    SWAP_TOTAL_KB="$(awk '/SwapTotal/ {print $2}' /proc/meminfo || true)"

    TOTAL_MEM_MB=$((TOTAL_MEM_KB / 1024))
    AVAIL_MEM_MB=$(( ${AVAIL_MEM_KB:-0} / 1024 ))
    SWAP_TOTAL_MB=$(( ${SWAP_TOTAL_KB:-0} / 1024 ))

    if [ "$TOTAL_MEM_MB" -ge 4000 ]; then
        pass "System RAM" "${TOTAL_MEM_MB} MB total (~${AVAIL_MEM_MB} MB available)"
    else
        warn "System RAM" "${TOTAL_MEM_MB} MB total. Android builds require ~4GB RAM to prevent LMK kills."
        echo -e "      ${YELLOW}→ Use: ./build.sh (which enables --no-daemon on ARM64) to save memory.${RESET}"
    fi

    if [ "$SWAP_TOTAL_MB" -ge 1000 ]; then
        pass "Swap Space" "${SWAP_TOTAL_MB} MB swap active"
    else
        warn "Swap Space" "Low or no swap space (${SWAP_TOTAL_MB} MB). A 2GB+ swap file is recommended on mobile."
    fi
else
    pass "Memory" "Could not read /proc/meminfo (non-Linux environment)"
fi
echo ""

# Summary
echo -e "${BOLD}======================================================${RESET}"
if [ "$ALL_PASS" = true ] && [ "$ERRORS" -eq 0 ]; then
    if [ "$WARNINGS" -eq 0 ]; then
        echo -e "${GREEN}${BOLD}✔ Environment Check PASSED! System is ready to build Koda Dev.${RESET}"
    else
        echo -e "${YELLOW}${BOLD}✔ Environment Check PASSED with $WARNINGS warning(s). Check notes above.${RESET}"
    fi
    exit 0
else
    echo -e "${RED}${BOLD}✖ Environment Check FAILED with $ERRORS error(s) and $WARNINGS warning(s).${RESET}"
    echo -e "Review the instructions above to resolve the missing requirements."
    exit 1
fi
