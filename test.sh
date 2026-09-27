#!/usr/bin/env bash
#
# Test script for CodeAgent Android Application
#
# Usage:
#   ./test.sh [options] [-- gradle options]
#
# Options:
#   -m, --module <name>   Run tests for specific module (e.g. core:agent, files, etc.)
#   -c, --class <filter>  Run specific test class or method pattern (e.g. PathSafetyTest)
#   -t, --test <filter>   Alias for -c, --class
#   -C, --clean           Clean test results before running
#   -f, --fail-fast       Stop execution after first test failure
#   -r, --report          Display paths to generated HTML reports
#   -v, --verbose         Run Gradle with --info
#   -s, --stacktrace      Run Gradle with --stacktrace
#   --rerun               Re-run all tests even if outputs are UP-TO-DATE
#   -h, --help            Show this help message
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
CodeAgent Test Runner

Usage:
  ./test.sh [options] [extra gradle args...]

Options:
  -m, --module <name>   Run unit tests for a specific module (e.g. core:agent, files, terminal)
  -c, --class <filter>  Run specific test class or method (e.g. PathSafetyTest, EditResolverTest)
  -t, --test <filter>   Alias for -c, --class
  -C, --clean           Run clean task before running tests
  -f, --fail-fast       Stop running tests after the first failure
  -r, --report          Display paths to HTML test reports
  -v, --verbose         Run Gradle with --info
  -s, --stacktrace      Run Gradle with --stacktrace
  --rerun               Force re-run of tests (--rerun-tasks)
  -h, --help            Display this help message

Examples:
  ./test.sh                             # Run all unit tests
  ./test.sh -m core:files               # Run tests in core/files module
  ./test.sh -m agent                    # Run tests in core/agent module (fuzzy match)
  ./test.sh -c PathSafetyTest           # Run a specific test class (auto-detects module)
  ./test.sh -m files -c PathSafetyTest  # Run specific test within module
  ./test.sh --fail-fast --report        # Stop on first failure and print report paths
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

    # 1. Check current JAVA_HOME
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

    # 3. Check update-java-alternatives
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
# Module Normalization & Class Resolution
# -----------------------------------------------------------------------------
normalize_module() {
    local input="$1"
    input="${input#:}"
    input="${input#/}"
    input="${input//\//:}"

    if [ -f "settings.gradle.kts" ]; then
        if grep -q "include(\":$input\")" settings.gradle.kts; then
            echo ":$input"
            return 0
        fi

        local matches
        matches="$(grep -o "include(\":[^\"]*${input}\")" settings.gradle.kts | sed 's/include("//;s/")//' || true)"
        local count
        count="$(echo "$matches" | grep -c . || true)"
        if [ "$count" -eq 1 ]; then
            echo "$matches"
            return 0
        elif [ "$count" -gt 1 ]; then
            local exact
            exact="$(echo "$matches" | grep -E "(:${input})$" | head -n 1 || true)"
            if [ -n "$exact" ]; then
                echo "$exact"
                return 0
            fi
            echo "$matches" | head -n 1
            return 0
        fi
    fi

    echo ":$input"
}

