package io.llmcompactor.gradle;

/**
 * Extension configuration for the LLM Build Compactor plugin.
 */
public interface LlmCompactorExtension {
    /**
     * Whether the plugin is enabled.
     *
     * @return property for enabling/disabling the plugin (default: true)
     */
    Property<Boolean> getEnabled();

    /**
     * Whether to output the summary as JSON.
     *
     * @return property for JSON output (default: false for human-readable)
     */
    Property<Boolean> getOutputAsJson();

    /**
     * Whether to compress stack traces in the output.
     *
     * @return property for stack frame compression (default: true)
     */
    Property<Boolean> getCompressStackFrames();

    /**
     * List of packages to include in the analysis.
     *
     * @return list property of package names to include
     */
    ListProperty<String> getIncludePackages();

    /**
     * Whether to show fix targets for errors.
     *
     * @return property for showing fix targets (default: false)
     */
    Property<Boolean> getShowFixTargets();

    /**
     * Whether to show recent git changes.
     *
     * @return property for showing recent changes (default: false)
     */
    Property<Boolean> getShowRecentChanges();

    /**
     * Whether to show test duration for each error.
     *
     * @return property for showing test duration (default: true)
     */
    Property<Boolean> getShowDuration();

    /**
     * Whether to show total build duration.
     *
     * @return property for showing total duration (default: false)
     */
    Property<Boolean> getShowTotalDuration();

    /**
     * Whether to show test duration percentiles report.
     *
     * @return property for showing duration report (default: false)
     */
    Property<Boolean> getShowDurationReport();

    /**
     * Custom output path for the summary file.
     *
     * @return property for custom output path (default: null for default location)
     */
    Property<String> getOutputPath();
}