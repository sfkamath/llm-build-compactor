#!/usr/bin/env bash
#
# Comprehensive test script - tests all modules across all Java versions.
# Uses version-specific Gradle wrappers for Java 8, 11, and 17+.
# Usage: ./test-comprehensive.sh [--verbose]
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

source "$SCRIPT_DIR/config.sh"
parse_args "$@"

declare -A RESULTS

log() {
    echo "[$(date '+%H:%M:%S')] $1"
}

test_java_version() {
    local java_version="$1"
    local version_key="java_${java_version//./_}"
    local gradlew_cmd gradle_ver

    gradlew_cmd=$(get_gradlew "$java_version")
    gradle_ver=$(get_gradle_version "$java_version")

    export JAVA_HOME
    JAVA_HOME=$(get_java_home "$java_version")
    export PATH="$JAVA_HOME/bin:$PATH"

    log "=========================================="
    log "Testing Java ${java_version} (${gradle_ver})"
    log "=========================================="

    java -version 2>&1 | head -1

    log "1. Main project (mvn clean install)..."
    cd "$PROJECT_ROOT"
    if run_mvn ./mvnw clean install; then
        RESULTS["${version_key}_main"]="PASS"
        log "   Main project: PASS"
    else
        RESULTS["${version_key}_main"]="FAIL"
        log "   Main project: FAIL"
        return 1
    fi

    log "2. Gradle plugin (${gradlew_cmd##*/} clean build)..."
    cd "$PROJECT_ROOT/$DIR_GRADLE_PLUGIN"
    if run_gradle "$gradlew_cmd" clean build; then
        RESULTS["${version_key}_gradle_plugin"]="PASS"
        log "   Gradle plugin: PASS"
    else
        RESULTS["${version_key}_gradle_plugin"]="FAIL"
        log "   Gradle plugin: FAIL"
        return 1
    fi

    log "3. Test-project Maven (mvn clean verify)..."
    cd "$PROJECT_ROOT/$DIR_TEST_MAVEN"
    if run_mvn mvn clean verify; then
        RESULTS["${version_key}_maven_test"]="PASS"
        log "   Maven test-project: PASS"
    else
        RESULTS["${version_key}_maven_test"]="FAIL"
        log "   Maven test-project: FAIL"
        return 1
    fi

    log "4. Test-project Gradle (${gradlew_cmd##*/} clean test)..."
    cd "$PROJECT_ROOT/$DIR_TEST_GRADLE"
    if run_gradle "$gradlew_cmd" clean test; then
        RESULTS["${version_key}_gradle_test"]="PASS"
        log "   Gradle test-project: PASS"
    else
        RESULTS["${version_key}_gradle_test"]="FAIL"
        log "   Gradle test-project: FAIL"
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

    printf "%-25s %-15s %-15s %-12s %-12s\n" "Java Version" "Main Project" "Gradle Plugin" "Maven Test" "Gradle Test"
    printf "%-25s %-15s %-15s %-12s %-12s\n" "------------" "------------" "-------------" "----------" "-----------"

    for java_version in "${JAVA_VERSIONS[@]}"; do
        local version_key="java_${java_version//./_}"
        printf "%-25s %-15s %-15s %-12s %-12s\n" \
            "$java_version" \
            "${RESULTS[${version_key}_main]:-N/A}" \
            "${RESULTS[${version_key}_gradle_plugin]:-N/A}" \
            "${RESULTS[${version_key}_maven_test]:-N/A}" \
            "${RESULTS[${version_key}_gradle_test]:-N/A}"
    done
}

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
