#!/usr/bin/env bash
#
# Quick test script - runs tests for all Java versions
# Outputs summary per version
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

JAVA_VERSIONS=("1.8.0.351" "11.0.17" "17.0.8" "21" "25")

echo "=========================================="
echo "LLM Build Compactor - Quick Test Suite"
echo "=========================================="
echo ""

for java_version in "${JAVA_VERSIONS[@]}"; do
    echo "=== Java ${java_version} ==="
    
    export JAVA_HOME="/Users/sfk/.jenv/versions/${java_version}"
    export PATH="$JAVA_HOME/bin:$PATH"
    
    java -version 2>&1 | head -1
    
    # Main project tests
    echo -n "  Main project tests: "
    cd "$PROJECT_ROOT"
    if ./mvnw clean test -q 2>&1 | tail -1 | grep -q "BUILD SUCCESS"; then
        echo "PASS"
    else
        echo "FAIL"
    fi
    
    # Gradle plugin tests (Java 17+ only)
    if [[ "$java_version" != "1.8.0.351" && "$java_version" != "11.0.17" ]]; then
        echo -n "  Gradle plugin tests: "
        cd "$PROJECT_ROOT/gradle-plugin"
        if ../gradlew clean test -q 2>&1 | tail -1 | grep -q "BUILD SUCCESS\|BUILD COMPLETED"; then
            echo "PASS"
        else
            echo "FAIL"
        fi
        
        echo -n "  Test-project-gradle: "
        cd "$PROJECT_ROOT/test-project-gradle"
        if ../gradlew clean test -q 2>&1 | grep -q "LLM Build Compactor Summary"; then
            echo "PASS (plugin working)"
        else
            echo "FAIL"
        fi
    else
        echo "  Gradle plugin tests: SKIP (requires Java 17+)"
        echo "  Test-project-gradle: SKIP (requires Java 17+)"
    fi
    
    # Maven test-project
    echo -n "  Test-project-maven: "
    cd "$PROJECT_ROOT/test-project"
    if mvn clean verify -q 2>&1 | grep -q "LLM Build Compactor Summary"; then
        echo "PASS (plugin working)"
    else
        echo "FAIL"
    fi
    
    echo ""
done

echo "=========================================="
echo "Test run complete"
echo "=========================================="
