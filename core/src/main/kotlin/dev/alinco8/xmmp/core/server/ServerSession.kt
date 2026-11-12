package dev.alinco8.xmmp.core.server

import dev.alinco8.xmmp.core.ChunkKey
import dev.alinco8.xmmp.core.LOGGER
import dev.alinco8.xmmp.core.RegionKey
import dev.alinco8.xmmp.core.ResourceId
import dev.alinco8.xmmp.core.SyncLayer
import dev.alinco8.xmmp.core.TickDispatcher
import dev.alinco8.xmmp.core.common.ServerHost
import dev.alinco8.xmmp.core.config.ServerConfig
import dev.alinco8.xmmp.core.io.FileChannelPool
import dev.alinco8.xmmp.core.network.CreditWindow
import dev.alinco8.xmmp.core.network.packet.C2SChunkUpload
import dev.alinco8.xmmp.core.network.packet.C2SHandshake
import dev.alinco8.xmmp.core.network.packet.C2SAdminSettings
import dev.alinco8.xmmp.core.network.packet.S2CSharingStatus
import dev.alinco8.xmmp.core.network.packet.S2CChunkData
import dev.alinco8.xmmp.core.network.packet.S2CHandshake
import dev.alinco8.xmmp.core.network.packet.S2CRegionIndex
import dev.alinco8.xmmp.core.network.packet.S2CRegionSyncDone
import dev.alinco8.xmmp.core.network.packet.S2CUploadAck
import dev.alinco8.xmmp.core.server.io.ServerRegionStore
import dev.alinco8.xmmp.core.server.network.ServerPacketHandlers
import dev.alinco8.xmmp.core.sharing.SharingPolicy
import dev.alinco8.xmmp.core.sharing.SharingMode
import dev.alinco8.xmmp.core.sharing.SharingIdentity
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.LockSupport
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class ServerSession(
    private val serverConfig: ServerConfig,
    private val worldDataDir: Path,
    val host: ServerHost,
    val standalone: Boolean = false,
) : AutoCloseable {
    val handlers = ServerPacketHandlers(this)

    /** Scoped stores and protocol 7; authenticated Minecraft server only. */
    val terrainTransportEnabled: Boolean get() = SharingPolicy.TERRAIN_TRANSPORT_ENABLED &&
        !standalone && host.hasAuthenticatedPlayers

    private val pool = FileChannelPool(64)
    private val adminControls = SharingAdminControls(worldDataDir.resolve("admin-controls.properties"))

    private val dispatcher = TickDispatcher()
    private val rootJob = SupervisorJob()
    private val scope = CoroutineScope(dispatcher + rootJob)

    private val playerStates = ConcurrentHashMap<UUID, PlayerState>()
    private var lastScopeCheckNanos = System.nanoTime()
    private var lastDiagnosticNanos = lastScopeCheckNanos
    private val indexReaders = Semaphore(2)
    private data class StoreId(val scope: String, val dimension: ResourceId, val layer: SyncLayer)
    private val regionStores = ConcurrentHashMap<StoreId, ServerRegionStore>()

    init {
        Files.createDirectories(worldDataDir)

        scope.launch {
            while (isActive) {
                delay(serverConfig.regionFlushInterval.milliseconds)

                runCatching {
                    regionStores.values.forEach { it.flush() }
                }.onFailure {
                    LOGGER.error("Failed to flush region stores", it)
                }
            }
        }
    }

    private fun getPlayerState(playerId: UUID) =
        playerStates[playerId] ?: error("Player state not found for player ${playerId}")

    private fun getRegionStore(scopeId: String, dimension: ResourceId, layer: SyncLayer): ServerRegionStore {
        require(SharingPolicy.isValidScope(scopeId)) { "Invalid server scope" }
        // New storage root. Never open old unscoped 'world_map' with unattributed uploads.
        val dimensionDir = worldDataDir.resolve("scoped-v2").resolve(scopeId)
            .resolve("world_map").resolve(dimension.namespace).resolve(dimension.path)
        return regionStores.computeIfAbsent(StoreId(scopeId,dimension,layer)) {
            ServerRegionStore(pool, layer.dirName(dimensionDir))
        }
    }

    /** Server-owned effective scope, recomputed on EVERY sensitive async operation. */
    private fun effectiveScope(playerId: UUID, state: PlayerState): String? =
        SharingPolicy.scope(SharingIdentity(playerId,host.getPartyTeamId(playerId)),state.sharingMode)
            ?.takeIf(adminControls::allows) ?: "private-$playerId"

    private fun validPublication(playerId: UUID, state: PlayerState): Boolean =
        state.handshakeComplete && state.activeScope != null &&
            state.activeScope == effectiveScope(playerId, state) &&
            adminControls.allows(state.activeScope!!) &&
            state.syncedDimension != null && host.getDimension(playerId) == state.syncedDimension

    /** Read ACL, distinct from publication. PRIVATE publishers may read TEAM. */
    private fun validScope(playerId: UUID, state: PlayerState, claimed: String?): Boolean {
        if (!validPublication(playerId, state) || claimed == null || !SharingPolicy.isValidScope(claimed)) return false
        return adminControls.allows(claimed) && SharingPolicy.canReceive(
            SharingIdentity(playerId, host.getPartyTeamId(playerId)), state.sharingMode,
            claimed, state.receiveTeam, state.receiveGlobal
        )
    }

    private fun revokeStale(playerId: UUID, state: PlayerState): Boolean {
        if (validPublication(playerId,state)) return false
        state.activeScope=null
        invalidateDownload(state)
        return true
    }

    private fun invalidateDownload(state: PlayerState) {
        state.indexJob?.cancel()
        state.indexJob = null
        state.syncedDimension = null
        state.outbound.clear()
        state.downloadCredits.reset()
    }

    fun onTickPost() {
        dispatcher.pump(maxTasks = 128, maxNanos = 2_000_000L)
        // Delivery still rechecks current ACLs. This poll triggers a fresh
        // client index even when the player stays in the same dimension.
        val now = System.nanoTime()
        if (now - lastScopeCheckNanos >= 1_000_000_000L) {
            lastScopeCheckNanos = now
            val telemetry = now - lastDiagnosticNanos >= 20_000_000_000L
            if (telemetry) lastDiagnosticNanos = now
            playerStates.forEach { (id, state) ->
                sendSharingStatus(id, state, force = telemetry)
            }
        }
    }

    fun onPlayerJoin(playerId: UUID) {
        if (!terrainTransportEnabled) return
        val state = PlayerState(scope, serverConfig)
        playerStates[playerId] = state

        state.scope.launch { runUploadWorker(playerId, state) }
        state.scope.launch { runDownloadWorker(playerId, state) }

        state.scope.launch {
            val sent = host.sendToPlayer(
                playerId,
                S2CHandshake(serverConfig.shared)
            )

            if (sent) state.handshakeSent = true
        }
    }

    fun onPlayerChangedDimension(
        playerId: UUID,
    ) {
        val state = playerStates[playerId] ?: return

        invalidateDownload(state)
    }

    fun onPlayerLeave(playerId: UUID) {
        playerStates.remove(playerId)?.close()
    }

    fun onPlayerChannelsReady(playerId: UUID) {
        if (!terrainTransportEnabled) return
        val state = playerStates[playerId] ?: return
        if (state.handshakeSent) return

        val sent = host.sendToPlayer(
            playerId,
            S2CHandshake(serverConfig.shared)
        )
        if (sent) state.handshakeSent = true
    }

    fun onHandshake(playerId: UUID, packet: C2SHandshake) {
        if (!terrainTransportEnabled) return
        val state = playerStates[playerId] ?: return

        val requestedMode=SharingPolicy.resolveMode(packet.sharingMode)
            ?: run { host.disconnectPlayer(playerId,"Invalid map sharing mode"); return }
        // Handshake renegotiation must never leave queued content from an old scope.
        invalidateDownload(state)
        state.syncId=-1
        state.sharingMode=requestedMode
        state.receiveTeam=packet.receiveTeam
        state.receiveGlobal=packet.receiveGlobal
        state.handshakeComplete=true
        state.enableWorldMapSync=packet.enableWorldMapSync
        state.activeScope=effectiveScope(playerId,state)
        // Missing/disallowed FTB party privileges fall back to the owner's private scope.
        // No unverified team UUID is ever used for publication or download.
        state.downloadCredits = CreditWindow(
            packet.downloadWindow.coerceIn(1, serverConfig.maxDownloadWindow.coerceAtLeast(1))
        )
        state.outbound.signal()
        sendSharingStatus(playerId, state)
    }

    /** All controls are verified server-side; changing them cannot authorize queued data. */
    fun onAdminSettings(playerId: UUID, packet: C2SAdminSettings) {
        if (!terrainTransportEnabled || !host.isMapAdministrator(playerId)) return
        try {
            adminControls.change(SharingAdminSettings(packet.allowTeam, packet.allowGlobal, packet.allowUploads))
        } catch (e: Exception) {
            LOGGER.error("Failed to save server sharing controls", e)
            return
        }
        for ((id, state) in playerStates) sendSharingStatus(id, state)
    }

    private fun sendSharingStatus(playerId: UUID, state: PlayerState, force: Boolean = true) {
        if (!state.handshakeComplete) return
        val partyId = host.getPartyTeamId(playerId)
        val identity = SharingIdentity(playerId, partyId)
        val current = adminControls.settings
        val publish = SharingPolicy.scope(identity, state.sharingMode)
            ?.takeIf(adminControls::allows) ?: "private-$playerId"
        val status = S2CSharingStatus(
            publish, partyId?.let { "team-$it" } ?: "",
            partyId != null && state.receiveTeam && current.allowTeam,
            state.receiveGlobal && current.allowGlobal,
            host.isMapAdministrator(playerId), current.allowTeam,
            current.allowGlobal, current.allowUploads,
            state.uploadsStored.get(), state.uploadsRejected.get(),
            state.downloadPackets.get(), state.downloadedBytes.get(),
            state.regionRequests.get(), state.regionThrottled.get()
        )
        val previous = state.lastSharingStatus
        val scopeChanged = previous != null && !status.sameScope(previous)
        if (scopeChanged) invalidateDownload(state)
        state.activeScope = publish
        if (force || scopeChanged || previous == null) {
            if (host.sendToPlayer(playerId, status)) state.lastSharingStatus = status
        }
    }

    fun onDimensionSync(playerId: UUID, syncId: Int) {
        if (!terrainTransportEnabled) return
        val playerState = getPlayerState(playerId)
        if (!playerState.handshakeComplete) return
        val dimension = host.getDimension(playerId) ?: return
        val scopeId=effectiveScope(playerId,playerState) ?: return
        playerState.activeScope=scopeId

        invalidateDownload(playerState)
        playerState.syncedDimension = dimension
        playerState.syncId = syncId
        playerState.outbound.signal()

        if (!playerState.enableWorldMapSync) return
        playerState.indexJob = playerState.scope.launch {
            val syncCaves = serverConfig.shared.syncCaves
            // PRIVATE first, then TEAM, then GLOBAL. Each source has its own
            // revision cursor, and every outgoing chunk is scope-tagged.
            val identity=SharingIdentity(playerId,host.getPartyTeamId(playerId))
            for (readScope in SharingPolicy.readableScopes(identity,playerState.sharingMode,
                playerState.receiveTeam,playerState.receiveGlobal).filter(adminControls::allows)) {
                for (layer in SyncLayer.entries) {
                    if (!syncCaves && layer == SyncLayer.FULL_CAVE) continue
                    if (!validScope(playerId,playerState,readScope) || playerState.syncId!=syncId) return@launch
                    val revisions = indexReaders.withPermit {
                        getRegionStore(readScope, dimension, layer).listRegionRevisions()
                    }
                    // The disk scan suspends. Validate again after it finishes.
                    if (!validScope(playerId, playerState, readScope) || playerState.syncId != syncId) return@launch
                    host.sendToPlayer(playerId, S2CRegionIndex(dimension, layer, revisions, readScope))
                    yield()
                }
            }
        }
    }

    fun onChunkUpload(playerId: UUID, packet: C2SChunkUpload) {
        if (!terrainTransportEnabled) return
        if (!adminControls.settings.allowUploads) {
            playerStates[playerId]?.uploadsRejected?.incrementAndGet()
            host.sendToPlayer(playerId, S2CUploadAck(packet.seq))
            return
        }
        val dimension = host.getDimension(playerId)
        if (dimension != packet.dimension) {
            playerStates[playerId]?.uploadsRejected?.incrementAndGet()
            host.sendToPlayer(playerId,S2CUploadAck(packet.seq)) // release client credits on rejected data
            LOGGER.warn(
                "Player {} sent chunk upload for dimension {} but is in {}, ignoring",
                playerId,
                packet.dimension,
                dimension
            )
            return
        }

        val state = playerStates[playerId] ?: return
        val publishScope = state.activeScope
        if (publishScope == null || !validPublication(playerId,state)) {
            state.uploadsRejected.incrementAndGet()
            host.sendToPlayer(playerId, S2CUploadAck(packet.seq))
            revokeStale(playerId,state)
            return
        }
        // A payload is untrusted even when its codec parses. Bound bytes and observed position.
        if (packet.payload.size !in 1..262_144 || !host.canObserveChunk(playerId,packet.chunkPos)) {
            state.uploadsRejected.incrementAndGet()
            host.sendToPlayer(playerId,S2CUploadAck(packet.seq))
            return
        }
        if (!serverConfig.shared.syncCaves && packet.layer==SyncLayer.FULL_CAVE) {
            state.uploadsRejected.incrementAndGet()
            host.sendToPlayer(playerId,S2CUploadAck(packet.seq))
            return
        }
        val upload = PlayerState.PendingUpload(
            packet.dimension,
            packet.layer,
            packet.chunkPos,
            packet.seq,
            packet.payload,
            publishScope,
            state.syncId
        )
        if (state.uploads.trySend(upload).isFailure) {
            LOGGER.warn(
                "Player {} exceeded the upload window, disconnecting",
                playerId
            )
            host.disconnectPlayer(playerId, "[XMMP] Upload window exceeded")
        }
    }

    private suspend fun runUploadWorker(uploaderId: UUID, state: PlayerState) {
        if (!terrainTransportEnabled) return
        val batch = ArrayList<PlayerState.PendingUpload>()
        val maxBatch = serverConfig.shared.uploadBurst.toInt().coerceIn(1, 32)

        for (first in state.uploads) {
            batch.add(first)
            while (batch.size < maxBatch) batch += state.uploads.tryReceive().getOrNull() ?: break

            state.uploadLimiter.waitForTokens(batch.size.toDouble())

            val syncCaves = serverConfig.shared.syncCaves
            val targets = batch.filter {
                (syncCaves || it.layer != SyncLayer.FULL_CAVE)
            }
            // Never allow config to disable payload validation for shared terrain.
            val validated = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                targets.filter { host.validateChunkBytes(it.payload) }
            }
            state.uploadsRejected.addAndGet((batch.size - validated.size).toLong())

            for (upload in validated) {
                try {
                    if (!adminControls.settings.allowUploads) {
                        state.uploadsRejected.incrementAndGet(); continue
                    }
                    val dimension = upload.dimension
                    val scopeId=upload.publishScope
                    if (upload.syncId != state.syncId || scopeId != state.activeScope ||
                        !validPublication(uploaderId,state) ||
                        host.getDimension(uploaderId)!=upload.dimension ||
                        !host.canObserveChunk(uploaderId,upload.chunkPos)) {
                        state.uploadsRejected.incrementAndGet()
                        revokeStale(uploaderId,state); continue
                    }
                    // Each explorer permanently keeps a private copy in addition to any share.
                    val privateScope="private-$uploaderId"
                    getRegionStore(privateScope,dimension,upload.layer).writeChunk(upload.chunkPos,upload.payload)
                    state.uploadsStored.incrementAndGet()
                    if (scopeId != privateScope) {
                        // Private I/O may suspend. Never route queued pixels under a new team's scope.
                        if (upload.syncId != state.syncId || scopeId != state.activeScope ||
                            !adminControls.settings.allowUploads || !validPublication(uploaderId,state)) continue
                        getRegionStore(scopeId,dimension,upload.layer).writeChunk(upload.chunkPos,upload.payload)
                    }
                    for (playerId in host.listPlayers()) {
                        if (playerId==uploaderId) continue
                        val receiver=playerStates[playerId] ?: continue
                        if (!receiver.enableWorldMapSync || receiver.syncedDimension!=dimension) continue
                        if (!validScope(playerId,receiver,scopeId)) {
                            if (receiver.activeScope!=effectiveScope(playerId,receiver)) revokeStale(playerId,receiver)
                            continue
                        }
                        receiver.outbound.enqueueChunk(upload.layer,upload.chunkPos,scopeId,receiver.syncId)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LOGGER.error(
                        "Failed to process upload from player {} for chunk at ({}, {})",
                        uploaderId,
                        upload.chunkPos.globalX,
                        upload.chunkPos.globalZ,
                        e
                    )
                }
            }

            host.sendToPlayer(uploaderId, S2CUploadAck(batch.last().seq))
            batch.clear()
        }
    }

    private suspend fun runDownloadWorker(playerId: UUID, state: PlayerState) {
        if (!terrainTransportEnabled) return
        while (true) {
            state.outbound.awaitSignal()

            while (true) {
                val dimension = state.syncedDimension ?: break
                val scopeId=state.activeScope ?: break
                if (!validPublication(playerId,state)) { revokeStale(playerId,state); break }
                val credits = state.downloadCredits
                if (!credits.tryAcquire()) break

                val item = state.outbound.poll() ?: run {
                    credits.release(1)
                    break
                }

                when (item) {
                    is Outbound.Chunk -> {
                        if (item.syncId != state.syncId || !validScope(playerId,state,item.scopeId)) {
                            credits.release(1); continue
                        }
                        val data =
                            getRegionStore(item.scopeId,dimension,item.layer).readChunkWithRevision(item.pos)
                                ?: run {
                                    credits.release(1)
                                    continue
                                }

                        state.downloadLimiter.waitForTokens(1.0)
                        // Revalidate after async rate limiting, right before enqueueing the packet.
                        if (item.syncId!=state.syncId || !validScope(playerId,state,item.scopeId)) {
                            credits.release(1); continue
                        }
                        val sent = host.sendToPlayer(
                            playerId,
                            S2CChunkData(
                                dimension,
                                item.layer,
                                item.pos,
                                data.second,
                                data.first,
                                item.scopeId,
                                item.syncId
                            )
                        )
                        if (sent) {
                            state.downloadPackets.incrementAndGet()
                            state.downloadedBytes.addAndGet(data.first.size.toLong())
                        } else credits.release(1)
                    }

                    is Outbound.Done -> {
                        credits.release(1)
                        if (item.syncId != state.syncId || !validScope(playerId,state,item.scopeId)) continue
                        host.sendToPlayer(
                            playerId,
                            S2CRegionSyncDone(
                                dimension,
                                item.layer,
                                item.region,
                                item.revision,
                                item.syncId,
                                item.scopeId
                            )
                        )
                    }
                }
            }
        }
    }

    fun onRegionSync(playerId: UUID, layer: SyncLayer, regionPos: RegionKey, cursor: Long, scopeId: String) {
        if (!terrainTransportEnabled) return
        if (!serverConfig.shared.syncCaves && layer == SyncLayer.FULL_CAVE) return

        val playerState = playerStates[playerId] ?: return
        if (!validScope(playerId,playerState,scopeId)) {
            if (!validScope(playerId,playerState,playerState.activeScope)) revokeStale(playerId,playerState)
            return
        }
        playerState.regionRequests.incrementAndGet()
        if (!playerState.regionRequestLimiter.tryConsume(1.0)) {
            playerState.regionThrottled.incrementAndGet()
            LOGGER.warn(
                "Player {} sent region sync for ({}, {}) but is rate limited, ignoring",
                playerId,
                regionPos.x,
                regionPos.z
            )
            return
        }

        val dimension = host.getDimension(playerId) ?: return
        val syncId = playerState.syncId
        val store = getRegionStore(scopeId,dimension,layer)
        playerState.scope.launch {
            if (!validScope(playerId,playerState,scopeId)) return@launch
            val file = store.regionReadonly(regionPos) ?: return@launch
            val snapshot = file.regionRevision() ?: return@launch

            for (chunkX in 0 until 32) {
                if (chunkX > 0 && chunkX % 2 == 0) {
                    yield()
                    if (playerState.syncId != syncId || !validScope(playerId, playerState, scopeId)) return@launch
                }
                for (chunkZ in 0 until 32) {
                    val revision = file.chunkRevision(chunkX, chunkZ) ?: continue
                    if (revision <= cursor) continue
                    if (playerState.syncId != syncId || !validScope(playerId,playerState,scopeId)) return@launch

                    if (!playerState.outbound.enqueueChunk(
                        layer,
                        ChunkKey.fromLocal(regionPos, chunkX, chunkZ),scopeId,syncId
                    )) return@launch // no Done: cursor must not advance on dropped data
                }
            }

            if (playerState.syncId == syncId && validScope(playerId,playerState,scopeId)) playerState.outbound.enqueueDone(
                layer,
                regionPos,
                snapshot,
                syncId,scopeId
            )
        }
    }

    fun onDownloadAck(playerId: UUID, completed: Int) {
        if (!terrainTransportEnabled) return
        if (completed <= 0) return

        val state = playerStates[playerId] ?: return
        if (!validPublication(playerId,state)) { revokeStale(playerId,state); return }
        state.downloadCredits.release(completed)
        state.outbound.signal()
    }

    override fun close() {
        playerStates.values.forEach { it.close() }
        rootJob.cancel()

        val deadline = System.nanoTime() + 5.seconds.inWholeNanoseconds
        while (!rootJob.isCompleted && System.nanoTime() < deadline) {
            dispatcher.pump()
            LockSupport.parkNanos(1_000_000)
        }
        if (!rootJob.isCompleted)
            LOGGER.warn("Server session did not shut down cleanly, some tasks may still be running")
        dispatcher.close()

        runBlocking {
            withTimeoutOrNull(30.seconds) {
                runCatching {
                    regionStores.values.forEach { it.flush() }
                }.onFailure {
                    LOGGER.error("Failed to flush region stores", it)
                }

                pool.close()
            }
        }
    }
}
