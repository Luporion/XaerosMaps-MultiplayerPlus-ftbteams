package dev.alinco8.xmmp.core.network

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RegionRetryPolicyGradleTest {
    @Test fun exponentialBackoffIsBounded() {
        assertEquals(15_000L, RegionRetryPolicy.delayMs(1))
        assertEquals(30_000L, RegionRetryPolicy.delayMs(2))
        assertEquals(60_000L, RegionRetryPolicy.delayMs(3))
        assertEquals(120_000L, RegionRetryPolicy.delayMs(4))
        assertEquals(180_000L, RegionRetryPolicy.delayMs(5))
        assertEquals(180_000L, RegionRetryPolicy.delayMs(10))
        assertThrows(IllegalArgumentException::class.java) { RegionRetryPolicy.delayMs(0) }
        assertTrue(RegionRetryPolicy.MAX_ATTEMPTS in 3..12)
        assertTrue(RegionRetryPolicy.MAX_PENDING <= 16_384)
    }
}
