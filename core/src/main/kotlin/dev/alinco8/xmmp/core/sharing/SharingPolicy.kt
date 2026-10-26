package dev.alinco8.xmmp.core.sharing

import java.util.UUID

/** Immutable, framework-independent policy. Server-sourced identities only. */
enum class SharingMode { PRIVATE, TEAM, GLOBAL }

data class SharingIdentity(val playerId: UUID, val partyTeamId: UUID?)

data class SharedMapGrant(
    val owner: UUID,
    val mode: SharingMode,
    /** The FTB party UUID captured WHEN data was published. Never inherit a new team. */
    val originalPartyTeam: UUID?,
)

object SharingPolicy {
    /** This release intentionally refuses ALL network terrain transfers.
     *  v0.2 must replace this gate only when stores, indexes, queues and ACLs are scoped.
     */
    const val TERRAIN_TRANSPORT_ENABLED = false

    fun allowed(grant: SharedMapGrant, viewer: SharingIdentity): Boolean = when {
        viewer.playerId == grant.owner -> true
        grant.mode == SharingMode.PRIVATE -> false
        grant.mode == SharingMode.GLOBAL -> true
        grant.mode == SharingMode.TEAM -> grant.originalPartyTeam != null &&
            viewer.partyTeamId == grant.originalPartyTeam
        else -> false
    }

    fun canPublishTeam(identity: SharingIdentity): Boolean = identity.partyTeamId != null

    fun captureGrant(identity: SharingIdentity, mode: SharingMode): SharedMapGrant = when (mode) {
        SharingMode.PRIVATE -> SharedMapGrant(identity.playerId, mode, null)
        SharingMode.GLOBAL -> SharedMapGrant(identity.playerId, mode, null)
        SharingMode.TEAM -> {
            require(canPublishTeam(identity)) { "No FTB party team - sharing must fail closed" }
            SharedMapGrant(identity.playerId, mode, identity.partyTeamId)
        }
    }
}
