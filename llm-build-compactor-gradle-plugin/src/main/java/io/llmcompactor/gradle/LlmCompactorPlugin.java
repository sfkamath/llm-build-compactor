package io.llmcompactor.gradle;

import io.llmcompactor.core.CompactorConfig;
import io.llmcompactor.core.CompactorDefaults;
import io.llmcompactor.core.parser.ParserUtils;
import io.llmcompactor.core.util.ConfigAccessor;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.build.event.BuildEventsListenerRegistry;

/**
 * Gradle plugin that compacts build output for LLM-assisted development. Captures test results and
 * compilation errors, then emits a condensed summary.
 */
public class LlmCompactorPlugin implements Plugin<Project> {

  private static final String ROOT_LISTENER_REGISTERED = "llmCompactorRootListenerRegistered";

  private final BuildEventsListenerRegistry eventsRegistry;

  /**
   * Constructs the plugin with the build events listener registry.
   *
   * @param eventsRegistry registry for build lifecycle events
   */
  @Inject
  public LlmCompactorPlugin(BuildEventsListenerRegistry eventsRegistry) {
    this.eventsRegistry = eventsRegistry;
  }

  /** Extension configuration for the LLM Build Compactor plugin. */
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
     * @return property for JSON output (default: true)
     */
    Property<Boolean> getOutputAsJson();

    /**
     * Whether to compress stack traces in the output.
     *
     * @return property for stack frame compression (default: true)
     */
    Property<Boolean> getCompressStackFrames();

    /**
     * List of packages to include in stack traces (whitelist).
     *
     * @return list property of package names to always include in stack traces
     */
    ListProperty<String> getStackFrameWhitelist();

    /**
     * List of packages to exclude from stack traces (blacklist).
     *
     * @return list property of package names to always exclude from stack traces
     */
    ListProperty<String> getStackFrameBlacklist();

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
     * Output mode preset. When set, overrides individual flags.
     *
     * @return property for mode preset (default: null)
     */
    Property<String> getMode();

    /**
     * Whether to show test duration for slow tests (above threshold).
     *
     * @return property for showing slow test durations (default: true)
     */
    Property<Boolean> getShowSlowTests();

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

    /**
     * Whether to show logs from failed tests.
     *
     * @return property for showing failed test logs (default: true)
     */
    Property<Boolean> getShowFailedTestLogs();

    /**
     * Threshold in milliseconds for showing test duration. Tests with duration below this threshold
     * won't show duration.
     *
     * @return property for test duration threshold (default: 100)
     */
    Property<Double> getTestDurationThresholdMs();

