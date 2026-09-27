#!/usr/bin/env bash
#
# Install script for CodeAgent Android Application
#
# Provides two install methods:
#   1. Direct installation via ADB (USB or Wi-Fi debugging)
#   2. Local Python HTTP server with an instant Terminal QR Code for phone camera scanning
#
# Usage:
#   ./install.sh [options]
#
# Options:
#   -a, --adb           Install directly to connected device/emulator via adb
#   -s, --server, -q    Start local Python web server and display terminal QR code
#   -b, --build         Build/rebuild APK before installing
#   -r, --release       Use release APK instead of debug APK
#   -p, --port <port>   Port for Python web server (default: 8080)
#   -i, --ip <ip>       Override advertised local IP address for QR code
#   -d, --device <id>   Specific ADB target device serial
#   -l, --launch        Automatically launch app after ADB installation
#   -h, --help          Show this help message
#

set -euo pipefail

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
CodeAgent Installer

Install CodeAgent on your Android device via ADB or local Wi-Fi QR code.

Usage:
  ./install.sh [options]

Options:
  -a, --adb             Install directly via ADB (USB / Wi-Fi debugging)
  -s, --server, -q, --qr Start local HTTP server with terminal QR code
  -b, --build           Build or rebuild APK before installing
  -r, --release         Use release APK (default: debug APK)
  -p, --port <port>     Port for QR code server (default: 8080)
  -i, --ip <address>    Override advertised IP address in QR code
  -d, --device <serial> Target specific ADB device serial
  -l, --launch          Launch CodeAgent automatically after ADB install
  -h, --help            Display this help message

Examples:
  ./install.sh                  # Interactive choice (ADB or QR Code server)
  ./install.sh --adb            # Install directly via ADB
  ./install.sh --qr             # Display terminal QR code for phone scanning
  ./install.sh --build --qr     # Build APK first, then display QR code
  ./install.sh --adb --launch   # Install via ADB and launch app immediately
EOF
}

# -----------------------------------------------------------------------------
# Configuration & Defaults
# -----------------------------------------------------------------------------
MODE=""               # "adb", "server", or "" (interactive)
DO_BUILD=false
USE_RELEASE=false
SERVER_PORT=8080
SERVER_IP=""
TARGET_DEVICE=""
AUTO_LAUNCH=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        -a|--adb)
            MODE="adb"
            shift
            ;;
        -s|--server|-q|--qr)
            MODE="server"
            shift
            ;;
        -b|--build)
            DO_BUILD=true
            shift
            ;;
        -r|--release)
            USE_RELEASE=true
            shift
            ;;
        -p|--port)
            SERVER_PORT="$2"
            shift 2
            ;;
        -i|--ip)
            SERVER_IP="$2"
            shift 2
            ;;
        -d|--device)
            TARGET_DEVICE="$2"
            shift 2
            ;;
        -l|--launch)
            AUTO_LAUNCH=true
            shift
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            show_help
            exit 1
            ;;
    esac
done

# -----------------------------------------------------------------------------
# Helper: Locate APK
# -----------------------------------------------------------------------------
find_apk() {
    if [ "$USE_RELEASE" = true ]; then
        if [ -f "app/build/outputs/apk/release/app-release.apk" ]; then
            echo "app/build/outputs/apk/release/app-release.apk"
            return 0
        elif [ -f "app/build/outputs/apk/release/app-release-unsigned.apk" ]; then
            echo "app/build/outputs/apk/release/app-release-unsigned.apk"
            return 0
        fi
    else
        if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
            echo "app/build/outputs/apk/debug/app-debug.apk"
            return 0
        fi
    fi

    # Fallback search for any .apk in build outputs
    local any_apk
    any_apk="$(find app/build/outputs/apk -type f -name "*.apk" 2>/dev/null | head -n 1 || true)"
    if [ -n "$any_apk" ]; then
        echo "$any_apk"
        return 0
    fi

    return 1
}

