package com.hkbuscal.app.data.repository

import android.content.Context
import com.hkbuscal.app.data.api.NetworkClient
import com.hkbuscal.app.data.local.BusDatabase
import com.hkbuscal.app.data.local.BusStopEntity
import com.hkbuscal.app.data.local.CommuteRuleEntity
import com.hkbuscal.app.data.location.LocationHelper
import com.hkbuscal.app.domain.AppSettings
import com.hkbuscal.app.domain.BusOperator
import com.hkbuscal.app.domain.CalendarEventItem
import com.hkbuscal.app.domain.EventCommuteSuggestion
import com.hkbuscal.app.domain.UnifiedBusEta
import com.hkbuscal.app.domain.UserLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.ceil

class TransitCommuteRepository(context: Context) {

    private val dao = BusDatabase.getInstance(context).busDao()
    private val kmbApi = NetworkClient.kmbApi
    private val ctbApi = NetworkClient.citybusApi

    fun observeRules(): Flow<List<CommuteRuleEntity>> = dao.observeRules()

    suspend fun addRule(rule: CommuteRuleEntity) = withContext(Dispatchers.IO) {
        dao.insertRule(rule.copy(route = rule.route.trim().uppercase()))
    }

    suspend fun deleteRule(rule: CommuteRuleEntity) = withContext(Dispatchers.IO) {
        dao.deleteRule(rule)
    }

    /**
     * Ensures the KMB & LWB stop catalog (~6,500 stops) is cached in Room DB.
     */
    suspend fun ensureKmbStopsCached(forceRefresh: Boolean = false) = withContext(Dispatchers.IO) {
        val count = dao.countStops("KMB")
        if (count > 0 && !forceRefresh) return@withContext

        val response = kmbApi.getAllStops()
        val now = System.currentTimeMillis()
        val entities = response.data.mapNotNull { dto ->
            val lat = dto.lat.toDoubleOrNull() ?: return@mapNotNull null
            val lon = dto.long.toDoubleOrNull() ?: return@mapNotNull null
            BusStopEntity(
                stopKey = "KMB:${dto.stop}",
                operatorCode = "KMB",
                stopId = dto.stop,
                nameTc = dto.nameTc,
                nameEn = dto.nameEn,
                lat = lat,
                lon = lon,
                updatedAtMillis = now
            )
        }
        if (entities.isNotEmpty()) {
            dao.upsertStops(entities)
        }
    }

