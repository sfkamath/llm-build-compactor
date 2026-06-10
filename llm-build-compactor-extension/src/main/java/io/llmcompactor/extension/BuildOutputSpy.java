/*
 * Copyright 2024 Jaromir Hamala (jerrinot)
 * Copyright 2024 LLM Build Compactor Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.llmcompactor.extension;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.llmcompactor.core.BuildError;
import io.llmcompactor.core.BuildSummary;
import io.llmcompactor.core.CompactorConfig;
import io.llmcompactor.core.CompactorDefaults;
import io.llmcompactor.core.SummaryBuilder;
import io.llmcompactor.core.SummaryWriter;
import io.llmcompactor.core.extract.CompilationErrorExtractor;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;

@Named("llm-compactor-extension")
@Singleton
public class BuildOutputSpy extends AbstractEventSpy {

  private static final String PROP_REDIRECT_TEST_OUTPUT = "maven.test.redirectTestOutputToFile";
  private static final String PROP_EXTENSION_ACTIVE = "llmCompactor.extension.active";

  private static final List<String> INTERACTIVE_GOALS =
      Arrays.asList(
          "exec:java",
          "exec:exec",
          "spring-boot:run",
          "quarkus:dev",
          "micronaut:run",
          "jetty:run",
          "tomcat:run",
          "wildfly:run");

  // Captured before we suppress output so we can always write the final summary.
  private static final PrintStream REAL_OUT = System.out;
  private static final PrintStream REAL_ERR = System.err;

  private PrintStream originalOut;
  private PrintStream originalErr;
  private String previousLogLevel;
  private MavenSession session;

  private final List<BuildError> compileErrors = new ArrayList<>();
  private volatile boolean initialized;
  private volatile boolean buildFailed;

  // =========================================================================
  // AbstractEventSpy lifecycle
  // =========================================================================

  @Override
  public void init(Context context) throws Exception {
    ensureInitialized();
  }

  @Override
  public void onEvent(Object event) throws Exception {
    if (!initialized) {
      ensureInitialized();
    }
    if (event instanceof ExecutionEvent) {
      handleExecutionEvent((ExecutionEvent) event);
    }
  }

  @Override
  public void close() throws Exception {
    restoreLogLevel();
    resetSlf4j();
    // In pass-through mode the streams were never captured/suppressed, so originalOut/Err are
    // null. Restoring unconditionally would install null streams and break stdout/stderr for the
    // rest of the JVM. Only restore when we actually captured the originals.
    if (originalOut != null) {
      System.setOut(originalOut);
    }
    if (originalErr != null) {
      System.setErr(originalErr);
    }
    System.clearProperty(PROP_EXTENSION_ACTIVE);
  }

  // =========================================================================
  // Initialisation
  // =========================================================================

  private synchronized void ensureInitialized() {
    if (initialized) {
      return;
    }
    if (shouldPassThrough()) {
      System.clearProperty(PROP_EXTENSION_ACTIVE);
      initialized = true;
      return;
    }
    captureAndSuppressStreams();
    initialized = true;
  }

  /** Returns {@code true} when the extension should do nothing and let Maven run normally. */
  private boolean shouldPassThrough() {
    return isDisabled() || isInteractiveGoal(session);
  }

  private void captureAndSuppressStreams() {
    originalOut = System.out;
    originalErr = System.err;

    previousLogLevel = System.getProperty("org.slf4j.simpleLogger.defaultLogLevel");
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "off");
    resetSlf4j();

    PrintStream nullPrint = CompactorDefaults.nullPrintStream();
    System.setOut(nullPrint);
    System.setErr(nullPrint);
    System.setProperty(PROP_EXTENSION_ACTIVE, "true");
  }

  private void restoreLogLevel() {
    if (previousLogLevel != null) {
      System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", previousLogLevel);
    } else {
      System.clearProperty("org.slf4j.simpleLogger.defaultLogLevel");
    }
  }

  // =========================================================================
  // Event handling
  // =========================================================================

  private void handleExecutionEvent(ExecutionEvent ee) {
    switch (ee.getType()) {
      case SessionStarted:
        this.session = ee.getSession();
        if (!shouldPassThrough()) {
          suppressTestOutput();
        } else {
          initialized = true;
        }
        break;
      case MojoFailed:
        handleMojoFailed(ee);
        break;
      case SessionEnded:
        emitSummary();
        break;
      default:
        break;
    }
  }

  private void suppressTestOutput() {
    // Set on both channels so Surefire picks it up regardless of how it resolves the flag.
    System.setProperty(PROP_REDIRECT_TEST_OUTPUT, "true");
    if (session != null && session.getUserProperties() != null) {
      session.getUserProperties().setProperty(PROP_REDIRECT_TEST_OUTPUT, "true");
    }
  }

  // =========================================================================
  // Failure handling
  // =========================================================================

  private void handleMojoFailed(ExecutionEvent ee) {
    MojoExecution mojo = ee.getMojoExecution();
    String output = extractFailureOutput(ee);

    buildFailed = true;

    boolean isSurefirePlugin =
        mojo != null
            && (mojo.getArtifactId().contains("surefire")
                || mojo.getArtifactId().contains("failsafe"));

    // Surefire/failsafe test failures are captured from XML reports by SurefireParser.
    // Adding errors here would double-count them regardless of the failure message text.
    if (isSurefirePlugin) {
      return;
    }

    if (output != null && !output.isEmpty()) {
      compileErrors.addAll(extractOrWrap(ee, output));
    }
  }

  private List<BuildError> extractOrWrap(ExecutionEvent ee, String output) {
    MavenProject project = ee.getProject();
    String file =
        project != null && project.getFile() != null ? project.getFile().getPath() : "pom.xml";
    return CompilationErrorExtractor.extractOrWrap(output, file);
  }

  private String extractFailureOutput(ExecutionEvent ee) {
    Throwable cause = ee.getException();
    for (int depth = 0; cause != null && depth < 5; depth++) {
      String msg = longMessageOf(cause);
      if (msg != null && !msg.isEmpty()) {
        return msg;
      }
      cause = cause.getCause();
    }
    Throwable original = ee.getException();
    return original != null ? original.getMessage() : null;
  }

  private String longMessageOf(Throwable t) {
    try {
      Method m = t.getClass().getMethod("getLongMessage");
      Object result = m.invoke(t);
      return result instanceof String ? (String) result : null;
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      return null;
    }
  }

  // =========================================================================
  // Summary emission
  // =========================================================================

  private void emitSummary() {
    if (session == null || originalOut == null || isInteractiveGoal(session)) {
      return;
    }
    if (!buildFailed && session.getResult() != null && session.getResult().hasExceptions()) {
      buildFailed = true;
    }

    PropertyResolver props = propertyResolver();
    CompactorConfig config = OutputConfig.resolve(props).resolved();
    long sessionStartTime = sessionStartTime();

    TestResultCollector collector = new TestResultCollector();
    collector.collectFrom(session, config, sessionStartTime);

    List<BuildError> allErrors = new ArrayList<>(compileErrors);
    allErrors.addAll(collector.errors());

    BuildSummary summary =
        new SummaryBuilder()
            .addErrors(allErrors)
            .addDurations(collector.durations())
            .addSlowTests(collector.slowTests())
            .withTestsRun(collector.testsRun())
            .withFailures(collector.failures())
            .withBuildFailed(buildFailed)
            .withSessionStartTime(sessionStartTime)
            .withConfig(config)
            .build();

    writeToOutputPath(config, summary);
    printSummary(config, summary);
  }

  private void writeToOutputPath(CompactorConfig config, BuildSummary summary) {
    String outputPath = config.outputPath();
    if (outputPath != null) {
      SummaryWriter.write(summary, Paths.get(outputPath));
    }
  }

  private void printSummary(CompactorConfig config, BuildSummary summary) {
    if (config.outputAsJson()) {
      REAL_OUT.print(SummaryWriter.toJson(summary, config.testDurationThresholdMs()));
    } else {
      REAL_OUT.println(
          SummaryWriter.toHumanReadable(
              summary, config.showSlowTests(), config.testDurationThresholdMs()));
    }
  }

  // =========================================================================
  // Utilities
  // =========================================================================

  private PropertyResolver propertyResolver() {
    MavenProject top = session.getTopLevelProject();
    Properties projectProps = top != null ? top.getProperties() : new Properties();
    return new PropertyResolver(session, projectProps);
  }

  private long sessionStartTime() {
    return session.getStartTime() != null ? session.getStartTime().getTime() : 0;
  }

  private boolean isDisabled() {
    MavenProject topProject = session != null ? session.getTopLevelProject() : null;
    Properties props = topProject != null ? topProject.getProperties() : new Properties();
    PropertyResolver resolver = new PropertyResolver(session, props);
    boolean llmcePresent = resolver.getString("llmce", null) != null;
    String enabledValue = resolver.getString("llmCompactor.enabled", null);
    return !CompactorDefaults.resolveEnabled(llmcePresent, enabledValue);
  }

  private boolean isInteractiveGoal(MavenSession session) {
    if (session != null && session.getGoals() != null) {
      for (String goal : session.getGoals()) {
        if (INTERACTIVE_GOALS.contains(goal)) {
          return true;
        }
      }
    }
    String command = System.getProperty("sun.java.command");
    if (command != null) {
      for (String goal : INTERACTIVE_GOALS) {
        if (command.contains(" " + goal) || command.endsWith(" " + goal)) {
          return true;
        }
      }
    }
    return false;
  }

  private void resetSlf4j() {
    try {
      Class.forName("org.slf4j.MavenSlf4jFriend").getMethod("reset").invoke(null);
      Class.forName("org.slf4j.impl.MavenSlf4jSimpleFriend").getMethod("init").invoke(null);
    } catch (ClassNotFoundException
        | NoSuchMethodException
        | IllegalAccessException
        | InvocationTargetException ignored) {
      // SLF4J bridge not present in this Maven version; nothing to reset.
    }
  }

  // =========================================================================
  // Public accessors (used by other extension components)
  // =========================================================================

  @SuppressFBWarnings("MS_EXPOSE_REP")
  public static PrintStream getRealOut() {
    return REAL_OUT;
  }

  @SuppressFBWarnings("MS_EXPOSE_REP")
  public static PrintStream getRealErr() {
    return REAL_ERR;
  }

  // =========================================================================
  // Inner lifecycle participant
  // =========================================================================

  @Named("llm-compactor-participant")
  @Singleton
  public static class Participant extends AbstractMavenLifecycleParticipant {

    private final BuildOutputSpy spy;

    @Inject
    @SuppressFBWarnings("EI_EXPOSE_REP2")
    public Participant(BuildOutputSpy spy) {
      this.spy = spy;
    }

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
      spy.session = session;
      spy.ensureInitialized();
    }
  }
}
