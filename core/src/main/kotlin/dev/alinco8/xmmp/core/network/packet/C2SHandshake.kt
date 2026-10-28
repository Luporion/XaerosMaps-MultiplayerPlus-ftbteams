package dev.alinco8.xmmp.core.network.packet

import dev.alinco8.xmmp.core.network.XMMPPacket
import dev.alinco8.xmmp.core.network.XMMPStreamCodec

data class C2SHandshake(
    val enableWorldMapSync: Boolean,
    val downloadWindow: Int,
    val sharingMode: Int,
    val receiveTeam: Boolean,
    val receiveGlobal: Boolean,
) : XMMPPacket<C2SHandshake>(Companion) {
    companion object : Type<C2SHandshake>() {
        override val codec = with(XMMPStreamCodec) {
            composite(
                boolean, C2SHandshake::enableWorldMapSync,
                int, C2SHandshake::downloadWindow,
                int, C2SHandshake::sharingMode,
                boolean, C2SHandshake::receiveTeam,
                boolean, C2SHandshake::receiveGlobal,
                ::C2SHandshake,
            )
        }
    }
}
