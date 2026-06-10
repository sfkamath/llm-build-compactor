package io.llmcompactor.gradle;

import io.llmcompactor.core.DefaultCompactorConfig;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.build.event.BuildEventsListenerRegistry;

class BuildSummaryEmitter {

  private final BuildEventsListenerRegistry eventsRegistry;

  @Inject
  BuildSummaryEmitter(BuildEventsListenerRegistry eventsRegistry) {
    this.eventsRegistry = eventsRegistry;
  }

  void register(
      Project rootProject,
      LlmCompactorPlugin.LlmCompactorExtension extension,
      long sessionStartTime) {
    Provider<CompletionService> provider =
        rootProject
            .getGradle()
            .getSharedServices()
            .registerIfAbsent(
                "llmCompactorCompletion",
                CompletionService.class,
                spec -> {
                  CompletionService.Params params = spec.getParameters();
                  params.getSessionStartTime().set(sessionStartTime);
                  params.getRootDir().set(rootProject.getRootDir());
                  params.getBuildDir().set(rootProject.getLayout().getBuildDirectory().getAsFile());

                  List<File> allBuildDirs = new ArrayList<>();
                  List<File> allSourceDirs = new ArrayList<>();
                  for (Project p : rootProject.getAllprojects()) {
                    allBuildDirs.add(p.getLayout().getBuildDirectory().getAsFile().get());
                    File mainSrc = p.file("src/main/java");
                    if (mainSrc.exists()) allSourceDirs.add(mainSrc);
                    File testSrc = p.file("src/test/java");
                    if (testSrc.exists()) allSourceDirs.add(testSrc);
                  }
                  params.getAllBuildDirs().set(allBuildDirs);
                  params.getAllSourceDirs().set(allSourceDirs);

                  params.getConfig().set(rootProject.provider(() -> buildConfig(extension)));
                });

    CompletionService service = provider.get();

    // Register listener before applying output suppression so listener() captures log lines
    rootProject.allprojects(
        p -> {
          p.getLogging().addStandardOutputListener(service.listener());
          p.getLogging().addStandardErrorListener(service.listener());
        });

    eventsRegistry.onTaskCompletion(provider);
  }

  private static DefaultCompactorConfig buildConfig(LlmCompactorPlugin.LlmCompactorExtension ext) {
    return DefaultCompactorConfig.builder()
        .enabled(ext.getEnabled().get())
        .outputPath(ext.getOutputPath().getOrNull())
        .mode(ext.getMode().getOrNull())
        .outputAsJson(ext.getOutputAsJson().get())
        .compressStackFrames(ext.getCompressStackFrames().get())
        .showFixTargets(ext.getShowFixTargets().get())
        .showRecentChanges(ext.getShowRecentChanges().get())
        .showSlowTests(ext.getShowSlowTests().get())
        .showTotalDuration(ext.getShowTotalDuration().get())
        .showDurationReport(ext.getShowDurationReport().get())
        .showFailedTestLogs(ext.getShowFailedTestLogs().get())
        .testDurationThresholdMs(ext.getTestDurationThresholdMs().get())
        .stackFrameWhitelist(ext.getStackFrameWhitelist().get())
        .stackFrameBlacklist(ext.getStackFrameBlacklist().get())
        .build();
  }
}
