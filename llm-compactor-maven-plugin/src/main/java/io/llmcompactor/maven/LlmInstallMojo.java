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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Installs the Maven extension into the project by creating .mvn/extensions.xml.
 */
@Mojo(name = "install")
public class LlmInstallMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    private java.io.File basedir;

    @Parameter(property = "llmCompactor.version", defaultValue = "0.1.0")
    private String version;

    public void execute() throws MojoExecutionException {
        Path mvnDir = basedir.toPath().resolve(".mvn");
        Path extensionsXml = mvnDir.resolve("extensions.xml");

        try {
            Files.createDirectories(mvnDir);

            String content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<extensions>\n" +
                    "    <extension>\n" +
                    "        <groupId>io.llmcompactor</groupId>\n" +
                    "        <artifactId>maven-extension</artifactId>\n" +
                    "        <version>" + version + "</version>\n" +
                    "    </extension>\n" +
                    "</extensions>";

            Files.writeString(extensionsXml, content);
            getLog().info("Successfully installed LLM Build Compactor extension to " + extensionsXml);
            getLog().info("Future Maven commands will run in silent mode with JSON summary output.");

        } catch (IOException e) {
            throw new MojoExecutionException("Failed to install extension: " + e.getMessage(), e);
        }
    }
}