find_module_for_test() {
    local query="$1"
    local match
    match="$(find . -path "*/src/test/*" -type f \( -name "*${query}*.kt" -o -name "*${query}*.java" \) | head -n 1)"
    if [ -n "$match" ]; then
        local mod_path="${match#./}"
        mod_path="${mod_path%/src/test/*}"
        local gradle_mod=":${mod_path//\//:}"
        echo "$gradle_mod"
    fi
}

# -----------------------------------------------------------------------------
# Parse Arguments
# -----------------------------------------------------------------------------
TARGET_MODULE=""
TARGET_CLASS=""
DO_CLEAN=false
SHOW_REPORTS=false
GRADLE_ARGS=()

while [[ $# -gt 0 ]]; do
    case "$1" in
        -m|--module)
            TARGET_MODULE="$2"
            shift 2
            ;;
        -c|--class|-t|--test)
            TARGET_CLASS="$2"
            shift 2
            ;;
        -C|--clean)
            DO_CLEAN=true
            shift
            ;;
        -f|--fail-fast)
            GRADLE_ARGS+=("--fail-fast")
            shift
            ;;
        -r|--report)
            SHOW_REPORTS=true
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
        --rerun)
            GRADLE_ARGS+=("--rerun-tasks")
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
echo -e "${COLOR_BOLD}              CodeAgent Test Runner                   ${COLOR_RESET}"
echo -e "${COLOR_BOLD}======================================================${COLOR_RESET}"

setup_jdk

if [ ! -f "./gradlew" ]; then
    log_error "Gradle wrapper ./gradlew not found in $SCRIPT_DIR"
    exit 1
fi

chmod +x ./gradlew

# Resolve Gradle tasks
TASKS=()

if [ "$DO_CLEAN" = true ]; then
    TASKS+=("clean")
fi

RESOLVED_MOD_DIR=""

if [ -n "$TARGET_CLASS" ]; then
    test_filter="*$TARGET_CLASS*"
    if [[ "$TARGET_CLASS" == *"*"* ]]; then
        test_filter="$TARGET_CLASS"
    fi

    if [ -z "$TARGET_MODULE" ]; then
        auto_module="$(find_module_for_test "$TARGET_CLASS")"
        if [ -n "$auto_module" ]; then
            TARGET_MODULE="$auto_module"
            log_info "Auto-detected test module: ${COLOR_CYAN}$TARGET_MODULE${COLOR_RESET}"
        fi
    fi

    if [ -n "$TARGET_MODULE" ]; then
        resolved_module="$(normalize_module "$TARGET_MODULE")"
        RESOLVED_MOD_DIR="${resolved_module#:}"
        RESOLVED_MOD_DIR="${RESOLVED_MOD_DIR//://}"
        TASKS+=("${resolved_module}:testDebugUnitTest")
    else
        TASKS+=("testDebugUnitTest")
    fi
    GRADLE_ARGS+=("--tests" "$test_filter")
elif [ -n "$TARGET_MODULE" ]; then
    resolved_module="$(normalize_module "$TARGET_MODULE")"
    RESOLVED_MOD_DIR="${resolved_module#:}"
    RESOLVED_MOD_DIR="${RESOLVED_MOD_DIR//://}"
    TASKS+=("${resolved_module}:test")
else
    TASKS+=("test")
fi

log_info "Running tasks: ${COLOR_BOLD}${TASKS[*]}${COLOR_RESET} ${GRADLE_ARGS[*]:-}"
echo ""

START_TIME=$(date +%s)

set +e
./gradlew "${TASKS[@]}" "${GRADLE_ARGS[@]}"
GRADLE_STATUS=$?
set -e

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
echo -e "${COLOR_BOLD}------------------------------------------------------${COLOR_RESET}"
echo -e "${COLOR_BOLD}Test Execution Summary:${COLOR_RESET}"

# Parse XML test results using python3
SEARCH_DIR="."
if [ -n "$RESOLVED_MOD_DIR" ] && [ -d "$RESOLVED_MOD_DIR" ]; then
    SEARCH_DIR="$RESOLVED_MOD_DIR"
fi

if command -v python3 &>/dev/null; then
    SUMMARY_OUTPUT="$(python3 - "$SEARCH_DIR" "$TARGET_CLASS" << 'PYEOF'
import sys, glob, os, xml.etree.ElementTree as ET

search_dir = sys.argv[1] if len(sys.argv) > 1 and sys.argv[1] else "."
target_filter = sys.argv[2] if len(sys.argv) > 2 else ""

xml_files = sorted(glob.glob(f"{search_dir}/**/build/test-results/**/*.xml", recursive=True))
total_tests = 0
total_failed = 0
total_errors = 0
total_skipped = 0
total_time = 0.0
failures = []

for xml_file in xml_files:
    try:
        tree = ET.parse(xml_file)
        root = tree.getroot()
        if root.tag != 'testsuite':
            continue
        suite_name = root.attrib.get('name', os.path.basename(xml_file))
        
        # Check testcases
        suite_matches = target_filter in suite_name if target_filter else True
        for tc in root.findall('testcase'):
            tc_name = tc.attrib.get('name', 'unknown')
            tc_class = tc.attrib.get('classname', suite_name)
            
            if target_filter and not (target_filter in tc_class or target_filter in tc_name):
                continue

            total_tests += 1
            tc_time = float(tc.attrib.get('time', 0.0))
            total_time += tc_time

            fail_node = tc.find('failure')
            err_node = tc.find('error')
            skip_node = tc.find('skipped')

            if fail_node is not None:
                total_failed += 1
                msg = fail_node.attrib.get('message', (fail_node.text or '').strip().split('\n')[0] if fail_node.text else 'Failed')
                failures.append(f"{tc_class} > {tc_name}: {msg}")
            elif err_node is not None:
                total_errors += 1
                msg = err_node.attrib.get('message', (err_node.text or '').strip().split('\n')[0] if err_node.text else 'Error')
                failures.append(f"{tc_class} > {tc_name}: {msg}")
            elif skip_node is not None:
                total_skipped += 1
    except Exception:
        pass

passed = max(0, total_tests - total_failed - total_errors - total_skipped)
print(f"SUMMARY|{total_tests}|{passed}|{total_failed + total_errors}|{total_skipped}|{total_time:.2f}")
for f in failures:
    print(f"FAILURE|{f}")
PYEOF
    )"

    SUMMARY_LINE="$(echo "$SUMMARY_OUTPUT" | grep '^SUMMARY|' || true)"
    if [ -n "$SUMMARY_LINE" ]; then
        IFS='|' read -r _ TOTAL PASSED FAILED SKIPPED TIME <<< "$SUMMARY_LINE"
        echo -e "  Total Tests : ${COLOR_BOLD}$TOTAL${COLOR_RESET}"
        echo -e "  Passed      : ${COLOR_GREEN}${COLOR_BOLD}$PASSED${COLOR_RESET}"
        if [ "$FAILED" -gt 0 ]; then
            echo -e "  Failed      : ${COLOR_RED}${COLOR_BOLD}$FAILED${COLOR_RESET}"
        else
            echo -e "  Failed      : $FAILED"
        fi
        if [ "$SKIPPED" -gt 0 ]; then
            echo -e "  Skipped     : ${COLOR_YELLOW}$SKIPPED${COLOR_RESET}"
        fi
        echo -e "  Test Time   : ${TIME}s (Total runtime: ${DURATION}s)"

        FAILURES="$(echo "$SUMMARY_OUTPUT" | grep '^FAILURE|' || true)"
        if [ -n "$FAILURES" ]; then
            echo ""
            echo -e "${COLOR_RED}${COLOR_BOLD}Failures / Errors:${COLOR_RESET}"
            while IFS='|' read -r _ fail_detail; do
                echo -e "  ${COLOR_RED}✘${COLOR_RESET} $fail_detail"
            done <<< "$FAILURES"
        fi
    fi
fi

# Locate HTML reports
REPORTS=()
while IFS= read -r -d '' rpt; do
    REPORTS+=("${rpt#./}")
done < <(find "$SEARCH_DIR" -path "*/build/reports/tests/testDebugUnitTest/index.html" -print0 2>/dev/null)

if [ ${#REPORTS[@]} -gt 0 ]; then
    echo ""
    echo -e "${COLOR_BOLD}HTML Test Reports:${COLOR_RESET}"
    for rpt in "${REPORTS[@]}"; do
        echo -e "  ${COLOR_CYAN}➜${COLOR_RESET} $rpt"
    done
fi

echo -e "${COLOR_BOLD}------------------------------------------------------${COLOR_RESET}"

if [ $GRADLE_STATUS -eq 0 ]; then
    log_success "All tests passed successfully!"
    exit 0
else
    log_error "Test execution finished with failure (code $GRADLE_STATUS)."
    exit $GRADLE_STATUS
fi
