package com.blackbox.jalrakshak.ui.map

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * =====================================================================================
 *  TileStore — bundled PMTiles ko app ke files dir mein rakhta hai
 * =====================================================================================
 *
 *  KYUN COPY KARNA PADA (pehle seedha assets se padhne ki koshish ki thi):
 *   Style mein `pmtiles://asset://assam.pmtiles` diya tha. APK mein file sahi thi —
 *   uncompressed ("Stored") aur header bhi sahi ("PMTiles" magic). Phir bhi MapLibre
 *   ka PMTilesFileSource native crash ho gaya:
 *
 *       std::runtime_error: incorrect header check   (thread: PMTilesFileSour)
 *
 *   "incorrect header check" zlib ka error hai — matlab usne jo bytes padhe wo valid
 *   gzip nahi the. PMTiles ko file ke BEECH se padhna hota hai (random access): pehle
 *   header, phir directory, phir sirf zaroori tile. Android ka `asset://` source ye
 *   range reads theek se support nahi karta — usse galat offset ke bytes mile aur
 *   decompress fail ho gaya.
 *
 *   Asli file path (`file://`) pe range reads bilkul theek chalte hain. Isliye app
 *   pehli baar assets se file ko filesDir mein copy karti hai aur uske baad wahi
 *   use karti hai.
 *
 *  YE "PEHLI LAUNCH PE DOWNLOAD" NAHI HAI — koi network nahi lagta. File already APK
 *  ke andar hai; hum bas use aisi jagah rakh rahe hain jahan se random access ho sake.
 *  Airplane mode mein bhi ye pehli baar hi chal jaata hai.
 * =====================================================================================
 */
object TileStore {

    private const val ASSET = "assam.pmtiles"
    private const val TAG = "JalRakshakTiles"

    /**
     * ensureTiles() — file ready karo aur MapLibre ke liye URL lautao.
     *
     * INPUT : context | OUTPUT: "pmtiles://file:///data/.../assam.pmtiles"
     *
     * Size compare karke skip karte hain: agar file pehle se sahi size ki hai to
     * dobara copy nahi hoti (har launch pe 35 MB likhna bewakoofi hoti).
     */
    suspend fun ensureTiles(context: Context): String = withContext(Dispatchers.IO) {
        val out = File(context.filesDir, ASSET)
        val expected = context.assets.openFd(ASSET).use { it.length }

        if (!out.exists() || out.length() != expected) {
            Log.i(TAG, "Copying $ASSET to filesDir (${expected / 1_000_000} MB)…")
            context.assets.open(ASSET).use { input ->
                out.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
            }
            Log.i(TAG, "Copied: ${out.length()} bytes")
        }

        "pmtiles://file://${out.absolutePath}"
    }

    /**
     * styleJson() — asset se style template padho aur PMTiles URL bhar do.
     *
     * INPUT : context, pmtiles url | OUTPUT: poora style JSON string
     * KYUN template: URL runtime pe hi pata chalta hai (filesDir ka path device pe
     * banta hai), isliye style file mein hardcode nahi kar sakte.
     */
    suspend fun styleJson(context: Context, pmtilesUrl: String): String = withContext(Dispatchers.IO) {
        context.assets.open("map_style.json").bufferedReader().use { it.readText() }
            .replace("{{PMTILES_URL}}", pmtilesUrl)
    }
}
