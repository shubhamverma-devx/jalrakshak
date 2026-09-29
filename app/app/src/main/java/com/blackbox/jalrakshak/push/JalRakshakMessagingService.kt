package com.blackbox.jalrakshak.push

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.blackbox.jalrakshak.MainActivity
import com.blackbox.jalrakshak.R
import com.blackbox.jalrakshak.core.Config
import com.blackbox.jalrakshak.data.Repository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * =====================================================================================
 *  JalRakshakMessagingService — officer ka alert phone tak.
 * =====================================================================================
 *
 *  YEHI WO JAGAH HAI JAHAN POORA PRODUCT SAARTHAK HOTA HAI. Officer dashboard pe button
 *  dabata hai -> Laravel FcmService push bhejta hai -> ye class use pakadti hai ->
 *  citizen ke phone pe asli notification bajti hai. Isse pehle sab kuch officer ki
 *  screen tak seemit tha.
 *
 *  DO KAAM:
 *   1. onNewToken()    — FCM naya token de to backend ko batao
 *   2. onMessageReceived() — alert aaye to system notification dikhao
 * =====================================================================================
 */
class JalRakshakMessagingService : FirebaseMessagingService() {

    /**
     * Service ka apna scope. SupervisorJob isliye ki ek call fail ho to doosri na ruke.
     * KYUN service ke andar scope: FirebaseMessagingService ka apna lifecycle scope nahi
     * hota, aur network call (token register) suspend function hai.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * onNewToken() — FCM ne naya registration token diya.
     *
     * KAB CHALTA HAI: pehli install pe, app data clear karne pe, ya jab Firebase khud
     * token rotate kare. App ka kaam hai turant backend ko batana — warna backend purane
     * (ab dead) token pe push bhejta rahega aur phone kabhi nahi bajega.
     *
     * INPUT: naya token | OUTPUT: kuch nahi (backend register ho jaata hai)
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "Naya FCM token mila")

        scope.launch {
            val repo = Repository(applicationContext)
            // Gaon chuna hi nahi hai to abhi register karne ka koi matlab nahi —
            // token kis gaon ke liye hai ye pata hi nahi. Village chunte hi
            // MainViewModel khud register kar dega.
            val villageId = repo.prefs.villageId.first() ?: return@launch
            repo.registerToken(token, villageId)
                .onFailure { Log.w(TAG, "Token register fail: ${it.message}") }
        }
    }

    /**
     * onMessageReceived() — server se push aaya.
     *
     * KAB CHALTA HAI: app foreground mein ho to HAMESHA. App background/band ho aur
     * message mein `notification` block ho, to Android khud notification bana deta hai
     * aur ye method call NAHI hota.
     *
     * KYUN phir bhi yahan notification banate hain: backend `notification` + `data`
     * DONO bhejta hai. Foreground case mein Android kuch nahi dikhata — us waqt ye code
     * hi notification banata hai. Iske bina app khuli hone par alert chup-chaap aata
     * aur citizen ko pata hi nahi chalta. Flood mein wo unacceptable hai.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        Log.i(TAG, "Push aaya: alert_id=${data[Config.EXTRA_ALERT_ID]}")

        // Title/body notification block se, warna data se — dono handle karte hain.
        val villageName = data["village_name"].orEmpty()
        val title = message.notification?.title
            ?: if (villageName.isNotEmpty()) "JalRakshak · $villageName" else "JalRakshak"

        // Hindi message default hai (BUILD_PLAN: Hindi primary).
        val body = message.notification?.body
            ?: data["message_hi"]
            ?: data["message_en"].orEmpty()

        if (body.isBlank()) return

        val alertId = data[Config.EXTRA_ALERT_ID]?.toIntOrNull() ?: 0
        val villageId = data[Config.EXTRA_VILLAGE_ID]?.toIntOrNull() ?: 0

        showNotification(title = title, body = body, alertId = alertId, villageId = villageId)

        /*
         * Notification dikhane ke SAATH alert local DB mein bhi daal do.
         *
         * KYUN: Alerts tab sirf local DB se padhta hai. Pehle yahan sirf notification
         * dikhti thi, to tab agle 5-minute refresh tak khaali rehta tha — aur
         * notification pe tap karne se wahi khaali tab khulta tha.
         *
         * Yahan network call NAHI karte, push ke data se hi row banate hain — isse ye
         * offline bhi chalta hai (push aa gaya matlab data aa gaya).
         */
        scope.launch {
            try {
                Repository(applicationContext).saveAlertFromPush(
                    alertId = alertId,
                    villageId = villageId,
                    messageHi = data["message_hi"].orEmpty(),
                    messageEn = data["message_en"].orEmpty(),
                    sentBy = data["sent_by"].orEmpty(),
                    sentAt = data["sent_at"],
                )
                Log.i(TAG, "Alert $alertId local DB mein save ho gaya")
            } catch (e: Exception) {
                // Save fail ho to notification phir bhi dikh chuki hai — wo zyada zaroori
                // hai. Agla refresh alert ko server se le aayega.
                Log.w(TAG, "Alert local DB mein save nahi hua: ${e.message}")
            }
        }
    }

    /**
     * showNotification() — asli system notification.
     *
     * INPUT : title, body (Hindi), alertId, villageId
     * OUTPUT: phone pe notification (awaaz ke saath)
     *
     * TAP KARNE PE: MainActivity khulti hai extras ke saath, aur app seedha Alerts tab
     * pe chali jaati hai (MainActivity.handleIntent dekho) — user ko dhoondhna na pade.
     */
    private fun showNotification(title: String, body: String, alertId: Int, villageId: Int) {
        val intent = Intent(this, MainActivity::class.java).apply {
            // Pehle se khuli app ke upar naya task na bane — wahi instance use ho.
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(Config.EXTRA_ALERT_ID, alertId)
            putExtra(Config.EXTRA_VILLAGE_ID, villageId)
        }

        val pending = PendingIntent.getActivity(
            this,
            alertId, // har alert ka apna request code, warna extras purane wale hi rehte hain
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, Config.ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(getColor(R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(body)
            // BigTextStyle: alert ka poora message ek line mein nahi samaata
            // ("Nadi ka paani khatre ke nishaan se 1.49 m upar hai. Turant..."),
            // aur aadha message padh ke citizen galat faisla le sakta hai.
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Android 7 aur neeche ke liye
            .setCategory(NotificationCompat.CATEGORY_ALARM) // ye emergency hai, promo nahi
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        // Android 13+ pe POST_NOTIFICATIONS permission chahiye. Na ho to notify() chup-chaap
        // fail hoti hai — isliye pehle check karke log karte hain, taaki debug mein pata chale.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "POST_NOTIFICATIONS permission nahi — notification nahi dikhega")
            return
        }

        // alertId ko notification id banate hain: ek hi alert do baar aaye to duplicate
        // nahi dikhega, par alag alerts alag-alag dikhenge (stack ho jaayenge).
        NotificationManagerCompat.from(this).notify(
            if (alertId != 0) alertId else NotificationManager.IMPORTANCE_DEFAULT,
            notification,
        )
    }

    companion object {
        private const val TAG = "JalRakshakFCM"
    }
}
