package com.blackbox.jalrakshak.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * =====================================================================================
 *  DTO — Laravel API ke JSON ka Kotlin roop.
 * =====================================================================================
 *  Ye shapes backend ke response se EXACTLY match karte hain (Day 1 mein bane the,
 *  docs/ai-context.md dekho). Naam bhi wahi rakhe hain jo JSON mein hain, taaki
 *  kisi ko do jagah dekh ke match na karna pade.
 *
 *  Sab fields ke default values hain (`= 0`, `= null`).
 *  KYUN: agar backend kabhi koi field na bheje (ya naya field jode), to app CRASH nahi
 *  hogi — bas default le legi. Flood ke waqt app ka crash hona sabse bura outcome hai.
 *  Isi wajah se Json parser bhi `ignoreUnknownKeys = true` ke saath banaya hai (Network.kt).
 * =====================================================================================
 */

/** GET /api/villages ka poora response. */
@Serializable
data class VillagesResponse(
    val mode: String = "live",
    val date: String? = null,
    @SerialName("generated_at") val generatedAt: String? = null,
    @SerialName("data_ok") val dataOk: Boolean = true,
    val villages: List<VillageDto> = emptyList(),
)

/** Ek gaon + uska risk (list aur detail dono mein yehi shape aata hai). */
@Serializable
data class VillageDto(
    val id: Int = 0,
    val name: String = "",
    val district: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    @SerialName("elevation_m") val elevationM: Int = 0,
    val population: Int = 0,
    val risk: RiskDto = RiskDto(),
)

/**
 * RiskEngine ka output. App ismein KUCH BHI CALCULATE NAHI KARTI —
 * level, reason, advice, ETA sab backend se ready-made aate hain (CLAUDE.md convention:
 * risk logic sirf app/Services/RiskEngine.php mein). App sirf dikhati hai.
 */
@Serializable
data class RiskDto(
    val level: String = "green",          // "red" | "yellow" | "green"
    val score: Int = 0,
    @SerialName("reason_hi") val reasonHi: String = "",
    @SerialName("reason_en") val reasonEn: String = "",
    @SerialName("water_eta_hi") val waterEtaHi: String = "",
    @SerialName("water_eta_en") val waterEtaEn: String = "",
    @SerialName("advice_hi") val adviceHi: String = "",
    @SerialName("advice_en") val adviceEn: String = "",
    // Kitne ghante mein paani danger mark tak pahunch sakta hai.
    // null = ya to paani badh nahi raha, ya river data hi nahi (RiskEngine dono case
    // mein null bhejta hai). Home screen ka "To rise" metric isi se banta hai.
    @SerialName("hours_to_danger") val hoursToDanger: Int? = null,
    val factors: FactorsDto = FactorsDto(),
)

@Serializable
data class FactorsDto(
    @SerialName("rainfall_mm") val rainfallMm: Double = 0.0,
    @SerialName("rainfall_3day_mm") val rainfall3dayMm: Double = 0.0,
    @SerialName("river_level_m") val riverLevelM: Double? = null,
    @SerialName("danger_level_m") val dangerLevelM: Double? = null,
    @SerialName("warning_level_m") val warningLevelM: Double? = null,
    // false => is station ka threshold data hi nahi (decision D10).
    // App tab "नदी का डेटा नहीं" dikhati hai — jhoota number nahi.
    @SerialName("river_data") val riverData: Boolean = false,
)

/** GET /api/village/{id} — home screen ka poora data ek hi call mein. */
@Serializable
data class VillageDetailResponse(
    val village: VillageDto = VillageDto(),
    @SerialName("generated_at") val generatedAt: String? = null,
    val shelters: List<ShelterDto> = emptyList(),
    @SerialName("recent_alerts") val recentAlerts: List<AlertDto> = emptyList(),
)

@Serializable
data class ShelterDto(
    val id: Int = 0,
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val capacity: Int = 0,
)

/** GET /api/alerts?village_id= */
@Serializable
data class AlertsResponse(
    val count: Int = 0,
    val alerts: List<AlertDto> = emptyList(),
)

@Serializable
data class AlertDto(
    val id: Int = 0,
    @SerialName("message_hi") val messageHi: String = "",
    @SerialName("message_en") val messageEn: String = "",
    @SerialName("sent_by") val sentBy: String = "",
    @SerialName("sent_at") val sentAt: String? = null,
)

/** POST /api/relief ka body (citizen ka SOS). */
@Serializable
data class ReliefRequestBody(
    @SerialName("village_id") val villageId: Int,
    val lat: Double,
    val lng: Double,
    val message: String,
)

@Serializable
data class ReliefResponse(
    val message: String = "",
)

/** POST /api/register-token ka body (FCM). */
@Serializable
data class RegisterTokenBody(
    val token: String,
    @SerialName("village_id") val villageId: Int,
    val platform: String = "android",
)

@Serializable
data class RegisterTokenResponse(
    val registered: Boolean = false,
)
