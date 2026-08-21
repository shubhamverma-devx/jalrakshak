package com.blackbox.jalrakshak.core

/**
 * Config — poore app ki settings EK jagah.
 *
 * KYUN EK FILE (CLAUDE.md convention): API ka URL 6 files mein bikhra hota to droplet pe
 * deploy karte waqt har jagah dhoondhna padta. Yahan ek line badlo, poora app badal jaata hai.
 */
object Config {

    /**
     * Laravel API ka base URL.
     *
     * ============ 10.0.2.2 KYA HAI (ye sabse zyada confuse karta hai) ============
     * Emulator ek alag virtual machine hai. Uske andar "localhost" ka matlab KHUD EMULATOR
     * hota hai, laptop nahi. Laptop pe chal raha `php artisan serve` emulator ke liye
     * localhost pe hai hi nahi — isliye har call "connection refused" se fail hoti.
     *
     * Android emulator laptop (host) ko ek fixed address deta hai: 10.0.2.2
     * Yahi wajah hai ki yahan localhost:8000 nahi, 10.0.2.2:8000 likha hai.
     *
     * ASLI DEVICE PE: 10.0.2.2 kaam nahi karega. Tab laptop ka LAN IP daalo
     * (jaise http://192.168.1.5:8000/api) — dono ek hi wifi pe hone chahiye.
     *
     * DROPLET PE (Day 4): https://jalrakshak.<domain>/api — aur tab
     * network_security_config.xml ki cleartext chhoot ki zaroorat hi nahi rahegi.
     * =============================================================================
     */
    const val API_BASE_URL = "http://10.0.2.2:8000/api/"

    /**
     * Risk kitni der mein khud refresh ho (milliseconds).
     * KYUN 5 min: backend ka scheduler waise bhi har 30 min chalta hai aur live risk map
     * 15 min cache hota hai (BUILD_PLAN section 6). Isse tez poll karne se sirf citizen
     * ka mobile data aur battery jalti, naya data milta hi nahi.
     */
    const val REFRESH_INTERVAL_MS = 5 * 60 * 1000L

    /** Notification channel id — ye backend ke FcmService ke channel_id se EXACT match hona chahiye. */
    const val ALERT_CHANNEL_ID = "jalrakshak_alerts"

    /** Push tap karne pe MainActivity ko ye extras milte hain. */
    const val EXTRA_ALERT_ID = "alert_id"
    const val EXTRA_VILLAGE_ID = "village_id"
}
