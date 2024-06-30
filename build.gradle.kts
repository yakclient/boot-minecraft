import dev.extframework.gradle.common.*
import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.dm.jobs

plugins {
    kotlin("jvm") version "2.0.0-Beta1"
    id("maven-publish")
    id("org.jetbrains.dokka") version "1.9.10"
    java
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("dev.extframework.common") version "1.0.5"
}

group = "dev.extframework"
version = "1.1.2-SNAPSHOT"

repositories {
    mavenCentral()
    extFramework()
}

application {
    mainClass = "dev.extframework.client.MainKt"
}

kotlin {
    explicitApi()
}

tasks.wrapper {
    gradleVersion = "8.5"
}

val artifactResolverVersions = "1.1.4-SNAPSHOT"

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.13.4")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.22")

    jobs()
    archives()
    boot()
    objectContainer()
    artifactResolver(maven = true)
    commonUtil()

    testImplementation(kotlin("test"))

}

open class ListAllDependencies : DefaultTask() {
    init {
        // Define the output file within the build directory
        val outputFile = project.buildDir.resolve("dependencies.txt")
        outputs.file(outputFile)
    }

    @TaskAction
    fun listDependencies() {
        val outputFile = project.layout.buildDirectory.get().file("dependencies.txt").asFile
        // Ensure the directory for the output file exists
        outputFile.parentFile.mkdirs()
        // Clear or create the output file
        outputFile.writeText("")

        val set = HashSet<String>()

        // Process each configuration that can be resolved
        project.configurations.filter { it.isCanBeResolved }.forEach { configuration ->
            println("Processing configuration: ${configuration.name}")
            try {
                configuration.resolvedConfiguration.firstLevelModuleDependencies.forEach { dependency ->
                    collectDependencies(dependency, set)
                }
            } catch (e: Exception) {
                println("Skipping configuration '${configuration.name}' due to resolution errors.")
            }
        }

        set.forEach {
            outputFile.appendText(it)
        }
    }

    private fun collectDependencies(dependency: ResolvedDependency, set: MutableSet<String>) {
        set.add("${dependency.moduleGroup}:${dependency.moduleName}\n")
        dependency.children.forEach { childDependency ->
            collectDependencies(childDependency, set)
        }
    }
}

// Register the custom task in the project
tasks.register<ListAllDependencies>("listAllDependencies")


tasks.shadowJar {
    from(tasks.named("listAllDependencies"))
}

common {
    defaultJavaSettings()

    publishing {
        publication {
            withSources()
            withDokka()
            artifact(tasks.shadowJar)

            artifactId = "client"
        }
        repositories {
            extFramework(credentials = propertyCredentialProvider)
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}