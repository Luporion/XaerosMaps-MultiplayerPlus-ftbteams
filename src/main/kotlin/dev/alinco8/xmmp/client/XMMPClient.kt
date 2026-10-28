package dev.alinco8.xmmp.client

import dev.alinco8.xmmp.XMMP.LOGGER
import dev.alinco8.xmmp.core.sharing.SharingPolicy
import dev.alinco8.xmmp.core.network.packet.S2CSharingStatus
import dev.alinco8.xmmp.core.network.packet.C2SAdminSettings
import dev.alinco8.xmmp.client.network.ClientPacketSender
import dev.alinco8.xmmp.client.network.standalone.StandalonePacketSender
import dev.alinco8.xmmp.client.xaero.XaeroController.snapshot
import dev.alinco8.xmmp.common.ClientEvents
import dev.alinco8.xmmp.id
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.LevelResource
import xaero.map.region.MapTile

object XMMPClient {
    @Volatile
    var session: ClientSession? = null

    @Volatile
    var packetSender: ClientPacketSender? = null

    @Volatile var sharingStatus: S2CSharingStatus? = null

    fun sendAdminSettings(allowTeam: Boolean, allowGlobal: Boolean, allowUploads: Boolean) {
        if (sharingStatus?.admin != true) return
        packetSender?.send(C2SAdminSettings(allowTeam, allowGlobal, allowUploads))
    }

    fun onInitializeClient(events: ClientEvents) {
        LOGGER.debug("Initializing XMMP client...")

        with(events) {
            registerWorldJoin(::onJoinWorld)
            registerTickPost(::onTickPost)
            registerWorldLeave(::onLeaveWorld)
        }
    }

    fun onJoinWorld() {
        sharingStatus = null
        // v0.2.0 requires authenticated in-game server UUIDs for scoped sharing.
        // Never send personal terrain to an unauthenticated standalone endpoint.
        if (!SharingPolicy.TERRAIN_TRANSPORT_ENABLED) return
        check(session == null) { "Session should be null when joining a world" }

        val sender = ClientPacketSender.create()
        packetSender = sender

        if (sender is StandalonePacketSender) {
            sender.close()
            packetSender = null
            showMessage("Standalone sharing disabled: authenticated NeoForge server required")
        }
    }

    fun onLeaveWorld() {
        sharingStatus = null
        val s = session
        session = null
        s?.close()

        val ps = packetSender
        packetSender = null
        ps?.close()
    }

    fun onTickPost() = session?.onTickPost()

    @JvmStatic
    fun onTileWritten(dimension: ResourceKey<Level>, layer: Int, x: Int, z: Int, mapTile: MapTile) {
        session?.onTileWritten(dimension.id(), layer, x, z, mapTile.snapshot() ?: return)
    }

    fun onStandaloneDisconnected(sender: StandalonePacketSender) {
        if (packetSender != sender) return
        packetSender = null

        val currentSession = session
        session = null

        try {
            currentSession?.close()
        } finally {
            sender.close()
        }

        showMessage("Disconnected from the standalone server")
    }

    fun getWorldId(): String? {
        val mc = Minecraft.getInstance()

        mc.singleplayerServer?.let { server ->
            val folder = server.getWorldPath(LevelResource.ROOT).toAbsolutePath()
                .normalize().fileName.toString()

            return "single:$folder"
        }

        val data = mc.currentServer ?: return null
        return "multi:${data.ip.trim().lowercase().trimEnd('.').removeSuffix(":25565")}"
    }

    fun showMessage(message: String) {
        Minecraft.getInstance().player
            ?.displayClientMessage(Component.literal("[XMMP] $message"), false)
    }
}

//? if >=26 {
/*private fun LocalPlayer.displayClientMessage(message: Component, actionBar: Boolean) {
    if (actionBar) sendOverlayMessage(message)
    else sendSystemMessage(message)
}
*///? }
