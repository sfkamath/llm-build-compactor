package io.llmcompactor.extension;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackageScannerTest {

  @Test
  void emptyProjectReturnsNoPackages() {
    assertThat(PackageScanner.scan(new MavenProject())).isEmpty();
  }

  @Test
  void discoversPackageFromJavaFile(@TempDir Path tmpDir) throws IOException {
    Path pkgDir = tmpDir.resolve("io/llmcompactor/test");
    Files.createDirectories(pkgDir);
    Files.createFile(pkgDir.resolve("Foo.java"));

    MavenProject project = new MavenProject();
    project.addCompileSourceRoot(tmpDir.toString());

    List<String> packages = PackageScanner.scan(project);
    assertThat(packages).containsExactly("io.llmcompactor.test");
  }

  @Test
  void discoversMultiplePackages(@TempDir Path tmpDir) throws IOException {
    for (String pkg : new String[]{"com.example.a", "com.example.b"}) {
      Path dir = tmpDir.resolve(pkg.replace('.', '/'));
      Files.createDirectories(dir);
      Files.createFile(dir.resolve("X.java"));
    }

    MavenProject project = new MavenProject();
    project.addCompileSourceRoot(tmpDir.toString());

    List<String> packages = PackageScanner.scan(project);
    assertThat(packages).containsExactlyInAnyOrder("com.example.a", "com.example.b");
  }

  @Test
  void deduplicatesPackages(@TempDir Path tmpDir) throws IOException {
    Path pkgDir = tmpDir.resolve("io/example");
    Files.createDirectories(pkgDir);
    Files.createFile(pkgDir.resolve("A.java"));
    Files.createFile(pkgDir.resolve("B.java"));

    MavenProject project = new MavenProject();
    project.addCompileSourceRoot(tmpDir.toString());

    assertThat(PackageScanner.scan(project)).containsExactly("io.example");
  }

  @Test
  void nonExistentRootIgnored(@TempDir Path tmpDir) {
    MavenProject project = new MavenProject();
    project.addCompileSourceRoot(tmpDir.resolve("does-not-exist").toString());
    assertThat(PackageScanner.scan(project)).isEmpty();
  }
}
