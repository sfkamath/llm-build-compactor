package io.llmcompactor.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

@UtilityClass
public class PackageDiscoverer {

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

  /**
   * Resolves a package and file name back to a physical source path using provided source roots.
   *
   * @param packageName the package name (e.g., "io.llmcompactor.core")
   * @param fileName the source file name (e.g., "PackageDiscoverer.java")
   * @param roots the list of source roots to search (e.g., ["src/main/java", "src/test/java"])
   * @return the absolute path to the source file, or null if not found
   */
  public static String resolveSourceFile(String packageName, String fileName, List<Path> roots) {
    if (packageName == null || fileName == null || roots == null) {
      return null;
    }
    String relativePath = packageName.replace('.', File.separatorChar) + File.separator + fileName;
    for (Path root : roots) {
      Path filePath = root.resolve(relativePath);
      if (Files.exists(filePath)) {
        return filePath.toString();
      }
    }
    return null;
  }
}
