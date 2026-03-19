#!/bin/zsh

set -euo pipefail

duration="${1:-15s}"
task="${2:-test}"

{
  timeout "$duration" stdbuf -oL -eL ../gradlew "$task" --rerun-tasks --console=plain 2>&1 || true
} | awk '
  /^\/.*:[0-9]+: warning:/ { cwh++ }
  /^\/.*:[0-9]+: error:/ { ceh++ }
  /has been deprecated and marked for removal/ { ddl++ }
  /^\[WARN\]/ { warn_lines++ }
  /^Note:/ { note_lines++ }
  /^\[ant:checkstyle\] \[ERROR\]/ { checkstyle_lines++ }
  /^SLF4J\(W\):/ { slf4j_warn_lines++ }
  /^> Task / { gt++ }
  /^ *Test .* (PASSED|FAILED|SKIPPED)$/ { tsl++ }
  /^io\.[A-Za-z0-9_.$]+$/ { tql++ }
  /^=== LLM Build Summary ===$/ { summary_lines++ }
  /^Status: / { summary_lines++ }
  /^Tests Run: / { summary_lines++ }
  /^Failures: / { summary_lines++ }
  /^BUILD SUCCESSFUL/ { build_footer_lines++ }
  /^BUILD FAILED/ { build_footer_lines++ }
  END {
    printf "%s counts: compiler_warning_headers=%d, compiler_error_headers=%d, deprecation_detail_lines=%d, warn_lines=%d, note_lines=%d, checkstyle_lines=%d, slf4j_warn_lines=%d, gradle_tasks=%d, test_status_lines=%d, test_suite_lines=%d, summary_lines=%d, build_footer_lines=%d\n",
      duration, cwh + 0, ceh + 0, ddl + 0, warn_lines + 0, note_lines + 0, checkstyle_lines + 0, slf4j_warn_lines + 0, gt + 0, tsl + 0, tql + 0, summary_lines + 0, build_footer_lines + 0
  }
' duration="$duration"
