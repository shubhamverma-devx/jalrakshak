package com.blackbox.jalrakshak.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * =====================================================================================
 *  Room entities — OFFLINE CACHE (BUILD_PLAN section 2 ka locked feature)
 * =====================================================================================
 *
 *  KYUN YE FEATURE ITNA ZAROORI HAI:
 *   Baadh mein sabse pehle mobile tower jaate hain. Theek us waqt jab citizen ko sabse
 *   zyada zaroorat hoti hai, network nahi hota. Agar app tab khaali screen dikhaye to
 *   wo bekaar hai.
 *
 *   Isliye har successful API response Room mein likh dete hain. Network na ho to app
 *   wahi PURANA data dikhati hai — saaf batate hue ki ye kitna purana hai
 *   ("ऑफ़लाइन — पिछली जानकारी"). Purana sach, naye jhooth se behtar hai; aur
 *   "shelter kahan hai" ka jawaab kal ka bhi kaam ka hai.
 *
 *  KYUN alag-alag entity, ek bada JSON blob nahi: alerts ki list badhti rehti hai,
 *  risk har refresh pe badal jaata hai. Alag tables se sirf jo badla wahi likhna padta hai.
 * =====================================================================================
 */

/**
 * Chune hue gaon ka aakhri risk snapshot.
 *
 * Sirf EK row rehti hai (id = village id, aur naya aane pe REPLACE ho jaata hai) —
 * app ek waqt mein ek hi gaon dikhati hai.
 */
@Entity(tableName = "cached_village")
data class CachedVillage(
    @PrimaryKey val id: Int,
    val name: String,
    val district: String,
    val lat: Double,
    val lng: Double,
    val population: Int,
    val elevationM: Int,

    // RiskEngine ka output — jaisa aaya waisa store. App ismein kuch nahi badalti.
    val level: String,
    val score: Int,
    val reasonHi: String,
    val reasonEn: String,
    val adviceHi: String,
    val adviceEn: String,
    val waterEtaHi: String,
    val waterEtaEn: String,
    /** Ghante — null agar paani badh nahi raha ya river data nahi. */
    val hoursToDanger: Int?,

    val rainfallMm: Double,
    val riverLevelM: Double?,
    val dangerLevelM: Double?,
    val riverData: Boolean,

    /**
     * Ye data kab SAVE hua (device ka apna time).
     * KYUN server ka `generated_at` nahi: offline banner mein "2 ghante purana" dikhana hai.
     * Wo hisaab device ke clock se hi sahi banta hai. Server ka timestamp alag timezone
     * ya clock-skew mein ho sakta hai, aur us waqt server se poochh bhi nahi sakte.
     */
    val cachedAt: Long,
)

/** Is gaon ko mile alerts — offline mein bhi "aakhri chetavani kya thi" dikhna chahiye. */
@Entity(tableName = "cached_alert")
data class CachedAlert(
    @PrimaryKey val id: Int,
    val villageId: Int,
    val messageHi: String,
    val messageEn: String,
    val sentBy: String,
    val sentAt: String?,
)

/**
 * Nazdeeki shelter.
 * OFFLINE MEIN YE SABSE KAAM KI CHEEZ HAI: "kahan jaana hai" ka jawaab network ke bina
 * bhi milna chahiye. Yehi JalRakshak ka SMS se aage wala hissa hai.
 */
@Entity(tableName = "cached_shelter")
data class CachedShelter(
    @PrimaryKey val id: Int,
    val villageId: Int,
    val name: String,
    val lat: Double,
    val lng: Double,
    val capacity: Int,
)