    /**
     * Handles unknown properties gracefully for backward compatibility. Allows newer plugin
     * versions to work with older build scripts setting properties that don't exist yet.
     *
     * @param name the property name that was set
     * @param value the value being set
     * @return null (property is ignored)
     */
    default Object propertyMissing(String name, Object value) {
      System.err.println(
          "[LLM Compactor] Warning: Unknown property '"
              + name
              + "' - this may be from a newer plugin version");
      return null;
    }
  }

  @Override
  public void apply(Project project) {
    LlmCompactorExtension extension =
        project.getExtensions().create("llmCompactor", LlmCompactorExtension.class);

    // llmce is a kill-switch alias; llmCompactor.enabled=false also disables
    boolean llmcePresent =
        System.getProperty("llmce") != null
            || project.getProviders().gradleProperty("llmce").isPresent();
    String enabledProp =
        System.getProperty("llmCompactor.enabled") != null
            ? System.getProperty("llmCompactor.enabled")
            : project.getProviders().gradleProperty("llmCompactor.enabled").isPresent()
                ? project.getProviders().gradleProperty("llmCompactor.enabled").get()
                : null;
    boolean enabledValue = CompactorDefaults.resolveEnabled(llmcePresent, enabledProp);
    project
        .getLogger()
        .debug("[LLM Compactor] llmcePresent={} enabledValue={}", llmcePresent, enabledValue);
    extension.getEnabled().set(enabledValue);

    // Bind extension properties to gradle properties with defaults
    ProviderFactory providers = project.getProviders();
    extension
        .getOutputAsJson()
        .convention(boolProp(providers, "outputAsJson", CompactorConfig.DEFAULT_OUTPUT_AS_JSON));
    extension
        .getCompressStackFrames()
        .convention(
            boolProp(
                providers, "compressStackFrames", CompactorConfig.DEFAULT_COMPRESS_STACK_FRAMES));
    extension
        .getShowFixTargets()
        .convention(
            boolProp(providers, "showFixTargets", CompactorConfig.DEFAULT_SHOW_FIX_TARGETS));
    extension
        .getShowRecentChanges()
        .convention(
            boolProp(providers, "showRecentChanges", CompactorConfig.DEFAULT_SHOW_RECENT_CHANGES));
    extension
        .getShowSlowTests()
        .convention(boolProp(providers, "showSlowTests", CompactorConfig.DEFAULT_SHOW_SLOW_TESTS));
    extension
        .getShowTotalDuration()
        .convention(
            boolProp(providers, "showTotalDuration", CompactorConfig.DEFAULT_SHOW_TOTAL_DURATION));
    extension
        .getShowDurationReport()
        .convention(
            boolProp(
                providers, "showDurationReport", CompactorConfig.DEFAULT_SHOW_DURATION_REPORT));
    extension
        .getShowFailedTestLogs()
        .convention(
            boolProp(
                providers, "showFailedTestLogs", CompactorConfig.DEFAULT_SHOW_FAILED_TEST_LOGS));
    extension
        .getTestDurationThresholdMs()
        .convention(
            doubleProp(
                providers,
                "testDurationThresholdMs",
                CompactorConfig.DEFAULT_TEST_DURATION_THRESHOLD_MS));
    extension.getStackFrameWhitelist().convention(listProp(providers, "stackFrameWhitelist"));
    extension.getStackFrameBlacklist().convention(listProp(providers, "stackFrameBlacklist"));

    Provider<String> modeProp = stringProp(providers, "mode");
    if (modeProp.isPresent()) {
      extension.getMode().set(modeProp.get());
    }
    Provider<String> outputPathProp = stringProp(providers, "outputPath");
    if (outputPathProp.isPresent()) {
      extension.getOutputPath().set(outputPathProp.get());
    }

    GradlePropertiesInstaller.registerTasks(project);
    if (enabledValue) {
      GradlePropertiesInstaller.autoInstall(project);
    }

    Project rootProject = project.getRootProject();
    if (!rootProject.getExtensions().getExtraProperties().has(ROOT_LISTENER_REGISTERED)) {
      rootProject.getExtensions().getExtraProperties().set(ROOT_LISTENER_REGISTERED, true);
      long sessionStartTime = System.currentTimeMillis();
      boolean isEnabled = Boolean.TRUE.equals(extension.getEnabled().get());

      CompletionService.captureOriginals();

      BuildOutputSuppressor.apply(rootProject, isEnabled);
      BuildSummaryEmitter emitter = new BuildSummaryEmitter(eventsRegistry);
      emitter.register(rootProject, extension, sessionStartTime);
    }
  }

  private static Provider<Boolean> boolProp(
      ProviderFactory providers, String name, boolean defaultValue) {
    return providers
        .gradleProperty("llmCompactor." + name)
        .orElse(providers.systemProperty("llmCompactor." + name))
        .map(v -> ConfigAccessor.parseBoolean(v))
        .orElse(defaultValue);
  }

  private static Provider<Double> doubleProp(
      ProviderFactory providers, String name, double defaultValue) {
    return providers
        .gradleProperty("llmCompactor." + name)
        .orElse(providers.systemProperty("llmCompactor." + name))
        .map(v -> ConfigAccessor.parseDouble(v, defaultValue))
        .orElse(defaultValue);
  }

  private static Provider<List<String>> listProp(ProviderFactory providers, String name) {
    return providers
        .gradleProperty("llmCompactor." + name)
        .orElse(providers.systemProperty("llmCompactor." + name))
        .map(ParserUtils::splitCsv)
        .orElse(Collections.emptyList());
  }

  private static Provider<String> stringProp(ProviderFactory providers, String name) {
    return providers
        .gradleProperty("llmCompactor." + name)
        .orElse(providers.systemProperty("llmCompactor." + name));
  }
}
