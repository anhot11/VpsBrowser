package com.vpsbrowser.app.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpsbrowser.app.model.VpsProfile
import com.vpsbrowser.app.ssh.ServerMetrics
import com.vpsbrowser.app.ssh.SshRemoteLifecycle
import com.vpsbrowser.app.ui.theme.DarkBorder
import com.vpsbrowser.app.ui.theme.StatusGreen
import com.vpsbrowser.app.ui.theme.StatusRed
import com.vpsbrowser.app.ui.theme.StatusYellow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerManagerScreen(
    profile: VpsProfile,
    isTunnelActive: Boolean,
    onBack: () -> Unit,
    onEnvironmentDestroyed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var metrics by remember { mutableStateOf<ServerMetrics?>(null) }
    var isLoadingMetrics by remember { mutableStateOf(true) }
    var actionInProgress by remember { mutableStateOf<String?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var showNukeDialog by remember { mutableStateOf(false) }
    var auditResult by remember { mutableStateOf<String?>(null) }
    var showAuditDialog by remember { mutableStateOf(false) }

    fun refreshMetrics() {
        isLoadingMetrics = true
        scope.launch {
            val res = SshRemoteLifecycle.fetchServerMetrics(profile)
            res.onSuccess { metrics = it }
            isLoadingMetrics = false
        }
    }

    LaunchedEffect(profile) {
        refreshMetrics()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestión y Privacidad VPS") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshMetrics() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refrescar Métricas")
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
            // Card: Privacy & Tunnel Status
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Túnel Cifrado de Extremo a Extremo", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isTunnelActive)
                            "✓ Túnel SSH activo. Todo el tráfico hacia el navegador se transmite por un túnel SSH cifrado (ChaCha20-Poly1305). Ningún puerto está expuesto al internet público en tu VPS."
                        else
                            "Conexión directa activa a tu servidor VPS.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Card: Cloudflare Tunnel
            if (profile.cloudflareUrl.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Túnel Cloudflare HTTPS Activo", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Acceso seguro sin abrir puertos en tu VPS ni cortafuegos, enrutado mediante la red perimetral de Cloudflare.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = profile.cloudflareUrl,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("Cloudflare URL", profile.cloudflareUrl))
                                    Toast.makeText(context, "URL de Cloudflare copiada", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Card: Server Metrics
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Estado del Servidor (${profile.name})", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    if (isLoadingMetrics) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Consultando métricas en la VPS por SSH...", fontSize = 13.sp)
                        }
                    } else if (metrics != null) {
                        val m = metrics!!
                        MetricRow("Contenedor Firefox:", m.status, if (m.containerRunning) StatusGreen else StatusRed)
                        MetricRow("Memoria RAM:", "${m.ramUsageMb} MB usados / ${m.totalRamMb} MB totales")
                        MetricRow("Carga CPU:", m.cpuLoad)
                        MetricRow("Host:", "${profile.sshUser}@${profile.getCleanHost()}:${profile.sshPort}")
                    } else {
                        Text("No se pudo obtener métricas en este momento.", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Section: Management Actions
            Text("Acciones Remotas", style = MaterialTheme.typography.titleMedium)

            // Restart Button
            ActionButtonItem(
                icon = Icons.Default.Refresh,
                title = "Reiniciar Navegador Remoto",
                description = "Reinicia el contenedor Firefox si alguna página pesada se quedó congelada.",
                enabled = actionInProgress == null,
                onClick = {
                    actionInProgress = "Reiniciando Firefox..."
                    scope.launch {
                        val res = SshRemoteLifecycle.restartContainer(profile)
                        actionInProgress = null
                        actionMessage = if (res.isSuccess) "Navegador reiniciado con éxito." else "Error: ${res.exceptionOrNull()?.message}"
                        refreshMetrics()
                    }
                }
            )

            // Update Button
            ActionButtonItem(
                icon = Icons.Default.SystemUpdate,
                title = "Actualizar a la Última Versión",
                description = "Descarga la imagen más reciente de LinuxServer Firefox con parches de seguridad.",
                enabled = actionInProgress == null,
                onClick = {
                    actionInProgress = "Actualizando imagen..."
                    scope.launch {
                        val res = SshRemoteLifecycle.updateContainer(profile)
                        actionInProgress = null
                        actionMessage = if (res.isSuccess) "Navegador actualizado con éxito." else "Error: ${res.exceptionOrNull()?.message}"
                        refreshMetrics()
                    }
                }
            )

            // Security Audit Button
            ActionButtonItem(
                icon = Icons.Default.Security,
                title = "Auditar Seguridad de la Instancia",
                description = "Comprueba el estado de aislamiento Docker, Nginx Basic Auth, uBlock Origin y políticas de privacidad.",
                enabled = actionInProgress == null,
                onClick = {
                    actionInProgress = "Auditando seguridad en VPS..."
                    scope.launch {
                        val res = SshRemoteLifecycle.auditSecurity(profile)
                        actionInProgress = null
                        if (res.isSuccess) {
                            auditResult = res.getOrNull()
                            showAuditDialog = true
                        } else {
                            actionMessage = "Error en auditoría: ${res.exceptionOrNull()?.message}"
                        }
                    }
                }
            )

            // Clear Cache Button
            ActionButtonItem(
                icon = Icons.Default.CleaningServices,
                title = "Limpiar Caché y Temporales",
                description = "Elimina archivos temporales y caché del navegador remoto en la VPS.",
                enabled = actionInProgress == null,
                onClick = {
                    actionInProgress = "Limpiando caché..."
                    scope.launch {
                        val res = SshRemoteLifecycle.clearSessionCache(profile)
                        actionInProgress = null
                        actionMessage = if (res.isSuccess) "Caché limpiada con éxito." else "Error: ${res.exceptionOrNull()?.message}"
                    }
                }
            )

            // Nuke Button (Destruir entorno)
            ActionButtonItem(
                icon = Icons.Default.DeleteForever,
                title = "💣 Destruir Entorno Remoto (Nuke)",
                description = "Borra completamente el contenedor, datos, cookies y archivos en la VPS sin dejar rastro.",
                isDestructive = true,
                enabled = actionInProgress == null,
                onClick = {
                    showNukeDialog = true
                }
            )

            actionMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            }

            if (actionInProgress != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(actionInProgress!!, fontSize = 13.sp)
                }
            }
        }
    }

    // Confirmation Dialog for Nuke
    if (showNukeDialog) {
        AlertDialog(
            onDismissRequest = { showNukeDialog = false },
            title = { Text("¿Destruir entorno remoto?") },
            text = {
                Text("Esta acción eliminará el contenedor Docker, todos los volúmenes, perfiles y la carpeta /opt/vps-browser en tu VPS. La VPS quedará limpia como si nada se hubiese instalado.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNukeDialog = false
                        actionInProgress = "Destruyendo entorno remoto..."
                        scope.launch {
                            val res = SshRemoteLifecycle.nukeAndDestroyEnvironment(profile)
                            actionInProgress = null
                            if (res.isSuccess) {
                                onEnvironmentDestroyed()
                            } else {
                                actionMessage = "Error al destruir: ${res.exceptionOrNull()?.message}"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sí, Destruir Todo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNukeDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Security Audit Dialog
    if (showAuditDialog && auditResult != null) {
        AlertDialog(
            onDismissRequest = { showAuditDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = StatusGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Auditoría de Seguridad VPS")
                }
            },
            text = {
                Column {
                    Text(
                        "Resultados de la comprobación de integridad y seguridad remota:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color(0xFF0D1117),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(4.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                text = auditResult!!,
                                color = Color(0xFF39D353),
                                fontSize = 12.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showAuditDialog = false }) {
                    Text("Aceptar")
                }
            }
        )
    }
}

@Composable
private fun MetricRow(label: String, value: String, statusDot: Color? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            statusDot?.let { dotColor ->
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ActionButtonItem(
    icon: ImageVector,
    title: String,
    description: String,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isDestructive) StatusRed.copy(alpha = 0.5f) else DarkBorder, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) StatusRed else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isDestructive) StatusRed else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onClick,
                enabled = enabled,
                colors = if (isDestructive) ButtonDefaults.outlinedButtonColors(contentColor = StatusRed) else ButtonDefaults.outlinedButtonColors()
            ) {
                Text(if (isDestructive) "Destruir" else "Ejecutar", fontSize = 12.sp)
            }
        }
    }
}
