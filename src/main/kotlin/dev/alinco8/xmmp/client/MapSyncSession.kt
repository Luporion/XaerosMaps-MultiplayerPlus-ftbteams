package dev.alinco8.xmmp.client

import dev.alinco8.xmmp.core.ChunkKey
import dev.alinco8.xmmp.core.RegionKey
import dev.alinco8.xmmp.XMMP.LOGGER
import dev.alinco8.xmmp.client.io.CursorStore
import dev.alinco8.xmmp.client.io.PersonalExplorationJournal
import dev.alinco8.xmmp.client.sync.ChunkDownloader
import dev.alinco8.xmmp.client.sync.ChunkUploader
import dev.alinco8.xmmp.client.sync.ExclusiveJob
import dev.alinco8.xmmp.client.sync.UploadFlow
import dev.alinco8.xmmp.core.SyncLayer
import dev.alinco8.xmmp.client.xaero.TileSnapshot
import dev.alinco8.xmmp.client.xaero.XaeroController
import dev.alinco8.xmmp.client.xaero.XaeroController.writeTile
import dev.alinco8.xmmp.client.xaero.awaitResult
import dev.alinco8.xmmp.config.XMMPConfig
import dev.alinco8.xmmp.core.config.ServerConfig
import dev.alinco8.xmmp.utils.ModPaths
import dev.alinco8.xmmp.io.TilePayloadCodec
import dev.alinco8.xmmp.core.io.PayloadHash
import dev.alinco8.xmmp.core.network.TokenBucket
import dev.alinco8.xmmp.id
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.minecraft.core.registries.Registries
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceKey
import xaero.map.core.XaeroWorldMapCore

class MapSyncContext(
    val key: MapKey,
    val world: WorldContext,
)

