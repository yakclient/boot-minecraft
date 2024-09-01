import dev.extframework.gradle.common.commonUtil
import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.dm.resourceApi
import dev.extframework.gradle.common.extFramework

plugins {
    kotlin("jvm") version "2.0.0-Beta1"
    id("dev.extframework.common") version "1.0.7"
}

repositories {
    mavenCentral()
    extFramework()
}

dependencies {
    artifactResolver(maven=true)
    resourceApi()
    commonUtil()
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4")

}

common {
    defaultJavaSettings()
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}