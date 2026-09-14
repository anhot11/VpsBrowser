package com.vpsbrowser.app.ssh

import android.util.Log
import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.Session
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * High-Performance Dual-Protocol Tunneling Proxy Server (SOCKS5 RFC 1928 + HTTP CONNECT).
 * Routes 100% of Android WebView traffic encrypted through the remote VPS via JSch ChannelDirectTCPIP.
 * Ensures 0ms native UI responsiveness with VPS IP address egress.
 */
class SshSocksServer(private val session: Session, val port: Int) {

    private companion object {
        private const val TAG = "SshSocksServer"
        private const val BUFFER_SIZE = 16384
        private const val CONNECT_TIMEOUT_MS = 15000
        private const val IDLE_SOCKET_TIMEOUT_MS = 120000 // 2 minutes keep-alive
    }

    private var serverSocket: ServerSocket? = null
    @Volatile
    private var isRunning = false
    private val threadPool = Executors.newCachedThreadPool()
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()

    fun isRunning(): Boolean = isRunning && serverSocket?.isClosed == false

    fun start() {
        val s = ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
        serverSocket = s
        isRunning = true

        threadPool.submit {
            Log.d(TAG, "Dual SOCKS5/HTTP Proxy Server listening on 127.0.0.1:$port")
            while (isRunning && !s.isClosed) {
                try {
                    val clientSocket = s.accept()
                    activeSockets.add(clientSocket)
                    threadPool.submit {
                        try {
                            handleClient(clientSocket)
                        } catch (e: Exception) {
                            Log.d(TAG, "Client connection ended: ${e.message}")
                        } finally {
                            activeSockets.remove(clientSocket)
                            try { clientSocket.close() } catch (_: Exception) {}
                        }
                    }
                } catch (e: Exception) {
                    if (!isRunning) break
                }
            }
        }
    }

    private fun handleClient(socket: Socket) {
        socket.tcpNoDelay = true
        socket.soTimeout = 15000 // Handshake timeout

        val inStream = socket.getInputStream()
        val outStream = socket.getOutputStream()

        val firstByte = inStream.read()
        if (firstByte < 0) return

        if (firstByte == 0x05) {
            handleSocks5(socket, inStream, outStream)
        } else {
            handleHttpProxy(socket, inStream, outStream, firstByte)
        }
    }

    private fun handleSocks5(socket: Socket, inStream: InputStream, outStream: OutputStream) {
        // 1. SOCKS5 Method Negotiation
        val nmethods = inStream.read()
        if (nmethods <= 0) return
        val methods = ByteArray(nmethods)
        readExact(inStream, methods)

        if (!methods.contains(0x00.toByte())) {
            // No acceptable methods
            outStream.write(byteArrayOf(0x05, 0xFF.toByte()))
            outStream.flush()
            return
        }

        // Reply: VER 5, METHOD 0 (NO AUTH)
        outStream.write(byteArrayOf(0x05, 0x00))
        outStream.flush()

        // 2. Client Request (CONNECT)
        val reqVer = inStream.read()
        val cmd = inStream.read()
        val rsv = inStream.read()
        val atyp = inStream.read()

        if (reqVer != 5) return

        if (cmd != 1) { // 1 = CONNECT
            sendSocksReply(outStream, 0x07) // Command not supported
            return
        }

        val targetHost = when (atyp) {
            1 -> { // IPv4
                val ip = ByteArray(4)
                readExact(inStream, ip)
                InetAddress.getByAddress(ip).hostAddress ?: return
            }
            3 -> { // Domain name
                val len = inStream.read()
                if (len <= 0) return
                val domainBytes = ByteArray(len)
                readExact(inStream, domainBytes)
                String(domainBytes, Charsets.UTF_8)
            }
            4 -> { // IPv6
                val ip = ByteArray(16)
                readExact(inStream, ip)
                InetAddress.getByAddress(ip).hostAddress ?: return
            }
            else -> {
                sendSocksReply(outStream, 0x08) // Address type not supported
                return
            }
        }

        val p1 = inStream.read()
        val p2 = inStream.read()
        if (p1 < 0 || p2 < 0) return
        val targetPort = ((p1 and 0xFF) shl 8) or (p2 and 0xFF)

        // 3. Connect ChannelDirectTCPIP via SSH to VPS
        if (!session.isConnected) {
            Log.e(TAG, "SSH session is down, cannot route SOCKS to $targetHost:$targetPort")
            sendSocksReply(outStream, 0x01) // General failure
            return
        }

        var channel: ChannelDirectTCPIP? = null
        try {
            channel = session.openChannel("direct-tcpip") as ChannelDirectTCPIP
            channel.setHost(targetHost)
            channel.setPort(targetPort)
            // Critical JSch fields: originator IP and Port prevent NullPointerException in genChannelOpenPacket
            channel.setOrgIPAddress("127.0.0.1")
            channel.setOrgPort(socket.port)

            val channelIn = channel.inputStream
            val channelOut = channel.outputStream

            channel.connect(CONNECT_TIMEOUT_MS)
            if (!channel.isConnected) {
                sendSocksReply(outStream, 0x05) // Connection refused
                return
            }

            // 4. Send SOCKS5 Success (0x00)
            sendSocksReply(outStream, 0x00)

            // 5. Bidirectional Streaming
            pipeBidirectional(socket, inStream, outStream, channel, channelIn, channelOut)
        } catch (e: Exception) {
            Log.e(TAG, "Failed SOCKS connection to $targetHost:$targetPort: ${e.message}")
            sendSocksReply(outStream, 0x05)
        } finally {
            try { channel?.disconnect() } catch (_: Exception) {}
        }
    }

