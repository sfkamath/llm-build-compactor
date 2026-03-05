#!/usr/bin/env bash

echo "Generating agent repair context..."

java \
-cp core/target/classes \
io.llmcompactor.core.context.RepairContextBuilder
