package com.blackbox.jalrakshak

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.blackbox.jalrakshak.core.Config

/**
 * JalRakshakApp — app start hote hi ek hi kaam: notification channel banana.
 *
 * KYUN CHANNEL ZAROORI HAI (Android 8+):
 *  Bina channel ke notification CHUP-CHAAP FAIL hoti hai — koi error nahi, kuch dikhta hi
 *  nahi. Ye Android ka sabse confusing behaviour hai aur FCM debug karte waqt ghante
 *  kha jaata hai.
 *
 * KYUN yahan (Application mein), notification bhejte waqt nahi:
 *  Push tab bhi aa sakta hai jab app kabhi khuli hi na ho. Channel pehle se hona chahiye.
 *  Application.onCreate() har case mein chalta hai — service start hone se bhi pehle.
 *
 * CHANNEL ID backend ke FcmService ke `channel_id` se EXACT match karta hai
 * (Config.ALERT_CHANNEL_ID = "jalrakshak_alerts"). Ek bhi akshar alag hua to
 * notification nahi dikhega.
 */
class JalRakshakApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createAlertChannel()
    }

    private fun createAlertChannel() {
        val channel = NotificationChannel(
            Config.ALERT_CHANNEL_ID,
            getString(R.string.alert_channel_name),
            // IMPORTANCE_HIGH = heads-up banner + awaaz.
            // KYUN sabse ooncha: ye baadh ki chetavani hai. Chup notification ka koi
            // matlab nahi — citizen so raha ho to bhi usse pata chalna chahiye.
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.alert_channel_desc)
            enableVibration(true)
            enableLights(true)
        }

        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
