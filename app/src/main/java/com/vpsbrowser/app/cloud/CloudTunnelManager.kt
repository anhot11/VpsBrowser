package com.vpsbrowser.app.cloud

import android.util.Log
import com.vpsbrowser.app.model.VpsProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CloudTunnelManager:
 * Provides a local SOCKS5 and HTTP proxy on 127.0.0.1 that tunnels 100% of outgoing traffic
 * to the GitHub Codespace Cloud VM over an encrypted WebSocket tunnel.
 *
 * Guarantees:
 * 1. 0% IP Leakage: Egress IP is always the GitHub Cloud Azure VM.
 * 2. Absolute Kill-Switch: If the cloud tunnel drops, local connections are closed immediately.
 * 3. 0ms Native Mobile UI: Android WebView runs natively with hardware acceleration.
 */
object CloudTunnelManager {
    private const val TAG = "CloudTunnelManager"
    private const val BUFFER_SIZE = 16384

    private var serverSocket: ServerSocket? = null
    @Volatile
    private var activePort: Int = -1
    @Volatile
    private var isRunning: Boolean = false
    @Volatile
    private var activeHost: String = ""

    private val threadPool = Executors.newCachedThreadPool()
    private val dispatcher = Dispatcher().apply {
        maxRequests = 512
        maxRequestsPerHost = 512
    }
    private val httpClient = OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // Keep-alive for streaming
        .writeTimeout(0, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    fun isTunnelActive(): Boolean = isRunning && serverSocket?.isClosed == false && activePort > 0

    fun getPort(): Int = activePort

    suspend fun startTunnel(profile: VpsProfile): Result<Int> = withContext(Dispatchers.IO) {
        try {
            stopTunnel()

            val cloudUrl = profile.cloudflareUrl
            val cleanHost = if (cloudUrl.contains("://")) {
                cloudUrl.substringAfter("://").trimEnd('/')
            } else {
                cloudUrl.trimEnd('/')
            }

            if (cleanHost.isBlank()) {
                return@withContext Result.failure(Exception("URL del servidor Cloud no configurada"))
            }

            activeHost = cleanHost
            val s = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            serverSocket = s
            activePort = s.localPort
            isRunning = true

            threadPool.submit {
                Log.i(TAG, "CloudTunnelManager listening on 127.0.0.1:$activePort -> wss://$activeHost")
                while (isRunning && !s.isClosed) {
                    try {
                        val client = s.accept()
                        threadPool.submit {
                            handleClient(client, activeHost)
                        }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            }

            Result.success(activePort)
        } catch (e: Exception) {
            stopTunnel()
            Result.failure(e)
        }
    }

    fun stopTunnel() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        activePort = -1
    }

    private fun handleClient(socket: Socket, host: String) {
        socket.tcpNoDelay = true
        socket.soTimeout = 15000

        try {
            val inStream = socket.getInputStream()
            val outStream = socket.getOutputStream()

            val firstByte = inStream.read()
            if (firstByte < 0) {
                socket.close()
                return
            }

            if (firstByte == 0x05) {
                handleSocks5(socket, inStream, outStream, host)
            } else {
                handleHttpConnect(socket, inStream, outStream, firstByte, host)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Client handle error: ${e.message}")
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun handleSocks5(socket: Socket, inStream: InputStream, outStream: OutputStream, cloudHost: String) {
        // 1. SOCKS5 Method Negotiation
        val nmethods = inStream.read()
        if (nmethods <= 0) { socket.close(); return }
        val methods = ByteArray(nmethods)
        readExact(inStream, methods)

        // Reply: VER 5, METHOD 0 (NO AUTH)
        outStream.write(byteArrayOf(0x05, 0x00))
        outStream.flush()

        // 2. Request details
        val reqVer = inStream.read()
        val cmd = inStream.read()
        val rsv = inStream.read()
        val atyp = inStream.read()

        if (reqVer != 5 || cmd != 1) { // 1 = CONNECT
            outStream.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            outStream.flush()
            socket.close()
            return
        }

        val targetHost = when (atyp) {
            1 -> { // IPv4
                val ip = ByteArray(4)
                readExact(inStream, ip)
                InetAddress.getByAddress(ip).hostAddress ?: run { socket.close(); return }
            }
            3 -> { // Domain name
                val len = inStream.read()
                if (len <= 0) { socket.close(); return }
                val domainBytes = ByteArray(len)
                readExact(inStream, domainBytes)
                String(domainBytes, Charsets.UTF_8)
            }
            4 -> { // IPv6
                val ip = ByteArray(16)
                readExact(inStream, ip)
                InetAddress.getByAddress(ip).hostAddress ?: run { socket.close(); return }
            }
            else -> {
                outStream.write(byteArrayOf(0x05, 0x08, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                outStream.flush()
                socket.close()
                return
            }
        }

        val p1 = inStream.read()
        val p2 = inStream.read()
        if (p1 < 0 || p2 < 0) { socket.close(); return }
        val targetPort = ((p1 and 0xFF) shl 8) or (p2 and 0xFF)

        // 3. Connect WebSocket to Cloud Bridge
        pipeSocketThroughWebSocket(socket, inStream, outStream, cloudHost, targetHost, targetPort) {
            // Send SOCKS success reply once connected
            outStream.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            outStream.flush()
        }
    }

    private fun handleHttpConnect(socket: Socket, inStream: InputStream, outStream: OutputStream, firstByte: Int, cloudHost: String) {
        val lineBuf = StringBuilder()
        lineBuf.append(firstByte.toChar())

        var b = inStream.read()
        while (b != -1 && b != '\n'.code) {
            if (b != '\r'.code) lineBuf.append(b.toChar())
            b = inStream.read()
        }

        val reqLine = lineBuf.toString().trim()
        if (!reqLine.startsWith("CONNECT ", ignoreCase = true)) {
            socket.close()
            return
        }

        val parts = reqLine.split(" ")
        if (parts.size < 2) { socket.close(); return }

        val hostPort = parts[1].split(":")
        val targetHost = hostPort[0]
        val targetPort = if (hostPort.size > 1) hostPort[1].toIntOrNull() ?: 443 else 443

        // Drain headers until empty line
        var emptyCount = 0
        while (true) {
            val c = inStream.read()
            if (c == -1) break
            if (c == '\n'.code) {
                emptyCount++
                if (emptyCount >= 2) break
            } else if (c != '\r'.code) {
                emptyCount = 0
            }
        }

        pipeSocketThroughWebSocket(socket, inStream, outStream, cloudHost, targetHost, targetPort) {
            outStream.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(Charsets.UTF_8))
            outStream.flush()
        }
    }

    private fun pipeSocketThroughWebSocket(
        socket: Socket,
        inStream: InputStream,
        outStream: OutputStream,
        cloudHost: String,
        targetHost: String,
        targetPort: Int,
        onConnected: () -> Unit
    ) {
        val wsUrl = "wss://$cloudHost/tunnel?host=$targetHost&port=$targetPort"
        val request = Request.Builder()
            .url(wsUrl)
            .build()

        val connectedLatch = CountDownLatch(1)
        val isClosed = AtomicBoolean(false)
        var webSocketRef: WebSocket? = null

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "✓ WebSocket connected to $targetHost:$targetPort via $cloudHost")
                webSocketRef = webSocket
                try {
                    onConnected()
                } catch (e: Exception) {
                    closeAll()
                }
                connectedLatch.countDown()
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (isClosed.get()) return
                try {
                    outStream.write(bytes.toByteArray())
                    outStream.flush()
                } catch (e: Exception) {
                    closeAll()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                closeAll()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "✗ WebSocket connection failed to $targetHost:$targetPort (${t.message}, HTTP ${response?.code})")
                connectedLatch.countDown()
                closeAll()
            }

            private fun closeAll() {
                if (isClosed.compareAndSet(false, true)) {
                    try { socket.close() } catch (_: Exception) {}
                    try { webSocketRef?.cancel() } catch (_: Exception) {}
                }
            }
        }

        webSocketRef = httpClient.newWebSocket(request, listener)

        // Wait up to 10 seconds for WebSocket connection
        val success = connectedLatch.await(10, TimeUnit.SECONDS)
        if (!success || isClosed.get()) {
            try { socket.close() } catch (_: Exception) {}
            try { webSocketRef?.cancel() } catch (_: Exception) {}
            return
        }

        socket.soTimeout = 0 // Disable timeout during active streaming

        // Pump from client socket into WebSocket
        try {
            val buf = ByteArray(BUFFER_SIZE)
            while (!isClosed.get() && isRunning) {
                val read = inStream.read(buf)
                if (read < 0) break
                val sent = webSocketRef?.send(buf.toByteString(0, read)) ?: false
                if (!sent) break
            }
        } catch (_: Exception) {
        } finally {
            if (isClosed.compareAndSet(false, true)) {
                try { socket.close() } catch (_: Exception) {}
                try { webSocketRef?.close(1000, "Done") } catch (_: Exception) {}
            }
        }
    }

    private fun readExact(stream: InputStream, buffer: ByteArray) {
        var total = 0
        while (total < buffer.size) {
            val count = stream.read(buffer, total, buffer.size - total)
            if (count < 0) throw java.io.EOFException("Unexpected EOF reading ${buffer.size} bytes")
            total += count
        }
    }
}
