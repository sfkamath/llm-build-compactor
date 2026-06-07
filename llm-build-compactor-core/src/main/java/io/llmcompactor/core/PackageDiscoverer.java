package io.llmcompactor.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class PackageDiscoverer {

  public static List<String> discoverPackages(Iterable<Path> roots) {
    List<String> packages = new ArrayList<>();
    for (Path root : roots) {
      if (!Files.exists(root)) {
        continue;
      }
      try (Stream<Path> walk = Files.walk(root)) {
        walk.filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(
                p -> {
                  Path parent = root.relativize(p).getParent();
                  if (parent != null) {
                    String pkg = parent.toString().replace(File.separator, ".");
                    if (!packages.contains(pkg)) {
                      packages.add(pkg);
                    }
                  }
                });
      } catch (IOException ignored) {
      }
    }
    return packages;
  }

  private PackageDiscoverer() {}
}
