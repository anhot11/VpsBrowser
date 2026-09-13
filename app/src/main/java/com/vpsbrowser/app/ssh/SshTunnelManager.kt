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

    suspend fun startTunnel(profile: VpsProfile): Result<String> = withContext(Dispatchers.IO) {
        try {
            stopTunnel()

            val jsch = JSch()
            if (profile.isKeyAuth()) {
                jsch.addIdentity("vps_key", profile.sshPrivateKey.toByteArray(), null, null)
            }

            val session = jsch.getSession(profile.sshUser, profile.getCleanHost(), profile.sshPort).apply {
                if (!profile.isKeyAuth()) {
                    setPassword(profile.sshPassword)
                }
                setConfig("StrictHostKeyChecking", "no")
                setConfig("PreferredAuthentications", "publickey,password,keyboard-interactive")
                serverAliveInterval = 10000
                serverAliveCountMax = 3
                connect(15000)
            }

            // Find an available random local port on the device
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

    fun isTunnelActive(): Boolean {
        return activeSession?.isConnected == true && localPort > 0
    }

    fun getLocalBoundUrl(): String? {
        return if (isTunnelActive()) "http://127.0.0.1:$localPort" else null
    }

    fun stopTunnel() {
        try {
            activeSession?.disconnect()
        } catch (e: Exception) {
            // Ignore
        } finally {
            activeSession = null
            localPort = -1
        }
    }

    private fun findFreePort(): Int {
        ServerSocket(0).use { socket ->
            return socket.localPort
        }
    }
}
