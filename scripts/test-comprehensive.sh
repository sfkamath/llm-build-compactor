#!/usr/bin/env bash
#
# Comprehensive test script for llm-build-compactor
# Tests main project, gradle-plugin, and both test projects across Java versions
# Uses version-specific Gradle wrappers for Java 8, 11, and 17+
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

JAVA_VERSIONS=("temurin64-1.8.0.482" "11.0.17" "17.0.8" "21" "25")

# Results tracking
declare -A RESULTS

log() {
    echo "[$(date '+%H:%M:%S')] $1"
}

# Get the appropriate gradlew command for a Java version
get_gradlew() {
    local java_version="$1"
    case "$java_version" in
        temurin64-1.8.0.482)
            echo "$PROJECT_ROOT/gradlew-java8"
            ;;
        11.0.17)
            echo "$PROJECT_ROOT/gradlew-java11"
            ;;
        *)
            echo "$PROJECT_ROOT/gradlew"
            ;;
    esac
}

# Get Gradle version description
get_gradle_version() {
    local java_version="$1"
    case "$java_version" in
        temurin64-1.8.0.482)
            echo "Gradle 8.14.4"
            ;;
        11.0.17)
            echo "Gradle 8.5"
            ;;
        *)
            echo "Gradle 9.4.0"
            ;;
    esac
}

test_java_version() {
    local java_version="$1"
    local java_home="/Users/sfk/.jenv/versions/${java_version}"
    local version_key="java_${java_version//./_}"
    local gradlew_cmd
    gradlew_cmd=$(get_gradlew "$java_version")
    local gradle_ver
    gradle_ver=$(get_gradle_version "$java_version")
    
    export JAVA_HOME="$java_home"
    export PATH="$JAVA_HOME/bin:$PATH"
    
    log "=========================================="
    log "Testing Java ${java_version} (${gradle_ver})"
    log "=========================================="
    
    java -version 2>&1 | head -1
    
    # 1. Main project build + tests
    log "1. Main project (mvn clean install)..."
    cd "$PROJECT_ROOT"
    if ./mvnw clean install -q 2>&1; then
        RESULTS["${version_key}_main"]="PASS"
        log "   Main project: PASS"
    else
        RESULTS["${version_key}_main"]="FAIL"
        log "   Main project: FAIL"
        return 1
    fi
    
    # 2. Gradle plugin build + tests
    log "2. Gradle plugin (${gradlew_cmd##*/} clean build)..."
    cd "$PROJECT_ROOT/gradle-plugin"
    if "$gradlew_cmd" clean build -q 2>&1; then
        RESULTS["${version_key}_gradle_plugin"]="PASS"
        log "   Gradle plugin: PASS"
    else
        RESULTS["${version_key}_gradle_plugin"]="FAIL"
        log "   Gradle plugin: FAIL"
        return 1
    fi
    
    # 3. Maven test-project
    log "3. Test-project Maven (mvn clean verify)..."
    cd "$PROJECT_ROOT/test-project"
    local maven_output
    maven_output=$(mvn clean verify 2>&1)
    if echo "$maven_output" | grep -qE '"status"\s*:\s*"(SUCCESS|FAILED)"|LLM Build Compactor Summary'; then
        RESULTS["${version_key}_maven_test"]="PASS"
        log "   Maven test-project: PASS (plugin working)"
    else
        RESULTS["${version_key}_maven_test"]="FAIL"
        log "   Maven test-project: FAIL (no plugin output)"
        return 1
    fi

    # 4. Gradle test-project-gradle
    log "4. Test-project Gradle (${gradlew_cmd##*/} clean test)..."
    cd "$PROJECT_ROOT/test-project-gradle"
    local gradle_output
    gradle_output=$("$gradlew_cmd" clean test 2>&1)
    if echo "$gradle_output" | grep -qE '"status"\s*:\s*"(SUCCESS|FAILED)"|LLM Build Compactor Summary'; then
        RESULTS["${version_key}_gradle_test"]="PASS"
        log "   Gradle test-project: PASS (plugin working)"
    else
        RESULTS["${version_key}_gradle_test"]="FAIL"
        log "   Gradle test-project: FAIL (no plugin output)"
        return 1
    fi
    
    log "Java ${java_version}: ALL TESTS PASSED"
    echo ""
    return 0
}

print_summary() {
    log "=========================================="
    log "TEST SUMMARY"
    log "=========================================="
    
    printf "%-15s %-20s %-20s %-20s %-20s\n" "Java Version" "Main Project" "Gradle Plugin" "Maven Test" "Gradle Test"
    printf "%-15s %-20s %-20s %-20s %-20s\n" "------------" "------------" "-------------" "----------" "-----------"
    
    for java_version in "${JAVA_VERSIONS[@]}"; do
        local version_key="java_${java_version//./_}"
        printf "%-15s %-20s %-20s %-20s %-20s\n" \
            "$java_version" \
            "${RESULTS[${version_key}_main]:-N/A}" \
            "${RESULTS[${version_key}_gradle_plugin]:-N/A}" \
            "${RESULTS[${version_key}_maven_test]:-N/A}" \
            "${RESULTS[${version_key}_gradle_test]:-N/A}"
    done
}

# Main
main() {
    log "Starting comprehensive test suite..."
    log "Java versions: ${JAVA_VERSIONS[*]}"
    log "Gradle wrappers: Java 8->8.14.4, Java 11->8.5, Java 17+->9.4.0"
    echo ""
    
    local failed=0
    for java_version in "${JAVA_VERSIONS[@]}"; do
        if ! test_java_version "$java_version"; then
            ((failed++))
        fi
    done
    
    print_summary
    
    if [ "$failed" -gt 0 ]; then
        log "ERROR: $failed Java version(s) had failures"
        exit 1
    else
        log "SUCCESS: All tests passed for all Java versions"
        exit 0
    fi
}

main "$@"
