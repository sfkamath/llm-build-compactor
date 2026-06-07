package io.llmcompactor.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackageDiscovererTest {

  @TempDir Path tempDir;

  @Test
  void emptyDirReturnsEmpty() {
    List<String> pkgs = PackageDiscoverer.discoverPackages(Collections.singletonList(tempDir));
    assertThat(pkgs).isEmpty();
  }

  @Test
  void discoversPackageFromJavaFile() throws IOException {
    Path pkg = Files.createDirectories(tempDir.resolve("com/example"));
    Files.createFile(pkg.resolve("Foo.java"));
    List<String> pkgs = PackageDiscoverer.discoverPackages(Collections.singletonList(tempDir));
    assertThat(pkgs).containsExactly("com.example");
  }

  @Test
  void ignoresNonJavaFiles() throws IOException {
    Path pkg = Files.createDirectories(tempDir.resolve("com/example"));
    Files.createFile(pkg.resolve("config.xml"));
    List<String> pkgs = PackageDiscoverer.discoverPackages(Collections.singletonList(tempDir));
    assertThat(pkgs).isEmpty();
  }

  @Test
  void deduplicatesPackages() throws IOException {
    Path pkg = Files.createDirectories(tempDir.resolve("com/example"));
    Files.createFile(pkg.resolve("Foo.java"));
    Files.createFile(pkg.resolve("Bar.java"));
    List<String> pkgs = PackageDiscoverer.discoverPackages(Collections.singletonList(tempDir));
    assertThat(pkgs).hasSize(1).containsExactly("com.example");
  }

  @Test
  void skipsNonexistentRoot() {
    Path missing = tempDir.resolve("does-not-exist");
    List<String> pkgs = PackageDiscoverer.discoverPackages(Collections.singletonList(missing));
    assertThat(pkgs).isEmpty();
  }

  @Test
  void rootLevelJavaFileYieldsNoPackage() throws IOException {
    Files.createFile(tempDir.resolve("Foo.java"));
    List<String> pkgs = PackageDiscoverer.discoverPackages(Collections.singletonList(tempDir));
    assertThat(pkgs).isEmpty();
  }

  @Test
  void accumulatesAcrossMultipleRoots() throws IOException {
    Path root1 = Files.createDirectories(tempDir.resolve("src1"));
    Path root2 = Files.createDirectories(tempDir.resolve("src2"));
    Files.createFile(Files.createDirectories(root1.resolve("com/foo")).resolve("A.java"));
    Files.createFile(Files.createDirectories(root2.resolve("com/bar")).resolve("B.java"));
    List<String> pkgs = PackageDiscoverer.discoverPackages(Arrays.asList(root1, root2));
    assertThat(pkgs).containsExactlyInAnyOrder("com.foo", "com.bar");
  }
}
