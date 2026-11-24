package com.hkbuscal.app.data.api

import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Official HKSAR DATA.GOV.HK API for Kowloon Motor Bus (KMB / 九巴)
 * and Long Win Bus (LWB / 龍運巴士).
 * Base URL: https://data.etabus.gov.hk/v1/transport/kmb/
 */
interface KmbApiService {

    @GET("stop")
    suspend fun getAllStops(): KmbListResponse<KmbStopDto>

    @GET("route/")
    suspend fun getAllRoutes(): KmbListResponse<KmbRouteDto>

    @GET("route-stop/{route}/{direction}/{serviceType}")
    suspend fun getRouteStops(
        @Path("route") route: String,
        @Path("direction") direction: String, // "outbound" or "inbound"
        @Path("serviceType") serviceType: String = "1"
    ): KmbListResponse<KmbRouteStopDto>

    @GET("stop-eta/{stopId}")
    suspend fun getStopEta(
        @Path("stopId") stopId: String
    ): KmbListResponse<KmbEtaDto>

    @GET("eta/{stopId}/{route}/{serviceType}")
    suspend fun getRouteStopEta(
        @Path("stopId") stopId: String,
        @Path("route") route: String,
        @Path("serviceType") serviceType: String = "1"
    ): KmbListResponse<KmbEtaDto>
}
