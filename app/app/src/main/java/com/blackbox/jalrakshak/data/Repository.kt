package com.blackbox.jalrakshak.data

import android.content.Context
import android.util.Log
import com.blackbox.jalrakshak.data.local.AppDatabase
import com.blackbox.jalrakshak.data.local.CachedAlert
import com.blackbox.jalrakshak.data.local.CachedShelter
import com.blackbox.jalrakshak.data.local.CachedVillage
import com.blackbox.jalrakshak.data.local.Prefs
import com.blackbox.jalrakshak.data.remote.Network
import com.blackbox.jalrakshak.data.remote.RegisterTokenBody
import com.blackbox.jalrakshak.data.remote.ReliefRequestBody
import com.blackbox.jalrakshak.data.remote.VillageDto

/**
 * =====================================================================================
 *  Repository — "kahan se data laayein" ka faisla ek hi jagah.
 * =====================================================================================
 *
 *  UI ko kabhi nahi pata hota ki data network se aaya ya cache se. Wo bas Room ko
 *  observe karti hai. Repository ka kaam sirf itna hai: network se laao aur Room mein
 *  daal do. Room badla to UI apne aap update ho jaati hai.
 *
 *  ============ OFFLINE STRATEGY (cache-first) ============
 *   1. UI hamesha ROOM se padhti hai — isliye app khulte hi TURANT kuch dikhta hai
 *      (bhale purana ho), khaali spinner nahi.
 *   2. Saath hi network call jaati hai. Safal hui to Room update -> UI apne aap naya
 *      dikha deti hai.
 *   3. Network fail hui to hum CACHE NAHI HATATE. Bas `lastSyncFailed` true karte hain,
 *      jisse UI "ऑफ़लाइन — पिछली जानकारी" banner dikhati hai.
 *
 *   KYUN aisa: baadh mein tower sabse pehle jaate hain. "Kuch nahi dikha" sabse bura
 *   outcome hai. Purana data + saaf label ("kitna purana") sabse imandaar aur kaam ka hai.
 *  =========================================================
 */
class Repository(context: Context) {

    private val dao = AppDatabase.get(context).cacheDao()
    private val api = Network.api
    val prefs = Prefs(context)

    // --- UI in flows ko observe karti hai (hamesha cache se) --------------------------
    fun observeVillage(villageId: Int) = dao.observeVillage(villageId)
    fun observeAlerts(villageId: Int) = dao.observeAlerts(villageId)

    /**
     * saveAlertFromPush() — push mein aaya alert SEEDHA local DB mein daal do.
     *
     * ============ YE KYUN ZAROORI HAI ============
     * Pehle push sirf notification dikhata tha; alert local DB mein jaata hi nahi tha.
     * Alerts tab sirf DB se padhta hai, aur DB `refreshVillage()` se bharta hai jo har
     * 5 MINUTE pe chalta hai. Nateeja: notification baj jaati thi par Alerts tab khaali
     * rehta tha — aur notification pe tap karke wahi khaali tab khulta tha. Demo mein
     * yehi sabse bura dikhta hai.
     *
     * Ab push aate hi alert DB mein chala jaata hai, to tab TURANT bhar jaata hai —
     * aur ye network ke bina bhi kaam karta hai (push aaya matlab data aa gaya, uske
     * baad server se dobara poochhne ki zaroorat nahi).
     *
     * `saveAlerts` REPLACE strategy pe hai, to wahi alert server refresh mein dobara
     * aaye to duplicate nahi banta — id same hai, row overwrite ho jaati hai.
     */
    suspend fun saveAlertFromPush(
        alertId: Int,
        villageId: Int,
        messageHi: String,
        messageEn: String,
        sentBy: String,
        sentAt: String?,
    ) {
        if (alertId == 0 || villageId == 0) return
        dao.saveAlerts(
            listOf(
                CachedAlert(
                    id = alertId,
                    villageId = villageId,
                    messageHi = messageHi,
                    messageEn = messageEn,
                    sentBy = sentBy.ifBlank { "District Control Room" },
                    sentAt = sentAt?.ifBlank { null },
                )
            )
        )
    }
    fun observeShelters(villageId: Int) = dao.observeShelters(villageId)

