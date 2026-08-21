package com.blackbox.jalrakshak.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * ApiService — Laravel ke saare endpoints jo citizen app use karti hai.
 *
 * App officer wale endpoints (POST /api/alert, GET /api/relief) ko HAATH NAHI LAGATI —
 * wo dashboard ke hain. Citizen sirf padhta hai aur SOS bhejta hai.
 *
 * `suspend` functions: Retrofit inhe coroutine mein chalata hai, to main thread block
 * nahi hoti (warna Android app ko ANR se maar deta hai).
 */
interface ApiService {

    /**
     * Saare gaon — sirf village-picker screen ke liye chahiye (naam + district).
     * Risk bhi saath aata hai par picker use ignore karta hai.
     */
    @GET("villages")
    suspend fun getVillages(
        @Query("mode") mode: String = "live",
    ): VillagesResponse

    /**
     * Ek gaon ka poora detail — risk + shelters + recent alerts, sab ek call mein.
     * KYUN ek call: gaon mein network kharab hota hai. 3 alag call ka matlab 3 gune
     * failure points. Ek response aaya to poori home screen ban jaati hai — aur wahi
     * response Room mein offline cache ho jaata hai.
     */
    @GET("village/{id}")
    suspend fun getVillage(
        @Path("id") id: Int,
        @Query("mode") mode: String = "live",
    ): VillageDetailResponse

    /** Is gaon ke alerts (feed screen). */
    @GET("alerts")
    suspend fun getAlerts(
        @Query("village_id") villageId: Int,
        @Query("limit") limit: Int = 50,
    ): AlertsResponse

    /** SOS — officer dashboard pe turant dikhta hai. */
    @POST("relief")
    suspend fun sendRelief(@Body body: ReliefRequestBody): ReliefResponse

    /** FCM token register/update — app har launch pe bhejti hai. */
    @POST("register-token")
    suspend fun registerToken(@Body body: RegisterTokenBody): RegisterTokenResponse
}
