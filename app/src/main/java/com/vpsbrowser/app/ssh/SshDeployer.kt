package com.vpsbrowser.app.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.vpsbrowser.app.model.VpsProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object SshDeployer {

    suspend fun executeDeploy(
        host: String,
        port: Int = 22,
        user: String = "root",
        password: String = "",
        privateKey: String = "",
        browserEngine: String = "firefox",
        onProgress: (stepTitle: String, percentage: Int) -> Unit,
        onLogLine: (line: String) -> Unit
    ): Result<VpsProfile> = withContext(Dispatchers.IO) {
        val jsch = JSch()
        var session: com.jcraft.jsch.Session? = null

        try {
            onProgress("Conectando con tu servidor por SSH...", 5)
            onLogLine(">>> Conectando a $user@$host:$port vía SSH...")

            if (privateKey.isNotBlank()) {
                jsch.addIdentity("deploy_key", privateKey.toByteArray(), null, null)
            }

            session = jsch.getSession(user, host, port).apply {
                if (privateKey.isBlank()) {
                    setPassword(password)
                }
                setConfig("StrictHostKeyChecking", "no")
                setConfig("PreferredAuthentications", if (privateKey.isNotBlank()) "publickey,password" else "password,keyboard-interactive")
                serverAliveInterval = 5000 // KeepAlive every 5s prevents mobile carrier/NAT drops
                serverAliveCountMax = 120 // Up to 10 minutes tolerance
                timeout = 600000 // 10 minutes socket read timeout
                connect(25000)
            }

            onProgress("Conexión SSH establecida. Iniciando instalador...", 10)
            onLogLine(">>> Conectado exitosamente por SSH. Ejecutando instalador universal...")

            val channel = session.openChannel("exec") as ChannelExec

            // Command executes deployment script with stderr merged into stdout (2>&1)
            // This prevents OS pipe deadlock and guarantees all logs/errors stream in real-time
            val remoteCmd = "curl -fsSL https://raw.githubusercontent.com/anhot11/VpsBrowser/main/server/deploy.sh | bash 2>&1"
            channel.setCommand(remoteCmd)
            channel.setErrStream(System.err)

            val inputStream = channel.inputStream
            channel.connect(15000)

            val reader = BufferedReader(InputStreamReader(inputStream))

            var detectedUser = "admin"
            var detectedPassword = ""
            var detectedCloudflareUrl = ""
            var currentPercentage = 10

            var line: String? = reader.readLine()
            while (line != null) {
                val cleanLine = line.replace(Regex("\u001B\\[[;?0-9]*[a-zA-Z]"), "").replace("\r", "").trim()
                if (cleanLine.isNotBlank()) {
                    onLogLine(cleanLine)
                }

                // Track and parse progress
                when {
                    cleanLine.contains("[DETECCIÓN]") -> {
                        currentPercentage = 25
                        onProgress("Auditando seguridad de instancia previa...", currentPercentage)
                    }
                    cleanLine.contains("100% SEGURA") -> {
                        currentPercentage = 95
                        onProgress("🛡️ Instancia previa verificada como 100% segura", currentPercentage)
                    }
                    cleanLine.contains("[1/7]") -> {
                        currentPercentage = 20
                        onProgress("Paso 1/7: Detectando sistema operativo y CPU...", currentPercentage)
                    }
                    cleanLine.contains("[2/7]") -> {
                        currentPercentage = 35
                        onProgress("Paso 2/7: Instalando utilidades del sistema...", currentPercentage)
                    }
                    cleanLine.contains("[3/7]") -> {
                        currentPercentage = 50
                        onProgress("Paso 3/7: Optimizando memoria RAM, SWAP y disco...", currentPercentage)
                    }
                    cleanLine.contains("[4/7]") -> {
                        currentPercentage = 65
                        onProgress("Paso 4/7: Configurando entorno Docker...", currentPercentage)
                    }
                    cleanLine.contains("[5/7]") -> {
                        currentPercentage = 75
                        onProgress("Paso 5/7: Abriendo puertos 3000/3001 en Firewall...", currentPercentage)
                    }
                    cleanLine.contains("[6/7]") -> {
                        currentPercentage = 85
                        onProgress("Paso 6/7: Optimizando Firefox y Cloudflare Tunnel...", currentPercentage)
                    }
                    cleanLine.contains("[7/7]") -> {
                        currentPercentage = 90
                        onProgress("Paso 7/7: Descargando y levantando navegador...", currentPercentage)
                    }
                    cleanLine.contains("⏳") || cleanLine.contains("Procesando y extrayendo") -> {
                        currentPercentage = 95
                        onProgress("Paso 7/7: Descomprimiendo capas en Docker...", currentPercentage)
                    }
                    cleanLine.contains("Usuario:") -> {
                        detectedUser = cleanLine.substringAfter("Usuario:").trim().ifBlank { "admin" }
                    }
                    cleanLine.contains("Contraseña / Token:") -> {
                        detectedPassword = cleanLine.substringAfter("Contraseña / Token:").trim()
                    }
                    cleanLine.contains("trycloudflare.com") -> {
                        val match = Regex("https://[a-zA-Z0-9.-]+\\.trycloudflare\\.com").find(cleanLine)
                        if (match != null) {
                            detectedCloudflareUrl = match.value
                        }
                    }
                    cleanLine.contains("INSTALADO CON ÉXITO") -> {
                        currentPercentage = 100
                        onProgress("¡Navegador VPS listo para usar!", 100)
                    }
                }

                line = reader.readLine()
            }

            while (!channel.isClosed) {
                Thread.sleep(200)
            }

            val exitStatus = channel.exitStatus
            channel.disconnect()

            if (exitStatus != 0) {
                return@withContext Result.failure(
                    Exception("El instalador finalizó con código de error $exitStatus. Revisa los logs de la terminal para ver el detalle.")
                )
            }

            val profile = VpsProfile(
                name = "VPS Firefox ($host)",
                host = host,
                sshPort = port,
                sshUser = user,
                sshPassword = password,
                sshPrivateKey = privateKey,
                useSshTunnel = true,
                cloudflareUrl = detectedCloudflareUrl,
                useCloudflareTunnel = false,
                browserPort = 3000,
                browserUser = detectedUser.ifBlank { "admin" },
                browserPassword = detectedPassword,
                useSsl = false,
                touchEmulation = true
            )

            Result.success(profile)
        } catch (e: Exception) {
            onLogLine(">>> ERROR: ${e.localizedMessage}")
            Result.failure(e)
        } finally {
            try {
                session?.disconnect()
            } catch (e: Exception) {
                // Ignore disconnect errors
            }
        }
    }
}
