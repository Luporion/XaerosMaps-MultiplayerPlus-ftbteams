package dev.alinco8.xmmp.core.server

import dev.alinco8.xmmp.core.ChunkKey
import dev.alinco8.xmmp.core.ResourceId
import dev.alinco8.xmmp.core.SyncLayer
import dev.alinco8.xmmp.core.config.ServerConfig
import dev.alinco8.xmmp.core.network.CreditWindow
import dev.alinco8.xmmp.core.network.TokenBucket
import dev.alinco8.xmmp.core.network.packet.S2CSharingStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.job
import java.util.concurrent.atomic.AtomicLong

class PlayerState(
    parentScope: CoroutineScope,
    serverConfig: ServerConfig,
) : AutoCloseable {
    class PendingUpload(
        val dimension: ResourceId,
        val layer: SyncLayer,
        val chunkPos: ChunkKey,
        val seq: Long,
        val payload: ByteArray,
        val publishScope: String,
        val syncId: Int,
    )

    val uploadLimiter =
        TokenBucket(serverConfig.shared.uploadBurst, serverConfig.shared.uploadRateLimit)
    val downloadLimiter = TokenBucket(serverConfig.downloadBurst, serverConfig.downloadRateLimit)
    val regionRequestLimiter =
        TokenBucket(
            serverConfig.shared.regionRequestBurst,
            serverConfig.shared.regionRequestRateLimit
        )

    val scope = CoroutineScope(
        parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext.job)
    )

    val uploads = Channel<PendingUpload>(serverConfig.shared.uploadWindow + 16)
    val outbound = Outbound()

    // Session-local diagnostics. These counters are NOT a guarantee of remote
    // client-side map visibility; separate client counters establish that.
    val uploadsStored = AtomicLong()
    val uploadsRejected = AtomicLong()
    val downloadPackets = AtomicLong()
    val downloadedBytes = AtomicLong()
    val regionRequests = AtomicLong()
    val regionThrottled = AtomicLong()

    @Volatile
    var downloadCredits = CreditWindow(1)

    @Volatile var sharingMode: dev.alinco8.xmmp.core.sharing.SharingMode = dev.alinco8.xmmp.core.sharing.SharingMode.PRIVATE
    @Volatile var activeScope: String? = null
    @Volatile var receiveTeam: Boolean = true
    @Volatile var receiveGlobal: Boolean = true
    @Volatile var handshakeComplete: Boolean = false

    var lastSharingStatus: S2CSharingStatus? = null
    var indexJob: Job? = null
    var syncId = -1
    var handshakeSent = false
    var syncedDimension: ResourceId? = null
    var enableWorldMapSync = false

    override fun close() {
        uploads.close()
        scope.cancel()
    }
}
