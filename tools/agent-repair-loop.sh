#!/usr/bin/env bash
set -e
echo "Starting agent repair loop..."
while true
do
  tools/run-agent-build.sh
  echo "Inspect target/llm-summary.json and apply fixes"
  sleep 5
done
