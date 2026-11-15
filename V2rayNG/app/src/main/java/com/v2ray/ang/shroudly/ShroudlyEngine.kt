package com.v2ray.ang.shroudly

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import me.codeshare.shroudly.EngineNative
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/*
 * Shroudly engine manager inside the app process.
 * Drives EngineNative.run() over pipes, parsing events and managing SOCKS5 proxy lifecycle.
 */
object ShroudlyEngine {
    private const val TAG = "ShroudlyEngine"
    const val START_TIMEOUT_MS = 6000L
    const val STOP_WAIT_MS = 5000L

    private const val STATE_STOPPED = 0
    private const val STATE_STARTING = 1
    private const val STATE_RUNNING = 2

    private val state = AtomicInteger(STATE_STOPPED)
    private val commandLock = Any()

    @Volatile private var commands: OutputStreamWriter? = null
    @Volatile private var runThread: Thread? = null
    @Volatile var lastError: String? = null
        private set
    @Volatile var exitCode: Int? = null
        private set

    @Volatile var currentPort: Int = 0
        private set
    @Volatile var currentAuth: SocksAuth? = null
        private set

    val isRunning: Boolean
        get() = runThread?.isAlive == true && state.get() == STATE_RUNNING

    val isBusy: Boolean
        get() = runThread?.isAlive == true

    private val _events = MutableSharedFlow<ShroudlyEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<ShroudlyEvent> = _events.asSharedFlow()

    fun freePort(): Int = ServerSocket().use {
        it.reuseAddress = false
        it.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        it.localPort
    }

    fun buildArgs(
        port: Int,
        authFile: File?,
        blockIps: List<String>,
        strategy: String,
        auto: Boolean = false,
        quicOff: Boolean = true
    ): List<String> {
        val (actualStrategy, isAuto) = if (strategy.equals("auto", ignoreCase = true)) {
            "tlsrec(sni+1)" to true
        } else {
            strategy to auto
        }

        val a = mutableListOf(
            "--listen", "127.0.0.1",
            "--port", port.toString(),
            "--strategy", actualStrategy
        )
        if (isAuto) a += "--auto"
        a += "--doh"
        if (authFile != null) a += listOf(SocksAuth.OPTION, authFile.path)
        for (ip in blockIps) a += listOf("--block-ip", ip)
        if (quicOff) a += "--drop-quic"
        return a
    }

    fun readBlockIps(context: Context): List<String> {
        return try {
            context.assets.open("shroudly/block-ips.txt").bufferedReader().useLines { lines ->
                lines.map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .toList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read block-ips.txt", e)
            emptyList()
        }
    }

    @Synchronized
    fun start(
        port: Int,
        authFile: File,
        blockIps: List<String>,
        strategy: String,
        auto: Boolean,
        quicOff: Boolean = true
    ): Boolean {
        lastError = null
        val loadErr = EngineNative.loadError
        if (loadErr != null) {
            lastError = "Engine native library error: $loadErr"
            Log.e(TAG, lastError!!)
            return false
        }
        if (isBusy) {
            lastError = "Previous engine run is still active"
            Log.w(TAG, lastError!!)
            return false
        }

        state.set(STATE_STARTING)
        currentPort = port

        val cmd: Array<ParcelFileDescriptor>
        val ev: Array<ParcelFileDescriptor>
        try {
            cmd = ParcelFileDescriptor.createPipe()
            ev = try {
                ParcelFileDescriptor.createPipe()
            } catch (e: IOException) {
                cmd.forEach { closeQuietly(it) }
                throw e
            }
        } catch (e: IOException) {
            lastError = "Failed to create pipes for engine: ${e.message}"
            state.set(STATE_STOPPED)
            Log.e(TAG, lastError!!, e)
            return false
        }

        val cmdFd = cmd[0].detachFd()
        val evFd = ev[1].detachFd()
        commands = OutputStreamWriter(ParcelFileDescriptor.AutoCloseOutputStream(cmd[1]), Charsets.UTF_8)
        val eventsStream = ParcelFileDescriptor.AutoCloseInputStream(ev[0])

        val readyLatch = CountDownLatch(1)
        val args = buildArgs(port, authFile, blockIps, strategy, auto, quicOff)
        val argv = args.toTypedArray()

        Log.i(TAG, "Starting engine on 127.0.0.1:$port with strategy '$strategy' (auto=$auto, quicOff=$quicOff)")

        val run = Thread({
            val code = try {
                EngineNative.run(argv, cmdFd, evFd)
            } catch (e: UnsatisfiedLinkError) {
                closeFd(cmdFd)
                closeFd(evFd)
                lastError = "Engine native link error: $e"
                -1
            }
            exitCode = code
            if (code == EngineNative.BUSY) {
                lastError = "Native engine reported busy"
            }
            Log.i(TAG, "Engine exited with code $code")
        }, "shroudly-engine")

        runThread = run
        run.start()

        Thread({
            try {
                eventsStream.bufferedReader(Charsets.UTF_8).forEachLine { raw ->
                    val line = raw.trim()
                    if (line.isEmpty()) return@forEachLine
                    val evObj = ShroudlyEvent.parse(line)
                    when (evObj) {
                        is ShroudlyEvent.Ready -> {
                            Log.i(TAG, "Engine ready on port ${evObj.port}")
                            readyLatch.countDown()
                        }
                        is ShroudlyEvent.Error -> {
                            lastError = evObj.message
                            Log.w(TAG, "Engine error: ${evObj.message}")
                        }
                        else -> {}
                    }
                    _events.tryEmit(evObj)
                }
            } catch (_: IOException) {
            } finally {
                closeQuietly(eventsStream)
            }

            try { run.join(STOP_WAIT_MS) } catch (_: InterruptedException) {}
            readyLatch.countDown()
            closeCommands()
            state.set(STATE_STOPPED)
        }, "shroudly-reader").apply { isDaemon = true }.start()

        val ready = readyLatch.await(START_TIMEOUT_MS, TimeUnit.MILLISECONDS) && run.isAlive
        if (ready && state.compareAndSet(STATE_STARTING, STATE_RUNNING)) {
            return true
        }

        if (lastError == null) {
            lastError = if (run.isAlive) "Engine start timed out" else "Engine exited prematurely (code ${exitCode ?: -1})"
        }
        stop()
        return false
    }

    fun send(command: String) {
        synchronized(commandLock) {
            val w = commands ?: return
            try {
                w.write(command)
                w.write("\n")
                w.flush()
            } catch (_: IOException) {}
        }
    }

    @Synchronized
    fun stop(waitMs: Long = STOP_WAIT_MS): Boolean {
        state.set(STATE_STOPPED)
        send("STOP")
        closeCommands()
        val t = runThread ?: return true
        try {
            t.join(waitMs)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        currentPort = 0
        currentAuth = null
        return !t.isAlive
    }

    fun setAuth(auth: SocksAuth) {
        currentAuth = auth
    }

    private fun closeCommands() {
        synchronized(commandLock) {
            val w = commands ?: return
            commands = null
            try { w.close() } catch (_: IOException) {}
        }
    }

    private fun closeQuietly(c: java.io.Closeable) {
        try { c.close() } catch (_: IOException) {}
    }

    private fun closeFd(fd: Int) {
        try { ParcelFileDescriptor.adoptFd(fd).close() } catch (_: Exception) {}
    }
}
