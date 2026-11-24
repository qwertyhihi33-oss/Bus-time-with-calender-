package com.hkbuscal.app.data.api

import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Official HKSAR DATA.GOV.HK API v2 for Citybus (CTB / 城巴).
 * Base URL: https://rt.data.gov.hk/v2/transport/citybus/
 */
interface CitybusApiService {

    @GET("route/CTB")
    suspend fun getAllRoutes(): CtbListResponse<CtbRouteDto>

    @GET("route-stop/CTB/{route}/{direction}")
    suspend fun getRouteStops(
        @Path("route") route: String,
        @Path("direction") direction: String // "outbound" or "inbound"
    ): CtbListResponse<CtbRouteStopDto>

    @GET("stop/{stopId}")
    suspend fun getStop(
        @Path("stopId") stopId: String
    ): CtbSingleResponse<CtbStopDto>

    @GET("eta/CTB/{stopId}/{route}")
    suspend fun getEta(
        @Path("stopId") stopId: String,
        @Path("route") route: String
    ): CtbListResponse<CtbEtaDto>
}
