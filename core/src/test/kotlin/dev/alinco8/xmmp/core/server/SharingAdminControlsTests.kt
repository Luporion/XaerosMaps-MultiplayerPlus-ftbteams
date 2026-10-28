package dev.alinco8.xmmp.core.server

import java.nio.file.Files

/** Standalone runnable checks, also called by JUnit on CI. */
fun runSharingAdminControlsChecks() {
    val dir = Files.createTempDirectory("xmmp-admin-test-")
    try {
        val file = dir.resolve("admin-controls.properties")
        val controls = SharingAdminControls(file)
        check(controls.settings == SharingAdminSettings())
        check(controls.allows("private-user"))
        check(controls.allows("team-abc"))
        check(controls.allows("global"))
        controls.change(SharingAdminSettings(false, true, false))
        check(!controls.allows("team-abc"))
        check(controls.allows("global"))
        check(controls.allows("private-anything"))
        check(!controls.settings.allowUploads)
        val afterReload = SharingAdminControls(file)
        check(afterReload.settings == SharingAdminSettings(false, true, false))
        afterReload.change(SharingAdminSettings(false, false, false))
        check(!SharingAdminControls(file).allows("global"))
        Files.writeString(file, "allowTeam=maybe\nallowGlobal=true\nallowUploads=false\n")
        check(!SharingAdminControls(file).settings.allowTeam)
        check(SharingAdminControls(file).settings.allowGlobal)
        Files.writeString(file, "invalid-properties")
        check(SharingAdminControls(file).settings == SharingAdminSettings(false, false, false))
        println("SharingAdminControlsTests: defaults, updates, durable reload and corrupt-file fail-closed OK")
    } finally {
        Files.list(dir).use { stream -> stream.forEach(Files::deleteIfExists) }
        Files.deleteIfExists(dir)
    }
}

fun main() = runSharingAdminControlsChecks()
