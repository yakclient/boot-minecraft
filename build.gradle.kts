plugins {
    kotlin("jvm") version "2.0.0-Beta1"
    id("maven-publish")
    id("org.jetbrains.dokka") version "1.9.10"
    java
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

version = "1.1-SNAPSHOT"

application {
    mainClass = "net.yakclient.client.MainKt"
}

configurations.all {
    resolutionStrategy.cacheChangingModulesFor(24, "hours")
}

//val jarInclude by configurations.creating
//configurations {
//    implementation {
//        extendsFrom(jarInclude)
//    }
//}

tasks.wrapper {
    gradleVersion = "8.5"
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.arrow-kt:arrow-core:1.1.2")
    implementation("com.durganmcbroom:jobs:1.0-SNAPSHOT")

    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("net.yakclient:archives:1.1-SNAPSHOT") {
        isChanging = true
    }
    implementation("net.yakclient:boot:2.0-SNAPSHOT") {
        isChanging = true
    }
    implementation("net.yakclient:object-container:1.0-SNAPSHOT") {
        isChanging = true
    }
    implementation("com.durganmcbroom:artifact-resolver:1.0-SNAPSHOT") {
        isChanging = true
    }
    implementation("com.durganmcbroom:artifact-resolver-simple-maven:1.0-SNAPSHOT") {
        isChanging = true
    }
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.13.4")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4")
    implementation("net.yakclient:common-util:1.0-SNAPSHOT") {
        isChanging = true
    }
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.22")
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


task<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

task<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    from(tasks.dokkaJavadoc)
}

tasks.shadowJar {
    from(tasks.named("listAllDependencies"))
}

//tasks.jar {
//    from(jarInclude.files) {
//        into("/")
//    }
//
//    println(jarInclude.files.joinToString(separator = " ") {
//        "${it.name}"
//    })
//
//    manifest {
//        attributes(
//                "Class-Path" to jarInclude.files.filter { it.name.contains("kotlin-stdlib-1.8.20.jar") } .joinToString(separator = " ") {
//                    "${it.name}"
//                },
//                "Main-Class" to "net.yakclient.client.MainKt",
//        )
//    }
//}


//tasks.implementationJar {
//    minimize()
//}
//


publishing {
    publications {
        create<MavenPublication>("prod") {
            artifact(tasks.shadowJar)
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])

            artifactId = "client"
        }
    }
}

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.dokka")

    group = "net.yakclient"

    repositories {
        mavenCentral()
        mavenLocal()
        maven {
            isAllowInsecureProtocol = true
            url = uri("http://maven.yakclient.net/snapshots")
        }
    }

    publishing {
        repositories {
            if (project.hasProperty("maven-user") && project.hasProperty("maven-secret")) maven {
                logger.quiet("Maven user and password found.")
                val repo = if ((version as String).endsWith("-SNAPSHOT")) "snapshots" else "releases"

                isAllowInsecureProtocol = true

                url = uri("http://maven.yakclient.net/$repo")

                credentials {
                    username = project.findProperty("maven-user") as String
                    password = project.findProperty("maven-secret") as String
                }
                authentication {
                    create<BasicAuthentication>("basic")
                }
            } else logger.quiet("Maven user and password not found.")
        }
    }

    kotlin {
        explicitApi()
    }

    dependencies {
        implementation(kotlin("stdlib"))
        implementation(kotlin("reflect"))
        testImplementation(kotlin("test"))
    }



    tasks.compileKotlin {
        destinationDirectory.set(tasks.compileJava.get().destinationDirectory.asFile.get())

        kotlinOptions {
            jvmTarget = "17"
        }
    }

    tasks.compileTestKotlin {
        kotlinOptions {
            jvmTarget = "17"
        }
    }

    tasks.test {
        useJUnitPlatform()
    }

    tasks.compileJava {
        targetCompatibility = "17"
        sourceCompatibility = "17"
    }
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
}