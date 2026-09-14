package com.vpsbrowser.app.ssh

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.vpsbrowser.app.model.VpsProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.ServerSocket

object SshTunnelManager {

    private var activeSession: Session? = null
    private var localPort: Int = -1
    private var activeSocksPort: Int = -1
    private var socksServer: SshSocksServer? = null

    private fun createSession(profile: VpsProfile): Session {
        val jsch = JSch()
        if (profile.isKeyAuth()) {
            jsch.addIdentity("vps_key", profile.sshPrivateKey.toByteArray(), null, null)
        }

        return jsch.getSession(profile.sshUser, profile.getCleanHost(), profile.sshPort).apply {
            if (!profile.isKeyAuth()) {
                setPassword(profile.sshPassword)
            }
            setConfig("StrictHostKeyChecking", "no")
            setConfig("PreferredAuthentications", "publickey,password,keyboard-interactive")
            serverAliveInterval = 10000
            serverAliveCountMax = 3
            connect(15000)
        }
    }

    /**
     * Start local port forwarding to remote browserPort (for Remote Desktop Streaming).
     */
    suspend fun startTunnel(profile: VpsProfile): Result<String> = withContext(Dispatchers.IO) {
        try {
            stopTunnel()

            val session = createSession(profile)
            val freePort = findFreePort()
            val assignedPort = session.setPortForwardingL(freePort, "127.0.0.1", profile.browserPort)

            activeSession = session
            localPort = assignedPort

            val localUrl = "http://127.0.0.1:$assignedPort"
            Result.success(localUrl)
        } catch (e: Exception) {
            stopTunnel()
            Result.failure(e)
        }
    }

    /**
     * Start Dynamic SOCKS5 Proxy through encrypted SSH tunnel (for Native Mobile Browser Egress).
     */
    suspend fun startSocksProxy(profile: VpsProfile): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (activeSession?.isConnected != true) {
                stopTunnel()
                activeSession = createSession(profile)
            }

            val session = activeSession!!
            if (activeSocksPort > 0 && socksServer?.isRunning() == true) {
                return@withContext Result.success(activeSocksPort)
            }

            socksServer?.stop()
            val freePort = findFreePort()
            val server = SshSocksServer(session, freePort)
            server.start()
            socksServer = server
            activeSocksPort = freePort

            Result.success(freePort)
        } catch (e: Exception) {
            stopTunnel()
            Result.failure(e)
        }
    }

    fun isTunnelActive(): Boolean {
        return activeSession?.isConnected == true && (localPort > 0 || (activeSocksPort > 0 && socksServer?.isRunning() == true))
    }

    fun isSocksProxyActive(): Boolean {
        return activeSession?.isConnected == true && activeSocksPort > 0 && socksServer?.isRunning() == true
    }

    fun getSocksPort(): Int = activeSocksPort

    fun getLocalBoundUrl(): String? {
        return if (activeSession?.isConnected == true && localPort > 0) "http://127.0.0.1:$localPort" else null
    }

    fun stopTunnel() {
        try {
            socksServer?.stop()
        } catch (e: Exception) {
            // Ignore
        }
        socksServer = null

        try {
            activeSession?.disconnect()
        } catch (e: Exception) {
            // Ignore
        } finally {
            activeSession = null
            localPort = -1
            activeSocksPort = -1
        }
    }

    private fun findFreePort(): Int {
        ServerSocket(0).use { socket ->
            return socket.localPort
        }
    }
}
