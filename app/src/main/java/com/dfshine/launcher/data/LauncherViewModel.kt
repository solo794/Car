package com.dfshine.launcher.data

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dfshine.launcher.model.AppInfo
import kotlinx.coroutines.launch

/** Single shared view model backing every screen (Home, Drawer, Settings). */
class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    val prefs = Prefs(application)
    private val repository = AppRepository(application)

    var allApps by mutableStateOf<List<AppInfo>>(emptyList())
        private set

    var isLoading by mutableStateOf(true)
        private set

    // Bumped whenever a pref that affects layout (dock, hidden apps, grid
    // columns...) changes, so Composables reading `prefs` directly know to
    // recompose even though Prefs itself isn't observable.
    var settingsVersion by mutableStateOf(0)
        private set

    init {
        reloadApps()
    }

    fun reloadApps() {
        viewModelScope.launch {
            isLoading = true
            allApps = repository.loadAllApps()
            isLoading = false
        }
    }

    fun notifySettingsChanged() {
        settingsVersion++
    }

    val visibleApps: List<AppInfo>
        get() = allApps.filter { it.key !in prefs.hiddenApps }

    fun appByKey(key: String): AppInfo? = allApps.firstOrNull { it.key == key }

    fun setDockApps(keys: List<String>) {
        prefs.dockApps = keys
        notifySettingsChanged()
    }

    fun toggleHidden(key: String) {
        val current = prefs.hiddenApps.toMutableSet()
        if (!current.add(key)) current.remove(key)
        prefs.hiddenApps = current
        notifySettingsChanged()
    }

    fun toggleLocked(key: String) {
        val current = prefs.lockedApps.toMutableSet()
        if (!current.add(key)) current.remove(key)
        prefs.lockedApps = current
        notifySettingsChanged()
    }
}
