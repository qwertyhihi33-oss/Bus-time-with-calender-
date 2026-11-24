package com.hkbuscal.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hkbuscal.app.domain.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "hk_bus_cal_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val LEAD_TIME_MIN = intPreferencesKey("lead_time_min")
        val SEARCH_RADIUS_M = intPreferencesKey("search_radius_m")
        val WALK_SPEED_MPM = intPreferencesKey("walk_speed_mpm")
        val BG_MONITOR = booleanPreferencesKey("bg_monitor")
        val USE_TC = booleanPreferencesKey("use_tc")
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            leadTimeMinutes = prefs[Keys.LEAD_TIME_MIN] ?: 60,
            searchRadiusMeters = prefs[Keys.SEARCH_RADIUS_M] ?: 450,
            walkingSpeedMetersPerMin = prefs[Keys.WALK_SPEED_MPM] ?: 75,
            backgroundMonitorEnabled = prefs[Keys.BG_MONITOR] ?: true,
            useTraditionalChinese = prefs[Keys.USE_TC] ?: true
        )
    }

    suspend fun getCurrentSettings(): AppSettings = settingsFlow.first()

    suspend fun saveSettings(settings: AppSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.LEAD_TIME_MIN] = settings.leadTimeMinutes
            prefs[Keys.SEARCH_RADIUS_M] = settings.searchRadiusMeters
            prefs[Keys.WALK_SPEED_MPM] = settings.walkingSpeedMetersPerMin
            prefs[Keys.BG_MONITOR] = settings.backgroundMonitorEnabled
            prefs[Keys.USE_TC] = settings.useTraditionalChinese
        }
    }
}
