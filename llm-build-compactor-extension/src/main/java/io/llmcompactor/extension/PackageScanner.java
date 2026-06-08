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

import io.llmcompactor.core.PackageDiscoverer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.apache.maven.project.MavenProject;

/**
 * Discovers Java package names by walking a project's compile and test source roots.
 *
 * <p>Used to seed the stack-frame whitelist so that only frames belonging to the project under
 * build are highlighted in failure output.
 */
@UtilityClass
final class PackageScanner {

  static List<String> scan(MavenProject project) {
    List<String> sourceRoots = new ArrayList<>();
    sourceRoots.addAll(project.getCompileSourceRoots());
    sourceRoots.addAll(project.getTestCompileSourceRoots());
    List<Path> roots = new ArrayList<>();
    for (String root : sourceRoots) {
      roots.add(Paths.get(root));
    }
    return PackageDiscoverer.discoverPackages(roots);
  }
}
