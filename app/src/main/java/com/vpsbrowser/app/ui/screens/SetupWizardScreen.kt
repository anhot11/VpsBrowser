package com.vpsbrowser.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Terminal
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
import com.vpsbrowser.app.model.VpsProfile
import com.vpsbrowser.app.ssh.SshDeployer
import com.vpsbrowser.app.ui.theme.DarkBorder
import com.vpsbrowser.app.ui.theme.StatusGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(
    onBack: () -> Unit,
    onDeploymentSuccess: (VpsProfile) -> Unit
) {
    val scope = rememberCoroutineScope()

    var host by remember { mutableStateOf("") }
    var sshPort by remember { mutableStateOf("22") }
    var sshUser by remember { mutableStateOf("root") }
    var password by remember { mutableStateOf("") }
    var privateKey by remember { mutableStateOf("") }
    var authMode by remember { mutableIntStateOf(0) } // 0: Password, 1: Private Key

    var isPasswordVisible by remember { mutableStateOf(false) }

    // Installation states
    var isDeploying by remember { mutableStateOf(false) }
    var deployStepTitle by remember { mutableStateOf("Conectando con tu servidor por SSH...") }
    var deployProgress by remember { mutableIntStateOf(0) }
    var terminalLogs by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var completedProfile by remember { mutableStateOf<VpsProfile?>(null) }

    val terminalScrollState = rememberScrollState()

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
            // Header Info Card
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

            if (!isDeploying && completedProfile == null) {
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

                        errorMessage?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
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
                                isDeploying = true
                                terminalLogs = ">>> Iniciando sesión SSH hacia $host...\n"

                                scope.launch {
                                    val result = SshDeployer.executeDeploy(
                                        host = host.trim(),
                                        port = sshPort.toIntOrNull() ?: 22,
                                        user = sshUser.trim().ifBlank { "root" },
                                        password = if (authMode == 0) password else "",
                                        privateKey = if (authMode == 1) privateKey else "",
                                        browserEngine = "firefox",
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
                                        isDeploying = false
                                        result.onSuccess { profile ->
                                            if (authMode == 1) {
                                                profile.sshPrivateKey = privateKey
                                            } else {
                                                profile.sshPassword = password
                                            }
                                            profile.sshPort = sshPort.toIntOrNull() ?: 22
                                            profile.sshUser = sshUser.trim().ifBlank { "root" }
                                            completedProfile = profile
                                        }.onFailure { err ->
                                            errorMessage = "Error: ${err.message}"
                                        }
                                    }
                                }
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
                                text = if (completedProfile != null) "¡Instalación Exitosa!" else "Configurando tu VPS...",
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Terminal de instalación en vivo", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Text(
                                text = terminalLogs.ifBlank { "Esperando salida del servidor..." },
                                color = Color(0xFF39D353),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }

                        if (completedProfile != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { onDeploymentSuccess(completedProfile!!) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Text("🌐 Conectar y Abrir Navegador Ahora")
                            }
                        }
                    }
                }
            }
        }
    }
}
