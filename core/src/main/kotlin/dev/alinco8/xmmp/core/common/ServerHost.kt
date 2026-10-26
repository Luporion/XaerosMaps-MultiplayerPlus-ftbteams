package dev.alinco8.xmmp.core.common

import dev.alinco8.xmmp.core.ResourceId
import dev.alinco8.xmmp.core.network.XMMPPacket
import java.util.UUID

interface ServerHost {
    /** Only adapters deriving player UUID and dimension from a real authenticated server may enable v5 terrain. */
    val hasAuthenticatedPlayers: Boolean get() = false
    fun <T : XMMPPacket<T>> sendToPlayer(playerId: UUID, packet: T): Boolean
    suspend fun validateChunkBytes(chunkBytes: ByteArray): Boolean

    fun listPlayers(): List<UUID>

    fun getDimension(playerId: UUID): ResourceId?
    /** The server, never the client, supplies FTB party membership. Nullable = no permission. */
    fun getPartyTeamId(playerId: UUID): UUID? = null
    /** Conservative upload bound: only recently visible/near player chunks. */
    fun canObserveChunk(playerId: UUID, chunk: dev.alinco8.xmmp.core.ChunkKey): Boolean = false
    fun disconnectPlayer(playerId: UUID, reason: String)

    fun onDimensionChanged(playerId: UUID, dimension: ResourceId) {}
}
