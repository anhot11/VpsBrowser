package com.vpsbrowser.app.ssh

import android.util.Log
import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.Session
import java.io.EOFException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * Local Dynamic SOCKS5 Proxy Server (RFC 1928) tunneled through JSch ChannelDirectTCPIP.
 * Encapsulates all web traffic from Android WebView and routes it securely through the VPS.
 */
class SshSocksServer(private val session: Session, val port: Int) {

    private companion object {
        private const val TAG = "SshSocksServer"
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
            Log.d(TAG, "SOCKS5 Proxy Server listening on 127.0.0.1:$port")
            while (isRunning && !s.isClosed) {
                try {
                    val clientSocket = s.accept()
                    activeSockets.add(clientSocket)
                    threadPool.submit {
                        try {
                            handleClient(clientSocket)
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
        socket.soTimeout = 30000

        val inStream = socket.getInputStream()
        val outStream = socket.getOutputStream()

        // 1. Negotiation
        val ver = inStream.read()
        if (ver != 5) return

        val nmethods = inStream.read()
        if (nmethods < 0) return
        val methods = ByteArray(nmethods)
        readExact(inStream, methods)

        // SOCKS5 greeting response: VER 5, METHOD 0 (NO AUTH)
        outStream.write(byteArrayOf(0x05, 0x00))
        outStream.flush()

        // 2. Client Request
        val reqVer = inStream.read()
        val cmd = inStream.read()
        val rsv = inStream.read()
        val atyp = inStream.read()

        if (reqVer != 5 || cmd != 1) { // 1 = CONNECT
            outStream.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            outStream.flush()
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
            else -> return
        }

        val p1 = inStream.read()
        val p2 = inStream.read()
        if (p1 < 0 || p2 < 0) return
        val targetPort = ((p1 and 0xFF) shl 8) or (p2 and 0xFF)

        // 3. Open ChannelDirectTCPIP via SSH to the VPS
        var channel: ChannelDirectTCPIP? = null
        try {
            if (!session.isConnected) {
                outStream.write(byteArrayOf(0x05, 0x05, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                outStream.flush()
                return
            }

            channel = session.openChannel("direct-tcpip") as ChannelDirectTCPIP
            channel.setHost(targetHost)
            channel.setPort(targetPort)
            val channelIn = channel.inputStream
            val channelOut = channel.outputStream
            channel.connect(15000)

            // 4. Send SOCKS5 Success (0x00)
            outStream.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            outStream.flush()

            // Remove socket timeout for long-lived connections (WebSockets, streaming, etc.)
            socket.soTimeout = 0

            // 5. Bi-directional forwarding
            val t1 = thread(name = "SocksForwardToVPS") {
                try {
                    inStream.copyTo(channelOut, 32768)
                } catch (_: Exception) {}
                try { channel.disconnect() } catch (_: Exception) {}
            }

            val t2 = thread(name = "SocksReceiveFromVPS") {
                try {
                    channelIn.copyTo(outStream, 32768)
                } catch (_: Exception) {}
                try { socket.close() } catch (_: Exception) {}
            }

            t1.join()
            t2.join()
        } catch (e: Exception) {
            try {
                outStream.write(byteArrayOf(0x05, 0x05, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                outStream.flush()
            } catch (_: Exception) {}
        } finally {
            try { channel?.disconnect() } catch (_: Exception) {}
        }
    }

    private fun readExact(stream: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val count = stream.read(buffer, offset, buffer.size - offset)
            if (count < 0) throw EOFException("Unexpected EOF while reading SOCKS5 packet")
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
