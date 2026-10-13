package dev.alinco8.xmmp.standalone

import com.akuleshov7.ktoml.Toml
import dev.alinco8.xmmp.core.LOGGER
import dev.alinco8.xmmp.core.config.ServerConfig
import dev.alinco8.xmmp.core.server.ServerSession
import dev.alinco8.xmmp.standalone.config.StandaloneConfig
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.Path
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString


fun main() {
    val root = Path("run").toAbsolutePath()

    LOGGER.info("Starting standalone server in $root")

    val configFile = root.resolve("config.toml").toFile()
    val serverConfig: ServerConfig
    val standaloneConfig: StandaloneConfig
    try {
        if (configFile.exists()) {
            serverConfig = Toml.decodeFromString<ServerConfig>(configFile.readText())
            standaloneConfig = Toml.decodeFromString<StandaloneConfig>(configFile.readText())
        } else {
            serverConfig = ServerConfig()
            standaloneConfig = StandaloneConfig()

            configFile.parentFile.mkdirs()
            configFile.writeText(
                Toml.encodeToString(standaloneConfig) + "\n" + Toml.encodeToString(serverConfig)
            )
        }
    } catch (e: Exception) {
        LOGGER.error("Failed to load configuration", e)
        return
    }

    val stopping = AtomicBoolean(false)
    val shutdownLock = Any()

    val group = MultiThreadIoEventLoopGroup(NioIoHandler.newFactory())
    val executor = ScheduledThreadPoolExecutor(1)

    val host = StandaloneServerHost()
    val session = try {
        ServerSession(
            serverConfig,
            root.resolve("data"),
            host,
            standalone = true
        )
    } catch (e: Exception) {
        executor.shutdown()
        group.shutdownGracefully().syncUninterruptibly()
        throw e
    }

    val tick = executor.scheduleAtFixedRate(
        { session.onTickPost() },
        0, 50, TimeUnit.MILLISECONDS
    )

    fun shutdown() {
        synchronized(shutdownLock) {
            if (!stopping.compareAndSet(false, true)) return
            LOGGER.info("Shutting down standalone server")

            tick.cancel(false)

            try {
                group.shutdownGracefully(
                    0, 5, TimeUnit.SECONDS
                ).syncUninterruptibly()
            } finally {
                try {
                    CompletableFuture.runAsync({ session.close() }, executor).join()
                } finally {
                    executor.shutdown()
                }
            }

            LOGGER.info("Standalone server stopped")
        }
    }

    val hook = Thread({ shutdown() }, "xmmp-shutdown")
    Runtime.getRuntime().addShutdownHook(hook)

    val port = standaloneConfig.port

    try {
        val channel = ServerBootstrap()
            .group(group)
            .channel(NioServerSocketChannel::class.java)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    ch.pipeline().addLast(
                        VarIntFrameDecoder(),
                        VarIntFrameEncoder(),
                        StandaloneInboundHandler(
                            session,
                            host,
                            executor,
                            stopping
                        )
                    )
                }
            })
            .bind(port)
            .sync()
            .channel()

        LOGGER.info("Server started on port $port")
        channel.closeFuture().sync()
    } finally {
        shutdown()
    }
}
