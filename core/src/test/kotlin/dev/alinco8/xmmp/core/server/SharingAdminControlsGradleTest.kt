package dev.alinco8.xmmp.core.server

import org.junit.jupiter.api.Test

class SharingAdminControlsGradleTest {
    @Test
    fun persistsServerControlsAndFailsClosedOnMalformedSettings() {
        runSharingAdminControlsChecks()
    }
}
