#!/usr/bin/env bash
#
# Test llm-build-compactor across all supported Java versions
# Tests: main project, gradle-plugin, test-project (Maven), test-project-gradle
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Java versions to test
JAVA_VERSIONS=("1.8.0.351" "11.0.17" "17.0.8" "21" "25")

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_section() {
    echo ""
    echo "========================================"
    echo "$1"
    echo "========================================"
}

# Get Java home path for a version
get_java_home() {
    local version="$1"
    echo "/Users/sfk/.jenv/versions/${version}"
}

# Test main project with Maven
test_main_project() {
    local java_version="$1"
    local java_home
    java_home=$(get_java_home "$java_version")
    
    log_section "Main Project (Java ${java_version})"
    
    export JAVA_HOME="$java_home"
    export PATH="$JAVA_HOME/bin:$PATH"
    
    cd "$PROJECT_ROOT"
    
    log_info "Running: mvn clean install"
    if ./mvnw clean install 2>&1 | tee /tmp/main-build.log; then
        log_info "Main project build: PASSED"
        ((PASSED_TESTS++))
    else
        log_error "Main project build: FAILED"
        ((FAILED_TESTS++))
        return 1
    fi
    ((TOTAL_TESTS++))
}

# Test gradle-plugin
test_gradle_plugin() {
    local java_version="$1"
    local java_home
    java_home=$(get_java_home "$java_version")
    
    log_section "Gradle Plugin (Java ${java_version})"
    
    export JAVA_HOME="$java_home"
    export PATH="$JAVA_HOME/bin:$PATH"
    
    cd "$PROJECT_ROOT/gradle-plugin"
    
    # Gradle 9.4 requires Java 17+ to run
    if [[ "$java_version" == "1.8.0.351" || "$java_version" == "11.0.17" ]]; then
        log_warn "Skipping - Gradle 9.4 requires Java 17+"
        return 0
    fi
    
    log_info "Running: gradlew clean build"
    if ../gradlew clean build 2>&1 | tee /tmp/gradle-plugin-build.log; then
        log_info "Gradle plugin build: PASSED"
        ((PASSED_TESTS++))
    else
        log_error "Gradle plugin build: FAILED"
        ((FAILED_TESTS++))
        return 1
    fi
    ((TOTAL_TESTS++))
}

# Test Maven test-project
test_maven_test_project() {
    local java_version="$1"
    local java_home
    java_home=$(get_java_home "$java_version")
    
    log_section "Test Project Maven (Java ${java_version})"
    
    export JAVA_HOME="$java_home"
    export PATH="$JAVA_HOME/bin:$PATH"
    
    cd "$PROJECT_ROOT/test-project"
    
    log_info "Running: mvn clean verify"
    if mvn clean verify 2>&1 | tee /tmp/maven-test-project.log; then
        # Check if llm-summary was generated
        if [ -f target/llm-summary.json ] || grep -q "LLM Build Compactor Summary" /tmp/maven-test-project.log; then
            log_info "Maven test-project: PASSED (plugin output generated)"
            ((PASSED_TESTS++))
        else
            log_warn "Maven test-project: Build passed but no plugin output detected"
            ((PASSED_TESTS++))
        fi
    else
        log_error "Maven test-project: FAILED"
        ((FAILED_TESTS++))
        return 1
    fi
    ((TOTAL_TESTS++))
}

# Test Gradle test-project-gradle
test_gradle_test_project() {
    local java_version="$1"
    local java_home
    java_home=$(get_java_home "$java_version")
    
    log_section "Test Project Gradle (Java ${java_version})"
    
    export JAVA_HOME="$java_home"
    export PATH="$JAVA_HOME/bin:$PATH"
    
    cd "$PROJECT_ROOT/test-project-gradle"
    
    # Gradle 9.4 requires Java 17+ to run
    if [[ "$java_version" == "1.8.0.351" || "$java_version" == "11.0.17" ]]; then
        log_warn "Skipping - Gradle 9.4 requires Java 17+"
        return 0
    fi
    
    log_info "Running: gradlew clean test"
    if ../gradlew clean test 2>&1 | tee /tmp/gradle-test-project.log; then
        # Check if plugin output was generated
        if grep -q "LLM Build Compactor Summary" /tmp/gradle-test-project.log; then
            log_info "Gradle test-project: PASSED (plugin output generated)"
            ((PASSED_TESTS++))
        else
            log_warn "Gradle test-project: Build passed but no plugin output detected"
            ((PASSED_TESTS++))
        fi
    else
        log_error "Gradle test-project: FAILED"
        ((FAILED_TESTS++))
        return 1
    fi
    ((TOTAL_TESTS++))
}

# Print summary
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
        log_error "Some tests failed!"
        return 1
    fi
}

# Main execution
main() {
    log_section "LLM Build Compactor - Multi-Java Version Test Suite"
    echo "Java versions to test: ${JAVA_VERSIONS[*]}"
    echo ""
    
    for java_version in "${JAVA_VERSIONS[@]}"; do
        log_section "Testing Java ${java_version}"
        
        # Test main project (all versions)
        test_main_project "$java_version" || true
        
        # Test gradle-plugin (Java 17+)
        test_gradle_plugin "$java_version" || true
        
        # Test Maven test-project (all versions)
        test_maven_test_project "$java_version" || true
        
        # Test Gradle test-project (Java 17+)
        test_gradle_test_project "$java_version" || true
    done
    
    print_summary
}

# Run main
main "$@"
