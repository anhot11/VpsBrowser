package com.vpsbrowser.app

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vpsbrowser.app.data.ProfileManager
import com.vpsbrowser.app.databinding.ActivityAutoDeployBinding
import com.vpsbrowser.app.model.VpsProfile
import com.vpsbrowser.app.ssh.SshDeployer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AutoDeployActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAutoDeployBinding
    private lateinit var profileManager: ProfileManager

    private var createdProfile: VpsProfile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAutoDeployBinding.inflate(layoutInflater)
        setContentView(binding.root)

        profileManager = ProfileManager(this)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.btnStartDeploy.setOnClickListener {
            startInstallation()
        }

        binding.btnOpenBrowser.setOnClickListener {
            createdProfile?.let {
                profileManager.setActiveProfileId(it.id)
            }
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun startInstallation() {
        val host = binding.etSshHost.text?.toString()?.trim().orEmpty()
        val user = binding.etSshUser.text?.toString()?.trim().orEmpty()
        val portStr = binding.etSshPort.text?.toString()?.trim().orEmpty()
        val password = binding.etSshPassword.text?.toString()?.trim().orEmpty()

        if (host.isEmpty()) {
            Toast.makeText(this, "Por favor ingresa la IP de tu VPS", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.isEmpty()) {
            Toast.makeText(this, "Por favor ingresa la contraseña SSH de tu VPS", Toast.LENGTH_SHORT).show()
            return
        }

        val port = portStr.toIntOrNull() ?: 22

        // Lock UI and show progress
        binding.cardForm.visibility = View.GONE
        binding.cardProgress.visibility = View.VISIBLE
        binding.btnOpenBrowser.visibility = View.GONE
        binding.tvTerminalLogs.text = ""

        lifecycleScope.launch {
            val result = SshDeployer.executeDeploy(
                host = host,
                port = port,
                user = user.ifEmpty { "root" },
                password = password,
                browserEngine = "firefox",
                onProgress = { title, progress ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        binding.tvStepDescription.text = title
                        binding.progressBarDeploy.progress = progress
                    }
                },
                onLogLine = { line ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        binding.tvTerminalLogs.append(line + "\n")
                        binding.scrollTerminal.post {
                            binding.scrollTerminal.fullScroll(View.FOCUS_DOWN)
                        }
                    }
                }
            )

            withContext(Dispatchers.Main) {
                result.onSuccess { profile ->
                    createdProfile = profile
                    profileManager.saveProfile(profile)
                    profileManager.setActiveProfileId(profile.id)

                    binding.tvProgressTitle.text = getString(R.string.status_success)
                    binding.tvStepDescription.text = "El navegador Firefox ya está corriendo en tu VPS."
                    binding.progressBarDeploy.progress = 100
                    binding.btnOpenBrowser.visibility = View.VISIBLE

                    Toast.makeText(this@AutoDeployActivity, "¡Instalación exitosa!", Toast.LENGTH_LONG).show()
                }.onFailure { error ->
                    binding.cardForm.visibility = View.VISIBLE
                    binding.cardProgress.visibility = View.GONE

                    MaterialAlertDialogBuilder(this@AutoDeployActivity)
                        .setTitle("Error en la Instalación")
                        .setMessage("No se pudo completar la instalación automática:\n\n${error.message}\n\nVerifica que la IP y la contraseña root sean correctas y que el puerto 22 esté accesible.")
                        .setPositiveButton("Reintentar", null)
                        .show()
                }
            }
        }
    }
}
