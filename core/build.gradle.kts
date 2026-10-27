import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlin.serialization)
}

group = "dev.alinco8.xmmp"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("stdlib"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")

    compileOnly(kotlin("stdlib"))
    compileOnly(libs.kotlinx.coroutines.core)
    compileOnly(libs.ktoml.core.jvm)

    compileOnly(libs.netty.buffer)
    compileOnly(libs.fastutil)
    compileOnly(libs.slf4j.api)
    compileOnly(libs.zstd.jni)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    testLogging { events("passed", "skipped", "failed") }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        apiVersion = KotlinVersion.KOTLIN_2_2
    }
}
