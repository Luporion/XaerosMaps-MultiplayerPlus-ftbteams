package dev.alinco8.xmmp.core.config

import dev.alinco8.xmmp.core.network.XMMPStreamCodec
import kotlinx.serialization.Serializable

@Serializable
data class ServerConfig(
    val enableChunkValidation: Boolean = true,
    val regionFlushInterval: Long = 30_000L,
    val maxDownloadWindow: Int = 256,
    val downloadRateLimit: Double = 200.0,
    val downloadBurst: Double = 64.0,
    val shared: SharedConfig = SharedConfig(),
) {
    @Serializable
    data class SharedConfig(
        val syncCaves: Boolean = false,
        val uploadWindow: Int = 64,
        val uploadRateLimit: Double = 200.0,
        val uploadBurst: Double = 64.0,
        val regionRequestRateLimit: Double = 10.0,
        val regionRequestBurst: Double = 20.0,
    ) {
        companion object {
            val codec = with(XMMPStreamCodec) {
                composite(
                    boolean, SharedConfig::syncCaves,
                    int, SharedConfig::uploadWindow,
                    double, SharedConfig::uploadRateLimit,
                    double, SharedConfig::uploadBurst,
                    double, SharedConfig::regionRequestRateLimit,
                    double, SharedConfig::regionRequestBurst,
                    ::SharedConfig
                )
            }
        }
    }
}
