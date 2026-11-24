package com.hkbuscal.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hkbuscal.app.data.calendar.CalendarRepository
import com.hkbuscal.app.data.local.CommuteRuleEntity
import com.hkbuscal.app.data.local.SettingsRepository
import com.hkbuscal.app.data.location.LocationHelper
import com.hkbuscal.app.data.repository.TransitCommuteRepository
import com.hkbuscal.app.domain.AppSettings
import com.hkbuscal.app.domain.EventCommuteSuggestion
import com.hkbuscal.app.domain.UnifiedBusEta
import com.hkbuscal.app.domain.UserLocation
import com.hkbuscal.app.worker.CommuteMonitorWorker
import com.hkbuscal.app.worker.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val currentLocation: UserLocation? = null,
    val suggestions: List<EventCommuteSuggestion> = emptyList(),
    val nearbyEtas: List<UnifiedBusEta> = emptyList(),
    val settings: AppSettings = AppSettings()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val calendarRepo = CalendarRepository(application)
    private val locationHelper = LocationHelper(application)
    private val transitRepo = TransitCommuteRepository(application)
    private val settingsRepo = SettingsRepository(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val rules: StateFlow<List<CommuteRuleEntity>> = transitRepo.observeRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            settingsRepo.settingsFlow.collect { saved ->
                _uiState.update { it.copy(settings = saved) }
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            try {
                val loc = locationHelper.getCurrentLocation()
                if (loc == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "無法取得位置 — 請開啟 GPS 及定位權限 / Location unavailable"
                        )
                    }
                    return@launch
                }

                val settings = settingsRepo.getCurrentSettings()
                val events = calendarRepo.getUpcomingEvents(hoursAhead = 18)
                val suggestions = events.take(5).map { event ->
                    transitRepo.buildCommuteSuggestionForEvent(event, loc, settings)
                }
                val nearby = transitRepo.getNearbyKmbLwbEtas(loc, settings, maxStops = 5)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentLocation = loc,
                        suggestions = suggestions,
                        nearbyEtas = nearby,
                        statusMessage = if (events.isEmpty()) {
                            "未來 18 小時沒有日曆活動 / No upcoming events in next 18h"
                        } else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "資料更新失敗：${e.localizedMessage ?: "Network error"}"
                    )
                }
            }
        }
    }

    fun addRule(keyword: String, operatorCode: String, route: String, direction: String) {
        if (route.isBlank()) return
        viewModelScope.launch {
            transitRepo.addRule(
                CommuteRuleEntity(
                    keyword = keyword.trim().ifBlank { "*" },
                    operatorCode = operatorCode,
                    route = route.trim().uppercase(),
                    direction = direction
                )
            )
            refreshAll()
        }
    }

    fun deleteRule(rule: CommuteRuleEntity) {
        viewModelScope.launch {
            transitRepo.deleteRule(rule)
            refreshAll()
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_uiState.value.settings)
        _uiState.update { it.copy(settings = updated) }
        if (updated.backgroundMonitorEnabled) {
            CommuteMonitorWorker.schedulePeriodic(getApplication())
        } else {
            CommuteMonitorWorker.cancelPeriodic(getApplication())
        }
        viewModelScope.launch {
            settingsRepo.saveSettings(updated)
        }
    }

    fun sendTestNotification() {
        val first = _uiState.value.suggestions.firstOrNull()
        if (first != null) {
            NotificationHelper.showCommuteNotification(
                context = getApplication(),
                suggestion = first,
                useTc = _uiState.value.settings.useTraditionalChinese
            )
        } else {
            CommuteMonitorWorker.runNow(getApplication())
        }
    }
}
