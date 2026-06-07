#!/usr/bin/env bash
#
# Quick test script - runs tests for all Java versions, one line per result.
# Usage: ./test-quick.sh [--verbose]
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

source "$SCRIPT_DIR/config.sh"
parse_args "$@"

echo "=========================================="
echo "LLM Build Compactor - Quick Test Suite"
echo "=========================================="
echo ""

for java_version in "${JAVA_VERSIONS[@]}"; do
    echo "=== Java ${java_version} ==="

    export JAVA_HOME
    JAVA_HOME=$(get_java_home "$java_version")
    export PATH="$JAVA_HOME/bin:$PATH"

    java -version 2>&1 | head -1

    echo -n "  Main project: "
    cd "$PROJECT_ROOT"
    if run_mvn ./mvnw clean install; then echo "PASS"; else echo "FAIL"; fi

    if is_gradle_skip "$java_version"; then
        echo "  Gradle plugin: SKIP (Java 8/11)"
        echo "  Test-project-gradle: SKIP (Java 8/11)"
    else
        gradlew=$(get_gradlew "$java_version")

        echo -n "  Gradle plugin: "
        cd "$PROJECT_ROOT/$DIR_GRADLE_PLUGIN"
        if run_gradle "$gradlew" clean build; then echo "PASS"; else echo "FAIL"; fi

        echo -n "  Test-project-gradle: "
        cd "$PROJECT_ROOT/$DIR_TEST_GRADLE"
        if run_gradle "$gradlew" clean test; then echo "PASS"; else echo "FAIL"; fi
    fi

    echo -n "  Test-project-maven: "
    cd "$PROJECT_ROOT/$DIR_TEST_MAVEN"
    if run_mvn mvn clean verify; then echo "PASS"; else echo "FAIL"; fi

    echo ""
done

echo "=========================================="
echo "Test run complete"
echo "=========================================="
