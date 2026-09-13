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
        password: String,
        browserEngine: String = "firefox",
        onProgress: (stepTitle: String, percentage: Int) -> Unit,
        onLogLine: (line: String) -> Unit
    ): Result<VpsProfile> = withContext(Dispatchers.IO) {
        val jsch = JSch()
        var session: com.jcraft.jsch.Session? = null

        try {
            onProgress("Conectando con tu servidor por SSH...", 5)
            onLogLine(">>> Conectando a $user@$host:$port vía SSH...")

            session = jsch.getSession(user, host, port).apply {
                setPassword(password)
                setConfig("StrictHostKeyChecking", "no")
                setConfig("PreferredAuthentications", "password,keyboard-interactive")
                connect(20000) // 20s connection timeout
            }

            onProgress("Conexión SSH establecida. Iniciando instalador...", 10)
            onLogLine(">>> Conectado exitosamente por SSH. Ejecutando instalador universal...")

            val channel = session.openChannel("exec") as ChannelExec

            // Command executes the smart universal deployment script directly on the VPS
            val remoteCmd = "curl -fsSL https://raw.githubusercontent.com/anhot11/VpsBrowser/main/server/deploy.sh | bash"
            channel.setCommand(remoteCmd)
            channel.setErrStream(System.err)

            val inputStream = channel.inputStream
            val errorStream = channel.errStream
            channel.connect(15000)

            val reader = BufferedReader(InputStreamReader(inputStream))
            val errorReader = BufferedReader(InputStreamReader(errorStream))

            var detectedPassword = ""
            var currentPercentage = 10

            var line: String? = reader.readLine()
            while (line != null) {
                val cleanLine = line.replace(Regex("\u001B\\[[;\\d]*m"), "")
                onLogLine(cleanLine)

                // Track and parse progress
                when {
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
                        onProgress("Paso 3/7: Optimizando memoria RAM y SWAP...", currentPercentage)
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
                        onProgress("Paso 6/7: Generando credenciales seguras...", currentPercentage)
                    }
                    cleanLine.contains("[7/7]") -> {
                        currentPercentage = 92
                        onProgress("Paso 7/7: Descargando y levantando navegador...", currentPercentage)
                    }
                    cleanLine.contains("Contraseña / Token:") -> {
                        val parts = cleanLine.split(":")
                        if (parts.size >= 2) {
                            detectedPassword = parts[1].trim()
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
                val errText = StringBuilder()
                var errLine = errorReader.readLine()
                while (errLine != null) {
                    errText.append(errLine).append("\n")
                    errLine = errorReader.readLine()
                }
                return@withContext Result.failure(
                    Exception("El instalador finalizó con código $exitStatus. ${errText.toString().take(300)}")
                )
            }

            val profile = VpsProfile(
                name = "VPS Firefox ($host)",
                host = host,
                port = 3000,
                password = detectedPassword,
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
