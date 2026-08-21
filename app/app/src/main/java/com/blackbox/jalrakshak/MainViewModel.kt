package com.blackbox.jalrakshak

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blackbox.jalrakshak.core.Config
import com.blackbox.jalrakshak.core.Lang
import com.blackbox.jalrakshak.data.Repository
import com.blackbox.jalrakshak.data.local.CachedAlert
import com.blackbox.jalrakshak.data.local.CachedShelter
import com.blackbox.jalrakshak.data.local.CachedVillage
import com.blackbox.jalrakshak.data.remote.VillageDto
import com.google.android.gms.location.LocationServices
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * =====================================================================================
 *  MainViewModel — app ka poora state ek jagah.
 * =====================================================================================
 *  KYUN ek hi ViewModel (har screen ka apna nahi): app chhoti hai aur teeno screen
 *  EK HI cheez ke around hain — chuna hua gaon. Alag ViewModels hote to gaon badalne pe
 *  teeno ko sync karna padta. Ek ViewModel = ek source of truth.
 *
 *  UI hamesha ROOM se padhti hai (Repository ka comment dekho) — isliye offline mein bhi
 *  screen bhari rehti hai.
 * =====================================================================================
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository(app)

    // --- Settings (DataStore se) -----------------------------------------------------
    val villageId: StateFlow<Int?> = repo.prefs.villageId
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val lang: StateFlow<Lang> = repo.prefs.lang
        .stateIn(viewModelScope, SharingStarted.Eagerly, Lang.HI)

    /**
     * Pehli baar prefs padhne mein ek pal lagta hai. Us pal mein villageId null hota hai,
     * jisse app galti se village-picker dikha deti (jabki gaon chuna hua hai) — screen
     * blink karti. Ye flag batata hai ki prefs padhi ja chuki hain.
     */
    private val _prefsLoaded = MutableStateFlow(false)
    val prefsLoaded: StateFlow<Boolean> = _prefsLoaded.asStateFlow()

    // --- Cache se aane wala data (villageId badle to apne aap switch ho jaata hai) ----
    val village: StateFlow<CachedVillage?> = villageId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repo.observeVillage(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val alerts: StateFlow<List<CachedAlert>> = villageId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observeAlerts(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val shelters: StateFlow<List<CachedShelter>> = villageId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observeShelters(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Network status ---------------------------------------------------------------
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** true = aakhri sync fail hui -> UI "offline" banner dikhaye. */
    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline.asStateFlow()

    // --- Village picker ---------------------------------------------------------------
    private val _villageList = MutableStateFlow<List<VillageDto>>(emptyList())
    val villageList: StateFlow<List<VillageDto>> = _villageList.asStateFlow()

    private val _villageListError = MutableStateFlow<String?>(null)
    val villageListError: StateFlow<String?> = _villageListError.asStateFlow()

    // --- SOS --------------------------------------------------------------------------
    private val _sosState = MutableStateFlow<SosState>(SosState.Idle)
    val sosState: StateFlow<SosState> = _sosState.asStateFlow()

    sealed interface SosState {
        data object Idle : SosState
        data object Sending : SosState
        data class Sent(val message: String) : SosState
        data class Failed(val message: String) : SosState
    }

    init {
        viewModelScope.launch {
            // Pehli value aate hi picker/home ka faisla ho sakta hai.
            repo.prefs.villageId.first()
            _prefsLoaded.value = true
        }

        /**
         * Gaon pata chalte hi refresh, aur phir har 5 min.
         *
         * ============ YE PEHLE TOOTA HUA THA (emulator pe pakda gaya) ============
         *  Pehle yahan `villageId.value?.let { refresh() }` tha — ek `while(true)` loop
         *  ke andar. Dikkat: `villageId` DataStore se aata hai, jo ASYNC hai. init ke
         *  waqt uski value abhi bhi `null` hoti hai (disk se padha hi nahi gaya).
         *  To pehla iteration chup-chaap skip ho jaata tha, aur loop 5 MINUTE so jaata.
         *
         *  Nateeja: app launch pe refresh hota hi nahi tha. Purana cache dikhta rehta,
         *  `offline` flag kabhi set nahi hota, aur offline banner kabhi nahi aata.
         *  Airplane mode mein test karte waqt yahi pakda gaya — banner aaya hi nahi.
         *
         *  Ab `.value` padhne ke bajaye flow ko COLLECT karte hain, to jaise hi asli
         *  village id aati hai refresh chal padta hai.
         *
         *  collectLatest: gaon badle to purana refresh-loop cancel hoke naya shuru ho
         *  jaata hai — warna do loop saath chalte aur dono alag gaon ka data laate.
         * ========================================================================
         */
        viewModelScope.launch {
            villageId.filterNotNull().collectLatest {
                while (true) {
                    refresh()
                    delay(Config.REFRESH_INTERVAL_MS)
                }
            }
        }

        // FCM token backend ko bhejo (gaon pata chalte hi).
        viewModelScope.launch {
            repo.prefs.villageId.collect { id -> if (id != null) syncToken(id) }
        }
    }

    /**
     * refresh() — server se naya risk laao.
     * OUTPUT: kuch nahi; `offline` flag set hota hai jo UI banner dikhata hai.
     * KYUN failure pe cache nahi hatate: purana data + saaf label, khaali screen se behtar.
     */
    fun refresh() {
        val id = villageId.value ?: return
        viewModelScope.launch {
            _refreshing.value = true
            repo.refreshVillage(id)
                .onSuccess { _offline.value = false }
                .onFailure {
                    _offline.value = true
                    Log.w(TAG, "Refresh fail: ${it.message}")
                }
            _refreshing.value = false
        }
    }

    /** Village picker ke liye list laao. */
    fun loadVillageList() {
        viewModelScope.launch {
            _villageListError.value = null
            repo.villageList()
                .onSuccess { _villageList.value = it }
                .onFailure { _villageListError.value = it.message }
        }
    }

    /** Gaon chuna — save karo, token dobara register karo, data laao. */
    fun selectVillage(id: Int, name: String) {
        viewModelScope.launch {
            repo.prefs.setVillage(id, name)
            syncToken(id)
            // refresh() yahan call karne ki zaroorat nahi — upar wala collectLatest
            // villageId badalte hi khud naya refresh loop shuru kar deta hai.
        }
    }

    fun setLang(lang: Lang) {
        viewModelScope.launch { repo.prefs.setLang(lang) }
    }

    /**
     * syncToken() — FCM token lo aur backend ko do.
     *
     * KYUN har baar (app launch + gaon badalne pe): token kabhi bhi badal sakta hai.
     * Backend token pe upsert karta hai, to duplicate nahi bante. Ye na ho to officer
     * ka alert bheja to jaayega par is phone tak kabhi nahi pahunchega.
     */
    private fun syncToken(villageId: Int) {
        viewModelScope.launch {
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                repo.registerToken(token, villageId).getOrThrow()
            }.onFailure { Log.w(TAG, "Token sync fail: ${it.message}") }
        }
    }

    /**
     * sendSos() — madad ki request bhejo.
     *
     * INPUT : message (citizen ne likha)
     * OUTPUT: sosState badalta hai (Sending -> Sent/Failed)
     *
     * LOCATION: pehle phone ka asli GPS try karte hain. Na mile (permission nahi, ya
     * indoor) to GAON ka lat/lng bhejte hain — kuch na bhejne se behtar hai, kam se kam
     * officer ko gaon to pata chale. Ye baat UI mein bhi saaf likhi hai
     * ("जगह नहीं मिली — गाँव की जगह भेजी जाएगी"), chhupate nahi.
     */
    @SuppressLint("MissingPermission") // permission UI mein check hoti hai; yahan fallback hai
    fun sendSos(message: String, hasLocationPermission: Boolean) {
        val v = village.value ?: return
        viewModelScope.launch {
            _sosState.value = SosState.Sending

            var lat = v.lat
            var lng = v.lng

            if (hasLocationPermission) {
                runCatching {
                    val client = LocationServices.getFusedLocationProviderClient(getApplication())
                    client.lastLocation.await()?.let { lat = it.latitude; lng = it.longitude }
                }.onFailure { Log.w(TAG, "Location nahi mili, gaon ka lat/lng bhej rahe hain") }
            }

            repo.sendSos(v.id, lat, lng, message)
                .onSuccess { _sosState.value = SosState.Sent(it) }
                .onFailure { _sosState.value = SosState.Failed(it.message.orEmpty()) }
        }
    }

    fun resetSos() { _sosState.value = SosState.Idle }

    companion object {
        private const val TAG = "JalRakshakVM"
    }
}