    private fun handleHttpProxy(socket: Socket, inStream: InputStream, outStream: OutputStream, firstByte: Int) {
        val lineBytes = ByteArrayOutputStream()
        lineBytes.write(firstByte)
        while (true) {
            val b = inStream.read()
            if (b < 0) return
            if (b == '\n'.code) break
            if (b != '\r'.code) {
                lineBytes.write(b)
            }
        }
        val requestLine = lineBytes.toString("UTF-8").trim()
        if (requestLine.isBlank()) return

        val parts = requestLine.split(" ")
        if (parts.size < 2) return
        val method = parts[0]
        val target = parts[1]

        val targetHost: String
        val targetPort: Int

        if (method.equals("CONNECT", ignoreCase = true)) {
            // Consume remaining headers until empty line
            skipHeaders(inStream)

            val hostParts = target.split(":")
            targetHost = hostParts[0]
            targetPort = if (hostParts.size > 1) hostParts[1].toIntOrNull() ?: 443 else 443

            if (!session.isConnected) {
                sendHttpError(outStream, 502, "Bad Gateway")
                return
            }

            var channel: ChannelDirectTCPIP? = null
            try {
                channel = session.openChannel("direct-tcpip") as ChannelDirectTCPIP
                channel.setHost(targetHost)
                channel.setPort(targetPort)
                channel.setOrgIPAddress("127.0.0.1")
                channel.setOrgPort(socket.port)

                val channelIn = channel.inputStream
                val channelOut = channel.outputStream

                channel.connect(CONNECT_TIMEOUT_MS)
                if (!channel.isConnected) {
                    sendHttpError(outStream, 504, "Gateway Timeout")
                    return
                }

                // Send 200 Connection Established
                val response = "HTTP/1.1 200 Connection Established\r\nProxy-Agent: VpsBrowser-Proxy\r\n\r\n"
                outStream.write(response.toByteArray(Charsets.US_ASCII))
                outStream.flush()

                pipeBidirectional(socket, inStream, outStream, channel, channelIn, channelOut)
            } catch (e: Exception) {
                Log.e(TAG, "Failed HTTP CONNECT to $targetHost:$targetPort: ${e.message}")
                sendHttpError(outStream, 502, "Bad Gateway")
            } finally {
                try { channel?.disconnect() } catch (_: Exception) {}
            }
        } else {
            // Plain HTTP request (GET / POST)
            val uri = try {
                java.net.URI(target)
            } catch (_: Exception) {
                null
            }
            targetHost = uri?.host ?: target.split("/")[0].split(":")[0]
            targetPort = if (uri?.port != null && uri.port > 0) uri.port else 80

            if (!session.isConnected) {
                sendHttpError(outStream, 502, "Bad Gateway")
                return
            }

            var channel: ChannelDirectTCPIP? = null
            try {
                channel = session.openChannel("direct-tcpip") as ChannelDirectTCPIP
                channel.setHost(targetHost)
                channel.setPort(targetPort)
                channel.setOrgIPAddress("127.0.0.1")
                channel.setOrgPort(socket.port)

                val channelIn = channel.inputStream
                val channelOut = channel.outputStream

                channel.connect(CONNECT_TIMEOUT_MS)
                if (!channel.isConnected) {
                    sendHttpError(outStream, 504, "Gateway Timeout")
                    return
                }

                // Forward original request line
                val rawPath = if (uri != null) {
                    val raw = (uri.rawPath ?: "/") + if (uri.rawQuery != null) "?${uri.rawQuery}" else ""
                    if (raw.isBlank()) "/" else raw
                } else {
                    target
                }
                val rewrittenLine = "$method $rawPath ${parts.getOrNull(2) ?: "HTTP/1.1"}\r\n"
                channelOut.write(rewrittenLine.toByteArray(Charsets.UTF_8))
                channelOut.flush()

                pipeBidirectional(socket, inStream, outStream, channel, channelIn, channelOut)
            } catch (e: Exception) {
                Log.e(TAG, "Failed plain HTTP to $targetHost:$targetPort: ${e.message}")
                sendHttpError(outStream, 502, "Bad Gateway")
            } finally {
                try { channel?.disconnect() } catch (_: Exception) {}
            }
        }
    }

