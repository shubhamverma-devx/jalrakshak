package com.blackbox.jalrakshak.core

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * =====================================================================================
 *  Bhasha system — Hindi + English (BUILD_PLAN section 2: sirf ye do, LOCKED)
 * =====================================================================================
 *
 *  KYUN res/values-hi/strings.xml NAHI USE KIYA:
 *   Android ka standard tareeka locale-based hai — phone ki bhasha Hindi ho to values-hi
 *   uthata hai. Par hamari requirement alag hai: app ke ANDAR ek toggle hona chahiye
 *   (section 2 ka locked feature). Locale badalne ke liye Activity recreate karni padti
 *   hai — screen blink karti hai aur scroll position/state khoti hai. Flood alert padhte
 *   waqt wo bura experience hai.
 *
 *   Yahan `Lang` bas ek state hai. Toggle dabao -> CompositionLocal badalta hai ->
 *   Compose sirf text dobara draw karta hai. Instant, koi flicker nahi.
 *
 *  BONUS: dono bhasha ek hi file mein saath-saath dikhti hain, to koi string translate
 *  hone se chhoot nahi sakti (compiler hi nahi banne dega — data class hai).
 * =====================================================================================
 */

/** Do hi bhasha. */
enum class Lang { HI, EN }

/**
 * Har UI string. `data class` isliye ki agar koi nayi string add ho aur ek bhasha mein
 * bhoolein, to COMPILE ERROR aayega — runtime pe khaali text nahi dikhega.
 */
data class Strings(
    val appName: String,
    val tagline: String,

    // --- village picker ---
    val chooseVillage: String,
    val chooseVillageSub: String,
    val searchVillage: String,
    val noVillageFound: String,
    val confirm: String,

    // --- home ---
    val yourVillage: String,
    val changeVillage: String,
    val whatToDo: String,
    val rainfallToday: String,
    val riverLevel: String,
    val dangerMark: String,
    val toRise: String,        // metrics row ka teesra column (mockup: "To rise")
    val noAlertShort: String,  // jab hours_to_danger null ho (mockup: "No alert")

    // --- offline map ---
    val mapTitle: String,
    val mapOfflineNote: String,
    val mapPreparing: String,
    val directions: String,
    val away: String,
    val straightLineNote: String,
    val inYourVillage: String,   // jab shelter gaon ke andar hi ho (doori ~0)
    val compass: List<String>,   // 8 dishayein — index compassIndex() se aata hai
    val back: String,
    val language: String,
    val noRiverData: String,
    val nearestShelter: String,
    val noShelter: String,
    val capacity: String,
    val lastUpdated: String,
    val refresh: String,

    // --- risk levels ---
    val levelRed: String,
    val levelYellow: String,
    val levelGreen: String,

    // --- offline ---
    val offlineBanner: String,
    val offlineNoData: String,
    val retry: String,

    // --- alerts ---
    val alerts: String,
    val noAlerts: String,
    val noAlertsSub: String,
    val from: String,

    // --- SOS ---
    val sos: String,
    val sosTitle: String,
    val sosSub: String,
    val sosMessageHint: String,
    val sosSend: String,
    val sosSending: String,
    val sosSent: String,
    val sosFailed: String,
    val sosEmptyMessage: String,
    val cancel: String,
    val locationAttached: String,
    val locationMissing: String,

    // --- nav ---
    val tabHome: String,
    val tabAlerts: String,

    // --- misc ---
    val loading: String,
    val dataSourceNote: String,
)

