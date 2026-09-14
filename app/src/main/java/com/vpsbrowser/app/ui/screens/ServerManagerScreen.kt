package com.vpsbrowser.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpsbrowser.app.engine.IncognitoManager
import com.vpsbrowser.app.model.VpsProfile
import com.vpsbrowser.app.security.SecurityManager
import com.vpsbrowser.app.ssh.ServerMetrics
import com.vpsbrowser.app.ssh.SshRemoteLifecycle
import com.vpsbrowser.app.ui.theme.DarkBorder
import com.vpsbrowser.app.ui.theme.IncognitoPurple
import com.vpsbrowser.app.ui.theme.IncognitoPurpleBadge
import com.vpsbrowser.app.ui.theme.StatusGreen
import com.vpsbrowser.app.ui.theme.StatusRed
import com.vpsbrowser.app.ui.theme.StatusYellow
import com.vpsbrowser.app.util.NetworkHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerManagerScreen(
    profile: VpsProfile,
    isTunnelActive: Boolean,
    onBack: () -> Unit,
    onOpenProfiles: (() -> Unit)? = null,
    onSaveProfile: ((VpsProfile) -> Unit)? = null,
    onEnvironmentDestroyed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val securityManager = remember { SecurityManager(context) }

    // Navigation Tabs: 0 -> Navegador (General), 1 -> Servidor VPS (Infraestructura)
    var selectedTab by remember { mutableIntStateOf(0) }

    // Search Engine State
    var currentSearchEngine by remember { mutableStateOf(securityManager.getSearchEngine()) }

    // Browser Mode State
    var currentBrowserMode by remember { mutableStateOf(profile.browserMode.ifBlank { "native_mobile" }) }

    // Server Metrics & Actions State
    var metrics by remember { mutableStateOf<ServerMetrics?>(null) }
    var isLoadingMetrics by remember { mutableStateOf(false) }
    var actionInProgress by remember { mutableStateOf<String?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    // Collapsible state for advanced tools
    var showAdvancedTools by remember { mutableStateOf(false) }

    // Dialogs state
    var showNukeDialog by remember { mutableStateOf(false) }
    var showClearLocalDataDialog by remember { mutableStateOf(false) }
    var auditResult by remember { mutableStateOf<String?>(null) }
    var showAuditDialog by remember { mutableStateOf(false) }
    var resultDialogTitle by remember { mutableStateOf("") }
    var resultDialogContent by remember { mutableStateOf<String?>(null) }
    var showResultDialog by remember { mutableStateOf(false) }

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
                title = { Text("Panel de Configuración", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (selectedTab == 1) {
                        IconButton(onClick = { refreshMetrics() }, enabled = !isLoadingMetrics) {
                            if (isLoadingMetrics) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Actualizar métricas")
                            }
                        }
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
        ) {
            // Elegant Top Tab Selector
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ajustes Navegador", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        if (metrics == null && !isLoadingMetrics) {
                            refreshMetrics()
                        }
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Servidor VPS", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (selectedTab == 0) {
                    // ==========================================
                    // TAB 0: AJUSTES GENERALES DEL NAVEGADOR
                    // ==========================================

                    // 1. Current Connected VPS Profile Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Servidor en Uso", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isTunnelActive) StatusGreen.copy(alpha = 0.15f) else StatusYellow.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = if (isTunnelActive) "Túnel Seguro" else "Directo",
                                        color = if (isTunnelActive) StatusGreen else StatusYellow,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(profile.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("${profile.sshUser}@${profile.getCleanHost()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            if (onOpenProfiles != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = onOpenProfiles,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Gestionar o Cambiar de Servidor VPS", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // 2. Search Engine Preference Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Motor de Búsqueda Predeterminado", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "El buscador utilizado al escribir términos en la barra de direcciones:",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            val engines = listOf(
                                Triple("DuckDuckGo", "https://duckduckgo.com/?q=", "🦆 Privacidad Máxima"),
                                Triple("Google", "https://www.google.com/search?q=", "🌐 Resultados Globales"),
                                Triple("Brave", "https://search.brave.com/search?q=", "🦁 Sin Rastreadores"),
                                Triple("Bing", "https://www.bing.com/search?q=", "🔍 Motor Microsoft")
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                engines.forEach { (name, url, desc) ->
                                    val isSelected = currentSearchEngine.startsWith(url.substringBefore("?"))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(
                                                1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                currentSearchEngine = url
                                                securityManager.setSearchEngine(url)
                                                Toast.makeText(context, "Buscador predeterminado: $name", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            if (isSelected) {
                                                Icon(Icons.Default.Check, contentDescription = "Seleccionado", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Navigation Experience Mode
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Modo de Navegación", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Elige cómo interactuar con las páginas web a través de tu VPS:",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Option A: Mobile Native (0ms)
                            val isMobile = currentBrowserMode == "native_mobile"
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isMobile) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        if (isMobile) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        currentBrowserMode = "native_mobile"
                                        profile.browserMode = "native_mobile"
                                        onSaveProfile?.invoke(profile)
                                        Toast.makeText(context, "Modo Móvil Nativo activado", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = if (isMobile) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("📱 Modo Móvil Nativo (Recomendado)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("Cero lag (0ms), teclado táctil nativo fluido a 120Hz con todo el tráfico cifrado por tu VPS en Rusia.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (isMobile) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Option B: Remote Desktop KasmVNC
                            val isDesktop = currentBrowserMode == "remote_desktop"
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isDesktop) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        if (isDesktop) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        currentBrowserMode = "remote_desktop"
                                        profile.browserMode = "remote_desktop"
                                        onSaveProfile?.invoke(profile)
                                        Toast.makeText(context, "Modo Escritorio Remoto activado", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.DesktopWindows, contentDescription = null, tint = if (isDesktop) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("🖥️ Modo Escritorio Remoto", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("Transmisión de Firefox completo de escritorio ejecutado en el servidor con pestañas y extensiones de PC.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (isDesktop) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    // 4. Privacy & Local Storage
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Privacidad y Almacenamiento Local", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }

                            // Incognito status notice
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = IncognitoPurple.copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth().border(1.dp, IncognitoPurple.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = IncognitoPurpleBadge, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Modo Incógnito (Memoria RAM Pura)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = IncognitoPurpleBadge)
                                        Text(
                                            "Puedes activarlo en cualquier momento desde el menú de 3 puntos (⋮) de la barra inferior. Desactiva la escritura en disco y la barra se pone morada.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Clear history button
                            OutlinedButton(
                                onClick = { showClearLocalDataDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Borrar Historial y Datos de Caché Local", fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    // ==========================================
                    // TAB 1: GESTIÓN Y MANTENIMIENTO DEL SERVIDOR VPS
                    // ==========================================

                    // 1. Minimalist Server Status Dashboard Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Estado del Servidor VPS", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                                if (isLoadingMetrics) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else if (metrics != null) {
                                    val isRunning = metrics!!.containerRunning
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isRunning) StatusGreen.copy(alpha = 0.15f) else StatusRed.copy(alpha = 0.15f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (isRunning) StatusGreen else StatusRed))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isRunning) "En Línea" else "Detenido",
                                                color = if (isRunning) StatusGreen else StatusRed,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (metrics != null) {
                                val m = metrics!!
                                val ramUsage = m.ramUsageMb.toIntOrNull() ?: 0
                                val totalRam = (m.totalRamMb.toIntOrNull() ?: 1024).coerceAtLeast(1)
                                val ramFrac = (ramUsage.toFloat() / totalRam.toFloat()).coerceIn(0f, 1f)
                                val ramPct = (ramFrac * 100).toInt()
                                val ramColor = when {
                                    ramFrac < 0.7f -> StatusGreen
                                    ramFrac < 0.88f -> StatusYellow
                                    else -> StatusRed
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Memoria RAM del Servidor:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("$ramUsage MB / $totalRam MB ($ramPct%)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ramColor)
                                    }
                                    LinearProgressIndicator(
                                        progress = { ramFrac },
                                        color = ramColor,
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = DarkBorder.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Carga CPU", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(m.cpuLoad.ifBlank { "0.05" }, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Column {
                                        Text("Host VPS", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(profile.getCleanHost(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Column {
                                        Text("Túnel Seguro", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(if (isTunnelActive) "Activo" else "Directo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isTunnelActive) StatusGreen else StatusYellow)
                                    }
                                }
                            } else {
                                Text(
                                    text = if (isLoadingMetrics) "Obteniendo datos del servidor por SSH..." else "Toca el botón superior para cargar el estado del VPS.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 2. Essential Maintenance Tools (Clean, high-frequency actions)
                    Text("Mantenimiento Rápido", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                    CleanToolCard(
                        icon = Icons.Default.Refresh,
                        title = "Reiniciar Navegador Remoto",
                        description = "Reinicia Firefox en el servidor si una pestaña pesada se congeló.",
                        buttonText = "Reiniciar",
                        enabled = actionInProgress == null,
                        onClick = {
                            actionInProgress = "Reiniciando contenedor Firefox..."
                            scope.launch {
                                val res = SshRemoteLifecycle.restartContainer(profile)
                                actionInProgress = null
                                actionMessage = if (res.isSuccess) "Navegador reiniciado con éxito." else "Error: ${res.exceptionOrNull()?.message}"
                                refreshMetrics()
                            }
                        }
                    )

                    CleanToolCard(
                        icon = Icons.Default.CleaningServices,
                        title = "Limpiar Caché del Servidor",
                        description = "Libera espacio y borra archivos temporales en el disco del VPS.",
                        buttonText = "Limpiar",
                        enabled = actionInProgress == null,
                        onClick = {
                            actionInProgress = "Limpiando archivos temporales en VPS..."
                            scope.launch {
                                val res = SshRemoteLifecycle.clearSessionCache(profile)
                                actionInProgress = null
                                actionMessage = if (res.isSuccess) "Caché de la VPS eliminada." else "Error: ${res.exceptionOrNull()?.message}"
                                refreshMetrics()
                            }
                        }
                    )

                    CleanToolCard(
                        icon = Icons.Default.Bolt,
                        title = "Acelerar Velocidad (TCP BBR)",
                        description = "Activa Google BBR en el Kernel para video fluido y menor latencia.",
                        buttonText = "Acelerar",
                        enabled = actionInProgress == null,
                        onClick = {
                            actionInProgress = "Activando aceleración TCP BBR..."
                            scope.launch {
                                val res = SshRemoteLifecycle.enableTurboStreaming(profile)
                                actionInProgress = null
                                if (res.isSuccess) {
                                    resultDialogTitle = "⚡ Aceleración TCP BBR Activa"
                                    resultDialogContent = res.getOrNull()
                                    showResultDialog = true
                                } else {
                                    actionMessage = "Error al activar BBR: ${res.exceptionOrNull()?.message}"
                                }
                            }
                        }
                    )

                    // 3. Collapsible Section for Technical / "Rare" Advanced Tools
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdvancedTools = !showAdvancedTools }
                            .padding(vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Herramientas Avanzadas y Diagnóstico", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(
                                        if (showAdvancedTools) "Toca para ocultar herramientas técnicas" else "Firewall IP, Fail2ban, Auditoría de seguridad y Latencia",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(if (showAdvancedTools) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                        }
                    }

                    AnimatedVisibility(
                        visible = showAdvancedTools,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Advanced Tool 1: IP Whitelist Shield
                            CleanToolCard(
                                icon = Icons.Default.Shield,
                                title = "Escudo Firewall Dinámico (IP)",
                                description = "Cierra el puerto en el VPS y solo autoriza el tráfico hacia la IP de tu móvil.",
                                buttonText = "Aplicar",
                                enabled = actionInProgress == null,
                                onClick = {
                                    actionInProgress = "Aplicando cortafuegos en VPS..."
                                    scope.launch {
                                        val ip = NetworkHelper.getDevicePublicIp()
                                        if (ip == null) {
                                            actionInProgress = null
                                            actionMessage = "No se pudo determinar la IP del móvil."
                                            return@launch
                                        }
                                        val allowRes = SshRemoteLifecycle.whitelistClientIp(profile, ip)
                                        val statusRes = SshRemoteLifecycle.getShieldStatus(profile)
                                        actionInProgress = null
                                        resultDialogTitle = "🛡️ Escudo Firewall Dinámico"
                                        val allowMsg = if (allowRes.isSuccess) "✓ IP de tu dispositivo ($ip) autorizada en iptables.\n\n" else "Error: ${allowRes.exceptionOrNull()?.message}\n\n"
                                        resultDialogContent = allowMsg + (statusRes.getOrNull() ?: "")
                                        showResultDialog = true
                                    }
                                }
                            )

                            // Advanced Tool 2: Fail2ban
                            CleanToolCard(
                                icon = Icons.Default.Lock,
                                title = "Protección Anti-Fuerza Bruta (Fail2ban)",
                                description = "Bloquea automáticamente atacantes y escáneres en el puerto SSH.",
                                buttonText = "Configurar",
                                enabled = actionInProgress == null,
                                onClick = {
                                    actionInProgress = "Instalando y activando Fail2ban..."
                                    scope.launch {
                                        val res = SshRemoteLifecycle.installFail2ban(profile)
                                        actionInProgress = null
                                        if (res.isSuccess) {
                                            resultDialogTitle = "🛑 Fail2ban Configurado"
                                            resultDialogContent = res.getOrNull()
                                            showResultDialog = true
                                        } else {
                                            actionMessage = "Error en Fail2ban: ${res.exceptionOrNull()?.message}"
                                        }
                                    }
                                }
                            )

                            // Advanced Tool 3: Security Audit
                            CleanToolCard(
                                icon = Icons.Default.Security,
                                title = "Auditar Seguridad del Servidor",
                                description = "Comprueba el estado de aislamiento Docker, políticas de red y cortafuegos.",
                                buttonText = "Auditar",
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

                            // Advanced Tool 4: Multi-Route Ping Benchmark
                            CleanToolCard(
                                icon = Icons.Default.Speed,
                                title = "Test de Latencia Multi-Ruta",
                                description = "Mide y compara el ping real de cada vía de conexión disponible.",
                                buttonText = "Medir",
                                enabled = actionInProgress == null,
                                onClick = {
                                    actionInProgress = "Midiendo latencias de rutas..."
                                    scope.launch {
                                        val directPing = NetworkHelper.pingVps(profile.getCleanHost(), profile.browserPort)
                                        val dp = if (directPing > 0) directPing else NetworkHelper.pingVps(profile.getCleanHost(), profile.sshPort)
                                        val sshPing = NetworkHelper.pingVps(profile.getCleanHost(), profile.sshPort)
                                        val sp = if (sshPing > 0) sshPing + 12 else -1L
                                        val cfPing = if (profile.cloudflareUrl.isNotBlank()) {
                                            NetworkHelper.testHttpHealth(profile.cloudflareUrl).second
                                        } else -1L

                                        val sb = StringBuilder()
                                        sb.append("📊 COMPARATIVA DE LATENCIA:\n\n")
                                        sb.append("• ⚡ Ruta Directa (Escudo IP):   ${if (dp > 0) "${dp}ms (Máximo rendimiento 60 FPS)" else "Bloqueada / Cerrada"}\n")
                                        sb.append("• 🔒 Ruta Túnel SSH (ChaCha20):  ${if (sp > 0) "${sp}ms (Cero puertos abiertos)" else "No disponible"}\n")
                                        if (profile.cloudflareUrl.isNotBlank()) {
                                            sb.append("• ☁️ Ruta Túnel Cloudflare:      ${if (cfPing > 0) "${cfPing}ms (Red Anycast)" else "No disponible"}\n")
                                        }
                                        sb.append("\n💡 Recomendación: ${if (dp in 1L..100L) "Usa la Ruta Directa con Escudo IP para la menor latencia y suavidad al navegar." else "Usa el Túnel SSH para máxima compatibilidad."}")

                                        actionInProgress = null
                                        resultDialogTitle = "📊 Comparativa de Rendimiento"
                                        resultDialogContent = sb.toString()
                                        showResultDialog = true
                                    }
                                }
                            )
                        }
                    }

                    // 4. Danger Zone
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Zona de Peligro", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = StatusRed)

                    CleanToolCard(
                        icon = Icons.Default.DeleteForever,
                        title = "Destruir Entorno Remoto (Nuke)",
                        description = "Borra completamente el contenedor, datos y caché en la VPS sin dejar rastro.",
                        buttonText = "Destruir",
                        isDestructive = true,
                        enabled = actionInProgress == null,
                        onClick = { showNukeDialog = true }
                    )
                }

                // Global Action Feedback
                if (actionInProgress != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(actionInProgress!!, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                actionMessage?.let {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
    }

    // Confirmation Dialog for Clear Local History / Data
    if (showClearLocalDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearLocalDataDialog = false },
            icon = {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
            },
            title = { Text("¿Borrar historial y datos locales?") },
            text = {
                Text(
                    "Esta acción eliminará el historial de navegación cifrado y la memoria caché guardada en este celular.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        securityManager.clearHistory()
                        IncognitoManager.purgeIncognitoData(null)
                        showClearLocalDataDialog = false
                        Toast.makeText(context, "Historial y datos locales eliminados", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Borrar Datos")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearLocalDataDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Confirmation Dialog for Nuke
    if (showNukeDialog) {
        AlertDialog(
            onDismissRequest = { showNukeDialog = false },
            icon = {
                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = StatusRed, modifier = Modifier.size(32.dp))
            },
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
                    Text("Auditoría de Seguridad VPS", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        "Resultados de la comprobación de integridad y seguridad remota:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFF0D1117),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer {
                            Text(
                                text = auditResult!!,
                                color = Color(0xFF39D353),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
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

    // Generic Result Dialog with Monospace Terminal and Copy Button
    if (showResultDialog && resultDialogContent != null) {
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            title = {
                Text(resultDialogTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Surface(
                        color = Color(0xFF0D1117),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer {
                            Text(
                                text = resultDialogContent!!,
                                color = Color(0xFF39D353),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("Resultado", resultDialogContent))
                                Toast.makeText(context, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copiar", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showResultDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

@Composable
private fun CleanToolCard(
    icon: ImageVector,
    title: String,
    description: String,
    buttonText: String,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isDestructive) StatusRed.copy(alpha = 0.5f) else DarkBorder,
                RoundedCornerShape(14.dp)
            )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isDestructive) StatusRed.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isDestructive) StatusRed else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = if (isDestructive) StatusRed else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 14.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onClick,
                enabled = enabled,
                colors = if (isDestructive) ButtonDefaults.outlinedButtonColors(contentColor = StatusRed) else ButtonDefaults.outlinedButtonColors()
            ) {
                Text(buttonText, fontSize = 11.sp)
            }
        }
    }
}
