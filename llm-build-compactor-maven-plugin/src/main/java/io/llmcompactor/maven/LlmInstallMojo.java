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
package io.llmcompactor.maven;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/** Installs the Maven extension into the project by creating .mvn/extensions.xml. */
@Mojo(name = "install")
public class LlmInstallMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project.basedir}", readonly = true)
  private File basedir;

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  public void execute() throws MojoExecutionException {
    if (!project.isExecutionRoot()) {
      return;
    }
    String version = loadPluginVersion();
    Path mvnDir = basedir.toPath().resolve(".mvn");
    Path extensionsXml = mvnDir.resolve("extensions.xml");

    try {
      Files.createDirectories(mvnDir);

      String content =
          "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
              + "<extensions>\n"
              + "    <extension>\n"
              + "        <groupId>io.github.sfkamath</groupId>\n"
              + "        <artifactId>llm-build-compactor-extension</artifactId>\n"
              + "        <version>"
              + version
              + "</version>\n"
              + "    </extension>\n"
              + "</extensions>";

      Files.write(extensionsXml, content.getBytes(StandardCharsets.UTF_8));
      getLog().info("Successfully installed LLM Build Compactor extension to " + extensionsXml);
      getLog().info("Future Maven commands will run in silent mode with JSON summary output.");

    } catch (IOException e) {
      throw new MojoExecutionException("Failed to install extension: " + e.getMessage(), e);
    }
  }

  private String loadPluginVersion() throws MojoExecutionException {
    try (InputStream is =
        getClass().getResourceAsStream("/llm-compactor-plugin.properties")) {
      if (is == null) {
        throw new MojoExecutionException(
            "Cannot determine plugin version: llm-compactor-plugin.properties not found in JAR");
      }
      Properties props = new Properties();
      props.load(is);
      String v = props.getProperty("version");
      if (v == null || v.isEmpty()) {
        throw new MojoExecutionException(
            "Cannot determine plugin version: 'version' key missing in llm-compactor-plugin.properties");
      }
      return v;
    } catch (IOException e) {
      throw new MojoExecutionException(
          "Failed to read plugin version: " + e.getMessage(), e);
    }
  }
}
