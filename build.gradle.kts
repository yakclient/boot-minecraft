import dev.extframework.gradle.common.*
import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.dm.jobs
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.0"
    id("maven-publish")
    id("org.jetbrains.dokka") version "1.9.10"
    java
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("dev.extframework.common") version "1.0.38"

    id("me.champeau.mrjar") version "0.1.1"
}

group = "dev.extframework"
version = "1.0.10-BETA"

repositories {
    mavenCentral()
    mavenLocal()
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

multiRelease {
    targetVersions(8, 11)
}

val bootVersion = BOOT_VERSION

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation("com.github.ajalt.clikt:clikt:4.4.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.22")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    toolingApi()
    jobs()
    archives()
    boot(version = bootVersion)
    objectContainer()
    artifactResolver(maven = true)
    commonUtil()
    extLoader()
    minecraftBootstrapper()

    implementation("dev.extframework:boot:$bootVersion:jdk11")
    implementation("dev.extframework:archives:$ARCHIVES_VERSION:jdk11")


    "java11Implementation"("dev.extframework:boot:$bootVersion:jdk11")
    boot(version = bootVersion, configurationName = "java11Implementation")
    objectContainer(configurationName = "java11Implementation")


    testImplementation(kotlin("test"))
}


abstract class ListAllDependencies : DefaultTask() {
    init {
        // Define the output file within the build directory
        val outputFile = project.buildDir.resolve("resources/main/dependencies.txt")
        outputs.file(outputFile)
    }

    @TaskAction
    fun listDependencies() {
        val outputFile = project.buildDir.resolve("resources/main/dependencies.txt")
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

        set.add("${this.project.group}:minecraft-bootstrapper:${this.project.version}\n")

        set.forEach {
            outputFile.appendText(it)
        }
    }

    private fun collectDependencies(dependency: ResolvedDependency, set: MutableSet<String>) {
        set.add("${dependency.moduleGroup}:${dependency.moduleName}:${dependency.moduleVersion}\n")
        dependency.children.forEach { childDependency ->
            collectDependencies(childDependency, set)
        }
    }
}

tasks.register<GenerateProfileTask>("generateProfile")

tasks.named<Test>("java11Test") {
    description = "Runs tests in the java11Test source set"
    group = "verification"

    testClassesDirs = sourceSets["java11Test"].output.classesDirs
    classpath = sourceSets["java11Test"].runtimeClasspath

    // Use JUnit 5 if applicable
    useJUnitPlatform()
}


tasks.named<KotlinCompile>("compileJava11Kotlin") {
    kotlinJavaToolchain.toolchain.use(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(11))
    })
    kotlinOptions.jvmTarget = "11"
    kotlinOptions.freeCompilerArgs += "-Xexplicit-api=strict"
}

tasks.named<JavaCompile>("compileJava11Java") {
    sourceCompatibility = "11"
    targetCompatibility = "11"
}

tasks.named<JavaCompile>("compileJava11TestJava") {
    sourceCompatibility = "11"
    targetCompatibility = "11"
}

// Register the custom task in the project
val listAllDependencies by tasks.registering(ListAllDependencies::class)

tasks.compileKotlin {
    dependsOn(listAllDependencies)
}

tasks.jar {
//    from(tasks.named("listAllDependencies"))
}

tasks.shadowJar {
    from(tasks.named("listAllDependencies"))
    from(sourceSets["java11"].output) {
        into("META-INF/versions/11")
    }
    manifest {
        attributes("Multi-Release" to true)
    }

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
            extFramework(credentials = propertyCredentialProvider, type = RepositoryType.RELEASES)
        }
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
}