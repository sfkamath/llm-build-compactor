#!/usr/bin/env bash

set -e

cd "$(dirname "$0")"

echo "========================================"
echo "LLM Build Compactor Test Project"
echo "========================================"
echo ""

echo "Running Maven build (ignoring failures)..."
echo ""

mvn clean verify || true

echo ""
echo "========================================"
echo "LLM Summary"
echo "========================================"
echo ""

if [ -f target/llm-summary.json ]; then
    cat target/llm-summary.json
else
    echo "No summary generated"
fi

echo ""
echo "========================================"
echo "Test Results"
echo "========================================"
echo ""

echo "Surefire reports:"
ls -la target/surefire-reports/*.txt 2>/dev/null || echo "  No surefire reports"
echo ""

echo "Failsafe reports:"
ls -la target/failsafe-reports/*.txt 2>/dev/null || echo "  No failsafe reports"
echo ""

echo "Test log:"
head -50 target/test.log 2>/dev/null || echo "  No test log"
