package dev.alinco8.xmmp.standalone.config

import dev.alinco8.xmmp.core.config.ServerConfig
import kotlinx.serialization.Serializable

@Serializable
data class StandaloneConfig(
    val port: Int = 25580,
    val common: ServerConfig = ServerConfig(),
)
