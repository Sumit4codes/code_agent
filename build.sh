#!/usr/bin/env bash
#
# Build script for CodeAgent Android Application
#
# Usage:
#   ./build.sh [options] [-- gradle options]
#
# Options:
#   -d, --debug        Build debug APK (default)
#   -r, --release      Build release APK
#   -b, --bundle       Build Android App Bundle (.aab)
#   -c, --clean        Run clean task before building
#   -i, --install      Install APK to connected device/emulator via adb
#   -a, --all          Build all variants (assemble)
#   -o, --offline      Execute Gradle in offline mode
#   -v, --verbose      Run Gradle with --info
#   -s, --stacktrace   Run Gradle with --stacktrace
#   -h, --help         Show this help message
#

set -euo pipefail

# Determine repository root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Text styles and colors
if [ -t 1 ]; then
    COLOR_RESET="\033[0m"
    COLOR_BOLD="\033[1m"
    COLOR_GREEN="\033[32m"
    COLOR_YELLOW="\033[33m"
    COLOR_BLUE="\033[34m"
    COLOR_RED="\033[31m"
    COLOR_CYAN="\033[36m"
else
    COLOR_RESET=""
    COLOR_BOLD=""
    COLOR_GREEN=""
    COLOR_YELLOW=""
    COLOR_BLUE=""
    COLOR_RED=""
    COLOR_CYAN=""
fi

log_info() {
    echo -e "${COLOR_BLUE}[INFO]${COLOR_RESET} $*"
}

log_success() {
    echo -e "${COLOR_GREEN}[SUCCESS]${COLOR_RESET} $*"
}

log_warn() {
    echo -e "${COLOR_YELLOW}[WARN]${COLOR_RESET} $*"
}

log_error() {
    echo -e "${COLOR_RED}[ERROR]${COLOR_RESET} $*"
}

show_help() {
    cat << 'EOF'
CodeAgent Build Script

Usage:
  ./build.sh [options] [extra gradle args...]

Options:
  -d, --debug        Build debug APK (default: :app:assembleDebug)
  -r, --release      Build release APK (:app:assembleRelease)
  -b, --bundle       Build Android App Bundle (.aab)
  -c, --clean        Run Gradle clean before building
  -i, --install      Install debug/release APK to connected device via adb
  -a, --all          Build all build variants (assemble)
  -o, --offline      Run Gradle with --offline
  -v, --verbose      Run Gradle with --info
  -s, --stacktrace   Run Gradle with --stacktrace
  -h, --help         Display this help message

Examples:
  ./build.sh                  # Build debug APK
  ./build.sh --clean          # Clean and build debug APK
  ./build.sh --release        # Build release APK
  ./build.sh --bundle         # Build Android App Bundle
  ./build.sh --install        # Build and install on connected device
  ./build.sh -- --dry-run     # Pass extra args directly to Gradle
EOF
}

