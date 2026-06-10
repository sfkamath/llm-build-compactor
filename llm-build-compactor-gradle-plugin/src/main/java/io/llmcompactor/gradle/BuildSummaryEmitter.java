package io.llmcompactor.gradle;

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

                  params.getEnabled().set(extension.getEnabled());
                  params.getOutputAsJson().set(extension.getOutputAsJson());
                  params.getCompressStackFrames().set(extension.getCompressStackFrames());
                  params.getStackFrameWhitelist().set(extension.getStackFrameWhitelist());
                  params.getStackFrameBlacklist().set(extension.getStackFrameBlacklist());
                  params.getShowSlowTests().set(extension.getShowSlowTests());
                  params.getTestDurationThresholdMs().set(extension.getTestDurationThresholdMs());
                  params.getOutputPath().set(extension.getOutputPath());
                  params.getShowFailedTestLogs().set(extension.getShowFailedTestLogs());
                  params.getShowFixTargets().set(extension.getShowFixTargets());
                  params.getShowRecentChanges().set(extension.getShowRecentChanges());
                  params.getShowTotalDuration().set(extension.getShowTotalDuration());
                  params.getShowDurationReport().set(extension.getShowDurationReport());
                  params.getMode().set(extension.getMode());
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
}
