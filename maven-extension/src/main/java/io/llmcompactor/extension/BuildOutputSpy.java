/*
 * Copyright 2024 Jaromir Hamala (jerrinot)
 * Copyright 2024 LLM Build Compactor Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This file contains code derived from the Maven Silent Extension (MSE) project:
 * https://github.com/jerrinot/mse
 */
package io.llmcompactor.extension;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.llmcompactor.core.BuildError;
import io.llmcompactor.core.BuildSummary;
import io.llmcompactor.core.FixTarget;
import io.llmcompactor.core.SummaryWriter;
import io.llmcompactor.core.extract.CompilationErrorExtractor;
import io.llmcompactor.core.extract.FixTargetGenerator;
import io.llmcompactor.core.git.GitDiffExtractor;
import io.llmcompactor.core.parser.SurefireParser;
import io.llmcompactor.core.parser.TestResult;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/** Combined LifecycleParticipant and EventSpy to ensure silence in both modes. */
@Named("llm-compactor-extension")
@Singleton
public class BuildOutputSpy extends AbstractEventSpy {

  private static final String REDIRECT_TEST_OUTPUT_PROP = "maven.test.redirectTestOutputToFile";
  private static final String EXTENSION_ACTIVE_PROPERTY = "llmCompactor.extension.active";

  private static final PrintStream realOut = System.out;
  private static final PrintStream realErr = System.err;

  private PrintStream originalOut;
  private PrintStream originalErr;
  private String previousLogLevel;

  private MavenSession session;
  private final List<BuildError> compileErrors = new ArrayList<>();
  private volatile boolean initialized;

  public BuildOutputSpy() {}

  @Override
  public void init(Context context) throws Exception {
    ensureInitialized();
  }

  private synchronized void ensureInitialized() {
    if (initialized) {
      return;
    }

    // Check if explicitly disabled
    MavenProject topProject = session != null ? session.getTopLevelProject() : null;
    Properties projectProps = topProject != null ? topProject.getProperties() : new Properties();
    String enabledProp = getProperty("llmCompactor.enabled", projectProps, "true");
    if ("false".equalsIgnoreCase(enabledProp)) {
      System.clearProperty(EXTENSION_ACTIVE_PROPERTY);
      initialized = true; // Mark as initialized but skip redirection
      return;
    }

    // Check for interactive/app-running goals
    if (isInteractiveGoal(session)) {
      System.clearProperty(EXTENSION_ACTIVE_PROPERTY);
      initialized = true; // Skip redirection for interactive goals
      return;
    }

    originalOut = System.out;
    originalErr = System.err;

    // Suppress Maven internal logging
    previousLogLevel = System.getProperty("org.slf4j.simpleLogger.defaultLogLevel");
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "off");
    resetSlf4j();

    // Suppress all console output (Java 8 compatible null output stream)
    OutputStream nullOut =
        new OutputStream() {
          @Override
          public void write(int b) throws IOException {
            // Discard all bytes
          }
        };

    PrintStream nullPrint =
        new PrintStream(nullOut, true, StandardCharsets.UTF_8) {
          @Override
          public void write(byte[] buf, int off, int len) {}
        };

    System.setOut(nullPrint);
    System.setErr(nullPrint);
    System.setProperty(EXTENSION_ACTIVE_PROPERTY, "true");

