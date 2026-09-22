package dev.vcgms.aapp.vaterialfiles.navigation

import androidx.lifecycle.MediatorLiveData
import dev.vcgms.aapp.vaterialfiles.settings.Settings

object StandardDirectoriesLiveData : MediatorLiveData<List<StandardDirectory>>() {
    init {
        // Initialize value before we have any active observer.
        loadValue()
        addSource(Settings.STANDARD_DIRECTORY_SETTINGS) { loadValue() }
    }

    private fun loadValue() {
        value = standardDirectories
    }
}
