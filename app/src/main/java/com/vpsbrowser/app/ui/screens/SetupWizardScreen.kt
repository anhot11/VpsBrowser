package com.vpsbrowser.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.OpenInNew
import com.vpsbrowser.app.cloud.GitHubCodespacesManager
import com.vpsbrowser.app.security.SecurityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.text.font.FontWeight
import com.vpsbrowser.app.model.VpsProfile
import com.vpsbrowser.app.ssh.SshDeployer
import com.vpsbrowser.app.ui.theme.DarkBorder
import com.vpsbrowser.app.ui.theme.StatusGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(
    onBack: () -> Unit,
    onDeploymentSuccess: (VpsProfile) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var host by remember { mutableStateOf("") }
    var sshPort by remember { mutableStateOf("22") }
    var sshUser by remember { mutableStateOf("root") }
    var password by remember { mutableStateOf("") }
    var privateKey by remember { mutableStateOf("") }
    var authMode by remember { mutableIntStateOf(0) } // 0: Password, 1: Private Key

    var isPasswordVisible by remember { mutableStateOf(false) }

    val securityManager = remember { SecurityManager(context) }
    var setupType by remember { mutableIntStateOf(0) } // 0: Cloud Gratis (Codespaces), 1: Servidor VPS (SSH)
    var githubToken by remember { mutableStateOf(securityManager.getGitHubToken().orEmpty()) }
    var isTokenVisible by remember { mutableStateOf(false) }

    // Installation states
    var isDeploying by remember { mutableStateOf(false) }
    var deployStepTitle by remember { mutableStateOf("Conectando con tu servidor por SSH...") }
    var deployProgress by remember { mutableIntStateOf(0) }
    var terminalLogs by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var completedProfile by remember { mutableStateOf<VpsProfile?>(null) }

    // Deployment mode selection (Docker vs Native Bare-Metal)
    var showDeploymentModeDialog by remember { mutableStateOf(false) }
    var selectedDeployMode by remember { mutableStateOf("docker") } // "docker" or "native"
    var countdownSeconds by remember { mutableIntStateOf(5) }

    val terminalScrollState = rememberScrollState()

    val startDeployment: (mode: String) -> Unit = { mode ->
        showDeploymentModeDialog = false
        errorMessage = null
        isDeploying = true
        terminalLogs = ">>> Iniciando sesión SSH hacia $host en modo ${if (mode == "native") "⚡ NATIVO VPS (Sin Docker / Ultraligero)" else "🐳 DOCKER (Contenedor Aislado)"}...\n"

        scope.launch {
            val result = SshDeployer.executeDeploy(
                host = host.trim(),
                port = sshPort.toIntOrNull() ?: 22,
                user = sshUser.trim().ifBlank { "root" },
                password = if (authMode == 0) password else "",
                privateKey = if (authMode == 1) privateKey else "",
                browserEngine = "firefox",
                deployMode = mode,
                onProgress = { title, pct ->
                    scope.launch(Dispatchers.Main) {
                        deployStepTitle = title
                        deployProgress = pct
                    }
                },
                onLogLine = { line ->
                    scope.launch(Dispatchers.Main) {
                        terminalLogs += line + "\n"
                    }
                }
            )

            withContext(Dispatchers.Main) {
                result.onSuccess { profile ->
                    if (authMode == 1) {
                        profile.sshPrivateKey = privateKey
                    } else {
                        profile.sshPassword = password
                    }
                    profile.sshPort = sshPort.toIntOrNull() ?: 22
                    profile.sshUser = sshUser.trim().ifBlank { "root" }
                    deployStepTitle = "¡Configuración exitosa! Abriendo navegador..."
                    deployProgress = 100
                    Toast.makeText(context, "¡Configuración exitosa! Abriendo navegador...", Toast.LENGTH_SHORT).show()
                    completedProfile = profile
                    isDeploying = false
                    onDeploymentSuccess(profile)
                }.onFailure { err ->
                    isDeploying = false
                    errorMessage = "Error: ${err.message}"
                }
            }
        }
    }

    val startCloudDeployment: () -> Unit = {
        val token = githubToken.trim()
        if (token.isBlank()) {
            errorMessage = "Ingresa o pega tu token de GitHub con permiso 'codespace' para continuar."
        } else {
            errorMessage = null
            isDeploying = true
            terminalLogs = ">>> Iniciando conexión autónoma con GitHub Codespaces Cloud...\n"
            securityManager.saveGitHubToken(token)

            scope.launch {
                val result = GitHubCodespacesManager.orchestrateCloudBrowser(
                    token = token,
                    onProgress = { title, pct ->
                        scope.launch(Dispatchers.Main) {
                            deployStepTitle = title
                            deployProgress = pct
                        }
                    },
                    onLog = { line ->
                        scope.launch(Dispatchers.Main) {
                            terminalLogs += line + "\n"
                        }
                    }
                )

                withContext(Dispatchers.Main) {
                    result.onSuccess { profile ->
                        deployStepTitle = "¡Navegador Cloud listo! Abriendo navegador..."
                        deployProgress = 100
                        Toast.makeText(context, "¡Conexión exitosa con GitHub Codespaces!", Toast.LENGTH_SHORT).show()
                        completedProfile = profile
                        isDeploying = false
                        onDeploymentSuccess(profile)
                    }.onFailure { err ->
                        isDeploying = false
                        errorMessage = "Error Cloud: ${err.message}"
                    }
                }
            }
        }
    }

    // 5-second countdown timer for auto-selecting default mode (Docker)
    LaunchedEffect(showDeploymentModeDialog) {
        if (showDeploymentModeDialog) {
            countdownSeconds = 5
            while (countdownSeconds > 0 && showDeploymentModeDialog) {
                delay(1000L)
                countdownSeconds--
            }
            if (showDeploymentModeDialog) {
                // When 5s expires without user interaction, proceed with default Docker
                startDeployment("docker")
            }
        }
    }

    LaunchedEffect(terminalLogs) {
        terminalScrollState.animateScrollTo(terminalScrollState.maxValue)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Instalador Automático VPS") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!isDeploying && completedProfile == null) {
                // Selector de modo: Cloud Gratis vs Servidor Propio
                TabRow(
                    selectedTabIndex = setupType,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = setupType == 0,
                        onClick = {
                            setupType = 0
                            errorMessage = null
                        },
                        text = { Text("🚀 Cloud Gratis", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.CloudQueue, contentDescription = null) }
                    )
                    Tab(
                        selected = setupType == 1,
                        onClick = {
                            setupType = 1
                            errorMessage = null
                        },
                        text = { Text("🖥️ VPS Propia (SSH)", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.Dns, contentDescription = null) }
                    )
                }

                if (setupType == 0) {
                    // Modo Cloud Codespaces
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CloudQueue,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Navegador Cloud en GitHub (Gratis)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Cero configuración. La app levanta o reanuda tu navegador en la nube de GitHub sin tocar una sola terminal.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Highlights
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("⚡ 8 GB RAM", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text("2 Cores Azure", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("⏱️ 60 Horas", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text("Gratis al mes", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("🛡️ 0 Rastro", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text("Móvil invisible", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = githubToken,
                                onValueChange = { githubToken = it },
                                label = { Text("Token de GitHub (Personal Access Token)") },
                                placeholder = { Text("ghp_...") },
                                visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    Row {
                                        IconButton(onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            val clip = cm?.primaryClip?.getItemAt(0)?.text?.toString()
                                            if (!clip.isNullOrBlank()) {
                                                githubToken = clip.trim()
                                                Toast.makeText(context, "Token pegado", Toast.LENGTH_SHORT).show()
                                            }
                                        }) {
                                            Icon(Icons.Default.ContentPaste, contentDescription = "Pegar")
                                        }
                                        IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                                            Icon(
                                                if (isTokenVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = null
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val tokenUrl = "https://github.com/settings/tokens/new?scopes=codespace,repo&description=VPSBrowser-Cloud"
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(tokenUrl))
                                        context.startActivity(intent)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.OpenInNew,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "🔑 ¿No tienes token? Toca aquí para crearlo en 1 toque en GitHub (con permiso 'codespace').",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            errorMessage?.let { msg ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                ) {
                                    Text(
                                        text = msg,
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            Button(
                                onClick = startCloudDeployment,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Text("🚀 Iniciar Navegador Cloud Autónomo", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // Header Info Card (VPS SSH)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Instalación en 1 Toque (Sin Comandos)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "La app configurará automáticamente Docker, memoria SWAP, Firewall y Firefox con uBlock Origin preinstalado en tu VPS.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Form
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                    ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text("Dirección IP o Host del VPS") },
                            placeholder = { Text("ej: 198.51.100.1") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = sshUser,
                                onValueChange = { sshUser = it },
                                label = { Text("Usuario SSH") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = sshPort,
                                onValueChange = { sshPort = it },
                                label = { Text("Puerto SSH") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(110.dp),
                                singleLine = true
                            )
                        }

                        // Auth mode tabs (Password vs Private Key)
                        TabRow(selectedTabIndex = authMode) {
                            Tab(
                                selected = authMode == 0,
                                onClick = { authMode = 0 },
                                text = { Text("Contraseña") },
                                icon = { Icon(Icons.Default.Password, contentDescription = null) }
                            )
                            Tab(
                                selected = authMode == 1,
                                onClick = { authMode = 1 },
                                text = { Text("Clave Privada SSH") },
                                icon = { Icon(Icons.Default.Key, contentDescription = null) }
                            )
                        }

                        if (authMode == 0) {
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Contraseña SSH") },
                                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                        Icon(
                                            if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = null
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        } else {
                            OutlinedTextField(
                                value = privateKey,
                                onValueChange = { privateKey = it },
                                label = { Text("Pega tu Clave Privada (id_rsa / id_ed25519)") },
                                placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY-----...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp),
                                maxLines = 6
                            )
                        }

                        errorMessage?.let { msg ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Detalle del error:",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                cm?.setPrimaryClip(ClipData.newPlainText("Error VPS", msg))
                                                Toast.makeText(context, "Error copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = "Copiar error",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    SelectionContainer {
                                        Text(
                                            text = msg,
                                            color = MaterialTheme.colorScheme.error,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Mode info hint
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Modos: 🐳 Docker (Aislado / Default) o ⚡ Nativo VPS (Ultraligero). Al pulsar iniciar, tendrás 5s para elegir.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (host.isBlank()) {
                                    errorMessage = "Ingresa la dirección IP de tu VPS"
                                    return@Button
                                }
                                if (authMode == 0 && password.isBlank()) {
                                    errorMessage = "Ingresa la contraseña SSH de tu VPS"
                                    return@Button
                                }
                                if (authMode == 1 && privateKey.isBlank()) {
                                    errorMessage = "Pega tu clave privada SSH"
                                    return@Button
                                }

                                errorMessage = null
                                selectedDeployMode = "docker"
                                countdownSeconds = 5
                                showDeploymentModeDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text("🚀 Iniciar Instalación Automática")
                        }
                    }
                }
            }
            }

            // Modal Dialog: 5-Second Mode Selection (Docker default vs Native VPS)
            if (showDeploymentModeDialog) {
                AlertDialog(
                    onDismissRequest = { showDeploymentModeDialog = false },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    },
                    title = {
                        Text(
                            text = "¿Cómo deseas instalar en tu VPS?",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Countdown Banner
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "⏱️ Continuando con Docker automáticamente en: ${countdownSeconds}s",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { countdownSeconds / 5f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Toca tu opción preferida o espera a que inicie con Docker:",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Option 1: Docker (Default)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedDeployMode == "docker") MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(
                                    if (selectedDeployMode == "docker") 2.dp else 1.dp,
                                    if (selectedDeployMode == "docker") MaterialTheme.colorScheme.primary else DarkBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedDeployMode = "docker"
                                        startDeployment("docker")
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "🐳 Docker (Contenedor)",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = "PREDETERMINADO",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Aislamiento total y máxima compatibilidad. No altera paquetes del host.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }

                            // Option 2: Native VPS (Ultra-lightweight)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedDeployMode == "native") MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(
                                    if (selectedDeployMode == "native") 2.dp else 1.dp,
                                    if (selectedDeployMode == "native") MaterialTheme.colorScheme.tertiary else DarkBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedDeployMode = "native"
                                        startDeployment("native")
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "⚡ Nativo VPS (Sin Docker)",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = "MÁXIMO RENDIMIENTO",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.tertiary,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Ahorra 70% de RAM y 80% de disco. Sin sobrecarga de Docker. Ideal para VPS de 512MB/1GB.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { startDeployment(selectedDeployMode) }
                        ) {
                            Text("Continuar con Docker (${countdownSeconds}s)")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showDeploymentModeDialog = false }
                        ) {
                            Text("Cancelar")
                        }
                    }
                )
            }

            // Deployment Progress & Terminal Output
            if (isDeploying || completedProfile != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isDeploying) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = StatusGreen)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (completedProfile != null) "¡Instalación Exitosa! Abriendo navegador..." else "Configurando tu VPS...",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (completedProfile != null) StatusGreen else MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(deployStepTitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { deployProgress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Terminal de instalación en vivo", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (terminalLogs.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Terminal Logs", terminalLogs)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Logs copiados al portapapeles", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copiar logs", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copiar logs", fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        // Terminal Console Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .background(Color(0xFF090D13), RoundedCornerShape(8.dp))
                                .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                                .verticalScroll(terminalScrollState)
                        ) {
                            SelectionContainer {
                                Text(
                                    text = terminalLogs.ifBlank { "Esperando salida del servidor..." },
                                    color = Color(0xFF39D353),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        if (completedProfile != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            if (completedProfile!!.cloudflareUrl.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("☁️ Túnel Cloudflare HTTPS Activo (Sin abrir puertos):", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                            SelectionContainer {
                                                Text(completedProfile!!.cloudflareUrl, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("Cloudflare URL", completedProfile!!.cloudflareUrl)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "URL de Cloudflare copiada", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = "Copiar URL",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .clickable { onDeploymentSuccess(completedProfile!!) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Abriendo navegador de inmediato...", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
