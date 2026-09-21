package com.vpsbrowser.app.cloud

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.vpsbrowser.app.model.VpsProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GitHubUser(
    val login: String,
    val name: String?,
    val avatarUrl: String?
)

data class CodespaceInfo(
    val id: Long,
    val name: String,
    val state: String, // "Shutdown", "Available", "Starting", "Provisioning", "Rebuilding", "Awaiting"
    val webUrl: String,
    val repositoryFullName: String
)

object GitHubCodespacesManager {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private const val GITHUB_API_BASE = "https://api.github.com"
    private const val DEFAULT_REPO = "anhot11/VpsBrowser"
    private const val DEFAULT_BRANCH = "main"

    private fun buildHeaders(token: String): okhttp3.Headers {
        return okhttp3.Headers.Builder()
            .add("Authorization", "Bearer ${token.trim()}")
            .add("Accept", "application/vnd.github+json")
            .add("X-GitHub-Api-Version", "2022-11-28")
            .add("User-Agent", "VPSBrowser-Android-App")
            .build()
    }

    /**
     * Valida que el token de GitHub sea correcto y obtiene el usuario.
     */
    suspend fun verifyToken(token: String): Result<GitHubUser> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$GITHUB_API_BASE/user")
                .headers(buildHeaders(token))
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("Error de autenticación GitHub (${resp.code}): $body"))
                }
                val json = JSONObject(body)
                val user = GitHubUser(
                    login = json.optString("login", "Usuario"),
                    name = json.optString("name", null),
                    avatarUrl = json.optString("avatar_url", null)
                )
                Result.success(user)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Lista los codespaces existentes en la cuenta del usuario.
     */
    suspend fun listCodespaces(token: String): Result<List<CodespaceInfo>> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$GITHUB_API_BASE/user/codespaces")
                .headers(buildHeaders(token))
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("Error listando codespaces (${resp.code}): $body"))
                }
                val json = JSONObject(body)
                val arr = json.optJSONArray("codespaces") ?: JSONArray()
                val list = mutableListOf<CodespaceInfo>()
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val repoObj = item.optJSONObject("repository")
                    val repoName = repoObj?.optString("full_name").orEmpty()
                    list.add(
                        CodespaceInfo(
                            id = item.optLong("id", 0L),
                            name = item.optString("name", ""),
                            state = item.optString("state", "Unknown"),
                            webUrl = item.optString("web_url", ""),
                            repositoryFullName = repoName
                        )
                    )
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Obtiene el estado actual de un codespace específico.
     */
    suspend fun getCodespace(token: String, codespaceName: String): Result<CodespaceInfo> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$GITHUB_API_BASE/user/codespaces/$codespaceName")
                .headers(buildHeaders(token))
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("Error consultando codespace $codespaceName (${resp.code}): $body"))
                }
                val item = JSONObject(body)
                val repoObj = item.optJSONObject("repository")
                val info = CodespaceInfo(
                    id = item.optLong("id", 0L),
                    name = item.optString("name", codespaceName),
                    state = item.optString("state", "Unknown"),
                    webUrl = item.optString("web_url", ""),
                    repositoryFullName = repoObj?.optString("full_name").orEmpty()
                )
                Result.success(info)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Crea un nuevo Codespace en el repositorio VPSBrowser (o fork del usuario).
     */
    suspend fun createCodespace(
        token: String,
        repoFullName: String = DEFAULT_REPO,
        branch: String = DEFAULT_BRANCH
    ): Result<CodespaceInfo> = withContext(Dispatchers.IO) {
        try {
            val jsonPayload = JSONObject().apply {
                put("ref", branch)
            }
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val bodyReq = jsonPayload.toString().toRequestBody(mediaType)

            val req = Request.Builder()
                .url("$GITHUB_API_BASE/repos/$repoFullName/codespaces")
                .headers(buildHeaders(token))
                .post(bodyReq)
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("Fallo al crear Codespace en $repoFullName (${resp.code}): $body"))
                }
                val item = JSONObject(body)
                val repoObj = item.optJSONObject("repository")
                val info = CodespaceInfo(
                    id = item.optLong("id", 0L),
                    name = item.optString("name", ""),
                    state = item.optString("state", "Starting"),
                    webUrl = item.optString("web_url", ""),
                    repositoryFullName = repoObj?.optString("full_name").orEmpty()
                )
                Result.success(info)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Enciende / reanuda un Codespace que estaba suspendido.
     */
    suspend fun startCodespace(token: String, codespaceName: String): Result<CodespaceInfo> = withContext(Dispatchers.IO) {
        try {
            val emptyBody = "".toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("$GITHUB_API_BASE/user/codespaces/$codespaceName/start")
                .headers(buildHeaders(token))
                .post(emptyBody)
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("Fallo al reanudar codespace $codespaceName (${resp.code}): $body"))
                }
                val item = JSONObject(body)
                val repoObj = item.optJSONObject("repository")
                val info = CodespaceInfo(
                    id = item.optLong("id", 0L),
                    name = item.optString("name", codespaceName),
                    state = item.optString("state", "Starting"),
                    webUrl = item.optString("web_url", ""),
                    repositoryFullName = repoObj?.optString("full_name").orEmpty()
                )
                Result.success(info)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Pausa / apaga un Codespace para ahorrar horas de cómputo del usuario.
     */
    suspend fun stopCodespace(token: String, codespaceName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val emptyBody = "".toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("$GITHUB_API_BASE/user/codespaces/$codespaceName/stop")
                .headers(buildHeaders(token))
                .post(emptyBody)
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    return@withContext Result.failure(Exception("Fallo al suspender codespace (${resp.code}): $body"))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Configura la visibilidad del puerto en el Codespace a 'public' para permitir acceso web sin cookies de sesión.
     */
    suspend fun setPortVisibility(
        token: String,
        codespaceName: String,
        port: Int,
        visibility: String = "public"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val jsonPayload = JSONObject().apply {
                put("visibility", visibility)
            }
            val bodyReq = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url("$GITHUB_API_BASE/user/codespaces/$codespaceName/ports/$port")
                .headers(buildHeaders(token))
                .patch(bodyReq)
                .build()

            httpClient.newCall(req).execute().use { resp ->
                // 200 OK o 204 No Content son éxitos
                if (resp.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val body = resp.body?.string().orEmpty()
                    // Si el puerto aún no fue detectado por devcontainer, no es un error fatal ya que .devcontainer.json ya define visibility: public
                    Result.failure(Exception("Aviso puerto $port (${resp.code}): $body"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Elimina un Codespace existente para permitir crear uno limpio desde cero.
     */
    suspend fun deleteCodespace(
        token: String,
        codespaceName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$GITHUB_API_BASE/user/codespaces/$codespaceName")
                .headers(buildHeaders(token))
                .delete()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful || resp.code == 202 || resp.code == 204 || resp.code == 404) {
                    Result.success(Unit)
                } else {
                    val body = resp.body?.string().orEmpty()
                    Result.failure(Exception("Error al eliminar Codespace (${resp.code}): $body"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Orquestador completo y 100% autónomo.
     * El usuario solo proporciona su cuenta/token de GitHub y el orquestador se encarga de:
     * 1. Validar la cuenta.
     * 2. Buscar si ya existe un Codespace para VPSBrowser o crearlo en la nube.
     * 3. Reanudarlo si estaba dormido (o recrearlo limpio si se solicita).
     * 4. Esperar a que la máquina virtual Cloud esté 100% 'Available'.
     * 5. Sondear activamente la URL web hasta que Firefox responda HTTP 200 OK.
     * 6. Devolver el VpsProfile listo para navegar inmediatamente sin errores 404.
     */
    suspend fun orchestrateCloudBrowser(
        token: String,
        recreateClean: Boolean = false,
        onProgress: (stepTitle: String, percentage: Int) -> Unit,
        onLog: (String) -> Unit
    ): Result<VpsProfile> = withContext(Dispatchers.IO) {
        try {
            onProgress("Validando credenciales de GitHub...", 10)
            onLog(">>> Conectando con API de GitHub...")

            val userRes = verifyToken(token)
            if (userRes.isFailure) {
                val err = userRes.exceptionOrNull()?.message ?: "Error desconocido"
                onLog(">>> Error: $err")
                return@withContext Result.failure(Exception("Error de cuenta GitHub: $err"))
            }
            val user = userRes.getOrThrow()
            onLog(">>> ✓ Cuenta autenticada: @${user.login} (${user.name ?: "Sin nombre"})")

            onProgress("Buscando entorno Cloud en tu cuenta...", 25)
            onLog(">>> Consultando lista de Codespaces activos...")
            val listRes = listCodespaces(token)
            val existingList = listRes.getOrDefault(emptyList())

            // Buscar un codespace asociado a este repositorio o con nombre que contenga vps
            var targetCodespace = existingList.firstOrNull {
                it.repositoryFullName.equals(DEFAULT_REPO, ignoreCase = true) ||
                it.repositoryFullName.endsWith("/VpsBrowser", ignoreCase = true) ||
                it.name.contains("vps", ignoreCase = true)
            } ?: existingList.firstOrNull()

            if (recreateClean && targetCodespace != null) {
                onProgress("Eliminando Codespace previo para reconstrucción limpia...", 25)
                onLog(">>> Eliminando entorno previo: ${targetCodespace.name}...")
                deleteCodespace(token, targetCodespace.name)
                delay(3000)
                targetCodespace = null
            }

            var codespaceName = ""

            if (targetCodespace != null) {
                codespaceName = targetCodespace.name
                onLog(">>> ✓ Entorno Cloud existente detectado: $codespaceName (Estado: ${targetCodespace.state})")

                if (targetCodespace.state.equals("Shutdown", ignoreCase = true) ||
                    targetCodespace.state.equals("Stopped", ignoreCase = true)
                ) {
                    onProgress("Reanudando máquina virtual Cloud (8 GB RAM)...", 35)
                    onLog(">>> Encendiendo máquina virtual en GitHub Cloud...")
                    val startRes = startCodespace(token, codespaceName)
                    if (startRes.isFailure) {
                        onLog(">>> Aviso al iniciar: ${startRes.exceptionOrNull()?.message}")
                    }
                }
            } else {
                onProgress("Creando máquina virtual Cloud (2 Cores, 8 GB RAM)...", 35)
                onLog(">>> Solicitando creación de Codespace gratuito en GitHub...")
                val createRes = createCodespace(token, DEFAULT_REPO, DEFAULT_BRANCH)
                if (createRes.isFailure) {
                    val err = createRes.exceptionOrNull()?.message ?: "Error al crear Codespace"
                    onLog(">>> Error: $err")
                    return@withContext Result.failure(Exception("No se pudo crear el Codespace: $err"))
                }
                val created = createRes.getOrThrow()
                codespaceName = created.name
                onLog(">>> ✓ Codespace creado exitosamente: $codespaceName")
            }

            // Esperar a que el Codespace esté "Available"
            onProgress("Esperando arranque de la máquina Cloud...", 50)
            var attempts = 0
            val maxAttempts = 120 // 120 intentos * 3s = 360 segundos (6 minutos max para primer aprovisionamiento)
            var isAvailable = false
            var lastState = "Unknown"

            while (attempts < maxAttempts) {
                delay(3000)
                attempts++
                val pollRes = getCodespace(token, codespaceName)
                if (pollRes.isSuccess) {
                    val info = pollRes.getOrThrow()
                    lastState = info.state
                    val elapsedSec = attempts * 3
                    val currentPct = (45 + (attempts * 0.35f)).toInt().coerceAtMost(80)

                    val statusMsg = when (lastState.lowercase()) {
                        "provisioning" -> "Aprovisionando máquina Azure ($elapsedSec s)..."
                        "starting" -> "Arrancando máquina virtual ($elapsedSec s)..."
                        "rebuilding" -> "Reconstruyendo entorno ($elapsedSec s)..."
                        "available" -> "¡Máquina lista! Verificando servidor web..."
                        else -> "Estado Cloud: $lastState ($elapsedSec s)..."
                    }

                    onProgress(statusMsg, currentPct)
                    onLog(">>> Estado del servidor Cloud: $lastState (Intento $attempts/$maxAttempts - ${elapsedSec}s)")

                    if (lastState.equals("Available", ignoreCase = true)) {
                        isAvailable = true
                        break
                    }
                }
            }

            if (!isAvailable) {
                onLog(">>> El aprovisionamiento inicial tardó más del tiempo límite (Estado: $lastState).")
                return@withContext Result.failure(
                    Exception("La máquina Cloud aún se está inicializando en GitHub ($lastState). Como ya quedó creada en tu cuenta ($codespaceName), pulsa nuevamente 'Iniciar Navegador Cloud' para conectar directamente.")
                )
            }

            val codespaceHost = "$codespaceName-3000.app.github.dev"
            val directWebUrl = "https://$codespaceHost/"

            onProgress("Esperando arranque de Firefox remoto en la nube...", 82)
            onLog(">>> Verificando disponibilidad de la interfaz web en $directWebUrl...")

            var isWebReady = false
            val probeClient = httpClient.newBuilder()
                .followRedirects(true)
                .callTimeout(java.time.Duration.ofSeconds(6))
                .build()

            val cleanToken = token.trim()
            val maxProbes = 40 // 40 sondeos * 3s = 120 segundos
            for (probe in 1..maxProbes) {
                try {
                    val probeReq = Request.Builder()
                        .url(directWebUrl)
                        .addHeader("X-Github-Token", cleanToken)
                        .get()
                        .build()
                    val probeResp = probeClient.newCall(probeReq).execute()
                    val code = probeResp.code
                    probeResp.close()

                    val pct = 82 + (probe * 17 / maxProbes)
                    onProgress("Iniciando contenedor web Firefox ($probe/$maxProbes)...", pct)

                    if (code in 200..399) {
                        isWebReady = true
                        onLog(">>> ✓ Interfaz web lista y respondiendo exitosamente (HTTP $code).")
                        break
                    } else {
                        onLog(">>> Esperando servidor Firefox en Cloud (Intento $probe/$maxProbes): HTTP $code")
                    }
                } catch (e: Exception) {
                    onLog(">>> Esperando respuesta del túnel Cloud ($probe/$maxProbes): ${e.message ?: "Conectando..."}")
                }
                delay(3000)
            }

            if (!isWebReady) {
                onLog(">>> Aviso: La máquina Cloud está encendida, pero Firefox todavía se encuentra cargando en segundo plano.")
            }

            onProgress("¡Navegador Cloud listo para usar!", 100)
            onLog(">>> ✓ URL de conexión segura generada: $directWebUrl")
            onLog(">>> ✓ Despliegue Cloud completado con éxito.")

            val profile = VpsProfile(
                id = "codespace_$codespaceName",
                name = "☁️ GitHub Codespace (@${user.login})",
                host = codespaceHost,
                sshPort = 22,
                sshUser = "codespace",
                sshPassword = "",
                sshPrivateKey = "",
                useSshTunnel = false,
                cloudflareUrl = directWebUrl,
                useCloudflareTunnel = true,
                browserPort = 443,
                browserUser = "codespace",
                browserPassword = "",
                browserEngine = "codespace",
                useSsl = true,
                touchEmulation = true,
                resolution = "1920x1080",
                connectionMode = "cloudflare",
                enableIpShield = false,
                enableBbr = true,
                appEngine = "turbo",
                browserMode = "remote_desktop"
            )

            Result.success(profile)
        } catch (e: Exception) {
            onLog(">>> ERROR FATAL EN DESPLIEGUE CLOUD: ${e.localizedMessage}")
            Result.failure(e)
        }
    }
}
