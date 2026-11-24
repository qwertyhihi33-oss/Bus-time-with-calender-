package com.hkbuscal.app.domain

import java.time.Instant

enum class BusOperator(val code: String, val labelTc: String, val labelEn: String) {
    KMB("KMB", "九巴", "KMB"),
    LWB("LWB", "龍運", "LWB"),
    CTB("CTB", "城巴", "Citybus")
}

data class CalendarEventItem(
    val id: Long,
    val title: String,
    val location: String,
    val description: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val calendarName: String
) {
    val minutesUntilStart: Long
        get() = ((startTimeMillis - System.currentTimeMillis()) / 60_000L)
}

data class UnifiedBusEta(
    val operator: BusOperator,
    val route: String,
    val direction: String, // "O" or "I"
    val stopId: String,
    val stopNameTc: String,
    val stopNameEn: String,
    val destinationTc: String,
    val destinationEn: String,
    val distanceMeters: Double,
    val walkingMinutes: Int,
    val etaInstant: Instant?,
    val etaMinutes: Long?,
    val remarkTc: String,
    val remarkEn: String,
    val etaSeq: Int
) {
    /**
     * Minutes remaining before the user must leave their current position
     * to walk to the bus stop and catch this bus.
     */
    val leaveInMinutes: Long?
        get() = etaMinutes?.let { it - walkingMinutes }
}

data class EventCommuteSuggestion(
    val event: CalendarEventItem,
    val matchedRuleKeyword: String?,
    val recommendedEtas: List<UnifiedBusEta>,
    val statusSummary: String
)

data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float = 0f
)

data class AppSettings(
    val leadTimeMinutes: Int = 60,
    val searchRadiusMeters: Int = 450,
    val walkingSpeedMetersPerMin: Int = 75, // ~4.5 km/h
    val backgroundMonitorEnabled: Boolean = true,
    val useTraditionalChinese: Boolean = true
)
