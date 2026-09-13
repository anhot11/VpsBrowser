package com.vpsbrowser.app

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.vpsbrowser.app.data.ProfileManager
import com.vpsbrowser.app.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var profileManager: ProfileManager

    private val searchEngines = listOf(
        "Google" to "https://www.google.com/search?q=",
        "DuckDuckGo" to "https://duckduckgo.com/?q=",
        "Bing" to "https://www.bing.com/search?q=",
        "Brave" to "https://search.brave.com/search?q="
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        profileManager = ProfileManager(this)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        setupSearchEngineSpinner()
    }

    private fun setupSearchEngineSpinner() {
        val names = searchEngines.map { it.first }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        binding.spinnerSearchEngine.adapter = adapter

        val currentEngine = profileManager.getSearchEngine()
        val currentIndex = searchEngines.indexOfFirst { it.second == currentEngine }
        if (currentIndex >= 0) {
            binding.spinnerSearchEngine.setSelection(currentIndex)
        }

        binding.spinnerSearchEngine.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                profileManager.setSearchEngine(searchEngines[position].second)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
}
