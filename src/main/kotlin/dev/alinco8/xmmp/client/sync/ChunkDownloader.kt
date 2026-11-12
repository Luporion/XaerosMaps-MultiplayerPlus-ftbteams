package dev.alinco8.xmmp.client.sync

import dev.alinco8.xmmp.client.MapSyncContext
import dev.alinco8.xmmp.client.io.CursorStore
import dev.alinco8.xmmp.core.RegionKey
import dev.alinco8.xmmp.core.ResourceId
import dev.alinco8.xmmp.core.SyncLayer
import dev.alinco8.xmmp.core.network.TokenBucket
import dev.alinco8.xmmp.core.network.RegionRetryPolicy
import dev.alinco8.xmmp.core.network.packet.C2SRegionSync
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext

/**
 * Reliable (bounded) region requester. A rate-limited request may be dropped by
 * the server; only S2CRegionSyncDone after all tile writes acknowledges a region.
 * Requests are scoped, deduplicated and retried with exponential backoff.
 */
class ChunkDownloader(
    private val ctx: MapSyncContext,
    private val dimension: ResourceId,
    private val limiter: TokenBucket,
) {
    private data class Key(val scopeId: String, val layer: SyncLayer, val region: RegionKey)
    private class Request(val cursor: Long, val revision: Long, val onRequest: (RegionKey) -> Unit) {
        @Volatile var attempts: Int = 0
        @Volatile var nextAttemptMs: Long = 0L
    }

    private val pending = ConcurrentHashMap<Key, Request>()
    private val sent = AtomicLong()
    private val retried = AtomicLong()
    private val expired = AtomicLong()

    fun diagnostics(): String =
        "Region queue=${pending.size}, sent=${sent.get()}, retries=${retried.get()}, expired=${expired.get()}"

    fun reset() = pending.clear()

    suspend fun onRegionIndex(
        layer: SyncLayer,
        cursorStore: CursorStore,
        scopeId: String,
        regionRevisions: Map<RegionKey, Long>,
        isCurrentGeneration: () -> Boolean,
        onRequest: (RegionKey) -> Unit,
    ) {
        if (!isCurrentGeneration()) return
        var processed = 0
        for ((pos, remote) in regionRevisions) {
            if (processed++ % 128 == 0) {
                yield()
                if (!isCurrentGeneration()) return
            }
            var cursor = cursorStore.cursor(pos)
            if (remote < cursor) {
                cursorStore.setCursor(pos, 0)
                cursor = 0L
            }
            if (remote <= cursor) continue
            val key = Key(scopeId, layer, pos)
            pending.compute(key) { _, existing ->
                if (existing != null && existing.revision >= remote) existing
                else if (existing != null || pending.size < RegionRetryPolicy.MAX_PENDING) Request(cursor, remote, onRequest)
                else null // bounded queue: recover on reconnect rather than growing without limit
            }
        }
    }

    fun onRegionCompleted(layer: SyncLayer, scopeId: String, pos: RegionKey, revision: Long) {
        val key = Key(scopeId, layer, pos)
        pending.computeIfPresent(key) { _, value ->
            if (revision >= value.revision) null else value
        }
    }

    suspend fun runRetryLoop() {
        while (coroutineContext.isActive) {
            val now = System.currentTimeMillis()
            val due = pending.entries.asSequence()
                .filter { it.value.nextAttemptMs <= now }
                .take(16).toList()
            for ((key, value) in due) {
                if (value.attempts >= RegionRetryPolicy.MAX_ATTEMPTS) {
                    if (pending.remove(key, value)) expired.incrementAndGet()
                    continue
                }
                limiter.waitForTokens(1.0)
                if (pending[key] !== value) continue // renegotiated/finished while waiting
                value.attempts++
                value.nextAttemptMs = System.currentTimeMillis() + RegionRetryPolicy.delayMs(value.attempts)
                value.onRequest(key.region)
                if (value.attempts > 1) retried.incrementAndGet()
                sent.incrementAndGet()
                ctx.world.packetSender.send(
                    C2SRegionSync(dimension, key.layer, key.region, value.cursor, key.scopeId)
                )
            }
            delay(1000L)
        }
    }
}