    private fun skipHeaders(inStream: InputStream) {
        var prev1 = 0
        var prev2 = 0
        var prev3 = 0
        while (true) {
            val b = inStream.read()
            if (b < 0) break
            if (b == '\n'.code && prev1 == '\r'.code && prev2 == '\n'.code && prev3 == '\r'.code) {
                break
            }
            if (b == '\n'.code && prev1 == '\n'.code) {
                break
            }
            prev3 = prev2
            prev2 = prev1
            prev1 = b
        }
    }

    private fun sendSocksReply(out: OutputStream, replyCode: Byte) {
        try {
            out.write(byteArrayOf(0x05, replyCode, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            out.flush()
        } catch (_: Exception) {}
    }

    private fun sendHttpError(out: OutputStream, code: Int, message: String) {
        try {
            val msg = "HTTP/1.1 $code $message\r\nContent-Type: text/plain\r\nConnection: close\r\n\r\n$message"
            out.write(msg.toByteArray(Charsets.US_ASCII))
            out.flush()
        } catch (_: Exception) {}
    }

    private fun pipeBidirectional(
        socket: Socket,
        inStream: InputStream,
        outStream: OutputStream,
        channel: ChannelDirectTCPIP,
        channelIn: InputStream,
        channelOut: OutputStream
    ) {
        socket.soTimeout = IDLE_SOCKET_TIMEOUT_MS
        val latch = CountDownLatch(1)

        threadPool.submit {
            try {
                val buf = ByteArray(BUFFER_SIZE)
                while (true) {
                    val count = inStream.read(buf)
                    if (count < 0) break
                    channelOut.write(buf, 0, count)
                    channelOut.flush() // Immediate flush avoids buffering stalls
                }
            } catch (_: Exception) {
            } finally {
                latch.countDown()
            }
        }

        threadPool.submit {
            try {
                val buf = ByteArray(BUFFER_SIZE)
                while (true) {
                    val count = channelIn.read(buf)
                    if (count < 0) break
                    outStream.write(buf, 0, count)
                    outStream.flush() // Immediate flush sends data to WebView
                }
            } catch (_: Exception) {
            } finally {
                latch.countDown()
            }
        }

        try {
            latch.await()
        } catch (_: InterruptedException) {
        } finally {
            try { channel.disconnect() } catch (_: Exception) {}
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun readExact(stream: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val count = stream.read(buffer, offset, buffer.size - offset)
            if (count < 0) throw EOFException("Unexpected EOF while reading proxy packet")
            offset += count
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        for (socket in activeSockets) {
            try { socket.close() } catch (_: Exception) {}
        }
        activeSockets.clear()
        threadPool.shutdownNow()
    }
}
