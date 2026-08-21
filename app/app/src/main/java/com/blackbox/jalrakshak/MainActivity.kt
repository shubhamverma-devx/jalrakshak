package com.blackbox.jalrakshak

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blackbox.jalrakshak.core.Config
import com.blackbox.jalrakshak.core.Lang
import com.blackbox.jalrakshak.core.LocalLang
import com.blackbox.jalrakshak.core.LocalStrings
import com.blackbox.jalrakshak.core.stringsFor
import com.blackbox.jalrakshak.ui.screens.AlertsScreen
import com.blackbox.jalrakshak.ui.screens.HomeScreen
import com.blackbox.jalrakshak.ui.screens.SosSheet
import com.blackbox.jalrakshak.ui.screens.VillagePickerScreen
import com.blackbox.jalrakshak.ui.screens.collectAsStateSafe
import com.blackbox.jalrakshak.ui.theme.JalRakshakTheme
import com.blackbox.jalrakshak.ui.theme.riskColor

/**
 * =====================================================================================
 *  MainActivity — app ka single screen host.
 * =====================================================================================
 *  KYUN ek hi Activity (Compose ka standard): saari screens Composable hain, navigation
 *  ek simple state variable se hota hai. App mein sirf 3 screens hain — Navigation
 *  library ka poora setup yahan over-engineering hoti.
 *
 *  PUSH SE KHULNA: notification tap karne pe Android is Activity ko extras ke saath
 *  kholta hai. handleIntent() unhe padh ke seedha Alerts tab pe le jaata hai.
 * =====================================================================================
 */
class MainActivity : ComponentActivity() {

    /**
     * Push tap se aaya hai? — Compose isko padh ke Alerts tab kholta hai.
     * KYUN mutableStateOf: onNewIntent() baad mein bhi aa sakta hai (app pehle se khuli ho),
     * aur us waqt Compose ko turant pata chalna chahiye.
     */
    private var openAlertsFromPush by mutableStateOf(false)

