package com.blackbox.jalrakshak

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.Alignment
import com.blackbox.jalrakshak.ui.components.AppText
import com.blackbox.jalrakshak.ui.components.OfflineBanner
import com.blackbox.jalrakshak.ui.map.OfflineMapScreen
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    val offline by vm.offline.collectAsStateSafe()
    val shelters by vm.shelters.collectAsStateSafe()

    var tab by remember { mutableStateOf(Tab.HOME) }
    var showSos by remember { mutableStateOf(false) }
    // Offline map screen (shelter "Directions" se khulta hai)
    var showMap by remember { mutableStateOf(false) }
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
            VillagePickerScreen(
                vm = vm,
                onSelected = { forcePicker = false },
                // Pehli launch pe back nahi (gaon chunna zaroori hai). "Gaon badlein"
                // se aaye ho to back milta hai — bina badle wapas ja sako.
                onBack = if (villageId != null) ({ forcePicker = false }) else null,
            )
            return@CompositionLocalProvider
        }

        // Offline map — poori screen leta hai (apna back button andar hai).
        val v = village
        if (showMap && v != null) {
            OfflineMapScreen(village = v, shelters = shelters, onBack = { showMap = false })
            return@CompositionLocalProvider
        }

        /**
         * ============ CHROME: mockup ke hisaab se ============
         *  TopAppBar HATA diya — mockup mein har screen apna heading khud rakhti hai
         *  (Home pe gaon ka naam + bhasha, Alerts pe bada "Alerts"). Ek extra app bar
         *  screen ki jagah khaata aur design se match nahi karta.
         *
         *  FAB bhi hataya — mockup mein SOS ek full-width inline button hai jo content
         *  ke saath scroll hota hai. Functionality wahi hai, bas jagah design wali.
         *
         *  Offline banner ab sabse upar hai (mockup ka `.off`) — pehle wo Home screen
         *  ke andar tha. Upar hone se wo har tab pe dikhta hai, jo zyada sahi hai.
         * ====================================================
         */
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { BottomNav(tab, s) { tab = it } },
        ) { inner ->
            Column(Modifier.fillMaxSize().padding(inner)) {
                if (offline) OfflineBanner(village?.cachedAt)

                Box(Modifier.weight(1f)) {
                    when (tab) {
                        Tab.HOME -> HomeScreen(
                            vm = vm,
                            onChangeVillage = { forcePicker = true },
                            onSos = { showSos = true },
                            onOpenMap = { showMap = true },
                        )
                        Tab.ALERTS -> AlertsScreen(vm)
                    }
                }
            }
        }

        if (showSos && village != null) {
            SosSheet(vm) { showSos = false }
        }
    }
}

/**
 * BottomNav — mockup ka `.nav`.
 *
 * Card background, upar 1px border, line icons, active item BLUE.
 * Material ka NavigationBar use nahi kiya kyunki wo apna pill-shaped indicator aur
 * elevation le aata hai — mockup bilkul flat hai.
 *
 * ---- MOCKUP MEIN 4 TABS HAIN, YAHAN 2 KYUN ----
 * Mockup mein Home · Alerts · Shelter · Profile dikhte hain. App mein abhi sirf do
 * screens hain. Shelter aur Profile add karna matlab NAYE screens banana — aur ye
 * task "visual only, koi behaviour change nahi" tha.
 *
 * Dead tabs dikhana (jo tap pe kuch na karein) demo mein isse bhi bura hota. Isliye
 * abhi wahi do tabs hain jo asli hain. (Shelter/Profile aage jodna aasan hai — shelter
 * list already API se aati hai, aur profile mein bhasha + gaon badalna aa sakta hai.)
 */
@Composable
private fun BottomNav(
    current: Tab,
    s: com.blackbox.jalrakshak.core.Strings,
    onSelect: (Tab) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(com.blackbox.jalrakshak.ui.theme.CardBg)
            .padding(top = 1.dp)
            .padding(top = 11.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        NavItem(
            icon = Icons.Outlined.Home,
            label = s.tabHome,
            active = current == Tab.HOME,
        ) { onSelect(Tab.HOME) }

        NavItem(
            icon = Icons.Outlined.Notifications,
            label = s.tabAlerts,
            active = current == Tab.ALERTS,
        ) { onSelect(Tab.ALERTS) }
    }
}

@Composable
private fun NavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (active) {
        com.blackbox.jalrakshak.ui.theme.Blue
    } else {
        com.blackbox.jalrakshak.ui.theme.Ink3
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clickable(
                // Ripple hata diya — mockup flat hai, aur nav pe ripple bhaari lagta hai.
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onClick() }
            .padding(horizontal = 24.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
        AppText(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}