class MapSyncSession(
    private val ctx: MapSyncContext,
    regionRequestLimiter: TokenBucket,
    uploadFlow: UploadFlow,
    scope: CoroutineScope,
) {
    private class LayerData(
        val uploader: ChunkUploader,
        val cursorStores: ConcurrentHashMap<String, CursorStore> = ConcurrentHashMap(),
    ) {
        val writingJobs = ConcurrentHashMap<Pair<String,RegionKey>, MutableSet<Job>>()
        val failedRegions = ConcurrentHashMap.newKeySet<Pair<String,RegionKey>>()
        val appliedPriority = ConcurrentHashMap<ChunkKey,Int>()
        val appliedRevisions = ConcurrentHashMap<Pair<String,ChunkKey>, Long>()
        val latestRevisions = ConcurrentHashMap<Pair<String,ChunkKey>, Long>()
    }

    val dimension get() = ctx.key.dimension

    private val job = SupervisorJob(scope.coroutineContext.job)
    private val syncScope = scope + job

    private val layerData: Map<SyncLayer, LayerData> = SyncLayer.entries.associateWith { layer ->
        LayerData(
            ChunkUploader(ctx, dimension.id(), layer, uploadFlow),
        )
    }.toMap()
    private val downloader = ChunkDownloader(ctx, dimension.id(), regionRequestLimiter)

    private val uploaderTickJob = ExclusiveJob {
        syncScope.launch {
            layerData.values.forEach {
                it.uploader.tick()
            }
        }
    }

    private lateinit var cursorBase: java.nio.file.Path
    private val activeScopes = ConcurrentHashMap.newKeySet<String>()
    private val privateScopeId: String by lazy {
        // Not a client-provided authorization token: only a comparison against
        // the authenticated server scope for own pixels.
        "private-${net.minecraft.client.Minecraft.getInstance().player?.uuid ?: "none"}"
    }
    fun resetGeneration() {
        activeScopes.clear()
        layerData.values.forEach { it.appliedRevisions.clear();it.latestRevisions.clear() }
    }
    private lateinit var personalJournal: PersonalExplorationJournal
    private var lastJournalFlushNanos = System.nanoTime()
    var syncId = 0

    init {

        val processor = XaeroWorldMapCore.currentSession?.mapProcessor
            ?: error("Map processor not found, is Xaero's World Map mod loaded?")

        var base = ModPaths.gameDir()
            .resolve("xmmp")
            .resolve(ctx.key.mainId)
            .resolve(
                processor.getDimensionName(ctx.key.dimension)
            )
        if (ctx.key.multiworldId != null) {
            base = base.resolve(ctx.key.multiworldId)
        }

        cursorBase = base.resolve("scoped-v3")
        // Pre-v0.3 mixed Xaero tiles are NOT retrospectively marked personal.
        // Fail closed on corruption rather than silently losing personal provenance.
        personalJournal=PersonalExplorationJournal.open(base.resolve("personal-provenance-v3.bin"))

        syncScope.launch {
            while (isActive) {
                layerData.values.forEach { it.cursorStores.values.forEach { store -> store.flush() } }
                if (System.nanoTime()-lastJournalFlushNanos >= 30_000_000_000L) {
                    personalJournal.flush()
                    lastJournalFlushNanos=System.nanoTime()
                }

                delay(XMMPConfig.HANDLER.instance().flushInterval.milliseconds)
            }
        }
    }

    fun onTickPost() {
        uploaderTickJob.launch()
    }

    /** Physical visit recorded even when the tile was already shown through team sharing.
     *  Only the player's actual current chunk is marked; this does not fabricate
     *  an explored area from view distance or from client-received map tiles.
     */
    fun onPersonalVisit(x:Int,z:Int) {
        personalJournal.mark(0,x,z,PersonalExplorationJournal.VISITED)
    }

    fun onTileWritten(layer: Int, x: Int, z: Int, snap: TileSnapshot) {
        val layer = SyncLayer.of(layer) ?: return
        if (!ctx.world.sharedConfig.syncCaves && layer == SyncLayer.FULL_CAVE) return
        // Called only from native Xaero MapWriter; NOT our network writeTile().
        personalJournal.mark(layer.ordinal,x,z,PersonalExplorationJournal.NATIVE)

        syncScope.launch(Dispatchers.Default) {
            val buf = FriendlyByteBuf(Unpooled.buffer())
            val payload = try {
                TilePayloadCodec.encode(buf, snap.tileData, snap.blocks)
                ByteBufUtil.getBytes(buf)
            } finally {
                buf.release()
            }

            layerData[layer]!!.uploader.offer(
                ChunkKey(x, z),
                payload,
                PayloadHash.of(payload)
            )
        }
    }

    fun onRegionIndex(layer: SyncLayer, regionRevisions: Map<RegionKey, Long>, scopeId: String) {
        if (!dev.alinco8.xmmp.core.sharing.SharingPolicy.isValidScope(scopeId)) return
        // Multiple authorized scopes may be received together; revisions never cross scopes.
        activeScopes.add(scopeId)
        if (ctx.key.usingWorldSave) return

        syncScope.launch {
            val layerData = layerData[layer]!!

            val store = layerData.cursorStores[scopeId] ?: CursorStore.open(layer.dirName(cursorBase.resolve(scopeId)))
                .also { layerData.cursorStores[scopeId] = it }
            downloader.onRegionIndex(
                layer,
                store,
                scopeId,
                regionRevisions
            ) { region ->
                layerData.failedRegions.remove(scopeId to region)
            }
        }
    }

    fun onChunkData(
        chunkPos: ChunkKey,
        layer: SyncLayer,
        revision: Long,
        payload: ByteArray,
        scopeId: String,
        packetSyncId: Int,
    ): Job? {
        if (packetSyncId != syncId || scopeId !in activeScopes ||
            !dev.alinco8.xmmp.core.sharing.SharingPolicy.isValidScope(scopeId)) return null
        val layerData = layerData[layer]!!

        val cursorKey=scopeId to chunkPos
        val applied = layerData.appliedRevisions[cursorKey]
        if (applied != null && applied >= revision) return null

        var accepted = false
        layerData.latestRevisions.compute(cursorKey) { _, cur ->
            if (cur == null || revision > cur) {
                accepted = true
                revision
            } else cur
        }
        if (!accepted) return null

        val regionPos = chunkPos.toRegion()
        val scopedRegion = scopeId to regionPos
        val priority = when {
            scopeId == privateScopeId -> 3
            scopeId.startsWith("team-") -> 2
            else -> 1
        }

        val job = syncScope.launch {
            try {
                val (tileData, blocks) = withContext(Dispatchers.Default) {
                    TilePayloadCodec.decode(
                        FriendlyByteBuf(Unpooled.wrappedBuffer(payload))
                    )
                } ?: run {
                    LOGGER.warn(
                        "Failed to decode tile for chunk {}:{}",
                        chunkPos.globalX,
                        chunkPos.globalZ
                    )
                    layerData.failedRegions.add(scopedRegion)

                    return@launch
                }

                val written = awaitResult(maxAttempts = 20 * 60) {
                    if (packetSyncId!=syncId || scopeId !in activeScopes ||
                        layerData.latestRevisions[cursorKey] != revision) return@awaitResult XaeroController.Result.Success(
                        false
                    )

                    val processor = XaeroWorldMapCore.currentSession?.mapProcessor
                        ?: return@awaitResult XaeroController.Result.NotFound

                    // Preserve personally native terrain from being overwritten by a shared map.
                    // A merely VISITED marker proves presence, not native pixels; keep
                    // marking provenance even when previously shown foreign terrain.
                    if (scopeId != privateScopeId && personalJournal.isNative(layer.ordinal,chunkPos.globalX,chunkPos.globalZ))
                        return@awaitResult XaeroController.Result.Success(false)
                    // Serialize the check+write per layer to prevent a late GLOBAL
                    // packet from overriding previously applied TEAM/PRIVATE data.
                    synchronized(layerData.appliedPriority) {
                        if ((layerData.appliedPriority[chunkPos] ?: 0) > priority)
                            XaeroController.Result.Success(false)
                        else processor.writeTile(dimension,chunkPos,layer,tileData,blocks).map {
                            layerData.appliedPriority[chunkPos] = priority
                            true
                        }
                    }
                } ?: run {
                    LOGGER.warn(
                        "Failed to write tile for chunk {}:{}",
                        chunkPos.globalX,
                        chunkPos.globalZ
                    )
                    layerData.failedRegions.add(scopedRegion)

                    return@launch
                }

                when (written) {
                    true -> {
                        layerData.appliedRevisions.merge(cursorKey, revision, ::maxOf)
                        if(scopeId!=privateScopeId)
                            personalJournal.mark(layer.ordinal,chunkPos.globalX,chunkPos.globalZ,PersonalExplorationJournal.RECEIVED)
                    }
                    false -> layerData.appliedRevisions.merge(cursorKey,revision,::maxOf)
                }
            } finally {
                layerData.latestRevisions.remove(cursorKey, revision)
            }
        }

        layerData.writingJobs.computeIfAbsent(scopedRegion) { ConcurrentHashMap.newKeySet() }.add(job)
        job.invokeOnCompletion { layerData.writingJobs[scopedRegion]?.remove(job) }

        return job
    }

    fun onRegionSyncDone(layer: SyncLayer, regionPos: RegionKey, revision: Long, syncId: Int, scopeId: String) {
        if (syncId != this.syncId || scopeId !in activeScopes) return

        val layerData = layerData[layer]!!
        val jobs = layerData.writingJobs[scopeId to regionPos]?.toList().orEmpty()

        syncScope.launch {
            jobs.joinAll()
            if (layerData.failedRegions.remove(scopeId to regionPos)) return@launch

            layerData.cursorStores[scopeId]?.setCursor(regionPos, revision)
        }
    }

    fun close() {
        job.cancel()

        runBlocking {
            withTimeoutOrNull(1.seconds) {
                layerData.values.forEach { data -> data.cursorStores.values.forEach { it.flush() } }
                personalJournal.flush()
            }
        }
    }
}
