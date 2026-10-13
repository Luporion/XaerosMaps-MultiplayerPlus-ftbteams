package dev.alinco8.xmmp.standalone.config

import kotlinx.serialization.Serializable

@Serializable
data class StandaloneConfig(
    val port: Int = 25580,
)
