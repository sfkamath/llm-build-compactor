#!/usr/bin/env bash
set -e
echo "Running build with LLM compactor..."
mvn clean verify -Pllm-compactor | tee build.log || true
cat target/llm-summary.json
