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

import java.util.Properties;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * Resolves configuration properties using a prioritised lookup chain:
 *
 * <ol>
 *   <li>JVM system property ({@code -Dfoo=bar} on the command line)
 *   <li>Maven user property (set via {@code -Dfoo=bar} in the Maven launcher, may differ from the
 *       JVM system property depending on Maven version)
 *   <li>Top-level project {@code <properties>} block
 *   <li>Plugin {@code <configuration>} block in the current project, then the top-level project
 *   <li>Supplied default value
 * </ol>
 */
final class PropertyResolver {

  private static final String PLUGIN_KEY = "io.github.sfkamath:llm-build-compactor-maven-plugin";
  private static final String LLMCOMPACTOR_PREFIX = "llmCompactor.";

  private final MavenSession session;
  private final Properties projectProps;

  PropertyResolver(MavenSession session, Properties projectProps) {
    this.session = session;
    this.projectProps = projectProps != null ? projectProps : new Properties();
  }

  // -------------------------------------------------------------------------
  // Typed accessors
  // -------------------------------------------------------------------------

  String getString(String key, String defaultValue) {
    String sysProp = System.getProperty(key);
    if (sysProp != null) {
      return sysProp;
    }
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
      String fromCurrent = pluginConfigValue(session.getCurrentProject(), key);
      if (fromCurrent != null) {
        return fromCurrent;
      }
      String fromTop = pluginConfigValue(session.getTopLevelProject(), key);
      if (fromTop != null) {
        return fromTop;
      }
    }
    return defaultValue;
  }

  boolean getBoolean(String key, boolean defaultValue) {
    return "true".equalsIgnoreCase(getString(key, String.valueOf(defaultValue)));
  }

  double getDouble(String key, double defaultValue) {
    String value = getString(key, String.valueOf(defaultValue));
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  private String pluginConfigValue(MavenProject project, String key) {
    if (project == null) {
      return null;
    }
    Plugin plugin = project.getPlugin(PLUGIN_KEY);
    if (plugin == null) {
      return null;
    }
    Object config = plugin.getConfiguration();
    if (!(config instanceof Xpp3Dom)) {
      return null;
    }
    String configKey =
        key.startsWith(LLMCOMPACTOR_PREFIX) ? key.substring(LLMCOMPACTOR_PREFIX.length()) : key;
    Xpp3Dom child = ((Xpp3Dom) config).getChild(configKey);
    return child != null ? child.getValue() : null;
  }
}
