package com.vpsbrowser.app.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.vpsbrowser.app.model.VpsProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

data class ServerMetrics(
    val status: String,
    val ramUsageMb: String,
    val totalRamMb: String,
    val cpuLoad: String,
    val containerRunning: Boolean
)

object SshRemoteLifecycle {

    private suspend fun executeCommand(profile: VpsProfile, command: String): Result<String> = withContext(Dispatchers.IO) {
        val jsch = JSch()
        var session: com.jcraft.jsch.Session? = null

        try {
            if (profile.isKeyAuth()) {
                jsch.addIdentity("vps_key", profile.sshPrivateKey.toByteArray(), null, null)
            }

            session = jsch.getSession(profile.sshUser, profile.getCleanHost(), profile.sshPort).apply {
                if (!profile.isKeyAuth()) {
                    setPassword(profile.sshPassword)
                }
                setConfig("StrictHostKeyChecking", "no")
                connect(12000)
            }

            val channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)

            val inputStream = channel.inputStream
            val errorStream = channel.errStream
            channel.connect(10000)

            val reader = BufferedReader(InputStreamReader(inputStream))
            val errorReader = BufferedReader(InputStreamReader(errorStream))

            val output = StringBuilder()
            var line: String? = reader.readLine()
            while (line != null) {
                output.append(line).append("\n")
                line = reader.readLine()
            }

            while (!channel.isClosed) {
                Thread.sleep(100)
            }

            val exitStatus = channel.exitStatus
            channel.disconnect()

            if (exitStatus == 0) {
                Result.success(output.toString().trim())
            } else {
                val err = errorReader.readText().take(300)
                Result.failure(Exception("Comando falló con código $exitStatus: $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                session?.disconnect()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    suspend fun restartContainer(profile: VpsProfile): Result<String> {
        return executeCommand(profile, "cd /opt/vps-browser && docker compose restart")
    }

    suspend fun updateContainer(profile: VpsProfile): Result<String> {
        return executeCommand(profile, "cd /opt/vps-browser && docker compose pull && docker compose up -d")
    }

    suspend fun clearSessionCache(profile: VpsProfile): Result<String> {
        // Clears temporary caches while maintaining container integrity
        return executeCommand(profile, "rm -rf /opt/vps-browser/config/.cache/* 2>/dev/null || true")
    }

    suspend fun nukeAndDestroyEnvironment(profile: VpsProfile): Result<String> {
        // Destroys containers, volumes, networks, and deletes all files from the VPS
        val nukeCmd = "cd /opt/vps-browser && docker compose down -v --remove-orphans; cd / && rm -rf /opt/vps-browser"
        return executeCommand(profile, nukeCmd)
    }

    suspend fun fetchServerMetrics(profile: VpsProfile): Result<ServerMetrics> = withContext(Dispatchers.IO) {
        val checkCmd = "free -m | awk '/^Mem:/{print $3, $2}'; uptime | awk -F'load average:' '{print $2}'; docker ps --filter name=vps-firefox --format '{{.Status}}'"
        val result = executeCommand(profile, checkCmd)

        result.map { raw ->
            val lines = raw.lines().filter { it.isNotBlank() }
            val ramParts = lines.getOrNull(0)?.split(" ") ?: listOf("0", "0")
            val usedRam = ramParts.getOrNull(0) ?: "N/A"
            val totalRam = ramParts.getOrNull(1) ?: "N/A"
            val load = lines.getOrNull(1)?.trim() ?: "N/A"
            val containerStatus = lines.getOrNull(2) ?: "Detenido"
            val isRunning = containerStatus.contains("Up", ignoreCase = true)

            ServerMetrics(
                status = if (isRunning) "Activo ($containerStatus)" else "Detenido",
                ramUsageMb = usedRam,
                totalRamMb = totalRam,
                cpuLoad = load,
                containerRunning = isRunning
            )
        }
    }
}
