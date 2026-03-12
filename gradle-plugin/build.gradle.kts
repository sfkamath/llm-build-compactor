import java.util.Properties

plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "1.3.1"
}

val rootProps = Properties()
rootProps.load(file("../gradle.properties").inputStream())

group = "io.llmcompactor"
val pluginVersion = (findProperty("pluginVersion") as String?) ?: (rootProps["pluginVersion"] as String)
version = pluginVersion

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("io.llmcompactor:core:${version}")
    compileOnly("com.fasterxml.jackson.core:jackson-annotations:2.21")
}

gradlePlugin {
    website = "https://github.com/sfkamath/llm-build-compactor"
    vcsUrl = "https://github.com/sfkamath/llm-build-compactor"

    plugins {
        create("llmCompactor") {
            id = "io.llmcompactor.gradle"
            implementationClass = "io.llmcompactor.gradle.LlmCompactorPlugin"
            displayName = "LLM Build Compactor"
            description = "Compact build output for LLM-assisted development"
            tags = listOf("build", "llm", "ai", "gradle")
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

val generatePluginProperties by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/resources/plugin-properties")
    outputs.dir(outputDir)
    doLast {
        val dir = outputDir.get().asFile
        dir.mkdirs()
        dir.resolve("llm-compactor-plugin.properties").writeText("version=${project.version}\n")
    }
}

sourceSets.main {
    resources.srcDir(generatePluginProperties)
}