# -----------------------------------------------------------------------------
# JDK 21 Environment Detection
# -----------------------------------------------------------------------------
setup_jdk() {
    local detected_jdk=""
    local current_major=""

    get_java_major() {
        local j_bin="$1"
        if [ -x "$j_bin" ]; then
            local ver
            ver="$("$j_bin" -version 2>&1 | awk -F '"' '/version/ {print $2}')"
            local major
            major="$(echo "$ver" | cut -d'.' -f1)"
            if [ "$major" = "1" ]; then
                major="$(echo "$ver" | cut -d'.' -f2)"
            fi
            echo "$major"
        fi
    }

    # 1. Check current JAVA_HOME if set
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
        current_major="$(get_java_major "$JAVA_HOME/bin/java")"
        if [ "$current_major" = "21" ]; then
            detected_jdk="$JAVA_HOME"
        else
            log_warn "Current JAVA_HOME is JDK $current_major ($JAVA_HOME). AGP requires JDK 21."
        fi
    fi

    # 2. Search candidate locations for JDK 21
    if [ -z "$detected_jdk" ]; then
        local candidates=(
            "/usr/lib/jvm/java-21-openjdk-amd64"
            "/usr/lib/jvm/java-21-openjdk-arm64"
            "/usr/lib/jvm/java-21-openjdk"
            "/usr/lib/jvm/temurin-21-jdk-amd64"
            "/usr/lib/jvm/temurin-21-jdk"
            "$HOME/.local/opt/jdk-21"
            "$HOME/.sdkman/candidates/java/21"*
            "/opt/jdk-21"
            "/opt/homebrew/opt/openjdk@21"
        )

        for cand in "${candidates[@]}"; do
            if [ -d "$cand" ] && [ -x "$cand/bin/javac" ]; then
                local cand_major
                cand_major="$(get_java_major "$cand/bin/java")"
                if [ "$cand_major" = "21" ]; then
                    detected_jdk="$cand"
                    break
                fi
            fi
        done
    fi

    # 3. Check update-java-alternatives if on Debian/Ubuntu
    if [ -z "$detected_jdk" ] && command -v update-java-alternatives &>/dev/null; then
        local alt_path
        alt_path="$(update-java-alternatives -l 2>/dev/null | grep '21' | head -n 1 | awk '{print $NF}')"
        if [ -n "$alt_path" ] && [ -d "$alt_path" ] && [ -x "$alt_path/bin/javac" ]; then
            detected_jdk="$alt_path"
        fi
    fi

    # 4. Apply detected JDK or fallback
    if [ -n "$detected_jdk" ]; then
        export JAVA_HOME="$detected_jdk"
        export PATH="$detected_jdk/bin:$PATH"
        log_info "Using JDK 21 at: ${COLOR_CYAN}$JAVA_HOME${COLOR_RESET}"
    else
        if [ -n "${JAVA_HOME:-}" ]; then
            log_warn "Proceeding with current JAVA_HOME=$JAVA_HOME (JDK 21 is strongly recommended)"
        elif command -v java &>/dev/null; then
            local sys_java
            sys_java="$(command -v java)"
            local sys_major
            sys_major="$(get_java_major "$sys_java")"
            log_warn "Proceeding with system Java (JDK $sys_major at $sys_java). JDK 21 is strongly recommended."
        else
            log_error "No Java installation found. Please install JDK 21 (Temurin)."
            exit 1
        fi
    fi
}

# -----------------------------------------------------------------------------
# Android SDK Detection
# -----------------------------------------------------------------------------
setup_android_sdk() {
    local sdk_dir=""

    # Check local.properties first
    if [ -f "local.properties" ]; then
        sdk_dir="$(grep -E '^\s*sdk\.dir\s*=' local.properties 2>/dev/null | cut -d'=' -f2- | tr -d ' \r\t' || true)"
    fi

    # Check environment variables
    if [ -z "$sdk_dir" ] || [ ! -d "$sdk_dir" ]; then
        if [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME" ]; then
            sdk_dir="$ANDROID_HOME"
        elif [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -d "$ANDROID_SDK_ROOT" ]; then
            sdk_dir="$ANDROID_SDK_ROOT"
        fi
    fi

    # Check standard filesystem paths
    if [ -z "$sdk_dir" ] || [ ! -d "$sdk_dir" ]; then
        local sdk_candidates=(
            "$HOME/Android/Sdk"
            "$HOME/Library/Android/sdk"
            "/usr/lib/android-sdk"
            "/opt/android-sdk"
        )
        for cand in "${sdk_candidates[@]}"; do
            if [ -d "$cand" ]; then
                sdk_dir="$cand"
                break
            fi
        done
    fi

    if [ -n "$sdk_dir" ] && [ -d "$sdk_dir" ]; then
        export ANDROID_HOME="$sdk_dir"
        if [ ! -f "local.properties" ]; then
            echo "sdk.dir=$sdk_dir" > local.properties
            log_info "Generated local.properties with sdk.dir=$sdk_dir"
        fi
        log_info "Android SDK found at: ${COLOR_CYAN}$sdk_dir${COLOR_RESET}"
    else
        log_warn "Could not verify Android SDK path. Build may fail if Android SDK is required."
    fi
}

# -----------------------------------------------------------------------------
# Parse Arguments
# -----------------------------------------------------------------------------
BUILD_VARIANT="debug"
BUILD_TYPE="apk"     # apk, bundle, or all
DO_CLEAN=false
DO_INSTALL=false
GRADLE_ARGS=()

while [[ $# -gt 0 ]]; do
    case "$1" in
        -d|--debug)
            BUILD_VARIANT="debug"
            shift
            ;;
        -r|--release)
            BUILD_VARIANT="release"
            shift
            ;;
        -b|--bundle)
            BUILD_TYPE="bundle"
            shift
            ;;
        -a|--all)
            BUILD_TYPE="all"
            shift
            ;;
        -c|--clean)
            DO_CLEAN=true
            shift
            ;;
        -i|--install)
            DO_INSTALL=true
            shift
            ;;
        -o|--offline)
            GRADLE_ARGS+=("--offline")
            shift
            ;;
        -v|--verbose)
            GRADLE_ARGS+=("--info")
            shift
            ;;
        -s|--stacktrace)
            GRADLE_ARGS+=("--stacktrace")
            shift
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        --)
            shift
            GRADLE_ARGS+=("$@")
            break
            ;;
        *)
            GRADLE_ARGS+=("$1")
            shift
            ;;
    esac
