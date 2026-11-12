package dev.alinco8.xmmp.core.network

import dev.alinco8.xmmp.core.TickDispatcher
import dev.alinco8.xmmp.core.network.packet.S2CSharingStatus
import kotlin.coroutines.EmptyCoroutineContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MapScopeTransitionGradleTest {
    @Test fun telemetryAndUploadToggleDoNotResetDownloads() {
        val before = status()
        assertTrue(before.sameScope(before.copy(downloadPackets = 200, regionRequests = 12)))
        assertTrue(before.sameScope(before.copy(allowUploads = false)))
    }

    @Test fun membershipAndEffectivePermissionChangesResetDownloads() {
        val before = status()
        assertFalse(before.sameScope(before.copy(partyScope = "team-new")))
        assertFalse(before.sameScope(before.copy(activePublishScope = "private-player")))
        assertFalse(before.sameScope(before.copy(receiveTeam = false)))
        assertFalse(before.sameScope(before.copy(receiveGlobal = true)))
        val privatePublisher = before.copy(activePublishScope = "private-player")
        assertFalse(privatePublisher.sameScope(privatePublisher.copy(partyScope = "", receiveTeam = false)))
    }

    @Test fun boundedDispatcherCarriesBacklogToSubsequentPumps() {
        val dispatcher = TickDispatcher()
        val executions = ArrayList<Int>()
        repeat(300) { index -> dispatcher.dispatch(EmptyCoroutineContext, Runnable { executions.add(index) }) }
        dispatcher.pump(maxTasks = 128)
        assertEquals((0 until 128).toList(), executions)
        dispatcher.pump(maxTasks = 128)
        assertEquals(256, executions.size)
        dispatcher.pump(maxTasks = 128)
        assertEquals((0 until 300).toList(), executions)
    }

    private fun status() = S2CSharingStatus(
        activePublishScope = "team-current", partyScope = "team-current",
        receiveTeam = true, receiveGlobal = false, admin = false,
        allowTeam = true, allowGlobal = false, allowUploads = true
    )
}
