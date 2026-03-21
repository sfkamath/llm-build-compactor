package io.llmcompactor.extension;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.llmcompactor.core.BuildError;
import io.llmcompactor.core.BuildSummary;
import io.llmcompactor.core.CompactorDefaults;
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
import java.io.UnsupportedEncodingException;
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

  private static final PrintStream REAL_OUT = System.out;
  private static final PrintStream REAL_ERR = System.err;

  private PrintStream originalOut;
  private PrintStream originalErr;
  private String previousLogLevel;
  private MavenSession session;

  private final List<BuildError> compileErrors = new ArrayList<>();
  private volatile boolean initialized;

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
    if (previousLogLevel != null) {
      System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", previousLogLevel);
    } else {
      System.clearProperty("org.slf4j.simpleLogger.defaultLogLevel");
    }
    resetSlf4j();
    System.setOut(originalOut);
    System.setErr(originalErr);
    System.clearProperty(PROP_EXTENSION_ACTIVE);
  }

  private synchronized void ensureInitialized() {
    if (initialized) {
      return;
    }
    if (isDisabled() || isInteractiveGoal(session)) {
      System.clearProperty(PROP_EXTENSION_ACTIVE);
      initialized = true;
      return;
    }

    originalOut = System.out;
    originalErr = System.err;

    previousLogLevel = System.getProperty("org.slf4j.simpleLogger.defaultLogLevel");
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "off");
    resetSlf4j();

    PrintStream nullPrint = nullPrintStream();
    System.setOut(nullPrint);
    System.setErr(nullPrint);
    System.setProperty(PROP_EXTENSION_ACTIVE, "true");

    initialized = true;
  }

  private boolean isDisabled() {
    MavenProject topProject = session != null ? session.getTopLevelProject() : null;
    Properties props = topProject != null ? topProject.getProperties() : new Properties();
    return "false".equalsIgnoreCase(getProperty("llmCompactor.enabled", props, "true"));
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

  private void handleExecutionEvent(ExecutionEvent ee) {
    switch (ee.getType()) {
      case SessionStarted:
        this.session = ee.getSession();
        if (isInteractiveGoal(session)) {
          initialized = true;
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
        break;
    }
  }

  private void suppressTestOutput() {
    // Set as both user property (for @Parameter injection) and system property (for direct
    // System.getProperty lookups) so surefire picks it up regardless of how it resolves the flag.
    System.setProperty(PROP_REDIRECT_TEST_OUTPUT, "true");
    if (session != null && session.getUserProperties() != null) {
      session.getUserProperties().setProperty(PROP_REDIRECT_TEST_OUTPUT, "true");
    }
  }

  private void handleMojoFailed(ExecutionEvent ee) {
    MojoExecution mojo = ee.getMojoExecution();
    if (mojo == null || !"maven-compiler-plugin".equals(mojo.getArtifactId())) {
      return;
    }
    String output = extractFailureOutput(ee);
    if (output == null) {
      return;
    }
    List<BuildError> extracted =
        CompilationErrorExtractor.extract(Arrays.asList(output.split("\n")));
    if (extracted.isEmpty()) {
      extracted = Collections.singletonList(createGenericCompilationError(ee, output));
    }
    compileErrors.addAll(extracted);
  }

  private BuildError createGenericCompilationError(ExecutionEvent ee, String output) {
    MavenProject project = ee.getProject();
    String file =
        project != null && project.getFile() != null ? project.getFile().getPath() : "pom.xml";
    return new BuildError("COMPILATION_ERROR", file, 1, firstLine(output), output);
  }

  private String extractFailureOutput(ExecutionEvent ee) {
    Throwable cause = ee.getException();
    for (int depth = 0; cause != null && depth < 5; depth++) {
      String msg = getLongMessage(cause);
      if (msg != null && !msg.isEmpty()) {
        return msg;
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

  private String firstLine(String message) {
    if (message == null || message.isEmpty()) {
      return "";
    }
    return message.split("\n")[0].trim();
  }

  private void emitSummary() {
    if (session == null || originalOut == null || isInteractiveGoal(session)) {
      return;
    }

    MavenProject topProject = session.getTopLevelProject();
    Properties projectProps = topProject != null ? topProject.getProperties() : new Properties();

    boolean compress =
        boolProp(
            "llmCompactor.compressStackFrames",
            projectProps,
            CompactorDefaults.COMPRESS_STACK_FRAMES);

    String mode = getProperty("llmCompactor.mode", projectProps, null);
    boolean showFailedTestLogs =
        boolProp(
            "llmCompactor.showFailedTestLogs",
            projectProps,
            CompactorDefaults.SHOW_FAILED_TEST_LOGS);
    boolean showFixTargets =
        boolProp("llmCompactor.showFixTargets", projectProps, CompactorDefaults.SHOW_FIX_TARGETS);
    boolean outputAsJson =
        boolProp("llmCompactor.outputAsJson", projectProps, CompactorDefaults.OUTPUT_AS_JSON);
    boolean showSlowTests =
        boolProp("llmCompactor.showSlowTests", projectProps, CompactorDefaults.SHOW_SLOW_TESTS);

    // Apply mode preset if specified (overrides individual flags)
    if (mode != null && !mode.isEmpty()) {
      switch (mode.toLowerCase()) {
        case "agent":
          outputAsJson = true;
          showFixTargets = true;
          showFailedTestLogs = false;
          break;
        case "debug":
          outputAsJson = true;
          showFixTargets = true;
          showFailedTestLogs = true;
          break;
        case "human":
          outputAsJson = false;
          showFixTargets = true;
          showFailedTestLogs = false;
          break;
        default:
          break;
      }
    }

    List<String> includePackages = buildIncludePackages(projectProps);

    List<BuildError> allErrors = new ArrayList<>(compileErrors);
    List<Double> allDurations = new ArrayList<>();
    int totalTestsRun = 0;
    int totalTestFailures = 0;

    long sessionStartTime = session.getStartTime() != null ? session.getStartTime().getTime() : 0;

    List<MavenProject> projects = session.getProjects();
    if (projects != null) {
      for (MavenProject project : projects) {
        Path targetDir = project.getBasedir().toPath().resolve("target");
        if (Files.exists(targetDir)) {
          TestResult result =
              SurefireParser.parse(
                  targetDir, compress, includePackages, sessionStartTime, showFailedTestLogs);
          totalTestsRun += result.testsRun();
          totalTestFailures += result.failures();
          allErrors.addAll(result.errors());
          allDurations.addAll(result.allDurations());
        }
      }
    }

    Long totalBuildDurationMs = null;
    if (boolProp(
        "llmCompactor.showTotalDuration", projectProps, CompactorDefaults.SHOW_TOTAL_DURATION)) {
      totalBuildDurationMs = System.currentTimeMillis() - sessionStartTime;
    }

    Map<String, Double> testDurationPercentiles = null;
    if (boolProp(
            "llmCompactor.showDurationReport", projectProps, CompactorDefaults.SHOW_DURATION_REPORT)
        && !allDurations.isEmpty()) {
      testDurationPercentiles = BuildSummary.computePercentiles(allDurations);
    }

    allErrors = BuildSummary.aggregateErrors(allErrors);

    List<FixTarget> targets =
        showFixTargets
            ? FixTargetGenerator.generate(allErrors)
            : Collections.<FixTarget>emptyList();

    List<String> recentChanges =
        boolProp(
                "llmCompactor.showRecentChanges",
                projectProps,
                CompactorDefaults.SHOW_RECENT_CHANGES)
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

    double testDurationThresholdMs =
        getDoubleProp(
            "llmCompactor.testDurationThresholdMs",
            projectProps,
            CompactorDefaults.TEST_DURATION_THRESHOLD_MS);

    if (outputAsJson) {
      REAL_OUT.print(SummaryWriter.toJson(summary, testDurationThresholdMs));
    } else {
      REAL_OUT.println(
          SummaryWriter.toHumanReadable(summary, showSlowTests, testDurationThresholdMs));
    }
  }

  private List<String> buildIncludePackages(Properties projectProps) {
    String raw = getProperty("llmCompactor.includePackages", projectProps, "");
    List<String> packages =
        new ArrayList<>(
            raw.isEmpty() ? Collections.<String>emptyList() : Arrays.asList(raw.split(",")));
    List<MavenProject> projects = session != null ? session.getProjects() : null;
    if (projects != null) {
      for (MavenProject project : projects) {
        packages.addAll(scanProjectPackages(project));
      }
    }
    return packages;
  }

  private List<String> scanProjectPackages(MavenProject project) {
    List<String> packages = new ArrayList<>();
    List<String> sourceRoots = new ArrayList<>();
    sourceRoots.addAll(project.getCompileSourceRoots());
    sourceRoots.addAll(project.getTestCompileSourceRoots());

    for (String root : sourceRoots) {
      Path rootPath = Paths.get(root);
      if (!Files.exists(rootPath)) {
        continue;
      }
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
        // ignore
      }
    }
    return packages;
  }

  private boolean boolProp(String key, Properties projectProps, boolean defaultValue) {
    return "true".equalsIgnoreCase(getProperty(key, projectProps, String.valueOf(defaultValue)));
  }

  private double getDoubleProp(String key, Properties projectProps, double defaultValue) {
    String value = getProperty(key, projectProps, String.valueOf(defaultValue));
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private String getProperty(String key, Properties projectProps, String defaultValue) {
    String sysProp = System.getProperty(key);
    if (sysProp != null) {
      return sysProp;
    }
    // Maven -D flags are user properties; they may or may not be propagated to JVM system
    // properties depending on the Maven version and launcher. Check user properties explicitly
    // so that command-line flags always take precedence over plugin XML configuration.
    if (session != null && session.getUserProperties() != null) {
      String userProp = session.getUserProperties().getProperty(key);
      if (userProp != null) {
        return userProp;
      }
    }
    String projProp = projectProps.getProperty(key);
    if (projProp != null) {
      return projProp;
    }
    if (session != null) {
      MavenProject current = session.getCurrentProject();
      if (current != null) {
        String val = getPluginConfigValue(current, key);
        if (val != null) {
          return val;
        }
      }
      MavenProject top = session.getTopLevelProject();
      if (top != null) {
        String val = getPluginConfigValue(top, key);
        if (val != null) {
          return val;
        }
      }
    }
    return defaultValue;
  }

  private String getPluginConfigValue(MavenProject project, String key) {
    Plugin plugin = project.getPlugin("io.llmcompactor:llm-compactor-maven-plugin");
    if (plugin == null) {
      return null;
    }
    Object config = plugin.getConfiguration();
    if (!(config instanceof Xpp3Dom)) {
      return null;
    }
    String configKey =
        key.startsWith("llmCompactor.") ? key.substring("llmCompactor.".length()) : key;
    Xpp3Dom child = ((Xpp3Dom) config).getChild(configKey);
    return child != null ? child.getValue() : null;
  }

  private static PrintStream nullPrintStream() {
    OutputStream nullOut =
        new OutputStream() {
          @Override
          public void write(int b) {}
        };
    try {
      return new PrintStream(nullOut, true, StandardCharsets.UTF_8.name()) {
        @Override
        public void write(byte[] buf, int off, int len) {}
      };
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("UTF-8 not supported", e);
    }
  }

  private void resetSlf4j() {
    try {
      Class<?> friendClass = Class.forName("org.slf4j.MavenSlf4jFriend");
      friendClass.getMethod("reset").invoke(null);
      Class<?> simpleFriend = Class.forName("org.slf4j.impl.MavenSlf4jSimpleFriend");
      simpleFriend.getMethod("init").invoke(null);
    } catch (ClassNotFoundException
        | NoSuchMethodException
        | IllegalAccessException
        | InvocationTargetException e) {
      // ignore
    }
  }

  @SuppressFBWarnings("MS_EXPOSE_REP")
  public static PrintStream getRealOut() {
    return REAL_OUT;
  }

  @SuppressFBWarnings("MS_EXPOSE_REP")
  public static PrintStream getRealErr() {
    return REAL_ERR;
  }

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