    /**
     * Finds nearby KMB & LWB stops within [radiusMeters] of [location] and fetches live ETAs.
     */
    suspend fun getNearbyKmbLwbEtas(
        location: UserLocation,
        settings: AppSettings,
        maxStops: Int = 5
    ): List<UnifiedBusEta> = withContext(Dispatchers.IO) {
        ensureKmbStopsCached()
        val allStops = dao.getStopsByOperator("KMB")
        val nearby = allStops
            .map { stop ->
                stop to LocationHelper.distanceMeters(
                    location.latitude, location.longitude, stop.lat, stop.lon
                )
            }
            .filter { it.second <= settings.searchRadiusMeters }
            .sortedBy { it.second }
            .take(maxStops)

        if (nearby.isEmpty()) return@withContext emptyList()

        coroutineScope {
            nearby.map { (stop, dist) ->
                async {
                    try {
                        val walkMin = ceil(dist / settings.walkingSpeedMetersPerMin).toInt().coerceAtLeast(1)
                        val etaResp = kmbApi.getStopEta(stop.stopId)
                        etaResp.data
                            .filter { it.etaSeq == 1 || it.etaSeq == 2 }
                            .map { dto ->
                                val parsed = parseEtaInstant(dto.eta)
                                val mins = parsed?.let { minutesFromNow(it) }
                                UnifiedBusEta(
                                    operator = inferKmbOrLwb(dto.route, dto.co),
                                    route = dto.route,
                                    direction = dto.dir,
                                    stopId = stop.stopId,
                                    stopNameTc = stop.nameTc,
                                    stopNameEn = stop.nameEn,
                                    destinationTc = dto.destTc,
                                    destinationEn = dto.destEn,
                                    distanceMeters = dist,
                                    walkingMinutes = walkMin,
                                    etaInstant = parsed,
                                    etaMinutes = mins,
                                    remarkTc = dto.rmkTc,
                                    remarkEn = dto.rmkEn,
                                    etaSeq = dto.etaSeq
                                )
                            }
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
                .filter { it.etaMinutes != null && it.etaMinutes >= 0 }
                .sortedWith(compareBy({ it.distanceMeters }, { it.etaMinutes ?: Long.MAX_VALUE }))
        }
    }

    /**
     * Fetches live ETA for a specific rule (KMB, LWB, or Citybus) by automatically finding
     * the stop along that route closest to the user's current GPS location.
     */
    suspend fun getEtasForRule(
        rule: CommuteRuleEntity,
        location: UserLocation,
        settings: AppSettings
    ): List<UnifiedBusEta> = withContext(Dispatchers.IO) {
        val directions = when (rule.direction.lowercase()) {
            "outbound" -> listOf("outbound")
            "inbound" -> listOf("inbound")
            else -> listOf("outbound", "inbound")
        }

        when (rule.operatorCode.uppercase()) {
            "CTB" -> fetchCitybusRouteNearestEtas(rule, directions, location, settings)
            else -> fetchKmbLwbRouteNearestEtas(rule, directions, location, settings)
        }
    }

    private suspend fun fetchKmbLwbRouteNearestEtas(
        rule: CommuteRuleEntity,
        directions: List<String>,
        location: UserLocation,
        settings: AppSettings
    ): List<UnifiedBusEta> {
        ensureKmbStopsCached()
        val results = mutableListOf<UnifiedBusEta>()

        for (dir in directions) {
            val stopIdAndEntity: Pair<BusStopEntity, Double>? = if (!rule.preferredStopId.isNullOrBlank()) {
                dao.getStopByKey("KMB:${rule.preferredStopId}")?.let {
                    it to LocationHelper.distanceMeters(location.latitude, location.longitude, it.lat, it.lon)
                }
            } else {
                val routeStops = try {
                    kmbApi.getRouteStops(rule.route, dir, "1").data
                } catch (_: Exception) {
                    emptyList()
                }
                routeStops.mapNotNull { rs ->
                    dao.getStopByKey("KMB:${rs.stop}")?.let { entity ->
                        entity to LocationHelper.distanceMeters(
                            location.latitude, location.longitude, entity.lat, entity.lon
                        )
                    }
                }.minByOrNull { it.second }
            }

            if (stopIdAndEntity != null) {
                val (stop, dist) = stopIdAndEntity
                val walkMin = ceil(dist / settings.walkingSpeedMetersPerMin).toInt().coerceAtLeast(1)
                val etaData = try {
                    kmbApi.getRouteStopEta(stop.stopId, rule.route, "1").data
                } catch (_: Exception) {
                    emptyList()
                }
                val expectedDirChar = if (dir == "outbound") "O" else "I"
                etaData
                    .filter { it.dir.equals(expectedDirChar, ignoreCase = true) }
                    .forEach { dto ->
                        val parsed = parseEtaInstant(dto.eta)
                        results.add(
                            UnifiedBusEta(
                                operator = inferKmbOrLwb(dto.route, dto.co),
                                route = dto.route,
                                direction = dto.dir,
                                stopId = stop.stopId,
                                stopNameTc = stop.nameTc,
                                stopNameEn = stop.nameEn,
                                destinationTc = dto.destTc,
                                destinationEn = dto.destEn,
                                distanceMeters = dist,
                                walkingMinutes = walkMin,
                                etaInstant = parsed,
                                etaMinutes = parsed?.let { minutesFromNow(it) },
                                remarkTc = dto.rmkTc,
                                remarkEn = dto.rmkEn,
                                etaSeq = dto.etaSeq
                            )
                        )
                    }
            }
        }
        return results.sortedBy { it.etaMinutes ?: Long.MAX_VALUE }
    }

    private suspend fun fetchCitybusRouteNearestEtas(
        rule: CommuteRuleEntity,
        directions: List<String>,
        location: UserLocation,
        settings: AppSettings
    ): List<UnifiedBusEta> = coroutineScope {
        val results = mutableListOf<UnifiedBusEta>()

        for (dir in directions) {
            val nearestStop: Pair<BusStopEntity, Double>? = if (!rule.preferredStopId.isNullOrBlank()) {
                val cached = getOrFetchCtbStop(rule.preferredStopId)
                cached?.let {
                    it to LocationHelper.distanceMeters(location.latitude, location.longitude, it.lat, it.lon)
                }
            } else {
                val routeStops = try {
                    ctbApi.getRouteStops(rule.route, dir).data
                } catch (_: Exception) {
                    emptyList()
                }
                // Resolve stop coordinates with bounded concurrency (uses Room cache first)
                val semaphore = Semaphore(6)
                val resolved = routeStops.map { rs ->
                    async {
                        semaphore.withPermit { getOrFetchCtbStop(rs.stop) }
                    }
                }.awaitAll().filterNotNull()

                resolved.map { entity ->
                    entity to LocationHelper.distanceMeters(
                        location.latitude, location.longitude, entity.lat, entity.lon
                    )
                }.minByOrNull { it.second }
            }

            if (nearestStop != null) {
                val (stop, dist) = nearestStop
                val walkMin = ceil(dist / settings.walkingSpeedMetersPerMin).toInt().coerceAtLeast(1)
                val etaData = try {
                    ctbApi.getEta(stop.stopId, rule.route).data
                } catch (_: Exception) {
                    emptyList()
                }
                val expectedDirChar = if (dir == "outbound") "O" else "I"
                etaData
                    .filter { it.dir.equals(expectedDirChar, ignoreCase = true) }
                    .forEach { dto ->
                        val parsed = parseEtaInstant(dto.eta)
                        results.add(
                            UnifiedBusEta(
                                operator = BusOperator.CTB,
                                route = dto.route,
                                direction = dto.dir,
                                stopId = stop.stopId,
                                stopNameTc = stop.nameTc,
                                stopNameEn = stop.nameEn,
                                destinationTc = dto.destTc,
                                destinationEn = dto.destEn,
                                distanceMeters = dist,
                                walkingMinutes = walkMin,
                                etaInstant = parsed,
                                etaMinutes = parsed?.let { minutesFromNow(it) },
                                remarkTc = dto.rmkTc,
                                remarkEn = dto.rmkEn,
                                etaSeq = dto.etaSeq
                            )
                        )
                    }
            }
        }
        results.sortedBy { it.etaMinutes ?: Long.MAX_VALUE }
    }

    private suspend fun getOrFetchCtbStop(stopId: String): BusStopEntity? {
        dao.getStopByKey("CTB:$stopId")?.let { return it }
        return try {
            val dto = ctbApi.getStop(stopId).data ?: return null
            val lat = dto.lat.toDoubleOrNull() ?: return null
            val lon = dto.long.toDoubleOrNull() ?: return null
            val entity = BusStopEntity(
                stopKey = "CTB:${dto.stop}",
                operatorCode = "CTB",
                stopId = dto.stop,
                nameTc = dto.nameTc,
                nameEn = dto.nameEn,
                lat = lat,
                lon = lon,
                updatedAtMillis = System.currentTimeMillis()
            )
            dao.upsertStop(entity)
            entity
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Matches an upcoming calendar event with user commute rules (by event title/location keyword)
     * and nearby KMB/LWB stops (filtering by destination similarity when possible).
     */
    suspend fun buildCommuteSuggestionForEvent(
        event: CalendarEventItem,
        location: UserLocation,
        settings: AppSettings
    ): EventCommuteSuggestion = withContext(Dispatchers.IO) {
        val rules = dao.getAllRules()
        val haystack = "${event.title} ${event.location} ${event.description}".lowercase()

        // 1. Match explicit keyword rules first, then fallback to wildcard "*" favorite rules
        val keywordMatches = rules.filter { rule ->
            rule.keyword.isNotBlank() && rule.keyword != "*" &&
                haystack.contains(rule.keyword.trim().lowercase())
        }
        val activeRules = keywordMatches.ifEmpty { rules.filter { it.keyword == "*" } }

        val ruleEtas = coroutineScope {
            activeRules.map { rule ->
                async { getEtasForRule(rule, location, settings) }
            }.awaitAll().flatten()
        }

        // 2. Also fetch nearby KMB/LWB stops if no rule matched or to supplement options
        val nearbyEtas = if (ruleEtas.isEmpty()) {
            val allNearby = getNearbyKmbLwbEtas(location, settings, maxStops = 4)
            // Prefer buses whose destination matches event location tokens
            val locTokens = event.location
                .split(" ", ",", "、", "-", "/")
                .map { it.trim().lowercase() }
                .filter { it.length >= 2 }

            val destMatched = if (locTokens.isNotEmpty()) {
                allNearby.filter { eta ->
                    locTokens.any { token ->
                        eta.destinationEn.lowercase().contains(token) ||
                            eta.destinationTc.lowercase().contains(token)
                    }
                }
            } else emptyList()

            destMatched.ifEmpty { allNearby.take(6) }
        } else {
            emptyList()
        }

        val combined = (ruleEtas + nearbyEtas)
            .filter { it.etaMinutes != null && it.etaMinutes >= 0 }
            .distinctBy { "${it.operator.code}-${it.route}-${it.stopId}-${it.etaSeq}" }
            .sortedBy { it.etaMinutes ?: Long.MAX_VALUE }
            .take(6)

        val matchedKeyword = keywordMatches.firstOrNull()?.keyword
        val summary = buildSummaryText(event, combined, settings.useTraditionalChinese)

        EventCommuteSuggestion(
            event = event,
            matchedRuleKeyword = matchedKeyword,
            recommendedEtas = combined,
            statusSummary = summary
        )
    }

    private fun buildSummaryText(
        event: CalendarEventItem,
        etas: List<UnifiedBusEta>,
        useTc: Boolean
    ): String {
        val best = etas.firstOrNull { (it.leaveInMinutes ?: -1) >= 0 } ?: etas.firstOrNull()
        if (best == null) {
            return if (useTc) {
                "附近暫無實時班次資料，請檢查路線設定或位置。"
            } else {
                "No live bus arrivals found nearby. Add a route rule or check GPS."
            }
        }
        val leaveIn = best.leaveInMinutes ?: 0L
        val etaMin = best.etaMinutes ?: 0L
        return if (useTc) {
            val action = if (leaveIn <= 1) "請立即出發" else "建議 $leaveIn 分鐘後出發"
            "$action • 乘搭 ${best.operator.labelTc} ${best.route}（${best.stopNameTc}，${etaMin} 分鐘後到站）"
        } else {
            val action = if (leaveIn <= 1) "Leave now" else "Leave in $leaveIn min"
            "$action • Take ${best.operator.labelEn} ${best.route} at ${best.stopNameEn} (arrives in $etaMin min)"
        }
    }

    /**
     * KMB's Open Data API serves both KMB (九巴) and LWB (龍運).
     * LWB routes predominantly start with A, E, S, R, NA, N30/N31/N42/N64 airport/Lantau prefixes.
     */
    private fun inferKmbOrLwb(route: String, co: String): BusOperator {
        if (co.equals("LWB", ignoreCase = true)) return BusOperator.LWB
        val r = route.uppercase()
        val isLwbPattern = r.startsWith("A") || r.startsWith("E") || r.startsWith("NA") ||
            r.startsWith("S1") || r.startsWith("S64") || r.startsWith("R8") ||
            r.startsWith("N30") || r.startsWith("N31") || r.startsWith("N42") || r.startsWith("N64")
        return if (isLwbPattern) BusOperator.LWB else BusOperator.KMB
    }

    private fun parseEtaInstant(isoString: String?): Instant? {
        if (isoString.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(isoString).toInstant()
        } catch (_: Exception) {
            null
        }
    }

    private fun minutesFromNow(target: Instant): Long {
        return Duration.between(Instant.now(), target).toMinutes()
    }
}
