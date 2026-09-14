package com.vpsbrowser.app.ui.components

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpsbrowser.app.security.SecurityManager
import com.vpsbrowser.app.ui.theme.DarkBorder
import com.vpsbrowser.app.ui.theme.StatusGreen
import com.vpsbrowser.app.ui.theme.StatusRed
import com.vpsbrowser.app.ui.theme.StatusYellow
import java.net.URI

@Composable
fun MobileBrowserOmnibar(
    currentUrl: String,
    pageTitle: String = "",
    vpsHost: String,
    latencyMs: Long?,
    isSocksActive: Boolean,
    browserMode: String, // "native_mobile" or "remote_desktop"
    canGoBack: Boolean,
    canGoForward: Boolean,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefresh: () -> Unit,
    onToggleBrowserMode: () -> Unit,
    onVerifyIp: () -> Unit,
    onOpenServerManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val securityManager = remember { SecurityManager(context) }

    var isEditing by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf(currentUrl) }
    val focusRequester = remember { FocusRequester() }

    // Dialog & Menu visibility states
    var showMenu by remember { mutableStateOf(false) }
    var showShieldDialog by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showDownloadsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(currentUrl) {
        if (!isEditing) {
            inputText = currentUrl
        }
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            inputText = currentUrl
            focusRequester.requestFocus()
        }
    }

    val displayHost = remember(currentUrl) {
        try {
            if (currentUrl.isBlank()) {
                "Buscar o escribir URL"
            } else {
                val uri = URI(currentUrl)
                val host = uri.host ?: currentUrl
                host.removePrefix("www.")
            }
        } catch (e: Exception) {
            currentUrl.ifBlank { "Buscar o escribir URL" }
        }
    }

    val latencyColor = when {
        latencyMs == null || latencyMs < 0 -> StatusRed
        latencyMs < 70 -> StatusGreen
        latencyMs < 160 -> StatusYellow
        else -> StatusRed
    }

    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (isEditing) {
                // Editing Row: Full Width Omnibar Input with IME Auto-focus
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 6.dp).size(20.dp)
                    )

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Buscar en la web o ingresar URL...", fontSize = 14.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = DarkBorder
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isEditing = false
                            onNavigate(inputText)
                        }),
                        trailingIcon = {
                            if (inputText.isNotBlank()) {
                                IconButton(onClick = { inputText = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Borrar texto", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .focusRequester(focusRequester)
                    )

                    IconButton(
                        onClick = {
                            isEditing = false
                            inputText = currentUrl
                        }
                    ) {
                        Text("Cancelar", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                // Clean Modern Navigation Row with 3-dots Menu
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    // Back
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onBack()
                        },
                        enabled = canGoBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atrás",
                            tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Forward
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onForward()
                        },
                        enabled = canGoForward,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Adelante",
                            tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Central Omnibar Pill (Domain + VPS Shield Indicator)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .border(1.dp, DarkBorder, RoundedCornerShape(19.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isEditing = true
                            }
                            .padding(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (currentUrl.startsWith("https://")) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = displayHost,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // VPS Shield Badge inside the pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showShieldDialog = true
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(latencyColor)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "VPS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Refresh
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRefresh()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Recargar",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // 3-Dots Action Menu (Configuración, Modo PC, Descargas, Favoritos, Historial)
                    Box {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showMenu = true
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menú de Opciones",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            // 1. Modo PC / Móvil
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            if (browserMode == "native_mobile") "🖥️ Modo PC (Escritorio Remoto)" else "📱 Modo Móvil (0ms Nativo)",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            if (browserMode == "native_mobile") "Firefox completo ejecutado en VPS" else "Navegador celular ultra-rápido",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (browserMode == "native_mobile") Icons.Default.DesktopWindows else Icons.Default.PhoneAndroid,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onToggleBrowserMode()
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // 2. Favoritos
                            DropdownMenuItem(
                                text = { Text("⭐ Favoritos y Marcadores", fontSize = 13.sp, fontWeight = FontWeight.Medium) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFFFB300)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showBookmarksDialog = true
                                }
                            )

                            // 3. Historial
                            DropdownMenuItem(
                                text = { Text("🕒 Historial de Navegación", fontSize = 13.sp, fontWeight = FontWeight.Medium) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showHistoryDialog = true
                                }
                            )

                            // 4. Descargas
                            DropdownMenuItem(
                                text = { Text("📥 Gestor de Descargas", fontSize = 13.sp, fontWeight = FontWeight.Medium) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showDownloadsDialog = true
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // 5. Configuración VPS
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("⚙️ Configuración y VPS", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("Gestión de servidor, BBR y túneles", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onOpenServerManager()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // 1. Dialog Favoritos (Bookmarks)
    if (showBookmarksDialog) {
        var bookmarks by remember { mutableStateOf(securityManager.getBookmarks()) }
        var addSuccessMessage by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showBookmarksDialog = false },
            icon = {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(28.dp))
            },
            title = {
                Text("Favoritos y Marcadores", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Button to bookmark current page
                    if (currentUrl.isNotBlank() && !currentUrl.startsWith("about:")) {
                        OutlinedButton(
                            onClick = {
                                val titleToSave = if (pageTitle.isNotBlank()) pageTitle else displayHost
                                securityManager.addBookmark(titleToSave, currentUrl)
                                bookmarks = securityManager.getBookmarks()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                addSuccessMessage = "¡Página añadida a favoritos!"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Añadir página actual a Favoritos", fontSize = 12.sp)
                        }
                    }

                    if (addSuccessMessage != null) {
                        Text(addSuccessMessage!!, fontSize = 11.sp, color = StatusGreen, fontWeight = FontWeight.SemiBold)
                    }

                    if (bookmarks.isEmpty()) {
                        Text(
                            "No tienes marcadores guardados aún. Acceso directo a sitios recomendados:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val quickLinks = listOf(
                            "🌐 Verificar IP (BrowserLeaks)" to "https://browserleaks.com/ip",
                            "🔍 DuckDuckGo Privado" to "https://duckduckgo.com",
                            "Google" to "https://www.google.com",
                            "Wikipedia" to "https://wikipedia.org",
                            "YouTube" to "https://www.youtube.com"
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            quickLinks.forEach { (name, url) ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showBookmarksDialog = false
                                            onNavigate(url)
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(name, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(bookmarks) { b ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showBookmarksDialog = false
                                                onNavigate(b.url)
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Bookmark, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(b.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(b.url, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        IconButton(
                                            onClick = {
                                                securityManager.deleteBookmark(b.id)
                                                bookmarks = securityManager.getBookmarks()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showBookmarksDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    // 2. Dialog Historial (History)
    if (showHistoryDialog) {
        var history by remember { mutableStateOf(securityManager.getHistory()) }

        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            icon = {
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            },
            title = {
                Text("Historial de Navegación", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (history.isEmpty()) {
                        Text(
                            "No hay páginas en el historial o ha sido limpiado.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(history) { h ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showHistoryDialog = false
                                            onNavigate(h.url)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(h.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(h.url, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showHistoryDialog = false }) {
                    Text("Cerrar")
                }
            },
            dismissButton = {
                if (history.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            securityManager.clearHistory()
                            history = mutableListOf()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    ) {
                        Text("Borrar Historial", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }

    // 3. Dialog Descargas (Downloads)
    if (showDownloadsDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadsDialog = false },
            icon = {
                Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            },
            title = {
                Text("Gestor de Descargas", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = StatusGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Túnel Cifrado VPS Activo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = StatusGreen)
                            }
                            Text(
                                "Todas las descargas desde el navegador móvil viajan cifradas por tu VPS en Rusia sin dejar registros en tu operador local ni en WiFi.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 14.sp
                            )
                        }
                    }

                    Button(
                        onClick = {
                            showDownloadsDialog = false
                            try {
                                val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Abriendo gestor de archivos...", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Abrir Carpeta de Descargas")
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth().border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("⚡ Descarga Remota a 1 Gbps", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Descarga archivos masivos directamente en la VPS desde la pantalla de Gestión VPS.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showDownloadsDialog = false }) {
                    Text("Entendido")
                }
            }
        )
    }

    // VPS Shield Info & IP Verification Dialog
    if (showShieldDialog) {
        AlertDialog(
            onDismissRequest = { showShieldDialog = false },
            icon = {
                Icon(Icons.Default.Shield, contentDescription = null, tint = StatusGreen, modifier = Modifier.size(32.dp))
            },
            title = {
                Text("Escudo de Conexión VPS", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "El 100% del tráfico web, peticiones DNS y descargas viajan cifrados mediante tu VPS personal:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Servidor VPS:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(vpsHost, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Modo actual:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (browserMode == "native_mobile") "📱 Móvil Nativo (0ms Lag)" else "🖥️ Escritorio Remoto",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Túnel SOCKS5:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (isSocksActive) "Activo (Cifrado)" else "Iniciando...",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSocksActive) StatusGreen else StatusYellow
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Latencia VPS:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (latencyMs != null && latencyMs >= 0) "${latencyMs}ms" else "Verificando...",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = latencyColor
                                )
                            }
                        }
                    }

                    Text(
                        text = "Los sitios web ven únicamente la IP y ubicación de tu VPS, protegiendo tu privacidad sin retraso de video.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showShieldDialog = false
                        onVerifyIp()
                    }
                ) {
                    Text("🌐 Verificar mi IP en la Web")
                }
            },
            dismissButton = {
                TextButton(onClick = { showShieldDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}
