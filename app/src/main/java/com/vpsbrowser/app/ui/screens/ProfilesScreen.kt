package com.vpsbrowser.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpsbrowser.app.model.VpsProfile
import com.vpsbrowser.app.ui.theme.DarkBorder
import com.vpsbrowser.app.ui.theme.StatusGreen
import com.vpsbrowser.app.ui.theme.StatusRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    profiles: List<VpsProfile>,
    activeProfileId: String?,
    onBack: () -> Unit,
    onSelectProfile: (VpsProfile) -> Unit,
    onSaveProfile: (VpsProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onNavigateToWizard: () -> Unit
) {
    var editingProfile by remember { mutableStateOf<VpsProfile?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Servidores VPS") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { isAddingNew = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Añadir Manual") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Auto-Deploy Banner
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("¿Nueva VPS?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Instala todo en 1 toque por SSH sin comandos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = onNavigateToWizard) {
                            Text("Auto-Instalar", fontSize = 12.sp)
                        }
                    }
                }
            }

            items(profiles) { profile ->
                val isActive = profile.id == activeProfileId
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isActive) 2.dp else 1.dp,
                            color = if (isActive) MaterialTheme.colorScheme.primary else DarkBorder,
                            shape = RoundedCornerShape(16.dp)
                        )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${profile.sshUser}@${profile.getCleanHost()}:${profile.browserPort}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (isActive) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = StatusGreen.copy(alpha = 0.15f),
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text("Activo", color = StatusGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                            }
                            OutlinedButton(onClick = { onSelectProfile(profile) }) {
                                Text("Conectar", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (profile.useCloudflareTunnel && profile.cloudflareUrl.isNotBlank())
                                    "Túnel Cloudflare HTTPS"
                                else if (profile.useSshTunnel)
                                    "Túnel SSH Cifrado (Sin puertos)"
                                else
                                    "Conexión Directa",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            IconButton(onClick = { editingProfile = profile }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Editar", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onDeleteProfile(profile.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = StatusRed, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog for Adding / Editing Profile
    if (isAddingNew || editingProfile != null) {
        val target = editingProfile ?: VpsProfile(name = "Mi VPS", host = "")
        var name by remember { mutableStateOf(target.name) }
        var host by remember { mutableStateOf(target.host) }
        var sshUser by remember { mutableStateOf(target.sshUser) }
        var sshPort by remember { mutableStateOf(target.sshPort.toString()) }
        var sshPass by remember { mutableStateOf(target.sshPassword) }
        var cloudflareUrl by remember { mutableStateOf(target.cloudflareUrl) }
        var useCloudflare by remember { mutableStateOf(target.useCloudflareTunnel) }
        var browserPort by remember { mutableStateOf(target.browserPort.toString()) }
        var browserPass by remember { mutableStateOf(target.browserPassword) }
        var useTunnel by remember { mutableStateOf(target.useSshTunnel) }

        AlertDialog(
            onDismissRequest = {
                isAddingNew = false
                editingProfile = null
            },
            title = { Text(if (editingProfile != null) "Editar VPS" else "Añadir VPS Manualmente") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre del Perfil") }, singleLine = true)
                    OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Dirección IP / Host") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = sshUser, onValueChange = { sshUser = it }, label = { Text("Usuario SSH") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = sshPort, onValueChange = { sshPort = it }, label = { Text("Puerto SSH") }, modifier = Modifier.width(100.dp), singleLine = true)
                    }
                    OutlinedTextField(value = sshPass, onValueChange = { sshPass = it }, label = { Text("Contraseña SSH") }, singleLine = true)
                    OutlinedTextField(
                        value = cloudflareUrl,
                        onValueChange = { cloudflareUrl = it },
                        label = { Text("URL Cloudflare Tunnel (Opcional)") },
                        placeholder = { Text("https://xxx.trycloudflare.com") },
                        singleLine = true
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Usar Cloudflare Tunnel (Sin abrir puertos)", fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(checked = useCloudflare, onCheckedChange = { useCloudflare = it })
                    }
                    OutlinedTextField(value = browserPort, onValueChange = { browserPort = it }, label = { Text("Puerto Navegador") }, singleLine = true)
                    OutlinedTextField(value = browserPass, onValueChange = { browserPass = it }, label = { Text("Contraseña Navegador") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Usar Túnel SSH cifrado", fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(checked = useTunnel, onCheckedChange = { useTunnel = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val updated = target.copy(
                        name = name.ifBlank { "VPS" },
                        host = host,
                        sshUser = sshUser.ifBlank { "root" },
                        sshPort = sshPort.toIntOrNull() ?: 22,
                        sshPassword = sshPass,
                        cloudflareUrl = cloudflareUrl.trim(),
                        useCloudflareTunnel = useCloudflare,
                        browserPort = browserPort.toIntOrNull() ?: 3000,
                        browserPassword = browserPass,
                        useSshTunnel = useTunnel,
                        connectionMode = target.connectionMode,
                        enableIpShield = target.enableIpShield,
                        enableBbr = target.enableBbr,
                        appEngine = target.appEngine
                    )
                    onSaveProfile(updated)
                    onSelectProfile(updated)
                    isAddingNew = false
                    editingProfile = null
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    isAddingNew = false
                    editingProfile = null
                }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
