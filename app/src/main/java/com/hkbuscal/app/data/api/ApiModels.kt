package com.hkbuscal.app.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ==================== KMB & LWB (data.etabus.gov.hk) ====================

@Serializable
data class KmbListResponse<T>(
    val type: String = "",
    val version: String = "",
    @SerialName("generated_timestamp") val generatedTimestamp: String = "",
    val data: List<T> = emptyList()
)

@Serializable
data class KmbStopDto(
    val stop: String,
    @SerialName("name_en") val nameEn: String = "",
    @SerialName("name_tc") val nameTc: String = "",
    @SerialName("name_sc") val nameSc: String = "",
    val lat: String = "0.0",
    val long: String = "0.0"
)

@Serializable
data class KmbRouteDto(
    val route: String,
    val bound: String = "O",
    @SerialName("service_type") val serviceType: String = "1",
    @SerialName("orig_en") val origEn: String = "",
    @SerialName("orig_tc") val origTc: String = "",
    @SerialName("dest_en") val destEn: String = "",
    @SerialName("dest_tc") val destTc: String = ""
)

@Serializable
data class KmbRouteStopDto(
    val route: String,
    val bound: String,
    @SerialName("service_type") val serviceType: String,
    val seq: String,
    val stop: String
)

@Serializable
data class KmbEtaDto(
    val co: String = "KMB",
    val route: String = "",
    val dir: String = "O",
    @SerialName("service_type") val serviceType: Int = 1,
    val seq: Int = 1,
    @SerialName("dest_tc") val destTc: String = "",
    @SerialName("dest_en") val destEn: String = "",
    @SerialName("eta_seq") val etaSeq: Int = 1,
    val eta: String? = null,
    @SerialName("rmk_tc") val rmkTc: String = "",
    @SerialName("rmk_en") val rmkEn: String = "",
    @SerialName("data_timestamp") val dataTimestamp: String = ""
)

// ==================== Citybus v2 (rt.data.gov.hk/v2/transport/citybus) ====================

@Serializable
data class CtbListResponse<T>(
    val type: String = "",
    val version: String = "",
    @SerialName("generated_timestamp") val generatedTimestamp: String = "",
    val data: List<T> = emptyList()
)

@Serializable
data class CtbSingleResponse<T>(
    val type: String = "",
    val version: String = "",
    @SerialName("generated_timestamp") val generatedTimestamp: String = "",
    val data: T? = null
)

@Serializable
data class CtbRouteDto(
    val co: String = "CTB",
    val route: String,
    @SerialName("orig_tc") val origTc: String = "",
    @SerialName("orig_en") val origEn: String = "",
    @SerialName("dest_tc") val destTc: String = "",
    @SerialName("dest_en") val destEn: String = ""
)

@Serializable
data class CtbRouteStopDto(
    val co: String = "CTB",
    val route: String,
    val dir: String,
    val seq: Int,
    val stop: String
)

@Serializable
data class CtbStopDto(
    val stop: String,
    @SerialName("name_tc") val nameTc: String = "",
    @SerialName("name_en") val nameEn: String = "",
    val lat: String = "0.0",
    val long: String = "0.0"
)

@Serializable
data class CtbEtaDto(
    val co: String = "CTB",
    val route: String = "",
    val dir: String = "O",
    val seq: Int = 1,
    val stop: String = "",
    @SerialName("dest_tc") val destTc: String = "",
    @SerialName("dest_en") val destEn: String = "",
    @SerialName("eta_seq") val etaSeq: Int = 1,
    val eta: String? = null,
    @SerialName("rmk_tc") val rmkTc: String = "",
    @SerialName("rmk_en") val rmkEn: String = "",
    @SerialName("data_timestamp") val dataTimestamp: String = ""
)