    /**
     * villageList() — picker ke liye saare gaon.
     *
     * OUTPUT: Result<List<VillageDto>>
     * KYUN ye cache nahi hota: village list sirf PEHLI BAAR chahiye (gaon chunte waqt),
     * aur us waqt network hona hi chahiye — bina list ke gaon chun hi nahi sakte.
     * Uske baad app ko poori list ki zaroorat hi nahi padti.
     */
    suspend fun villageList(): Result<List<VillageDto>> = runCatching {
        api.getVillages().villages.sortedBy { it.name }
    }

    /**
     * refreshVillage() — is gaon ka risk + shelters + alerts server se laao aur cache karo.
     *
     * INPUT : villageId
     * OUTPUT: Result<Unit> — success/failure. UI isse offline banner decide karti hai.
     *
     * KYUN alerts ke DO source (detail response + alerts endpoint):
     *   /api/village/{id} ke saath sirf 10 recent alerts aate hain (home screen ke liye
     *   kaafi). Alerts TAB ko poori list chahiye, isliye /api/alerts alag se call hota hai.
     *   Dono ek hi Room table bharte hain, to koi duplicate nahi (id primary key hai).
     */
    suspend fun refreshVillage(villageId: Int): Result<Unit> = runCatching {
        val detail = api.getVillage(villageId)
        val v = detail.village
        val r = v.risk

        dao.saveVillage(
            CachedVillage(
                id = v.id,
                name = v.name,
                district = v.district,
                lat = v.lat,
                lng = v.lng,
                population = v.population,
                elevationM = v.elevationM,
                level = r.level,
                score = r.score,
                reasonHi = r.reasonHi,
                reasonEn = r.reasonEn,
                adviceHi = r.adviceHi,
                adviceEn = r.adviceEn,
                waterEtaHi = r.waterEtaHi,
                waterEtaEn = r.waterEtaEn,
                hoursToDanger = r.hoursToDanger,
                rainfallMm = r.factors.rainfallMm,
                riverLevelM = r.factors.riverLevelM,
                dangerLevelM = r.factors.dangerLevelM,
                riverData = r.factors.riverData,
                cachedAt = System.currentTimeMillis(),
            ),
        )

        dao.replaceShelters(
            villageId,
            detail.shelters.map {
                CachedShelter(it.id, villageId, it.name, it.lat, it.lng, it.capacity)
            },
        )

        // Poori alerts list alag endpoint se.
        val alerts = api.getAlerts(villageId).alerts
        dao.replaceAlerts(
            villageId,
            alerts.map {
                CachedAlert(it.id, villageId, it.messageHi, it.messageEn, it.sentBy, it.sentAt)
            },
        )
    }

    /**
     * sendSos() — POST /api/relief.
     *
     * INPUT : villageId, lat, lng, message
     * OUTPUT: Result<String> — server ka confirmation message
     *
     * KYUN ye offline queue NAHI karta:
     *   Socha tha ki network na ho to SOS locally save karke baad mein bhejein. Par flood
     *   mein wo KHATARNAK hai — citizen ko lagega madad maang li, jabki request phone mein
     *   padi hai. Isse behtar hai saaf bata dena "nahi bheja ja saka", taaki wo doosra
     *   tareeka (phone call, padosi) aazma sake. Jhoothi tasalli se jaan ja sakti hai.
     */
    suspend fun sendSos(villageId: Int, lat: Double, lng: Double, message: String): Result<String> =
        runCatching {
            api.sendRelief(ReliefRequestBody(villageId, lat, lng, message)).message
        }

    /**
     * registerToken() — FCM token backend ko do, taaki push aa sake.
     *
     * INPUT : token, villageId
     * OUTPUT: Result<Unit>
     *
     * KYUN har launch pe (aur gaon badalne pe): FCM token kabhi bhi refresh ho sakta hai
     * (reinstall, data clear, ya Firebase khud rotate kare). Backend `token` pe upsert
     * karta hai, isliye baar-baar bhejne se koi duplicate nahi banta — bas sync rehta hai.
     * Ye na karein to officer alert bhejta rahega aur phone kabhi bajega hi nahi.
     */
    suspend fun registerToken(token: String, villageId: Int): Result<Unit> = runCatching {
        api.registerToken(RegisterTokenBody(token = token, villageId = villageId))
        prefs.setTokenSynced(true)
        Log.i(TAG, "FCM token registered for village $villageId")
    }

    companion object {
        private const val TAG = "JalRakshakRepo"
    }
}
