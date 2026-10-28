package dev.alinco8.xmmp.core.network.packet

import dev.alinco8.xmmp.core.network.XMMPPacket
import dev.alinco8.xmmp.core.network.XMMPStreamCodec

/** Status is server-originated. No client supplied FTB team IDs are trusted. */
data class S2CSharingStatus(
    val activePublishScope: String,
    val partyScope: String,
    val receiveTeam: Boolean,
    val receiveGlobal: Boolean,
    val admin: Boolean,
    val allowTeam: Boolean,
    val allowGlobal: Boolean,
    val allowUploads: Boolean,
) : XMMPPacket<S2CSharingStatus>(Companion) {
    companion object : Type<S2CSharingStatus>() {
        override val codec = XMMPStreamCodec.of(
            { buf ->
                S2CSharingStatus(
                    XMMPStreamCodec.scopeId.decode(buf), XMMPStreamCodec.scopeId.decode(buf),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean()
                )
            },
            { buf, p ->
                XMMPStreamCodec.scopeId.encode(buf, p.activePublishScope)
                XMMPStreamCodec.scopeId.encode(buf, p.partyScope)
                buf.writeBoolean(p.receiveTeam)
                buf.writeBoolean(p.receiveGlobal)
                buf.writeBoolean(p.admin)
                buf.writeBoolean(p.allowTeam)
                buf.writeBoolean(p.allowGlobal)
                buf.writeBoolean(p.allowUploads)
            }
        )
    }
}