build_apk_if_needed() {
    local apk_path
    apk_path="$(find_apk || true)"

    if [ "$DO_BUILD" = true ] || [ -z "$apk_path" ]; then
        if [ "$DO_BUILD" = true ]; then
            log_info "Rebuilding APK as requested..."
        else
            log_warn "APK not found. Building APK now..."
        fi

        if [ ! -f "./build.sh" ]; then
            log_error "./build.sh not found. Run ./gradlew assembleDebug manually."
            exit 1
        fi

        if [ "$USE_RELEASE" = true ]; then
            ./build.sh --release
        else
            ./build.sh --debug
        fi

        apk_path="$(find_apk || true)"
        if [ -z "$apk_path" ]; then
            log_error "Build succeeded but no APK output was detected."
            exit 1
        fi
    fi

    echo "$apk_path"
}

# -----------------------------------------------------------------------------
# Method 1: ADB Installation
# -----------------------------------------------------------------------------
install_via_adb() {
    local apk_file="$1"

    echo ""
    echo -e "${COLOR_BOLD}--- Installing via ADB ---${COLOR_RESET}"

    if ! command -v adb &>/dev/null; then
        log_error "ADB command not found in PATH."
        echo -e "To install ADB on Ubuntu/Debian:  ${COLOR_CYAN}sudo apt install adb${COLOR_RESET}"
        echo -e "Or configure Android SDK platform-tools in your PATH."
        exit 1
    fi

    # Start adb server silently if not running
    adb start-server &>/dev/null || true

    # Query connected devices
    local devices_raw
    devices_raw="$(adb devices -l | sed '1d' | grep -v '^$' || true)"

    if [ -z "$devices_raw" ]; then
        log_warn "No connected Android devices or emulators detected via ADB."
        echo ""
        echo -e "${COLOR_BOLD}Troubleshooting ADB Connection:${COLOR_RESET}"
        echo "  1. Connect your Android device via USB cable."
        echo "  2. On device: Enable Developer Options (Settings > About Phone > tap 'Build Number' 7 times)."
        echo "  3. On device: Enable 'USB Debugging' under Developer Options."
        echo "  4. Unlock phone and tap 'Allow USB Debugging' on the popup."
        echo "  5. Or connect wirelessly: adb connect <phone-ip>:<port>"
        echo ""
        read -r -p "Would you like to switch to Wi-Fi QR Code Server instead? [Y/n]: " switch_choice
        case "${switch_choice:-Y}" in
            [yY]|[yY][eE][sS])
                install_via_server "$apk_file"
                return 0
                ;;
            *)
                log_error "Aborted ADB installation."
                exit 1
                ;;
        esac
    fi

    # Device selection
    local device_args=()
    local device_count
    device_count="$(echo "$devices_raw" | grep -c . || true)"

    if [ -n "$TARGET_DEVICE" ]; then
        device_args=("-s" "$TARGET_DEVICE")
        log_info "Targeting device: ${COLOR_CYAN}$TARGET_DEVICE${COLOR_RESET}"
    elif [ "$device_count" -eq 1 ]; then
        local dev_id
        dev_id="$(echo "$devices_raw" | awk '{print $1}')"
        device_args=("-s" "$dev_id")
        log_info "Connected device: ${COLOR_CYAN}$dev_id${COLOR_RESET}"
    else
        echo -e "${COLOR_BOLD}Multiple devices connected:${COLOR_RESET}"
        local i=1
        declare -A dev_map
        while IFS= read -r dev_line; do
            local d_id
            d_id="$(echo "$dev_line" | awk '{print $1}')"
            local d_model
            d_model="$(echo "$dev_line" | grep -o 'model:[^ ]*' | cut -d':' -f2 || echo "device")"
            echo "  [$i] $d_id ($d_model)"
            dev_map[$i]="$d_id"
            ((i++))
        done <<< "$devices_raw"

        read -r -p "Select device [1-$((i-1))]: " sel
        if [[ -n "${dev_map[$sel]:-}" ]]; then
            device_args=("-s" "${dev_map[$sel]}")
            log_info "Selected device: ${COLOR_CYAN}${dev_map[$sel]}${COLOR_RESET}"
        else
            log_error "Invalid selection."
            exit 1
        fi
    fi

    log_info "Pushing and installing APK ($apk_file)..."
    if adb "${device_args[@]}" install -r -d "$apk_file"; then
        log_success "CodeAgent installed successfully via ADB!"

        if [ "$AUTO_LAUNCH" = true ]; then
            log_info "Launching CodeAgent on device..."
            adb "${device_args[@]}" shell am start -n com.codeagent.app/.MainActivity &>/dev/null || true
        else
            echo ""
            read -r -p "Would you like to launch CodeAgent on the device now? [Y/n]: " launch_choice
            case "${launch_choice:-Y}" in
                [yY]|[yY][eE][sS])
                    log_info "Launching CodeAgent..."
                    adb "${device_args[@]}" shell am start -n com.codeagent.app/.MainActivity &>/dev/null || true
                    log_success "Application launched!"
                    ;;
            esac
        fi
    else
        log_error "Failed to install APK via ADB."
        exit 1
    fi
}

