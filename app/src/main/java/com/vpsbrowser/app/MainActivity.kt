package com.vpsbrowser.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vpsbrowser.app.model.VpsProfile
import com.vpsbrowser.app.security.SecurityManager
import com.vpsbrowser.app.ssh.SshTunnelManager
import com.vpsbrowser.app.ui.screens.BrowserScreen
import com.vpsbrowser.app.ui.screens.ProfilesScreen
import com.vpsbrowser.app.ui.screens.ServerManagerScreen
import com.vpsbrowser.app.ui.screens.SetupWizardScreen
import com.vpsbrowser.app.ui.theme.VPSBrowserTheme

enum class AppScreen {
    BROWSER,
    SETUP_WIZARD,
    PROFILES,
    SERVER_MANAGER
}

class MainActivity : ComponentActivity() {

    private lateinit var securityManager: SecurityManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        securityManager = SecurityManager(this)

        setContent {
            VPSBrowserTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VPSBrowserApp(securityManager = securityManager)
                }
            }
        }
    }

    override fun onDestroy() {
        SshTunnelManager.stopTunnel()
        super.onDestroy()
    }
}

@Composable
fun VPSBrowserApp(securityManager: SecurityManager) {
    var profiles by remember { mutableStateOf(securityManager.getAllProfiles()) }
    var activeProfile by remember { mutableStateOf(securityManager.getActiveProfile()) }

    var currentScreen by remember {
        mutableStateOf(
            if (activeProfile != null) AppScreen.BROWSER else AppScreen.SETUP_WIZARD
        )
    }

    BackHandler(enabled = currentScreen != AppScreen.BROWSER && activeProfile != null) {
        currentScreen = AppScreen.BROWSER
    }

    when (currentScreen) {
        AppScreen.BROWSER -> {
            if (activeProfile != null) {
                BrowserScreen(
                    profile = activeProfile!!,
                    searchEngineUrl = securityManager.getSearchEngine(),
                    onSaveProfile = { updated ->
                        securityManager.saveProfile(updated)
                        activeProfile = updated
                        profiles = securityManager.getAllProfiles()
                    },
                    onOpenServerManager = { currentScreen = AppScreen.SERVER_MANAGER },
                    onOpenProfiles = { currentScreen = AppScreen.PROFILES }
                )
            } else {
                currentScreen = AppScreen.SETUP_WIZARD
            }
        }

        AppScreen.SETUP_WIZARD -> {
            SetupWizardScreen(
                onBack = {
                    if (activeProfile != null) {
                        currentScreen = AppScreen.BROWSER
                    } else if (profiles.isNotEmpty()) {
                        currentScreen = AppScreen.PROFILES
                    }
                },
                onDeploymentSuccess = { newProfile ->
                    securityManager.saveProfile(newProfile)
                    securityManager.setActiveProfileId(newProfile.id)
                    profiles = securityManager.getAllProfiles()
                    activeProfile = newProfile
                    currentScreen = AppScreen.BROWSER
                }
            )
        }

        AppScreen.PROFILES -> {
            ProfilesScreen(
                profiles = profiles,
                activeProfileId = activeProfile?.id,
                onBack = {
                    if (activeProfile != null) currentScreen = AppScreen.BROWSER
                },
                onSelectProfile = { selected ->
                    securityManager.setActiveProfileId(selected.id)
                    activeProfile = selected
                    currentScreen = AppScreen.BROWSER
                },
                onSaveProfile = { saved ->
                    securityManager.saveProfile(saved)
                    if (activeProfile == null || activeProfile?.id == saved.id) {
                        activeProfile = saved
                        securityManager.setActiveProfileId(saved.id)
                    }
                    profiles = securityManager.getAllProfiles()
                },
                onDeleteProfile = { id ->
                    securityManager.deleteProfile(id)
                    profiles = securityManager.getAllProfiles()
                    activeProfile = securityManager.getActiveProfile()
                    if (activeProfile == null) {
                        currentScreen = AppScreen.SETUP_WIZARD
                    }
                },
                onNavigateToWizard = {
                    currentScreen = AppScreen.SETUP_WIZARD
                }
            )
        }

        AppScreen.SERVER_MANAGER -> {
            if (activeProfile != null) {
                ServerManagerScreen(
                    profile = activeProfile!!,
                    isTunnelActive = SshTunnelManager.isTunnelActive(),
                    onBack = { currentScreen = AppScreen.BROWSER },
                    onEnvironmentDestroyed = {
                        securityManager.deleteProfile(activeProfile!!.id)
                        profiles = securityManager.getAllProfiles()
                        activeProfile = securityManager.getActiveProfile()
                        currentScreen = if (activeProfile != null) AppScreen.BROWSER else AppScreen.SETUP_WIZARD
                    }
                )
            } else {
                currentScreen = AppScreen.SETUP_WIZARD
            }
        }
    }
}
