package com.autoflow.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autoflow.app.data.Rule
import com.autoflow.app.data.RuleLog
import com.autoflow.app.data.RuleRepository
import com.autoflow.app.engine.RuleEngine
import com.autoflow.app.util.InstalledApp
import com.autoflow.app.util.AppInfo
import com.autoflow.app.util.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SetupState(
    val accessibility: Boolean = false,
    val notificationAccess: Boolean = false,
    val batteryUnrestricted: Boolean = false,
) {
    val ready: Boolean get() = accessibility && notificationAccess
}

class AutoFlowViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RuleRepository.get(application)

    val rules: StateFlow<List<Rule>> = repository.observeRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val logs: StateFlow<List<RuleLog>> = repository.observeLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _setup = MutableStateFlow(SetupState())
    val setup: StateFlow<SetupState> = _setup.asStateFlow()

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    init {
        refreshSetup()
        loadApps()
    }

    /** Called on every resume: the user may have flipped a switch in Settings and come back. */
    fun refreshSetup() {
        val context = getApplication<Application>()
        _setup.value = SetupState(
            accessibility = Permissions.isAccessibilityEnabled(context),
            notificationAccess = Permissions.isNotificationAccessEnabled(context),
            batteryUnrestricted = Permissions.isBatteryOptimisationIgnored(context),
        )
    }

    private fun loadApps() {
        viewModelScope.launch {
            _apps.value = withContext(Dispatchers.IO) {
                AppInfo.launchableApps(getApplication())
            }
        }
    }

    fun save(rule: Rule) {
        viewModelScope.launch { repository.save(rule) }
    }

    fun delete(rule: Rule) {
        viewModelScope.launch { repository.delete(rule) }
    }

    fun setEnabled(rule: Rule, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(rule.id, enabled) }
    }

    fun runNow(rule: Rule) = RuleEngine.runManually(rule)

    fun clearLogs() {
        viewModelScope.launch { repository.clearLogs() }
    }

    suspend fun rule(id: Long): Rule? = repository.rule(id)
}