# -----------------------------------------------------------------------------
# Method 2: Local Web Server with Terminal QR Code
# -----------------------------------------------------------------------------
install_via_server() {
    local apk_file="$1"

    if ! command -v python3 &>/dev/null; then
        log_error "Python 3 is required to run the local install server."
        exit 1
    fi

    # Ensure qrcode library is available for optimal ASCII rendering
    if ! python3 -c "import qrcode" 2>/dev/null; then
        log_info "Installing python 'qrcode' for clean terminal QR rendering..."
        python3 -m pip install --break-system-packages --user --quiet qrcode 2>/dev/null || true
    fi

    local server_script="scripts/apk_server.py"
    if [ ! -f "$server_script" ]; then
        log_error "$server_script not found in $SCRIPT_DIR"
        exit 1
    fi

    local server_args=("--apk" "$apk_file" "--port" "$SERVER_PORT")
    if [ -n "$SERVER_IP" ]; then
        server_args+=("--ip" "$SERVER_IP")
    fi

    python3 "$server_script" "${server_args[@]}"
}

# -----------------------------------------------------------------------------
# Main Execution
# -----------------------------------------------------------------------------
echo -e "${COLOR_BOLD}======================================================${COLOR_RESET}"
echo -e "${COLOR_BOLD}              CodeAgent Install Tool                  ${COLOR_RESET}"
echo -e "${COLOR_BOLD}======================================================${COLOR_RESET}"

APK_PATH="$(build_apk_if_needed)"
APK_SIZE="$(du -h "$APK_PATH" | awk '{print $1}')"

log_info "Target APK: ${COLOR_CYAN}$APK_PATH${COLOR_RESET} (${COLOR_BOLD}$APK_SIZE${COLOR_RESET})"

# If mode specified via CLI flags, run it directly
if [ "$MODE" = "adb" ]; then
    install_via_adb "$APK_PATH"
    exit 0
elif [ "$MODE" = "server" ]; then
    install_via_server "$APK_PATH"
    exit 0
fi

# Interactive Menu
echo ""
echo -e "${COLOR_BOLD}Select installation method:${COLOR_RESET}"
echo -e "  ${COLOR_CYAN}[1]${COLOR_RESET} ${COLOR_BOLD}Install via ADB${COLOR_RESET} (USB cable or Wi-Fi debugging)"
echo -e "  ${COLOR_CYAN}[2]${COLOR_RESET} ${COLOR_BOLD}Local Web Server + Terminal QR Code${COLOR_RESET} (Scan with phone camera)"
echo -e "  ${COLOR_CYAN}[3]${COLOR_RESET} Rebuild APK and choose install method"
echo -e "  ${COLOR_CYAN}[4]${COLOR_RESET} Exit"
echo ""

read -r -p "Enter choice [1-4] (default: 2): " choice
choice="${choice:-2}"

case "$choice" in
    1)
        install_via_adb "$APK_PATH"
        ;;
    2)
        install_via_server "$APK_PATH"
        ;;
    3)
        DO_BUILD=true
        APK_PATH="$(build_apk_if_needed)"
        echo ""
        read -r -p "Choose install method: [1] ADB or [2] QR Code Server: " post_build_choice
        if [ "$post_build_choice" = "1" ]; then
            install_via_adb "$APK_PATH"
        else
            install_via_server "$APK_PATH"
        fi
        ;;
    4|q|Q)
        echo "Exiting."
        exit 0
        ;;
    *)
        log_error "Invalid selection."
        exit 1
        ;;
esac
