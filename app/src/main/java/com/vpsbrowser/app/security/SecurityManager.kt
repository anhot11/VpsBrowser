package com.vpsbrowser.app.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vpsbrowser.app.model.VpsProfile

class SecurityManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            "vps_secure_vault",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback to standard private preferences if keystore is unavailable in some emulator environments
        context.getSharedPreferences("vps_secure_vault_fallback", Context.MODE_PRIVATE)
    }

    private val gson = Gson()

    companion object {
        private const val KEY_PROFILES = "secure_vps_profiles"
        private const val KEY_ACTIVE_ID = "secure_active_vps_id"
        private const val KEY_SEARCH_ENGINE = "secure_search_engine"
        private const val KEY_FORCE_SSH_TUNNEL = "secure_force_ssh_tunnel"
    }

    fun getAllProfiles(): MutableList<VpsProfile> {
        val json = securePrefs.getString(KEY_PROFILES, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<VpsProfile>>() {}.type
        return try {
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveProfile(profile: VpsProfile) {
        val list = getAllProfiles()
        val index = list.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            list[index] = profile
        } else {
            list.add(profile)
        }
        saveAllProfiles(list)
    }

    fun deleteProfile(id: String) {
        val list = getAllProfiles()
        list.removeAll { it.id == id }
        saveAllProfiles(list)
        if (getActiveProfileId() == id) {
            setActiveProfileId(list.firstOrNull()?.id)
        }
    }

    private fun saveAllProfiles(list: List<VpsProfile>) {
        val json = gson.toJson(list)
        securePrefs.edit().putString(KEY_PROFILES, json).apply()
    }

    fun getActiveProfile(): VpsProfile? {
        val activeId = getActiveProfileId()
        val list = getAllProfiles()
        return if (activeId != null) {
            list.firstOrNull { it.id == activeId } ?: list.firstOrNull()
        } else {
            list.firstOrNull()
        }
    }

    fun getActiveProfileId(): String? {
        return securePrefs.getString(KEY_ACTIVE_ID, null)
    }

    fun setActiveProfileId(id: String?) {
        securePrefs.edit().putString(KEY_ACTIVE_ID, id).apply()
    }

    fun getSearchEngine(): String {
        return securePrefs.getString(KEY_SEARCH_ENGINE, "https://duckduckgo.com/?q=")
            ?: "https://duckduckgo.com/?q="
    }

    fun setSearchEngine(engineUrl: String) {
        securePrefs.edit().putString(KEY_SEARCH_ENGINE, engineUrl).apply()
    }

    fun isForceSshTunnel(): Boolean {
        return securePrefs.getBoolean(KEY_FORCE_SSH_TUNNEL, true)
    }

    fun setForceSshTunnel(enabled: Boolean) {
        securePrefs.edit().putBoolean(KEY_FORCE_SSH_TUNNEL, enabled).apply()
    }

    fun getBookmarks(): MutableList<BookmarkItem> {
        val json = securePrefs.getString("secure_browser_bookmarks", null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<BookmarkItem>>() {}.type
        return try {
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun addBookmark(title: String, url: String) {
        if (url.isBlank()) return
        val list = getBookmarks()
        list.removeAll { it.url.trim().equals(url.trim(), ignoreCase = true) }
        list.add(0, BookmarkItem(title = title.ifBlank { url }, url = url.trim()))
        val json = gson.toJson(list)
        securePrefs.edit().putString("secure_browser_bookmarks", json).apply()
    }

    fun deleteBookmark(id: String) {
        val list = getBookmarks()
        list.removeAll { it.id == id }
        val json = gson.toJson(list)
        securePrefs.edit().putString("secure_browser_bookmarks", json).apply()
    }

    fun getHistory(): MutableList<HistoryItem> {
        val json = securePrefs.getString("secure_browser_history", null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<HistoryItem>>() {}.type
        return try {
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun addHistory(title: String, url: String) {
        if (url.isBlank() || url.startsWith("about:") || url.startsWith("data:")) return
        val list = getHistory()
        list.removeAll { it.url.trim().equals(url.trim(), ignoreCase = true) }
        list.add(0, HistoryItem(title = title.ifBlank { url }, url = url.trim()))
        if (list.size > 150) {
            list.subList(150, list.size).clear()
        }
        val json = gson.toJson(list)
        securePrefs.edit().putString("secure_browser_history", json).apply()
    }

    fun clearHistory() {
        securePrefs.edit().remove("secure_browser_history").apply()
    }

    fun getGitHubToken(): String? {
        return securePrefs.getString("secure_github_token", null)
    }

    fun saveGitHubToken(token: String?) {
        if (token.isNullOrBlank()) {
            securePrefs.edit().remove("secure_github_token").apply()
        } else {
            securePrefs.edit().putString("secure_github_token", token.trim()).apply()
        }
    }
}

data class BookmarkItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class HistoryItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

