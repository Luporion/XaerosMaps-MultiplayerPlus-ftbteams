package dev.alinco8.xmmp.core.network

/** Bounded exponential backoff. A retry is safe only within the same sync generation. */
object RegionRetryPolicy {
    const val MAX_ATTEMPTS = 7
    const val MAX_PENDING = 16_384

    fun delayMs(attempts: Int): Long {
        require(attempts >= 1)
        return (15_000L * (1L shl (attempts - 1).coerceIn(0, 4)))
            .coerceAtMost(180_000L)
    }
}
