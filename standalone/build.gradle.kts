plugins {
    id("com.gradleup.shadow")
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

group = "dev.alinco8.xmmp"

repositories {
    mavenCentral()
}

base.archivesName = "xmmp-standalone"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation("dev.alinco8.xmmp:core")

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.netty.all)
    implementation(libs.fastutil)
    implementation(libs.slf4j.api)
    implementation(libs.zstd.jni)
    implementation(libs.ktoml.core.jvm)

    runtimeOnly(libs.slf4j.simple)
}

application {
    mainClass.set("dev.alinco8.xmmp.standalone.MainKt")
}

tasks {
    jar {
        archiveClassifier.set("plain")
        enabled = false
    }

    shadowJar {
        archiveClassifier.set("")
        manifest {
            attributes["Main-Class"] = application.mainClass.get()
        }
    }
}
