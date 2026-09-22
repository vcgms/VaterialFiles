package dev.vcgms.aapp.vaterialfiles.navigation

import androidx.lifecycle.MediatorLiveData
import java8.nio.file.Path
import dev.vcgms.aapp.vaterialfiles.util.valueCompat

object NavigationRootMapLiveData : MediatorLiveData<Map<Path, NavigationRoot>>() {
    init {
        // Initialize value before we have any active observer.
        loadValue()
        addSource(NavigationItemListLiveData) { loadValue() }
    }

    private fun loadValue() {
        value = NavigationItemListLiveData.valueCompat
            .mapNotNull { it as? NavigationRoot }
            .associateBy { it.path }
    }
}
