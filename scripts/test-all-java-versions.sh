#!/usr/bin/env bash
#
# Full test suite with per-test pass/fail tracking and failure summary.
# Tests: main project, gradle-plugin, test-project-maven, test-project-gradle.
# Usage: ./test-all-java-versions.sh [--verbose]
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

source "$SCRIPT_DIR/config.sh"
parse_args "$@"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
FAILED_LIST=()

log_info()    { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $1"; }
log_section() { echo ""; echo "========================================"; echo "$1"; echo "========================================"; }

test_main_project() {
    local java_version="$1"

    log_section "Main Project (Java ${java_version})"

    export JAVA_HOME
    JAVA_HOME=$(get_java_home "$java_version")
    export PATH="$JAVA_HOME/bin:$PATH"

    cd "$PROJECT_ROOT"

    log_info "Running: mvn clean install"
    ((TOTAL_TESTS++))
    if run_mvn ./mvnw clean install; then
        log_info "Main project build: PASSED"
        ((PASSED_TESTS++))
    else
        log_error "Main project build: FAILED"
        ((FAILED_TESTS++))
        FAILED_LIST+=("Main project (Java ${java_version})")
        return 1
    fi
}

test_gradle_plugin() {
    local java_version="$1"

    log_section "Gradle Plugin (Java ${java_version})"

    export JAVA_HOME
    JAVA_HOME=$(get_java_home "$java_version")
    export PATH="$JAVA_HOME/bin:$PATH"

    if is_gradle_skip "$java_version"; then
        log_warn "Skipping - Gradle 9.4 requires Java 17+"
        return 0
    fi

    cd "$PROJECT_ROOT/$DIR_GRADLE_PLUGIN"
    local gradlew
    gradlew=$(get_gradlew "$java_version")

    log_info "Running: ${gradlew##*/} clean build"
    ((TOTAL_TESTS++))
    if run_gradle "$gradlew" clean build; then
        log_info "Gradle plugin build: PASSED"
        ((PASSED_TESTS++))
    else
        log_error "Gradle plugin build: FAILED"
        ((FAILED_TESTS++))
        FAILED_LIST+=("Gradle plugin (Java ${java_version})")
        return 1
    fi
}

test_maven_test_project() {
    local java_version="$1"

    log_section "Test Project Maven (Java ${java_version})"

    export JAVA_HOME
    JAVA_HOME=$(get_java_home "$java_version")
    export PATH="$JAVA_HOME/bin:$PATH"

    cd "$PROJECT_ROOT/$DIR_TEST_MAVEN"

    log_info "Running: mvn clean verify"
    ((TOTAL_TESTS++))
    if run_mvn mvn clean verify; then
        log_info "Maven test-project: PASSED"
        ((PASSED_TESTS++))
    else
        log_error "Maven test-project: FAILED"
        ((FAILED_TESTS++))
        FAILED_LIST+=("Maven test-project (Java ${java_version})")
        return 1
    fi
}

test_gradle_test_project() {
    local java_version="$1"

    log_section "Test Project Gradle (Java ${java_version})"

    export JAVA_HOME
    JAVA_HOME=$(get_java_home "$java_version")
    export PATH="$JAVA_HOME/bin:$PATH"

    if is_gradle_skip "$java_version"; then
        log_warn "Skipping - Gradle 9.4 requires Java 17+"
        return 0
    fi

    cd "$PROJECT_ROOT/$DIR_TEST_GRADLE"
    local gradlew
    gradlew=$(get_gradlew "$java_version")

    log_info "Running: ${gradlew##*/} clean test"
    ((TOTAL_TESTS++))
    if run_gradle "$gradlew" clean test; then
        log_info "Gradle test-project: PASSED"
        ((PASSED_TESTS++))
    else
        log_error "Gradle test-project: FAILED"
        ((FAILED_TESTS++))
        FAILED_LIST+=("Gradle test-project (Java ${java_version})")
        return 1
    fi
}

print_summary() {
    log_section "Test Summary"
    echo "Total tests:   $TOTAL_TESTS"
    echo -e "Passed:        ${GREEN}$PASSED_TESTS${NC}"
    echo -e "Failed:        ${RED}$FAILED_TESTS${NC}"
    echo ""

    if [ "$FAILED_TESTS" -eq 0 ]; then
        log_info "All tests passed!"
        return 0
    else
        log_error "Failed tests:"
        for item in "${FAILED_LIST[@]}"; do
            echo -e "  ${RED}✗${NC} $item"
        done
        return 1
    fi
}

main() {
    log_section "LLM Build Compactor - Multi-Java Version Test Suite"
    echo "Java versions to test: ${JAVA_VERSIONS[*]}"
    echo ""

    for java_version in "${JAVA_VERSIONS[@]}"; do
        log_section "Testing Java ${java_version}"
        test_main_project "$java_version"       || true
        test_gradle_plugin "$java_version"      || true
        test_maven_test_project "$java_version" || true
        test_gradle_test_project "$java_version" || true
    done

    print_summary
}

main "$@"
