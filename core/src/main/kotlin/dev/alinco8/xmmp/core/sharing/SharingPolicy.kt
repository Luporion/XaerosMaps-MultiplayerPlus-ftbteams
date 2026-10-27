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
    /** Enabled only with server-authenticated single-scope storage, v5 packets and ACL checks. */
    const val TERRAIN_TRANSPORT_ENABLED = true

    /** IDs are generated from server-authenticated UUIDs, never from a client-provided group. */
    fun scope(identity: SharingIdentity, mode: SharingMode): String? = when(mode) {
        SharingMode.PRIVATE -> "private-${identity.playerId}"
        SharingMode.TEAM -> identity.partyTeamId?.let { "team-$it" }
        SharingMode.GLOBAL -> "global"
    }

    /** Personal maps always included; TEAM and GLOBAL can additionally receive
     *  team/global published terrain without granting uploads to those scopes.
     *  Publishing still uses exactly one chosen scope (plus private copy).
     */
    fun readableScopes(identity: SharingIdentity, mode: SharingMode): List<String> {
        val mine = "private-${identity.playerId}"
        if (mode == SharingMode.PRIVATE) return listOf(mine)
        val team = identity.partyTeamId?.let { "team-$it" }
        // Never infer a party from a client-supplied ID.
        return listOfNotNull(mine, team, "global").distinct()
    }

    fun canReceive(identity: SharingIdentity, mode: SharingMode, scopeId: String): Boolean =
        scopeId in readableScopes(identity, mode)

    fun resolveMode(raw: Int): SharingMode? = SharingMode.entries.getOrNull(raw)
    fun canReadScope(identity: SharingIdentity, mode: SharingMode, claimedScope: String): Boolean =
        scope(identity,mode) == claimedScope

    /** Scope names written under the NEW /scoped_v2 folder; no legacy shared data is read. */
    fun isValidScope(scope: String): Boolean = scope == "global" ||
        (scope.startsWith("team-") && isUuid(scope.removePrefix("team-"))) ||
        (scope.startsWith("private-") && isUuid(scope.removePrefix("private-")))

    private fun isUuid(text: String): Boolean = runCatching { UUID.fromString(text).toString() == text }.getOrDefault(false)

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
