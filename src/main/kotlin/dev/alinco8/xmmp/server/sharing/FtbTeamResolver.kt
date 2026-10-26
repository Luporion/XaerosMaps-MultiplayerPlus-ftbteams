package dev.alinco8.xmmp.server.sharing

import java.util.Optional
import java.util.UUID
import org.slf4j.LoggerFactory

/** Optional FTB Teams 2101.x API adapter. No class loading or hard dependency when absent.
 *  Uses the verified API methods rather than guessing team ownership from display names.
 *  This foundation does not yet send/receive shared terrain.
 */
object FtbTeamResolver {
    private val logger = LoggerFactory.getLogger(FtbTeamResolver::class.java)
    private var warned = false

    fun partyId(playerId: UUID): UUID? {
        return try {
        val apiType = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI")
        val api = apiType.getMethod("api").invoke(null) ?: return null
        if (api.javaClass.getMethod("isManagerLoaded").invoke(api) != true) return null
        val manager = api.javaClass.getMethod("getManager").invoke(api) ?: return null
        val found = manager.javaClass.getMethod("getTeamForPlayerID", UUID::class.java)
            .invoke(manager, playerId) as? Optional<*> ?: return null
        val team = found.orElse(null) ?: return null
        if (team.javaClass.getMethod("isPartyTeam").invoke(team) != true) return null
        val members = team.javaClass.getMethod("getMembers").invoke(team) as? Set<*>
            ?: return null
        if (playerId !in members) return null
        team.javaClass.getMethod("getId").invoke(team) as? UUID
    } catch (error: ReflectiveOperationException) {
        if (!warned) { warned = true; logger.warn("FTB Teams not accessible; team permissions fail closed", error) }
        null
    } catch (error: LinkageError) {
        if (!warned) { warned = true; logger.warn("FTB Teams API unavailable; team permissions fail closed", error) }
        null
    } catch (error: RuntimeException) {
        if (!warned) { warned = true; logger.warn("FTB Teams lookup failed; team permissions fail closed", error) }
        null
        }
    }
}
