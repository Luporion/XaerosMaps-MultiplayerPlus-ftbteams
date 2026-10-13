package dev.alinco8.xmmp.core.config

import com.akuleshov7.ktoml.Toml
import dev.alinco8.xmmp.core.LOGGER
import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

object ConfigIO {
    inline fun <reified T> load(string: String) = Toml.decodeFromString<T>(string)
    inline fun <reified T> save(file: File, config: T) = file.writeText(Toml.encodeToString(config))

    inline fun <reified T> loadOrInit(file: File, default: () -> T) = try {
        if (file.exists()) {
            load<T>(file.readText())
        } else {
            val config = default()
            save(file, config)

            config
        }
    } catch (e: Exception) {
        LOGGER.error(
            "Failed to load config from ${file.absolutePath}, reinitializing with default config",
            e
        )

        val config = default()
        save(file, config)

        config
    }
}
