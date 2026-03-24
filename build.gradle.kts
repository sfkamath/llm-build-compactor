plugins {
    id("java")
    id("io.github.sfkamath.llm-build-compactor") apply false
}

// Root build.gradle.kts for LLM Build Compactor
// This provides convenience tasks for running Gradle integration tests
// and dependency version management.

// Note: The gradle-plugin is resolved via includeBuild() in settings.gradle.kts
// This means the plugin is built from source and doesn't need to be published
// for local development and integration testing.

allprojects {
    repositories {
        mavenCentral()
        mavenLocal()
    }
}

subprojects {
    apply(plugin = "java")

    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