    initialized = true;
  }

  private boolean isInteractiveGoal(MavenSession session) {
    List<String> interactiveGoals =
        Arrays.asList(
            "exec:java",
            "exec:exec",
            "spring-boot:run",
            "quarkus:dev",
            "micronaut:run",
            "jetty:run",
            "tomcat:run",
            "wildfly:run");

    // 1. Check session goals if available
    if (session != null && session.getGoals() != null) {
      for (String goal : session.getGoals()) {
        if (interactiveGoals.contains(goal)) {
          return true;
        }
      }
    }

    // 2. Fallback: check command line arguments (important for early initialization)
    String command = System.getProperty("sun.java.command");
    if (command != null) {
      for (String goal : interactiveGoals) {
        if (command.contains(" " + goal) || command.endsWith(" " + goal)) {
          return true;
        }
      }
    }

    return false;
  }

  @Override
  public void onEvent(Object event) throws Exception {
    if (!initialized) {
      ensureInitialized();
    }

    if (event instanceof ExecutionEvent) {
      ExecutionEvent ee = (ExecutionEvent) event;
      handleExecutionEvent(ee);
    }
  }

  private void handleExecutionEvent(ExecutionEvent ee) {
    switch (ee.getType()) {
      case SessionStarted:
        this.session = ee.getSession();
        if (isInteractiveGoal(session)) {
          initialized = true; // Mark as initialized to prevent later redirection
          return;
        }
        suppressTestOutput();
        break;
      case MojoFailed:
        handleMojoFailed(ee);
        break;
      case SessionEnded:
        emitSummary();
        break;
      default:
        // Ignore other events
        break;
    }
  }

  private void suppressTestOutput() {
    if (session == null) {
      return;
    }
    Properties userProps = session.getUserProperties();
    if (userProps == null) {
      return;
    }
    userProps.setProperty(REDIRECT_TEST_OUTPUT_PROP, "true");
  }

  private void handleMojoFailed(ExecutionEvent ee) {
    MojoExecution mojo = ee.getMojoExecution();
    if (mojo == null) {
      return;
    }

    if ("maven-compiler-plugin".equals(mojo.getArtifactId())) {
      String output = extractFailureOutput(ee);
      if (output != null) {
        List<BuildError> extracted =
            CompilationErrorExtractor.extract(Arrays.asList(output.split("\n")));
        if (extracted.isEmpty()) {
          extracted = Collections.singletonList(createGenericCompilationError(ee, output));
        }
        compileErrors.addAll(extracted);
      }
    }
  }

  private BuildError createGenericCompilationError(ExecutionEvent ee, String output) {
    MavenProject project = ee.getProject();
    String file =
        project != null && project.getFile() != null ? project.getFile().getPath() : "pom.xml";
    String message = extractFirstLine(output);
    return new BuildError("COMPILATION_ERROR", file, 1, message, output);
  }

  private String extractFailureOutput(ExecutionEvent ee) {
    Throwable cause = ee.getException();
    for (int depth = 0; cause != null && depth < 5; depth++) {
      String longMsg = getLongMessage(cause);
      if (longMsg != null && !longMsg.isEmpty()) {
        return longMsg;
      }
      cause = cause.getCause();
    }
    Throwable original = ee.getException();
    return original != null ? original.getMessage() : null;
  }

  private String getLongMessage(Throwable t) {
    try {
      Method m = t.getClass().getMethod("getLongMessage");
      Object result = m.invoke(t);
      return result instanceof String ? (String) result : null;
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      return null;
    }
  }

  private String extractFirstLine(String message) {
    if (message == null || message.isEmpty()) {
      return "";
    }
    return message.split("\n")[0].trim();
  }

  private void emitSummary() {
    if (session == null || originalOut == null || isInteractiveGoal(session)) {
      return;
    }

    // Try to get configuration from the project properties (allows POM <properties> or plugin
    // <configuration>)
    MavenProject topProject = session.getTopLevelProject();
    Properties projectProps = topProject != null ? topProject.getProperties() : new Properties();

    String compressProp = getProperty("llmCompactor.compressStackFrames", projectProps, "true");
    boolean compress = "true".equalsIgnoreCase(compressProp);

    String includePackagesProp = getProperty("llmCompactor.includePackages", projectProps, "");
    List<String> includePackages =
        new ArrayList<>(
            includePackagesProp.isEmpty()
                ? Collections.<String>emptyList()
                : Arrays.asList(includePackagesProp.split(",")));

    List<MavenProject> projects = session.getProjects();

    // Auto-scan project packages from source directories
    if (projects != null) {
      for (MavenProject project : projects) {
        includePackages.addAll(scanProjectPackages(project));
      }
    }

    List<BuildError> allErrors = new ArrayList<>(compileErrors);
    List<Double> allDurations = new ArrayList<>();
    int totalTestsRun = 0;
    int totalTestFailures = 0;

    long sessionStartTime = session.getStartTime() != null ? session.getStartTime().getTime() : 0;

    // Collect reports from all modules in the session
    if (projects != null) {
      for (MavenProject project : projects) {
        Path targetDir = project.getBasedir().toPath().resolve("target");
        if (Files.exists(targetDir)) {
          TestResult result =
              SurefireParser.parse(targetDir, compress, includePackages, sessionStartTime);
          totalTestsRun += result.testsRun();
          totalTestFailures += result.failures();
          allErrors.addAll(result.errors());
          allDurations.addAll(result.allDurations());
        }
      }
    }

    String showTotalDurationProp =
        getProperty("llmCompactor.showTotalDuration", projectProps, "false");
    Long totalBuildDurationMs = null;
    if ("true".equalsIgnoreCase(showTotalDurationProp)) {
      totalBuildDurationMs = System.currentTimeMillis() - sessionStartTime;
    }

    String showDurationReportProp =
        getProperty("llmCompactor.showDurationReport", projectProps, "false");
    Map<String, Double> testDurationPercentiles = null;
    if ("true".equalsIgnoreCase(showDurationReportProp) && !allDurations.isEmpty()) {
      Collections.sort(allDurations);
      testDurationPercentiles = new TreeMap<>();
      testDurationPercentiles.put("p50", allDurations.get((int) (allDurations.size() * 0.50)));
      testDurationPercentiles.put("p90", allDurations.get((int) (allDurations.size() * 0.90)));
      testDurationPercentiles.put("p95", allDurations.get((int) (allDurations.size() * 0.95)));
      testDurationPercentiles.put("p99", allDurations.get((int) (allDurations.size() * 0.99)));
      testDurationPercentiles.put("max", allDurations.get(allDurations.size() - 1));
    }

    allErrors = BuildSummary.aggregateErrors(allErrors);

    String showFixTargetsProp = getProperty("llmCompactor.showFixTargets", projectProps, "true");
    List<FixTarget> targets =
        "true".equalsIgnoreCase(showFixTargetsProp)
            ? FixTargetGenerator.generate(allErrors)
            : Collections.<FixTarget>emptyList();

    String showRecentChangesProp =
        getProperty("llmCompactor.showRecentChanges", projectProps, "true");
    List<String> recentChanges =
        "true".equalsIgnoreCase(showRecentChangesProp)
            ? GitDiffExtractor.changedFiles()
            : Collections.<String>emptyList();

    BuildSummary summary =
        new BuildSummary(
            allErrors.isEmpty() ? "SUCCESS" : "FAILED",
            totalTestsRun,
            totalTestFailures,
            allErrors,
            targets,
            recentChanges,
            totalBuildDurationMs,
            testDurationPercentiles);

    String outputPath = getProperty("llmCompactor.outputPath", projectProps, null);
    if (outputPath != null) {
      SummaryWriter.write(summary, Paths.get(outputPath));
    }

    // Emit to real System.out
    if (realOut != null) {
      String outputAsJson = getProperty("llmCompactor.outputAsJson", projectProps, "true");
      if ("true".equalsIgnoreCase(outputAsJson)) {
        realOut.print(SummaryWriter.toJson(summary));
      } else {
        String showDurationProp = getProperty("llmCompactor.showDuration", projectProps, "true");
        boolean showDuration = "true".equalsIgnoreCase(showDurationProp);
        realOut.println(SummaryWriter.toHumanReadable(summary, showDuration));
      }
    }
  }

  private List<String> scanProjectPackages(MavenProject project) {
    List<String> packages = new ArrayList<>();
    List<String> sourceRoots = new ArrayList<>();
    sourceRoots.addAll(project.getCompileSourceRoots());
    sourceRoots.addAll(project.getTestCompileSourceRoots());

    for (String root : sourceRoots) {
      Path rootPath = Paths.get(root);
      if (Files.exists(rootPath)) {
        try (Stream<Path> walk = Files.walk(rootPath)) {
          walk.filter(Files::isRegularFile)
              .filter(p -> p.toString().endsWith(".java"))
              .forEach(
                  p -> {
                    Path relative = rootPath.relativize(p);
                    if (relative.getParent() != null) {
                      String pkg = relative.getParent().toString().replace("/", ".");
                      if (!packages.contains(pkg)) {
                        packages.add(pkg);
                      }
                    }
                  });
        } catch (IOException e) {
          // Ignore
        }
      }
    }
    return packages;
  }

  private String getProperty(String key, Properties projectProps, String defaultValue) {
    // 1. System property takes precedence
    String sysProp = System.getProperty(key);
    if (sysProp != null) {
      return sysProp;
    }

    // 2. Project property (<properties> in pom.xml)
    String projProp = projectProps.getProperty(key);
    if (projProp != null) {
      return projProp;
    }

    // 3. Plugin configuration (<configuration> in pom.xml)
    // Check current project first (for submodule configs), then top-level project
    if (session != null) {
      // Try current project (may be a submodule)
      MavenProject currentProject = session.getCurrentProject();
      if (currentProject != null) {
        String configValue = getPluginConfigValue(currentProject, key);
        if (configValue != null) {
          return configValue;
        }
      }

      // Fallback to top-level project
      MavenProject topProject = session.getTopLevelProject();
      if (topProject != null) {
        String configValue = getPluginConfigValue(topProject, key);
        if (configValue != null) {
          return configValue;
        }
      }
    }

    return defaultValue;
  }

  private String getPluginConfigValue(MavenProject project, String key) {
    Plugin plugin = project.getPlugin("io.llmcompactor:llm-compactor-maven-plugin");
    if (plugin != null) {
      Object config = plugin.getConfiguration();
      if (config instanceof Xpp3Dom) {
        Xpp3Dom dom = (Xpp3Dom) config;
        // Plugin config uses camelCase (e.g., <outputAsJson>),
        // whereas system properties use dot.notation (e.g., llmCompactor.outputAsJson)
        String configKey =
            key.startsWith("llmCompactor.") ? key.substring("llmCompactor.".length()) : key;
        Xpp3Dom child = dom.getChild(configKey);
        if (child != null) {
          return child.getValue();
        }
      }
    }
    return null;
  }

  @Override
  public void close() throws Exception {
    if (previousLogLevel != null) {
      System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", previousLogLevel);
    } else {
      System.clearProperty("org.slf4j.simpleLogger.defaultLogLevel");
    }
    resetSlf4j();

    System.setOut(originalOut);
    System.setErr(originalErr);
    System.clearProperty(EXTENSION_ACTIVE_PROPERTY);
  }

  private void resetSlf4j() {
    try {
      Class<?> friendClass = Class.forName("org.slf4j.MavenSlf4jFriend");
      Method reset = friendClass.getMethod("reset");
      reset.invoke(null);

      Class<?> simpleFriend = Class.forName("org.slf4j.impl.MavenSlf4jSimpleFriend");
      Method init = simpleFriend.getMethod("init");
      init.invoke(null);
    } catch (ClassNotFoundException
        | NoSuchMethodException
        | IllegalAccessException
        | InvocationTargetException e) {
      // Ignore
    }
  }

  @SuppressFBWarnings("MS_EXPOSE_REP")
  public static PrintStream getRealOut() {
    return realOut;
  }

  @SuppressFBWarnings("MS_EXPOSE_REP")
  public static PrintStream getRealErr() {
    return realErr;
  }

  /** Inner class to act as LifecycleParticipant for Build Extensions */
  @Named("llm-compactor-participant")
  @Singleton
  public static class Participant extends AbstractMavenLifecycleParticipant {
    private final BuildOutputSpy spy;

    @Inject
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
