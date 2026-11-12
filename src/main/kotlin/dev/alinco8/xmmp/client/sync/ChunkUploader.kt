package dev.alinco8.xmmp.client.sync

import dev.alinco8.xmmp.client.MapSyncContext
import dev.alinco8.xmmp.core.ChunkKey
import dev.alinco8.xmmp.core.ResourceId
import dev.alinco8.xmmp.core.SyncLayer
import dev.alinco8.xmmp.core.network.packet.C2SChunkUpload

class ChunkUploader(
    private val ctx: MapSyncContext,
    val dimension: ResourceId,
    val layer: SyncLayer,
    private val flow: UploadFlow,
) {
    private class Pending(val payload: ByteArray, val hash: Long)

    companion object {
        private const val MAX_PENDING_CHUNKS = 10_000
    }

    private val pending = LinkedHashMap<ChunkKey, Pending>()
    private val sentHashes = HashMap<ChunkKey, Long>()
    @Volatile
    var generation = 0L
        private set

    @Synchronized
    fun discardPending() {
        generation++
        pending.clear()
        sentHashes.clear()
    }

    @Synchronized
    fun pendingCount() = pending.size

    @Synchronized
    private fun markSent(key: ChunkKey, hash: Long, uploadGeneration: Long) {
        if (generation == uploadGeneration) sentHashes[key] = hash
    }

    @Synchronized
    private fun takeNext(): Triple<Long, ChunkKey, ByteArray>? {
        if (pending.isEmpty()) return null
        val e = pending.entries.first()
        val key = e.key
        val hash = e.value.hash
        val uploadGeneration = generation
        val seq = flow.tryAcquire { markSent(key, hash, uploadGeneration) } ?: return null
        pending.remove(key)

        return Triple(seq, key, e.value.payload)
    }

    @Synchronized
    fun offer(key: ChunkKey, payload: ByteArray, hash: Long, observedGeneration: Long) {
        if (observedGeneration != generation) return
        if (sentHashes[key] == hash) {
            pending.remove(key)
            return
        }
        pending[key] = Pending(payload, hash)

        if (MAX_PENDING_CHUNKS < pending.size) {
            pending.entries.iterator().run {
                next()
                remove()
            }
        }
    }

    @Synchronized
    private fun hasPending() = pending.isNotEmpty()

    suspend fun tick() {
        while (hasPending() && flow.hasCredit()) {
            flow.limiter.waitForTokens(1.0)

            val (seq, key, payload) = takeNext() ?: break
            ctx.world.packetSender.send(
                C2SChunkUpload(dimension, layer, key, seq, payload)
            )
        }
    }
}
