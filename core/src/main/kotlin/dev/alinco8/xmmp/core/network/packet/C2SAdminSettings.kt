package dev.alinco8.xmmp.core.network.packet

import dev.alinco8.xmmp.core.network.XMMPPacket
import dev.alinco8.xmmp.core.network.XMMPStreamCodec

/** Untrusted client request. The server MUST verify OP permission before acting. */
data class C2SAdminSettings(
    val allowTeam: Boolean,
    val allowGlobal: Boolean,
    val allowUploads: Boolean,
) : XMMPPacket<C2SAdminSettings>(Companion) {
    companion object : Type<C2SAdminSettings>() {
        override val codec = with(XMMPStreamCodec) {
            composite(
                boolean, C2SAdminSettings::allowTeam,
                boolean, C2SAdminSettings::allowGlobal,
                boolean, C2SAdminSettings::allowUploads,
                ::C2SAdminSettings,
            )
        }
    }
}
