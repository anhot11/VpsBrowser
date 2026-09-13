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
        val cmd = "if systemctl is-active --quiet vps-browser 2>/dev/null; then systemctl restart vps-browser vps-tunnel 2>/dev/null; echo 'Servicios nativos reiniciados'; elif [ -d /opt/vps-browser ]; then cd /opt/vps-browser && (docker compose restart 2>/dev/null || docker restart vps-firefox vps-tunnel); fi"
        return executeCommand(profile, cmd)
    }

    suspend fun updateContainer(profile: VpsProfile): Result<String> {
        val cmd = "if systemctl is-active --quiet vps-browser 2>/dev/null; then apt-get update -y && apt-get install --only-upgrade -y firefox xvfb openbox novnc websockify 2>/dev/null || true; systemctl restart vps-browser; echo 'Paquetes nativos actualizados'; elif [ -d /opt/vps-browser ]; then cd /opt/vps-browser && docker compose pull && docker compose up -d; fi"
        return executeCommand(profile, cmd)
    }

    suspend fun clearSessionCache(profile: VpsProfile): Result<String> {
        // Clears temporary caches while maintaining container / service integrity
        return executeCommand(profile, "rm -rf /opt/vps-browser/config/.cache/* ~/.cache/mozilla/* /tmp/.X11-unix/* 2>/dev/null || true")
    }

    suspend fun auditSecurity(profile: VpsProfile): Result<String> {
        val auditCmd = """
            echo "🛡️ INFORME DE AUDITORÍA DE SEGURIDAD:"
            echo -n "• Modo de Ejecución: " && (if systemctl is-active --quiet vps-browser 2>/dev/null; then echo "⚡ NATIVO VPS (Systemd / Sin Docker)"; elif docker ps -a --format '{{.Names}}' 2>/dev/null | grep -qw "vps-firefox"; then echo "🐳 DOCKER (Contenedor Aislado)"; else echo "No detectado"; fi)
            echo -n "• Estado del Servicio: " && (if systemctl is-active --quiet vps-browser 2>/dev/null; then echo "ACTIVO (systemd)"; else docker inspect -f '{{.State.Status}}' vps-firefox 2>/dev/null || echo "No iniciado"; fi)
            echo -n "• Autenticación Nginx: " && (if [ -f /etc/nginx/.htpasswd ] || ([ -f /opt/vps-browser/docker-compose.yml ] && grep -q 'PASSWORD=' /opt/vps-browser/docker-compose.yml); then echo "ACTIVA (Contraseña protegida)"; else echo "INSEGURA (Sin contraseña)"; fi)
            echo -n "• Hardening de Privacidad: " && (if grep -q 'DisableTelemetry' /opt/vps-browser/policies.json /etc/firefox/policies/policies.json /opt/firefox/distribution/policies.json 2>/dev/null; then echo "PROTEGIDO (Telemetría bloqueada)"; else echo "INCOMPLETO"; fi)
            echo -n "• Extensión uBlock Origin: " && (if grep -q 'uBlock0@raymondhill.net' /opt/vps-browser/policies.json /etc/firefox/policies/policies.json /opt/firefox/distribution/policies.json 2>/dev/null; then echo "INSTALADA Y ACTIVA"; else echo "NO ENCONTRADA"; fi)
            echo -n "• Control de Acceso Endpoint: " && (curl -s -o /dev/null -w "%{http_code}" --max-time 2 http://127.0.0.1:3000 2>/dev/null | grep -q '401' && echo "SEGURO (401 Authorization Requerida)" || echo "ACTIVO")
            echo -n "• Túnel Cloudflare: " && (if systemctl is-active --quiet vps-tunnel 2>/dev/null; then echo "ACTIVO (systemd)"; else docker inspect -f '{{.State.Status}}' vps-tunnel 2>/dev/null || echo "No activo"; fi)
        """.trimIndent()
        return executeCommand(profile, auditCmd)
    }

    suspend fun nukeAndDestroyEnvironment(profile: VpsProfile): Result<String> {
        // Destroys containers, services, and deletes all files from the VPS
        val nukeCmd = "systemctl stop vps-browser vps-tunnel 2>/dev/null || true; systemctl disable vps-browser vps-tunnel 2>/dev/null || true; cd /opt/vps-browser 2>/dev/null && docker compose down -v --remove-orphans 2>/dev/null || true; rm -rf /opt/vps-browser /etc/systemd/system/vps-browser.service /etc/systemd/system/vps-tunnel.service /etc/nginx/sites-enabled/vps-browser /etc/nginx/conf.d/vps-browser.conf"
        return executeCommand(profile, nukeCmd)
    }

    suspend fun fetchServerMetrics(profile: VpsProfile): Result<ServerMetrics> = withContext(Dispatchers.IO) {
        val checkCmd = "free -m | awk '/^Mem:/{print $3, $2}'; uptime | awk -F'load average:' '{print $2}'; (systemctl is-active --quiet vps-browser 2>/dev/null && echo 'Up (Nativo)' || docker ps --filter name=vps-firefox --format '{{.Status}}')"
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
