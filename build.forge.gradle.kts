@file:Suppress("UnstableApiUsage")

import buildlogic.ifProp
import buildlogic.prop
import buildlogic.propOrNull
import buildlogic.strictMaven
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.slf4j.event.Level

plugins {
    id("net.neoforged.moddev.legacyforge")
    id("dev.kikugie.fletching-table.lexforge")
    id("me.modmuss50.mod-publish-plugin")
    id("com.gradleup.shadow")
    id("project.common")
}

val minecraft = extra["minecraft"] as String
val loader = extra["loader"] as String

val shade = configurations.create("shade") {
    isTransitive = false
}

repositories {
    strictMaven(
        "thedarkcolour",
        "https://thedarkcolour.github.io/KotlinForForge/",
    ) // Kotlin for Forge
}

legacyForge {
    version = "${minecraft}-${prop("deps.forge.version")}"

    ifProp("deps.parchment.version") {
        parchment {
            mappingsVersion = prop("deps.parchment.version")
            minecraftVersion = minecraft
        }
    }

    runs {
        create("client") {
            client()
            ideName = "${project().name} - Client"
            programArguments.addAll("--username", "Dev")
        }
        create("client2") {
            client()
            ideName = "${project().name} - Client 2"
            programArguments.addAll("--username", "Dev2")
        }
        create("server") {
            server();
            programArgument("--nogui")
            ideName = "${project().name} - Server"
        }

        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            systemProperty("forge.logging.console.level", "debug")
            programArgument("--mixin.config=${prop("mod.id")}.mixins.json")

            logLevel = Level.DEBUG
        }
    }

    mods {
        create(prop("mod.id")) {
            sourceSet(sourceSets["main"])
        }
    }
}

dependencies {
    modImplementation("thedarkcolour:kotlinforforge:${prop("deps.kff.version")}")
    implementation("thedarkcolour:kfflib:${prop("deps.kff.version")}")
    implementation(libs.kotlinx.coroutines.core)

    jarJar("dev.alinco8.xmmp:core")
    implementation("dev.alinco8.xmmp:core")

    jarJar(libs.zstd.jni)
    implementation(libs.zstd.jni)
    add("additionalRuntimeClasspath", libs.zstd.jni)

    shade(libs.kotlinx.datetime.jvm)
    runtimeOnly(libs.kotlinx.datetime.jvm)

    shade(libs.ktoml.core.jvm)
    runtimeOnly(libs.ktoml.core.jvm)

    compileOnly(libs.night.config.toml)

    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")

    compileOnly("io.github.llamalad7:mixinextras-common:0.5.5")
    annotationProcessor("io.github.llamalad7:mixinextras-common:0.5.5")

    jarJar("io.github.llamalad7:mixinextras-forge:0.5.5") {
        version {
            strictly("[0.5.5,)")
            prefer("0.5.5")
        }
    }
    implementation("io.github.llamalad7:mixinextras-forge:0.5.5")

    listOf(
        "mcwifipnp"
    ).forEach {
        try {
            modRuntimeOnly(
                fletchingTable.modrinth(it)
            )
        } catch (_: Exception) {
            println("Failed to resolve mod '$it' from modrinth dependencies, skipping...")
        }
    }
}

val shadeLibs = tasks.register<ShadowJar>("shadeLibs") {
    configurations = listOf(shade)
    archiveClassifier = "shaded-libs"
    destinationDirectory = layout.buildDirectory.dir("tmp/shadeLibs")

    relocate("kotlinx.datetime", "dev.alinco8.xmmp.shadow.kotlinx.datetime")

    exclude("module-info.class", "META-INF/versions/**/module-info.class")
    exclude("META-INF/*.kotlin_module", "META-INF/maven/**")
}

mixin {
    add(sourceSets.main.get(), "${prop("mod.id")}.refmap.json")
}

val reobfJar = tasks.named<AbstractArchiveTask>("reobfJar")

tasks {
    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
    }
    named<ProcessResources>("processResources") {
        exclude("fabric.mod.json5", "META-INF/neoforge.mods.toml")
    }

    shadowJar {
        enabled = false
    }

    named<Jar>("jar") {
        dependsOn(shadeLibs)
        from(shadeLibs.map { zipTree(it.archiveFile) }) {
            exclude("META-INF/MANIFEST.MF")
        }

        manifest {
            attributes(
                "MixinConfigs" to "${prop("mod.id")}.mixins.json"
            )
        }
    }
}

publishMods {
    val prebuilt = propOrNull("PREBUILT_DIR")
    if (prebuilt != null) {
        file = reobfJar.flatMap { it.archiveFileName }.map { File(prebuilt, it) }
        additionalFiles.from(tasks.named<Jar>("sourcesJar").flatMap { it.archiveFileName }.map {
            File(prebuilt, it)
        })
    } else {
        file = reobfJar.map { it.archiveFile.get() }
        additionalFiles.from(tasks.named<Jar>("sourcesJar").map { it.archiveFile.get() })
    }

    val slugs = listOf("kotlin-for-forge")

    modrinth {
        slugs.forEach(::requires)
    }
    curseforge {
        slugs.forEach(::requires)
    }
}
