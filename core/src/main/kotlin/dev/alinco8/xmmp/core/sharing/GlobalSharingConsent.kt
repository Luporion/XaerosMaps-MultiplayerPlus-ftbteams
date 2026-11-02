package dev.alinco8.xmmp.core.sharing

/** UI-level guard against accidental GLOBAL publishing in competitive Fog-of-War servers.
 *  Server ACLs remain authoritative; a modified client can always bypass client UI.
 */
object GlobalSharingConsent {
    fun publishingMode(previous: String, requested: String, acknowledged: Boolean): String =
        if (requested.equals("GLOBAL", ignoreCase = true) &&
            !previous.equals("GLOBAL", ignoreCase = true) && !acknowledged
        ) previous else requested

    fun globalServerEnabled(previous: Boolean, requested: Boolean, acknowledged: Boolean): Boolean =
        if (!previous && requested && !acknowledged) false else requested
}
