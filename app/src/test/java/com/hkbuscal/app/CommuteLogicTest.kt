package com.hkbuscal.app

import com.hkbuscal.app.data.api.CtbEtaDto
import com.hkbuscal.app.data.api.KmbEtaDto
import com.hkbuscal.app.data.location.LocationHelper
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommuteLogicTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Test
    fun haversineDistance_hungHomToTst_isReasonable() {
        // Hung Hom Station to Tsim Sha Tsui Star Ferry (~1.6–1.8 km)
        val dist = LocationHelper.distanceMeters(22.3029, 114.1816, 22.2938, 114.1687)
        assertTrue("Expected ~1600-1800m, got $dist", dist in 1400.0..2000.0)
    }

    @Test
    fun kmbEtaDto_deserializesOfficialPayload() {
        val sample = """
            {
              "co": "KMB",
              "route": "1A",
              "dir": "O",
              "service_type": 1,
              "seq": 5,
              "dest_tc": "尖沙咀碼頭",
              "dest_en": "STAR FERRY",
              "eta_seq": 1,
              "eta": "2026-11-25T09:15:00+08:00",
              "rmk_tc": "",
              "rmk_en": "",
              "data_timestamp": "2026-11-25T09:05:00+08:00"
            }
        """.trimIndent()
        val dto = json.decodeFromString<KmbEtaDto>(sample)
        assertEquals("1A", dto.route)
        assertEquals("尖沙咀碼頭", dto.destTc)
        assertEquals(1, dto.etaSeq)
    }

    @Test
    fun ctbEtaDto_deserializesOfficialPayload() {
        val sample = """
            {
              "co": "CTB",
              "route": "A21",
              "dir": "O",
              "seq": 3,
              "stop": "001025",
              "dest_tc": "機場(地面運輸中心)",
              "dest_en": "Airport (Ground Transportation Centre)",
              "eta_seq": 1,
              "eta": "2026-11-25T09:20:00+08:00",
              "rmk_tc": "實時",
              "rmk_en": "",
              "data_timestamp": "2026-11-25T09:05:00+08:00"
            }
        """.trimIndent()
        val dto = json.decodeFromString<CtbEtaDto>(sample)
        assertEquals("A21", dto.route)
        assertEquals("001025", dto.stop)
        assertEquals(1, dto.etaSeq)
    }
}
