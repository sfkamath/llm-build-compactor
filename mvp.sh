#!/usr/bin/env bash
set -e

echo "Bootstrapping Universal LLM Build Compactor Plugin..."

mkdir -p core/src/main/java/io/llmcompactor/core
mkdir -p maven-plugin/src/main/java/io/llmcompactor/maven
mkdir -p gradle-plugin/src/main/java/io/llmcompactor/gradle
mkdir -p tools

# Core class for JSON output
cat > core/src/main/java/io/llmcompactor/core/BuildSummary.java <<'EOF'
package io.llmcompactor.core;

import java.util.List;

public record BuildSummary(
        String status,
        List<BuildError> errors,
        List<FixTarget> fixTargets,
        List<String> recentChanges
) {}
EOF

# Example BuildError
cat > core/src/main/java/io/llmcompactor/core/BuildError.java <<'EOF'
package io.llmcompactor.core;

public record BuildError(String type, String file, int line, String message) {}
EOF

# Example FixTarget
cat > core/src/main/java/io/llmcompactor/core/FixTarget.java <<'EOF'
package io.llmcompactor.core;

public record FixTarget(String file, int line, String reason, String snippet) {}
EOF

# Tools: run build + capture
cat > tools/run-agent-build.sh <<'EOF'
#!/usr/bin/env bash
set -e
echo "Running build with LLM compactor..."
mvn clean verify -Pllm-compactor | tee build.log || true
cat target/llm-summary.json
EOF
chmod +x tools/run-agent-build.sh

# Tools: iterative repair loop
cat > tools/agent-repair-loop.sh <<'EOF'
#!/usr/bin/env bash
set -e
echo "Starting agent repair loop..."
while true
do
  tools/run-agent-build.sh
  echo "Inspect target/llm-summary.json and apply fixes"
  sleep 5
done
EOF
chmod +x tools/agent-repair-loop.sh

echo "Bootstrap complete. Edit Maven/Gradle plugin classes in maven-plugin/ gradle-plugin/ to implement parsing and JSON generation."
echo "Use tools/run-agent-build.sh and tools/agent-repair-loop.sh for iterative AI-assisted build repair."