/** Hindi (default — gaon mein yahi padha jaata hai). Roman script, taaki har koi padh sake. */
val HindiStrings = Strings(
    appName = "जलरक्षक",
    tagline = "बाढ़ पूर्व-चेतावनी",

    chooseVillage = "अपना गाँव चुनें",
    chooseVillageSub = "आपको सिर्फ़ इसी गाँव की चेतावनी मिलेगी",
    searchVillage = "गाँव खोजें",
    noVillageFound = "कोई गाँव नहीं मिला",
    confirm = "आगे बढ़ें",

    yourVillage = "आपका गाँव",
    changeVillage = "गाँव बदलें",
    whatToDo = "क्या करें",
    rainfallToday = "आज की बारिश",
    riverLevel = "नदी का स्तर",
    dangerMark = "ख़तरे का निशान",
    toRise = "बढ़ने में",
    noAlertShort = "कोई चेतावनी नहीं",

    mapTitle = "शरण स्थल का नक़्शा",
    mapOfflineNote = "बिना इंटरनेट के भी चलता है",
    mapPreparing = "नक़्शा तैयार हो रहा है…",
    directions = "रास्ता",
    away = "दूर",
    // Ye line ZAROORI hai — hum turn-by-turn नहीं de rahe, aur ye chhupana nahi chahiye.
    straightLineNote = "यह सीधी दूरी और दिशा है — सड़क का रास्ता इससे लंबा होगा।",
    inYourVillage = "आपके गाँव में ही है",
    compass = listOf("उत्तर", "उत्तर-पूर्व", "पूर्व", "दक्षिण-पूर्व", "दक्षिण", "दक्षिण-पश्चिम", "पश्चिम", "उत्तर-पश्चिम"),
    back = "वापस",
    language = "भाषा",
    noRiverData = "नदी का डेटा नहीं",
    nearestShelter = "नज़दीकी शरण स्थल",
    noShelter = "कोई शरण स्थल दर्ज नहीं",
    capacity = "क्षमता",
    lastUpdated = "अंतिम अपडेट",
    refresh = "अपडेट करें",

    levelRed = "ख़तरा",
    levelYellow = "चेतावनी",
    levelGreen = "सुरक्षित",

    offlineBanner = "ऑफ़लाइन — पिछली जानकारी दिखाई जा रही है",
    offlineNoData = "इंटरनेट नहीं है और कोई पुरानी जानकारी भी सहेजी नहीं है।",
    retry = "फिर कोशिश करें",

    alerts = "चेतावनियाँ",
    noAlerts = "अभी कोई चेतावनी नहीं",
    noAlertsSub = "ख़तरा होने पर यहाँ और आपके फ़ोन पर सूचना आएगी",
    from = "भेजने वाले",

    sos = "मदद माँगें",
    sosTitle = "मदद माँगें",
    sosSub = "आपकी जगह और संदेश अधिकारी को तुरंत दिखेगा",
    sosMessageHint = "क्या हुआ? कितने लोग हैं?",
    sosSend = "भेजें",
    sosSending = "भेजा जा रहा है…",
    sosSent = "आपकी मदद की माँग भेज दी गई है",
    sosFailed = "भेजने में दिक़्क़त हुई",
    sosEmptyMessage = "कृपया कुछ लिखें",
    cancel = "रद्द करें",
    locationAttached = "आपकी जगह जोड़ दी गई",
    locationMissing = "जगह नहीं मिली — गाँव की जगह भेजी जाएगी",

    tabHome = "स्थिति",
    tabAlerts = "चेतावनियाँ",

    loading = "लोड हो रहा है…",
    dataSourceNote = "बारिश का डेटा: Open-Meteo",
)

/** English. */
val EnglishStrings = Strings(
    appName = "JalRakshak",
    tagline = "Flood early-warning",

    chooseVillage = "Choose your village",
    chooseVillageSub = "You'll only get alerts for this village",
    searchVillage = "Search village",
    noVillageFound = "No village found",
    confirm = "Continue",

    yourVillage = "Your village",
    changeVillage = "Change village",
    whatToDo = "What to do",
    rainfallToday = "Rainfall today",
    riverLevel = "River level",
    dangerMark = "Danger mark",
    toRise = "To rise",
    noAlertShort = "No alert",

    mapTitle = "Shelter map",
    mapOfflineNote = "Works without internet",
    mapPreparing = "Preparing map…",
    directions = "Directions",
    away = "away",
    straightLineNote = "This is straight-line distance and direction — the road route will be longer.",
    inYourVillage = "Inside your village",
    compass = listOf("North", "North-east", "East", "South-east", "South", "South-west", "West", "North-west"),
    back = "Back",
    language = "Language",
    noRiverData = "No river data",
    nearestShelter = "Nearest shelter",
    noShelter = "No shelter mapped",
    capacity = "Capacity",
    lastUpdated = "Last updated",
    refresh = "Refresh",

    levelRed = "Danger",
    levelYellow = "Warning",
    levelGreen = "Safe",

    offlineBanner = "Offline — showing last known information",
    offlineNoData = "No internet, and nothing saved from before.",
    retry = "Retry",

    alerts = "Alerts",
    noAlerts = "No alerts yet",
    noAlertsSub = "If there's danger, it'll appear here and on your phone",
    from = "From",

    sos = "Ask for help",
    sosTitle = "Ask for help",
    sosSub = "Your location and message go straight to the officer",
    sosMessageHint = "What happened? How many people?",
    sosSend = "Send",
    sosSending = "Sending…",
    sosSent = "Your request for help has been sent",
    sosFailed = "Could not send",
    sosEmptyMessage = "Please write something",
    cancel = "Cancel",
    locationAttached = "Your location was attached",
    locationMissing = "Location unavailable — village location will be used",

    tabHome = "Status",
    tabAlerts = "Alerts",

    loading = "Loading…",
    dataSourceNote = "Rainfall data: Open-Meteo",
)

fun stringsFor(lang: Lang): Strings = if (lang == Lang.HI) HindiStrings else EnglishStrings

/**
 * LocalStrings — har Composable bina prop drilling ke `LocalStrings.current.sos` likh sake.
 * KYUN staticCompositionLocalOf: bhasha bahut kam badalti hai. "static" variant tab
 * zyada tez hota hai (Compose har read ko track nahi karta, badalne pe poora subtree
 * dobara banata hai — jo yahan bilkul theek hai).
 */
val LocalStrings = staticCompositionLocalOf { HindiStrings }
val LocalLang = staticCompositionLocalOf { Lang.HI }
