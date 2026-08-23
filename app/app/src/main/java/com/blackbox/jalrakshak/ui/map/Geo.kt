package com.blackbox.jalrakshak.ui.map

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geo.kt — offline geometry. Koi network, koi routing API nahi.
 *
 * KYUN APNA HISAAB: routing service (Google/Mapbox Directions) ko network chahiye aur
 * aksar API key. Baadh mein dono nahi hote. Seedhi doori aur disha bina kisi service ke
 * nikalti hai — aur wahi honest cheez hai jo hum offline de sakte hain.
 */

/**
 * haversineKm() — do points ke beech SEEDHI doori (great-circle), km mein.
 * INPUT : lat1, lng1, lat2, lng2 | OUTPUT: km
 * NOTE  : ye sadak ki doori NAHI hai. Asli chalne ki doori isse zyada hogi.
 */
fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
    return r * 2 * asin(min(1.0, sqrt(a)))
}

/**
 * bearingDeg() — "from" se "to" ki disha, degrees (0 = North, clockwise).
 * INPUT : from lat/lng, to lat/lng | OUTPUT: 0-360
 * Ye initial bearing hai — chhoti dooriyon (kuch km) pe practically constant rehta hai.
 */
fun bearingDeg(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dl = Math.toRadians(lng2 - lng1)
    val y = sin(dl) * cos(p2)
    val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

/**
 * compassPoint() — degrees ko 8-point disha mein badalta hai.
 * INPUT : bearing degrees | OUTPUT: "N" | "NE" | "E" ... (index)
 * KYUN 8 point, 16 nahi: gaon mein "uttar-poorv" kaafi hai. 16-point (NNE, ENE)
 * padhne mein confusing hota hai aur hamari accuracy bhi utni nahi.
 */
fun compassIndex(bearing: Double): Int = (((bearing + 22.5) % 360.0) / 45.0).toInt().coerceIn(0, 7)

/** Doori ko padhne layak banao — 1 km se kam pe metres. */
fun formatDistance(km: Double): Pair<String, String> =
    if (km < 1.0) Pair("${(km * 1000).toInt()}", "m") else Pair("%.1f".format(km), "km")

/** Do bearings ka farq (degrees), -180..180. Compass needle ke liye. */
fun bearingDelta(a: Double, b: Double): Double {
    var d = (b - a + 540.0) % 360.0 - 180.0
    if (abs(d) < 1e-9) d = 0.0
    return d
}

/**
 * COINCIDENT_KM — isse kam doori pe shelter ko "gaon ke andar hi" maanta hai.
 *
 * KYUN CHAHIYE: seed data mein do shelter apne gaon ke BILKUL same lat/lng pe hain
 * (Silchar Relief Camp / Cachar, aur North Lakhimpur Camp / Lakhimpur — dono 0 m).
 * Aise mein:
 *   - bearing ka koi matlab nahi (do same point ke beech disha hoti hi nahi)
 *   - "0 m away · North" likhna bakwaas hai
 *   - map ka fitBounds degenerate ho jaata hai (neeche OfflineMapScreen dekho)
 *
 * 50 m isliye ki GPS ki apni error itni hoti hai — usse kam doori pe "udhar jao"
 * bolna waise bhi bekaar hai.
 */
const val COINCIDENT_KM = 0.05
