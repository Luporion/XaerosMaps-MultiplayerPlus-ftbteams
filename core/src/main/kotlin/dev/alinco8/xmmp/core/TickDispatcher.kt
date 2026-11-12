package dev.alinco8.xmmp.core

import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable

class TickDispatcher : CoroutineDispatcher() {
    private val queue = ConcurrentLinkedQueue<Runnable>()
    @Volatile
    private var isClosed = false

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        if (isClosed) return

        queue.add(block)
    }

    fun pump(maxTasks: Int = Int.MAX_VALUE, maxNanos: Long = Long.MAX_VALUE) {
        val started = System.nanoTime()
        var remaining = if (maxTasks == Int.MAX_VALUE) queue.size else maxTasks

        while (remaining-- > 0) {
            val task = queue.poll() ?: break

            task.run()
            if (System.nanoTime() - started >= maxNanos) break
        }
    }

    fun close() {
        queue.clear()
        isClosed = true
    }
}