    /**
     * Android 13+ (API 33) pe notification dikhane ke liye RUNTIME PERMISSION chahiye.
     *
     * KYUN YE ITNA ZAROORI HAI: bina iske FCM push aata to hai par CHUP-CHAAP GIR JAATA
     * hai — koi error nahi, notification bas nahi dikhta. Ye Android ka sabse common
     * "FCM kaam nahi kar raha" ka kaaran hai. Emulator API 33+ pe bhi yahi hota hai.
     */
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* mile ya na mile, app chalti rahegi — bas notification nahi dikhega */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)
        askNotificationPermission()

        setContent {
            JalRakshakTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    JalRakshakApp(
                        openAlerts = openAlertsFromPush,
                        onAlertsOpened = { openAlertsFromPush = false },
                    )
                }
            }
        }
    }

    /** App pehle se khuli ho aur notification tap ho — tab ye chalta hai (onCreate nahi). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * handleIntent() — push ke extras padho.
     *
     * INPUT: Intent | OUTPUT: openAlertsFromPush set hota hai
     * KYUN: citizen ne notification isliye dabaya kyunki wo ALERT padhna chahta hai.
     * Usse home screen pe chhod dena aur khud tab dhoondhne dena bura design hai.
     *
     * ============ EXTRA STRING BHI HO SAKTA HAI, INT BHI (ye bug tha) ============
     *  Do bilkul alag raaste se ye Activity khulti hai:
     *
     *   (a) APP FOREGROUND MEIN THI — hamara JalRakshakMessagingService.onMessageReceived
     *       chala aur usne khud PendingIntent banaya. Usmein humne putExtra(Int) kiya tha,
     *       to extra INT hai.
     *
     *   (b) APP BACKGROUND/BAND THI — is case mein onMessageReceived chalta hi NAHI.
     *       Android khud `notification` payload se notification bana deta hai, aur FCM ka
     *       `data` payload intent extras mein daalta hai — par SAB STRING ke roop mein
     *       (FCM data ki values hamesha string hoti hain).
     *
     *  Pehle yahan sirf getIntExtra() tha. Case (b) mein wo String extra ko padh hi nahi
     *  paata, chup-chaap default 0 lauta deta — aur app Home tab pe khulti thi, Alerts pe
     *  nahi. Emulator pe test karne pe yahi hua. Ab dono roop handle karte hain.
     * ============================================================================
     */
    private fun handleIntent(intent: Intent?) {
        val raw = intent?.extras?.get(Config.EXTRA_ALERT_ID)

        val alertId = when (raw) {
            is Int -> raw
            is String -> raw.toIntOrNull() ?: 0
            else -> 0
        }

        if (alertId != 0) openAlertsFromPush = true
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** App ke do tab. */
private enum class Tab { HOME, ALERTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JalRakshakApp(openAlerts: Boolean, onAlertsOpened: () -> Unit) {
    val vm: MainViewModel = viewModel()

    val villageId by vm.villageId.collectAsStateSafe()
    val prefsLoaded by vm.prefsLoaded.collectAsStateSafe()
    val lang by vm.lang.collectAsStateSafe()
    val village by vm.village.collectAsStateSafe()

    var tab by remember { mutableStateOf(Tab.HOME) }
    var showSos by remember { mutableStateOf(false) }
    // Gaon chuna hua ho par user "gaon badlein" dabaye — tab picker dobara dikhana hai.
    var forcePicker by remember { mutableStateOf(false) }

    // Push se aaye to seedha Alerts tab.
    LaunchedEffect(openAlerts) {
        if (openAlerts) { tab = Tab.ALERTS; onAlertsOpened() }
    }

    // Bhasha poore app mein CompositionLocal se pahunchti hai — koi prop drilling nahi.
    CompositionLocalProvider(
        LocalStrings provides stringsFor(lang),
        LocalLang provides lang,
    ) {
        val s = LocalStrings.current

        // prefs padhne se pehle kuch mat dikhao — warna ek pal ke liye galat screen
        // (picker) flash karti hai jabki gaon pehle se chuna hua hai.
        if (!prefsLoaded) {
            Box(Modifier.fillMaxSize())
            return@CompositionLocalProvider
        }

        // Gaon chuna hi nahi (pehli launch) ya user badalna chahta hai.
        if (villageId == null || forcePicker) {
            VillagePickerScreen(vm) { forcePicker = false }
            return@CompositionLocalProvider
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(s.appName, fontWeight = FontWeight.SemiBold)
                    },
                    navigationIcon = {
                        Icon(
                            Icons.Outlined.WaterDrop, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 14.dp),
                        )
                    },
                    actions = {
                        /**
                         * Bhasha toggle — HI / EN.
                         * KYUN plain text button (dropdown nahi): sirf DO bhasha hain
                         * (BUILD_PLAN section 2 LOCKED). Do options ke liye dropdown
                         * kholna ek faltu tap hai. Ek tap = bhasha badal gayi.
                         */
                        TextButton(onClick = { vm.setLang(if (lang == Lang.HI) Lang.EN else Lang.HI) }) {
                            Icon(Icons.Outlined.Translate, null, modifier = Modifier.padding(end = 6.dp))
                            Text(
                                if (lang == Lang.HI) "EN" else "हिं",
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = tab == Tab.HOME,
                        onClick = { tab = Tab.HOME },
                        icon = { Icon(Icons.Outlined.WaterDrop, null) },
                        label = { Text(s.tabHome) },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.ALERTS,
                        onClick = { tab = Tab.ALERTS },
                        icon = { Icon(Icons.Outlined.Notifications, null) },
                        label = { Text(s.tabAlerts) },
                    )
                }
            },
            floatingActionButton = {
                /**
                 * SOS button — hamesha dikhta hai, dono tab pe.
                 * KYUN FAB (menu mein chhupa hua nahi): flood mein ye sabse urgent action
                 * hai. Do tap ki doori pe nahi hona chahiye. Rang RED — aur poori app
                 * mein yahi ek laal button hai, isliye galti se nahi dabega.
                 */
                ExtendedFloatingActionButton(
                    onClick = { showSos = true },
                    containerColor = riskColor("red"),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Outlined.Notifications, null, modifier = Modifier.padding(end = 8.dp))
                    Text(s.sos, fontWeight = FontWeight.SemiBold)
                }
            },
        ) { inner ->
            Box(Modifier.fillMaxSize().padding(inner)) {
                when (tab) {
                    Tab.HOME -> HomeScreen(vm) { forcePicker = true }
                    Tab.ALERTS -> AlertsScreen(vm)
                }
            }
        }

        if (showSos && village != null) {
            SosSheet(vm) { showSos = false }
        }
    }
}
