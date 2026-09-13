package com.vpsbrowser.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vpsbrowser.app.model.VpsProfile

class ProfileManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("vps_browser_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_PROFILES = "saved_profiles"
        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
        private const val KEY_SEARCH_ENGINE = "search_engine"
    }

    fun getAllProfiles(): MutableList<VpsProfile> {
        val json = prefs.getString(KEY_PROFILES, null) ?: return mutableListOf()
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
        prefs.edit().putString(KEY_PROFILES, json).apply()
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
        return prefs.getString(KEY_ACTIVE_PROFILE_ID, null)
    }

    fun setActiveProfileId(id: String?) {
        prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, id).apply()
    }

    fun getSearchEngine(): String {
        return prefs.getString(KEY_SEARCH_ENGINE, "https://www.google.com/search?q=")
            ?: "https://www.google.com/search?q="
    }

    fun setSearchEngine(url: String) {
        prefs.edit().putString(KEY_SEARCH_ENGINE, url).apply()
    }
}
