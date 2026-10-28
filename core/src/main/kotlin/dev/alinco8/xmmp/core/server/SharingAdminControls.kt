package dev.alinco8.xmmp.core.server

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

/** Runtime server controls, not trusted client state. Defaults keep v0.3 behaviour. */
data class SharingAdminSettings(
    val allowTeam: Boolean = true,
    val allowGlobal: Boolean = true,
    val allowUploads: Boolean = true,
)

class SharingAdminControls(private val file: Path) {
    @Volatile var settings: SharingAdminSettings = load()
        private set

    private fun load(): SharingAdminSettings = try {
        if (!Files.exists(file)) SharingAdminSettings() else {
            val props = Properties()
            Files.newInputStream(file).use { props.load(it) }
            fun flag(key: String) = when (props.getProperty(key)) {
                "true" -> true
                "false" -> false
                else -> false // Fail closed on missing/corrupt saved permission switches.
            }
            SharingAdminSettings(flag("allowTeam"), flag("allowGlobal"), flag("allowUploads"))
        }
    } catch (_: Exception) { SharingAdminSettings(false, false, false) }

    @Synchronized fun change(next: SharingAdminSettings) {
        Files.createDirectories(file.parent)
        val temp = Files.createTempFile(file.parent, "xmmp-admin-", ".tmp")
        try {
            val props = Properties()
            props.setProperty("allowTeam", next.allowTeam.toString())
            props.setProperty("allowGlobal", next.allowGlobal.toString())
            props.setProperty("allowUploads", next.allowUploads.toString())
            Files.newOutputStream(temp).use { props.store(it, "XMMP server admin settings") }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            }
            settings = next // Only expose after durable success.
        } finally { Files.deleteIfExists(temp) }
    }

    fun allows(scope: String): Boolean = when {
        scope == "global" -> settings.allowGlobal
        scope.startsWith("team-") -> settings.allowTeam
        scope.startsWith("private-") -> true
        else -> false
    }
}
