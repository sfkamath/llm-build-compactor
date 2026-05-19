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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.apache.maven.project.MavenProject;

/**
 * Discovers Java package names by walking a project's compile and test source roots.
 *
 * <p>Used to seed the stack-frame whitelist so that only frames belonging to the project
 * under build are highlighted in failure output.
 */
final class PackageScanner {

  private PackageScanner() {}

  static List<String> scan(MavenProject project) {
    List<String> packages = new ArrayList<>();
    List<String> sourceRoots = new ArrayList<>();
    sourceRoots.addAll(project.getCompileSourceRoots());
    sourceRoots.addAll(project.getTestCompileSourceRoots());

    for (String root : sourceRoots) {
      Path rootPath = Paths.get(root);
      if (Files.exists(rootPath)) {
        packages.addAll(packagesUnder(rootPath));
      }
    }
    return packages;
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  private static List<String> packagesUnder(Path rootPath) {
    List<String> packages = new ArrayList<>();
    try (Stream<Path> walk = Files.walk(rootPath)) {
      walk
          .filter(Files::isRegularFile)
          .filter(p -> p.toString().endsWith(".java"))
          .forEach(p -> {
            Path parent = rootPath.relativize(p).getParent();
            if (parent != null) {
              String pkg = parent.toString().replace("/", ".");
              if (!packages.contains(pkg)) {
                packages.add(pkg);
              }
            }
          });
    } catch (IOException ignored) {
      // Best-effort scan; a missing or unreadable root is not fatal.
    }
    return packages;
  }
}
