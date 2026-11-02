package dev.alinco8.xmmp.core.sharing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GlobalSharingConsentTests {
    @Test
    fun playerCannotAccidentallyPublishGlobally() {
        assertEquals("PRIVATE", GlobalSharingConsent.publishingMode("PRIVATE", "GLOBAL", false))
        assertEquals("TEAM", GlobalSharingConsent.publishingMode("TEAM", "GLOBAL", false))
        assertEquals("GLOBAL", GlobalSharingConsent.publishingMode("TEAM", "GLOBAL", true))
        assertEquals("TEAM", GlobalSharingConsent.publishingMode("PRIVATE", "TEAM", false))
        assertEquals("PRIVATE", GlobalSharingConsent.publishingMode("GLOBAL", "PRIVATE", false))
        assertEquals("GLOBAL", GlobalSharingConsent.publishingMode("GLOBAL", "GLOBAL", false))
    }

    @Test
    fun adminCannotAccidentallyEnableGlobalForEveryone() {
        assertEquals(false, GlobalSharingConsent.globalServerEnabled(false, true, false))
        assertEquals(true, GlobalSharingConsent.globalServerEnabled(false, true, true))
        assertEquals(false, GlobalSharingConsent.globalServerEnabled(true, false, false))
        assertEquals(true, GlobalSharingConsent.globalServerEnabled(true, true, false))
    }
}
