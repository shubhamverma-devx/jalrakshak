package com.blackbox.jalrakshak.data.remote

import com.blackbox.jalrakshak.BuildConfig
import com.blackbox.jalrakshak.core.Config
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Network — Retrofit client banane ki ek hi jagah.
 *
 * KYUN object (singleton): har screen apna Retrofit banati to har baar naya OkHttp
 * thread-pool aur connection pool banta — battery aur memory dono barbaad. Ek client
 * poore app ke liye kaafi hai, aur wo connections reuse karta hai.
 */
object Network {

    /**
     * JSON parser.
     *  ignoreUnknownKeys — backend naya field jode (jaise Day 4 mein kuch), to purani
     *    app crash na ho. Ye flood app hai; forward-compatibility zaroori hai.
     *  coerceInputValues — null aaye jahan non-null expect kiya ho, to default le lo.
     *  explicitNulls=false — request body mein null fields bhejne se bachta hai.
     */
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val client = OkHttpClient.Builder()
        // Gaon ka network slow hota hai — thoda sabra rakho, par anant nahi.
        // 20s ke baad user ko "offline" dikhana behtar hai bajaye spinner ghumate rehne ke.
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        // Network hichki pe ek baar khud retry — gaon mein bahut kaam aata hai.
        .retryOnConnectionFailure(true)
        .apply {
            // Logging SIRF debug build mein. Release mein ye citizen ka SOS message aur
            // location logcat mein likh deta — privacy leak.
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
                )
            }
        }
        .build()

    val api: ApiService = Retrofit.Builder()
        .baseUrl(Config.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ApiService::class.java)
}
