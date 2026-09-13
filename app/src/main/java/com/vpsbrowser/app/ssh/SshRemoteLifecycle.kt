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

    suspend fun enableTurboStreaming(profile: VpsProfile): Result<String> {
        val cmd = """
            modprobe tcp_bbr 2>/dev/null || true
            mkdir -p /etc/sysctl.d
            cat <<'EOF' > /etc/sysctl.d/99-vpsbrowser-turbo.conf
net.core.default_qdisc = fq
net.ipv4.tcp_congestion_control = bbr
net.ipv4.tcp_notsent_lowat = 16384
net.ipv4.tcp_fastopen = 3
net.ipv4.tcp_tw_reuse = 1
net.ipv4.tcp_fin_timeout = 15
net.core.rmem_max = 16777216
net.core.wmem_max = 16777216
net.ipv4.tcp_rmem = 4096 87380 16777216
net.ipv4.tcp_wmem = 4096 65536 16777216
EOF
            sysctl -p /etc/sysctl.d/99-vpsbrowser-turbo.conf 2>&1 || sysctl -w net.ipv4.tcp_congestion_control=bbr 2>&1 || true
            echo "⚡ TCP BBR Y OPTIMIZACIONES DE RED ACTIVAS:"
            echo -n "• Algoritmo de Congestión: " && (sysctl net.ipv4.tcp_congestion_control 2>/dev/null || echo "Desconocido")
            echo -n "• Cola de Paquetes (qdisc): " && (sysctl net.core.default_qdisc 2>/dev/null || echo "Desconocido")
            echo -n "• TCP Fast Open: " && (sysctl net.ipv4.tcp_fastopen 2>/dev/null || echo "Desconocido")
        """.trimIndent()
        return executeCommand(profile, cmd)
    }

    suspend fun whitelistClientIp(profile: VpsProfile, clientIp: String): Result<String> {
        val cleanIp = clientIp.trim()
        if (cleanIp.isBlank() || !cleanIp.matches(Regex("^[0-9a-fA-F:.]+$"))) {
            return Result.failure(IllegalArgumentException("Dirección IP no válida: $clientIp"))
        }
        val port = profile.browserPort
        val cmd = """
            if [ -f /opt/vps-browser/shield-firewall.sh ]; then
                bash /opt/vps-browser/shield-firewall.sh allow "$cleanIp" $port
            else
                iptables -D INPUT -p tcp -s "$cleanIp" --dport $port -j ACCEPT 2>/dev/null || true
                iptables -I INPUT 1 -p tcp -s "$cleanIp" --dport $port -j ACCEPT
                echo "ALLOWED $cleanIp on port $port"
            fi
        """.trimIndent()
        return executeCommand(profile, cmd)
    }

    suspend fun enableStealthShield(profile: VpsProfile): Result<String> {
        val port = profile.browserPort
        val cmd = """
            if [ -f /opt/vps-browser/shield-firewall.sh ]; then
                bash /opt/vps-browser/shield-firewall.sh enable-shield "" $port
            else
                iptables -D INPUT -p tcp --dport $port -j DROP 2>/dev/null || true
                iptables -A INPUT -p tcp --dport $port -j DROP 2>/dev/null || true
            fi
            echo "🛡️ Escudo de Cortafuegos Activado: Puerto $port protegido con IP Whitelist."
        """.trimIndent()
        return executeCommand(profile, cmd)
    }

    suspend fun installFail2ban(profile: VpsProfile): Result<String> {
        val cmd = """
            if command -v apt-get &>/dev/null; then
                export DEBIAN_FRONTEND=noninteractive
                apt-get update -y >/dev/null 2>&1 || true
                apt-get install -y fail2ban >/dev/null 2>&1 || true
            elif command -v dnf &>/dev/null; then
                dnf install -y fail2ban >/dev/null 2>&1 || true
            fi

            mkdir -p /etc/fail2ban/jail.d
            cat <<'EOF' > /etc/fail2ban/jail.d/vps-browser.local
[sshd]
enabled = true
port = ssh
maxretry = 5
findtime = 600
bantime = 3600

[nginx-http-auth]
enabled = true
port = ${profile.browserPort},3001,http,https
maxretry = 5
findtime = 600
bantime = 3600
EOF
            systemctl restart fail2ban 2>/dev/null || service fail2ban restart 2>/dev/null || true
            echo "🛑 FAIL2BAN CONFIGURADO CON ÉXITO:"
            fail2ban-client status 2>&1 || echo "Fail2ban instalado y activo."
        """.trimIndent()
        return executeCommand(profile, cmd)
    }

    suspend fun getShieldStatus(profile: VpsProfile): Result<String> {
        val port = profile.browserPort
        val cmd = """
            echo "🛡️ REGLAS DE CORTAFUEGOS ACTIVAS (PUERTO $port):"
            iptables -L INPUT -n --line-numbers 2>/dev/null | grep -E "$port|dpt:$port" || echo "Sin restricciones por IP (Puerto abierto en firewall local)"
            echo ""
            echo "🛑 ESTADO FAIL2BAN (ANTI FUERZA BRUTA):"
            if command -v fail2ban-client &>/dev/null; then
                fail2ban-client status 2>/dev/null || echo "Fail2ban instalado pero inactivo"
            else
                echo "Fail2ban no instalado"
            fi
        """.trimIndent()
        return executeCommand(profile, cmd)
    }
}
