#!/usr/bin/env bash
# Shared configuration for llm-build-compactor test scripts.
# Usage: source "$(dirname "${BASH_SOURCE[0]}")/config.sh"
# Caller must set PROJECT_ROOT before sourcing.

JAVA_VERSIONS=("temurin64-1.8.0.482" "11.0.17" "17.0.8" "21" "25")

JENV_PREFIX="/Users/sfk/.jenv/versions"

# Directory names relative to PROJECT_ROOT
DIR_GRADLE_PLUGIN="llm-build-compactor-gradle-plugin"
DIR_TEST_MAVEN="test-project-maven"
DIR_TEST_GRADLE="test-project-gradle"

# Java versions that require Gradle 8.x (cannot run Gradle 9)
JAVA_SKIP_GRADLE=("temurin64-1.8.0.482" "11.0.17")

get_java_home() {
    echo "${JENV_PREFIX}/$1"
}

get_gradlew() {
    local java_version="$1"
    case "$java_version" in
        temurin64-1.8.0.482) echo "$PROJECT_ROOT/gradlew-java8" ;;
        11.0.17)              echo "$PROJECT_ROOT/gradlew-java11" ;;
        *)                    echo "$PROJECT_ROOT/gradlew" ;;
    esac
}

get_gradle_version() {
    local java_version="$1"
    case "$java_version" in
        temurin64-1.8.0.482) echo "Gradle 8.14.4" ;;
        11.0.17)              echo "Gradle 8.5" ;;
        *)                    echo "Gradle 9.4.0" ;;
    esac
}

is_gradle_skip() {
    local java_version="$1"
    for skip in "${JAVA_SKIP_GRADLE[@]}"; do
        [[ "$java_version" == "$skip" ]] && return 0
    done
    return 1
}

# Output control — quiet by default, --verbose/-v for full build output
VERBOSE=false

parse_args() {
    for arg in "$@"; do
        case "$arg" in
            --verbose|-v) VERBOSE=true ;;
        esac
    done
}

# Run a Maven command. Quiet mode: adds -q and suppresses all output.
run_mvn() {
    if [[ "$VERBOSE" == "true" ]]; then
        "$@"
    else
        "$@" -q >/dev/null 2>&1
    fi
}

# Run a Gradle command. Quiet mode: adds -q and suppresses all output.
run_gradle() {
    if [[ "$VERBOSE" == "true" ]]; then
        "$@"
    else
        "$@" -q >/dev/null 2>&1
    fi
}

# Run a Gradle command and capture combined stdout+stderr into GRADLE_OUTPUT.
# Returns the exit code of the gradle command.
run_gradle_capture() {
    GRADLE_OUTPUT=$("$@" 2>&1)
}
