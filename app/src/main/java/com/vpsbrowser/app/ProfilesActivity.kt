package com.vpsbrowser.app

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vpsbrowser.app.data.ProfileManager
import com.vpsbrowser.app.databinding.ActivityProfilesBinding
import com.vpsbrowser.app.databinding.DialogAddProfileBinding
import com.vpsbrowser.app.databinding.ItemProfileBinding
import com.vpsbrowser.app.model.VpsProfile

class ProfilesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfilesBinding
    private lateinit var profileManager: ProfileManager
    private lateinit var adapter: ProfilesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfilesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        profileManager = ProfileManager(this)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.rvProfiles.layoutManager = LinearLayoutManager(this)
        adapter = ProfilesAdapter(
            profiles = profileManager.getAllProfiles(),
            activeId = profileManager.getActiveProfileId(),
            onConnect = { profile ->
                profileManager.setActiveProfileId(profile.id)
                setResult(Activity.RESULT_OK)
                finish()
            },
            onEdit = { profile ->
                showProfileDialog(profile)
            },
            onDelete = { profile ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Eliminar VPS")
                    .setMessage("¿Deseas eliminar el perfil '${profile.name}'?")
                    .setPositiveButton(R.string.btn_delete) { _, _ ->
                        profileManager.deleteProfile(profile.id)
                        refreshList()
                    }
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show()
            }
        )
        val autoDeployLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                setResult(Activity.RESULT_OK)
                finish()
            }
        }

        binding.btnAutoDeploy.setOnClickListener {
            autoDeployLauncher.launch(android.content.Intent(this, AutoDeployActivity::class.java))
        }

        binding.fabAddProfile.setOnClickListener {
            showProfileDialog(null)
        }

        updateEmptyState()
    }


    private fun refreshList() {
        adapter.update(profileManager.getAllProfiles(), profileManager.getActiveProfileId())
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val count = profileManager.getAllProfiles().size
        binding.tvEmptyState.visibility = if (count == 0) View.VISIBLE else View.GONE
        binding.rvProfiles.visibility = if (count > 0) View.VISIBLE else View.GONE
    }

    private fun showProfileDialog(existing: VpsProfile?) {
        val dialogBinding = DialogAddProfileBinding.inflate(LayoutInflater.from(this))
        val isEditing = existing != null

        if (isEditing && existing != null) {
            dialogBinding.etDialogName.setText(existing.name)
            dialogBinding.etDialogHost.setText(existing.host)
            dialogBinding.etDialogPort.setText(existing.port.toString())
            dialogBinding.etDialogPassword.setText(existing.password)
            dialogBinding.switchDialogSsl.isChecked = existing.useSsl
            dialogBinding.switchDialogTouch.isChecked = existing.touchEmulation
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (isEditing) R.string.title_edit_profile else R.string.title_add_profile)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.btn_save, null)
            .setNegativeButton(R.string.btn_cancel, null)
            .create()

        dialog.setOnShowListener {
            val saveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveBtn.setOnClickListener {
                val name = dialogBinding.etDialogName.text?.toString()?.trim().orEmpty()
                val host = dialogBinding.etDialogHost.text?.toString()?.trim().orEmpty()
                val portStr = dialogBinding.etDialogPort.text?.toString()?.trim().orEmpty()
                val password = dialogBinding.etDialogPassword.text?.toString()?.trim().orEmpty()
                val useSsl = dialogBinding.switchDialogSsl.isChecked
                val touch = dialogBinding.switchDialogTouch.isChecked

                if (name.isEmpty() || host.isEmpty()) {
                    Toast.makeText(this, "Completa nombre y dirección IP/host", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val port = portStr.toIntOrNull() ?: 3000

                val profile = existing?.apply {
                    this.name = name
                    this.host = host
                    this.port = port
                    this.password = password
                    this.useSsl = useSsl
                    this.touchEmulation = touch
                } ?: VpsProfile(
                    name = name,
                    host = host,
                    browserPort = port,
                    browserPassword = password,
                    useSsl = useSsl,
                    touchEmulation = touch
                )

                profileManager.saveProfile(profile)
                if (profileManager.getActiveProfileId() == null) {
                    profileManager.setActiveProfileId(profile.id)
                }

                dialog.dismiss()
                refreshList()
            }
        }

        dialog.show()
    }

    inner class ProfilesAdapter(
        private var profiles: MutableList<VpsProfile>,
        private var activeId: String?,
        private val onConnect: (VpsProfile) -> Unit,
        private val onEdit: (VpsProfile) -> Unit,
        private val onDelete: (VpsProfile) -> Unit
    ) : RecyclerView.Adapter<ProfilesAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemProfileBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val itemBinding = ItemProfileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(itemBinding)
        }

        override fun getItemCount(): Int = profiles.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = profiles[position]
            holder.binding.tvProfileName.text = item.name
            holder.binding.tvProfileHost.text = item.getFullUrl()

            val isActive = item.id == activeId
            holder.binding.tvActiveBadge.visibility = if (isActive) View.VISIBLE else View.GONE

            holder.binding.btnConnectProfile.setOnClickListener {
                onConnect(item)
            }
            holder.binding.btnEditProfile.setOnClickListener {
                onEdit(item)
            }
            holder.binding.btnDeleteProfile.setOnClickListener {
                onDelete(item)
            }
        }

        fun update(newList: MutableList<VpsProfile>, newActiveId: String?) {
            profiles = newList
            activeId = newActiveId
            notifyDataSetChanged()
        }
    }
}
