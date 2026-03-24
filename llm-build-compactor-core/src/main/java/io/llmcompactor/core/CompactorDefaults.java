package io.llmcompactor.core;

/**
 * Centralized default values for LLM Build Compactor configuration.
 *
 * <p>These constants serve as the single source of truth for defaults. Each build tool (Maven,
 * Gradle, Maven Extension) declares its own default values, but should reference these constants
 * where possible.
 *
 * <p>Note: Maven Mojo annotations cannot reference Java constants directly (annotation values must
 * be compile-time constants), so Maven uses hardcoded strings that should match these values.
 */
public final class CompactorDefaults {

  private CompactorDefaults() {
    // Prevent instantiation
  }

  // ========================================================================
  // Core Behavior
  // ========================================================================

  /** Enable/disable the compactor. */
  public static final boolean ENABLED = true;

  /** Output as JSON (true) or human-readable text (false). */
  public static final boolean OUTPUT_AS_JSON = true;

  /** Compress stack traces by filtering framework noise. */
  public static final boolean COMPRESS_STACK_FRAMES = true;

  // ========================================================================
  // Output Content
  // ========================================================================

  /** Include suggested fix targets (file + line) for errors. */
  public static final boolean SHOW_FIX_TARGETS = true;

  /** Include recently changed files from git. */
  public static final boolean SHOW_RECENT_CHANGES = false;

  /** Show test duration only for slow tests (above threshold). */
  public static final boolean SHOW_SLOW_TESTS = true;

  /** Include total build duration in summary. */
  public static final boolean SHOW_TOTAL_DURATION = false;

  /** Include test duration percentile report (p50, p90, p95, p99, max). */
  public static final boolean SHOW_DURATION_REPORT = false;

  /** Include logs from failed tests (System.out, SLF4J, etc.). */
  public static final boolean SHOW_FAILED_TEST_LOGS = false;

  // ========================================================================
  // Thresholds
  // ========================================================================

  /** Threshold in milliseconds for considering a test "slow". */
  public static final double TEST_DURATION_THRESHOLD_MS = 100.0;

  // ========================================================================
  // Output Path
  // ========================================================================

  /** Default output path (null = use build tool default location). */
  public static final String OUTPUT_PATH = null;
}
