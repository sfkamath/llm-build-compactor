package io.llmcompactor.gradle;

import java.io.PrintStream;
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
      long sessionStartTime,
      boolean isEnabled,
      PrintStream originalOut,
      PrintStream originalErr) {
    Provider<CompletionService> provider =
        rootProject
            .getGradle()
            .getSharedServices()
            .registerIfAbsent(
                "llmCompactorCompletion",
                CompletionService.class,
                spec -> spec.getParameters().getSessionStartTime().set(sessionStartTime));

    CompletionService service = provider.get();
    service.init(rootProject, extension, originalOut, originalErr);

    // Register listener before applying output suppression so listener() captures log lines
    rootProject.allprojects(
        p -> {
          p.getLogging().addStandardOutputListener(service.listener());
          p.getLogging().addStandardErrorListener(service.listener());
        });

    eventsRegistry.onTaskCompletion(provider);
  }
}