done

# -----------------------------------------------------------------------------
# Main Execution
# -----------------------------------------------------------------------------
echo -e "${COLOR_BOLD}======================================================${COLOR_RESET}"
echo -e "${COLOR_BOLD}              CodeAgent Build Tool                    ${COLOR_RESET}"
echo -e "${COLOR_BOLD}======================================================${COLOR_RESET}"

setup_jdk
setup_android_sdk

if [ ! -f "./gradlew" ]; then
    log_error "Gradle wrapper ./gradlew not found in $SCRIPT_DIR"
    exit 1
fi

chmod +x ./gradlew

# Construct Gradle task list
TASKS=()

if [ "$DO_CLEAN" = true ]; then
    TASKS+=("clean")
fi

if [ "$DO_INSTALL" = true ]; then
    if [ "$BUILD_VARIANT" = "release" ]; then
        TASKS+=(":app:installRelease")
    else
        TASKS+=(":app:installDebug")
    fi
elif [ "$BUILD_TYPE" = "all" ]; then
    TASKS+=("assemble")
elif [ "$BUILD_TYPE" = "bundle" ]; then
    if [ "$BUILD_VARIANT" = "release" ]; then
        TASKS+=(":app:bundleRelease")
    else
        TASKS+=(":app:bundleDebug")
    fi
else
    if [ "$BUILD_VARIANT" = "release" ]; then
        TASKS+=(":app:assembleRelease")
    else
        TASKS+=(":app:assembleDebug")
    fi
fi

log_info "Running tasks: ${COLOR_BOLD}${TASKS[*]}${COLOR_RESET} ${GRADLE_ARGS[*]:-}"
echo ""

START_TIME=$(date +%s)

# Run Gradle
set +e
./gradlew "${TASKS[@]}" "${GRADLE_ARGS[@]}"
GRADLE_STATUS=$?
set -e

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
echo -e "${COLOR_BOLD}------------------------------------------------------${COLOR_RESET}"

if [ $GRADLE_STATUS -ne 0 ]; then
    log_error "Build failed with exit code $GRADLE_STATUS after ${DURATION}s."
    exit $GRADLE_STATUS
fi

log_success "Build completed successfully in ${DURATION}s!"

# Locate and display output artifacts
echo ""
echo -e "${COLOR_BOLD}Artifacts:${COLOR_RESET}"

found_artifact=false

# Search for APKs
if [ -d "app/build/outputs/apk" ]; then
    while IFS= read -r -d '' apk_file; do
        found_artifact=true
        size=$(du -h "$apk_file" | awk '{print $1}')
        rel_path="${apk_file#./}"
        echo -e "  ${COLOR_GREEN}✔ APK:${COLOR_RESET}    $rel_path (${COLOR_BOLD}$size${COLOR_RESET})"
    done < <(find app/build/outputs/apk -type f -name "*.apk" -print0 2>/dev/null)
fi

# Search for Bundles
if [ -d "app/build/outputs/bundle" ]; then
    while IFS= read -r -d '' aab_file; do
        found_artifact=true
        size=$(du -h "$aab_file" | awk '{print $1}')
        rel_path="${aab_file#./}"
        echo -e "  ${COLOR_GREEN}✔ Bundle:${COLOR_RESET} $rel_path (${COLOR_BOLD}$size${COLOR_RESET})"
    done < <(find app/build/outputs/bundle -type f -name "*.aab" -print0 2>/dev/null)
fi

if [ "$found_artifact" = false ]; then
    log_info "No APK or AAB artifacts found (task may not have generated packaged outputs)."
fi

echo -e "${COLOR_BOLD}------------------------------------------------------${COLOR_RESET}"
