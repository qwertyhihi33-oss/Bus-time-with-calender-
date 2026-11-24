package com.hkbuscal.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bus_stops")
data class BusStopEntity(
    @PrimaryKey val stopKey: String, // e.g. "KMB:A3ADFCDF8487ADB9" or "CTB:001025"
    val operatorCode: String,        // "KMB" (covers KMB & LWB) or "CTB"
    val stopId: String,
    val nameTc: String,
    val nameEn: String,
    val lat: Double,
    val lon: Double,
    val updatedAtMillis: Long
)

/**
 * Links a calendar keyword (matched against event title or location, e.g., "PolyU", "尖沙咀", "機場")
 * to one or more bus routes across KMB, LWB, or Citybus.
 * Leave keyword blank ("*") to treat as an always-watched favorite route.
 */
@Entity(tableName = "commute_rules")
data class CommuteRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,            // e.g., "PolyU", "紅磡", "Airport", or "*"
    val operatorCode: String,       // "KMB", "LWB", or "CTB"
    val route: String,              // e.g., "1A", "A21", "E23", "970X"
    val direction: String,          // "outbound", "inbound", or "both"
    val preferredStopId: String? = null // Optional fixed stop; null = auto-pick nearest GPS stop
)
