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

    private fun validScope(playerId: UUID, state: PlayerState, claimed: String?): Boolean {
        if (!state.handshakeComplete || claimed == null || !SharingPolicy.isValidScope(claimed)) return false
        val identity = SharingIdentity(playerId, host.getPartyTeamId(playerId))
        // A team change invalidates EVERY previously queued index and chunk.
        if (state.activeScope != effectiveScope(playerId, state)) return false
        return adminControls.allows(claimed) &&
            SharingPolicy.canReceive(identity, state.sharingMode, claimed,
                state.receiveTeam, state.receiveGlobal) &&
            host.getDimension(playerId) == state.syncedDimension
    }

    private fun revokeStale(playerId: UUID, state: PlayerState): Boolean {
        if (validScope(playerId,state,state.activeScope)) return false
        state.activeScope=null
        state.syncedDimension=null
        state.outbound.clear()
        return true
    }

    fun onTickPost() {
        dispatcher.pump()
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

        state.syncedDimension = null
        state.outbound.clear()
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
        state.outbound.clear()
        state.downloadCredits.reset()
        state.syncedDimension=null
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
        for ((id, state) in playerStates) {
            state.outbound.clear()
            state.downloadCredits.reset()
            state.syncedDimension=null
            state.activeScope=effectiveScope(id,state)
            sendSharingStatus(id,state)
        }
    }

    private fun sendSharingStatus(playerId: UUID, state: PlayerState) {
        if (!state.handshakeComplete) return
        val team=host.getPartyTeamId(playerId)?.let { "team-$it" } ?: ""
        val current=adminControls.settings
        host.sendToPlayer(playerId, S2CSharingStatus(
            effectiveScope(playerId,state) ?: "",team,
            state.receiveTeam && current.allowTeam && state.sharingMode != SharingMode.PRIVATE,
            state.receiveGlobal && current.allowGlobal && state.sharingMode != SharingMode.PRIVATE,
            host.isMapAdministrator(playerId), current.allowTeam,
            current.allowGlobal, current.allowUploads
        ))
    }

    fun onDimensionSync(playerId: UUID, syncId: Int) {
        if (!terrainTransportEnabled) return
        val playerState = getPlayerState(playerId)
        if (!playerState.handshakeComplete) return
        val dimension = host.getDimension(playerId) ?: return
        val scopeId=effectiveScope(playerId,playerState) ?: return
        playerState.activeScope=scopeId

        playerState.outbound.clear()
        playerState.downloadCredits.reset()
        playerState.syncedDimension = dimension
        playerState.syncId = syncId
        playerState.outbound.signal()

        playerState.scope.launch {
            val syncCaves = serverConfig.shared.syncCaves
            // PRIVATE first, then TEAM, then GLOBAL. Each source has its own
            // revision cursor, and every outgoing chunk is scope-tagged.
            val identity=SharingIdentity(playerId,host.getPartyTeamId(playerId))
            for (readScope in SharingPolicy.readableScopes(identity,playerState.sharingMode,
                playerState.receiveTeam,playerState.receiveGlobal).filter(adminControls::allows)) {
                for (layer in SyncLayer.entries) {
                    if (!syncCaves && layer == SyncLayer.FULL_CAVE) continue
                    if (!validScope(playerId,playerState,readScope) || playerState.syncId!=syncId) return@launch
                    host.sendToPlayer(playerId,S2CRegionIndex(
                        dimension,layer,
                        getRegionStore(readScope,dimension,layer).listRegionRevisions()
                            .entries.sortedWith(compareBy<Map.Entry<RegionKey, Long>>({ it.key.x }, { it.key.z }))
                            .take(16_384).associate { it.toPair() },
                        readScope
                    ))
                }
            }
        }
    }

    fun onChunkUpload(playerId: UUID, packet: C2SChunkUpload) {
        if (!terrainTransportEnabled) return
        if (!adminControls.settings.allowUploads) {
            host.sendToPlayer(playerId, S2CUploadAck(packet.seq))
            return
        }
        val dimension = host.getDimension(playerId)
        if (dimension != packet.dimension) {
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
        if (!validScope(playerId,state,state.activeScope)) { revokeStale(playerId,state); return }
        // A payload is untrusted even when its codec parses. Bound bytes and observed position.
        if (packet.payload.size !in 1..262_144 || !host.canObserveChunk(playerId,packet.chunkPos)) {
            host.sendToPlayer(playerId,S2CUploadAck(packet.seq))
            return
        }
        if (!serverConfig.shared.syncCaves && packet.layer==SyncLayer.FULL_CAVE) {
            host.sendToPlayer(playerId,S2CUploadAck(packet.seq))
            return
        }
        val upload = PlayerState.PendingUpload(
            packet.dimension,
            packet.layer,
            packet.chunkPos,
            packet.seq,
            packet.payload
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
        val maxBatch = serverConfig.shared.uploadBurst.toInt().coerceAtLeast(1)

        for (first in state.uploads) {
            batch.add(first)
            while (batch.size < maxBatch) batch += state.uploads.tryReceive().getOrNull() ?: break

            state.uploadLimiter.waitForTokens(batch.size.toDouble())

            val syncCaves = serverConfig.shared.syncCaves
            val targets = batch.filter {
                (syncCaves || it.layer != SyncLayer.FULL_CAVE)
            }
            // Never allow config to disable payload validation for shared terrain.
            val validated = targets.filter { host.validateChunkBytes(it.payload) }

            for (upload in validated) {
                try {
                    if (!adminControls.settings.allowUploads) continue
                    val dimension = upload.dimension
                    val scopeId=state.activeScope ?: continue
                    if (!validScope(uploaderId,state,scopeId) ||
                        host.getDimension(uploaderId)!=upload.dimension ||
                        !host.canObserveChunk(uploaderId,upload.chunkPos)) {
                        revokeStale(uploaderId,state); continue
                    }
                    // Each explorer permanently keeps a private copy in addition to any share.
                    val privateScope="private-$uploaderId"
                    getRegionStore(privateScope,dimension,upload.layer).writeChunk(upload.chunkPos,upload.payload)
                    if (scopeId!=privateScope)
                        getRegionStore(scopeId,dimension,upload.layer).writeChunk(upload.chunkPos,upload.payload)
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
                if (!validScope(playerId,state,scopeId)) { revokeStale(playerId,state); break }
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
                        host.sendToPlayer(
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
        if (!playerState.regionRequestLimiter.tryConsume(1.0)) {
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
        if (!validScope(playerId,state,state.activeScope)) { revokeStale(playerId,state); return }
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
