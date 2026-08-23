package com.blackbox.jalrakshak.ui.map

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.blackbox.jalrakshak.core.LocalStrings
import com.blackbox.jalrakshak.data.local.CachedShelter
import com.blackbox.jalrakshak.data.local.CachedVillage
import com.blackbox.jalrakshak.ui.components.AppCard
import com.blackbox.jalrakshak.ui.components.AppText
import com.blackbox.jalrakshak.ui.theme.Blue
import com.blackbox.jalrakshak.ui.theme.CardBg
import com.blackbox.jalrakshak.ui.theme.Green
import com.blackbox.jalrakshak.ui.theme.Ink
import com.blackbox.jalrakshak.ui.theme.Ink2
import com.blackbox.jalrakshak.ui.theme.Ink3
import com.blackbox.jalrakshak.ui.theme.Line
import com.blackbox.jalrakshak.ui.theme.Neutral
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * =====================================================================================
 *  OfflineMapScreen — gaon, shelters aur "kis taraf jaana hai" — BINA NETWORK ke
 * =====================================================================================
 *
 *  KYUN YE FEATURE HAI: baadh mein mobile tower sabse pehle jaate hain. Theek us waqt
 *  jab citizen ko "shelter kahan hai" jaanna hota hai, Google Maps khulta hi nahi.
 *  Ye screen poori tarah device pe chalti hai — koi API call nahi, koi key nahi.
 *
 *  ---- YE KAISE OFFLINE CHALTA HAI ----
 *  1. Basemap: `assets/assam.pmtiles` — Protomaps/OpenStreetMap ka vector extract,
 *     sirf hamare Assam demo bbox ka (89.5-96.0 E, 24.4-28.0 N), zoom 0-12.
 *     APK ke andar hai, to fresh install pe bhi bina internet ke chalta hai.
 *  2. MapLibre Native khud `pmtiles://asset://` padh leta hai — koi tile server nahi.
 *  3. Style (`assets/map_style.json`) mein koi TEXT layer nahi hai. Kyun: vector text ke
 *     liye glyph (font) files chahiye hoti hain jo aam taur pe network se aati hain.
 *     Labels hum Compose overlay se dikhate hain — apne bundled fonts ke saath.
 *
 *  ---- HUM KYA NAHI DE RAHE (aur ye saaf bolna hai) ----
 *  Turn-by-turn routing NAHI hai. Wo routing engine maangta hai. Hum seedhi doori,
 *  disha (compass bearing) aur map pe seedhi line dikhate hain. Ye kam hai — par
 *  sach hai, aur baadh mein "shelter उधर hai, 2 km" bhi bahut kaam ka hota hai.
 * =====================================================================================
 */
@SuppressLint("MissingPermission")
@Composable
fun OfflineMapScreen(
    village: CachedVillage,
    shelters: List<CachedShelter>,
    onBack: () -> Unit,
) {
    val s = LocalStrings.current
    val context = LocalContext.current

    // Sabse paas ka shelter — seedhi doori se.
    val nearest = remember(village.id, shelters) {
        shelters.minByOrNull { haversineKm(village.lat, village.lng, it.lat, it.lng) }
    }
    val distKm = remember(nearest) {
        nearest?.let { haversineKm(village.lat, village.lng, it.lat, it.lng) }
    }
    val bearing = remember(nearest) {
        nearest?.let { bearingDeg(village.lat, village.lng, it.lat, it.lng) }
    }

    Column(Modifier.fillMaxSize()) {
        // ---------- top bar with BACK ----------
        Row(
            Modifier
                .fillMaxWidth()
                .background(CardBg)
                // Status bar ke neeche se shuru karo — ye screen full-bleed hai
                // (Scaffold ke bahar), to inset khud lagana padta hai. Bina iske
                // title ghadi ke peeche chala jaata hai.
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .background(Neutral, RoundedCornerShape(10.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.ArrowBack, null, tint = Ink, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                AppText(s.mapTitle, style = MaterialTheme.typography.titleMedium)
                AppText(
                    s.mapOfflineNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink3,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))

        Box(Modifier.weight(1f)) {
            // ---------- MapLibre ----------
            MapLibreView(village = village, shelters = shelters)

            // ---------- legend chip ----------
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(CardBg, RoundedCornerShape(10.dp))
                    .border(1.dp, Line, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LegendDot(Blue, s.yourVillage)
                LegendDot(Green, s.nearestShelter)
            }
        }

        // ---------- direction card ----------
        Column(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp),
        ) {
            if (nearest == null || distKm == null || bearing == null) {
                AppCard(padding = 16.dp) {
                    AppText(s.noShelter, style = MaterialTheme.typography.titleMedium)
                }
            } else {
                // Shelter gaon ke andar hi ho to disha/doori ka koi matlab nahi —
                // "0 m away · North" likhna galat aur confusing hai.
                val coincident = distKm < COINCIDENT_KM

                AppCard(padding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(46.dp).background(Green.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (coincident) {
                                // Compass ki jagah ghar ka icon — koi disha dikhane ko hai hi nahi.
                                Icon(
                                    Icons.Outlined.Home, null,
                                    tint = Green, modifier = Modifier.size(24.dp),
                                )
                            } else {
                                // Compass needle — shelter ki disha mein ghoomta hai.
                                Icon(
                                    Icons.Filled.Navigation, null,
                                    tint = Green,
                                    modifier = Modifier.size(24.dp).rotate(bearing.toFloat()),
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            AppText(
                                nearest.name,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(3.dp))
                            AppText(
                                if (coincident) {
                                    "${s.inYourVillage} · ${s.capacity} ${nearest.capacity}"
                                } else {
                                    val (num, unit) = formatDistance(distKm)
                                    "$num $unit ${s.away} · ${s.compass[compassIndex(bearing)]} · ${s.capacity} ${nearest.capacity}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Ink2,
                            )
                        }
                    }
                    // Straight-line disclaimer sirf tab jab sach mein doori ho.
                    if (!coincident) {
                        Spacer(Modifier.height(10.dp))
                        AppText(
                            s.straightLineNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = Ink3,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(9.dp).background(color, CircleShape))
        AppText(label, style = MaterialTheme.typography.bodySmall, color = Ink2)
    }
}

/**
 * MapLibreView — MapView ko Compose mein wrap karta hai.
 *
 * MapView Android ka classic View hai aur uska apna lifecycle hai (onStart/onResume/...).
 * Agar wo lifecycle forward na karein to map background se aane pe blank aa jaata hai
 * aur memory leak hoti hai. Isliye DisposableEffect se lifecycle jodte hain.
 */
@Composable
private fun MapLibreView(village: CachedVillage, shelters: List<CachedShelter>) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    /**
     * Style runtime pe banta hai kyunki PMTiles ka file path device pe hi pata chalta hai
     * (TileStore ka comment padho — asset:// se range reads fail hote hain).
     * null jab tak file copy ho rahi hai — pehli launch pe ek-do second.
     */
    var styleJson by remember { mutableStateOf<String?>(null) }
    // Style sirf EK baar lage — update lambda har recomposition pe chalta hai.
    var styleApplied by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val url = TileStore.ensureTiles(context)
        styleJson = TileStore.styleJson(context, url)
    }

    /**
     * MapLibre.getInstance() har MapView banane se PEHLE call hona chahiye.
     * apiKey null — MapLibre ko koi key chahiye hi nahi.
     *
     * ---- setConnected(true) KYUN ----
     * MapLibre apna ConnectivityReceiver rakhta hai aur airplane mode mein khud ko
     * "offline" maan leta hai (log: `Mbgl-ConnectivityReceiver: connected - false`).
     * Us state mein wo resource loading ko network-gated maan ke rok deta hai —
     * aur hamare tiles LOCAL FILE se aate hain, network se nahi.
     * Isliye use explicitly bata dete hain ki connected samjho. Ye jhooth nahi hai:
     * hum sach mein kabhi network chhoote hi nahi, sab kuch device pe hai.
     */
    remember {
        MapLibre.getInstance(context)
        org.maplibre.android.net.ConnectivityReceiver.instance(context).setConnected(true)
        Unit
    }

    val mapView = remember { MapView(context) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        // Diagnostics: style/map fail ho to blank screen ke bajaye log mile.
        mapView.addOnDidFailLoadingMapListener { err ->
            android.util.Log.e("JalRakshakMap", "map load FAILED: $err")
        }
        mapView.onCreate(null)

        /**
         * ---- YE LINE ZAROORI HAI (ek asli bug tha) ----
         * LifecycleEventObserver sirf AAGE ke events deta hai, purane replay nahi karta.
         * Ye screen tab mount hoti hai jab Activity PEHLE SE RESUMED hai — to ON_START
         * aur ON_RESUME kabhi aate hi nahi. MapView bina onStart/onResume ke apna GL
         * surface initialise nahi karta, `getMapAsync` ka callback kabhi fire nahi hota,
         * aur style load hi nahi hoti.
         *
         * Nateeja: khaali beige map, koi error, koi log — kuch nahi. Debug karna mushkil.
         * Isliye current state ko manually catch-up karate hain.
         */
        val state = lifecycleOwner.lifecycle.currentState
        if (state.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (state.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    /**
     * AndroidView HAMESHA compose hota hai — pehle isko `if (json == null) return` ke
     * peeche rakha tha, aur wo ek asli bug tha: json null->non-null hone pe composable
     * ka SHAPE badal jaata tha, Compose AndroidView node ko naya slot deta tha, aur
     * MapView ka GL init poora hone se pehle hi wo dobara attach ho jaata tha —
     * `getMapAsync` ka callback kabhi fire hi nahi hota (na error, na log, bas khaali map).
     *
     * Ab View hamesha wahi rehta hai; sirf STYLE tab lagti hai jab tiles ready hon.
     */
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                val json = styleJson ?: return@AndroidView
                if (styleApplied) return@AndroidView
                styleApplied = true
                view.getMapAsync { map ->
                    android.util.Log.i("JalRakshakMap", "setStyle, json chars=${json.length}")
                    map.setStyle(Style.Builder().fromJson(json)) { style ->
                    android.util.Log.i(
                        "JalRakshakMap",
                        "style loaded: sources=${style.sources.size} layers=${style.layers.size}",
                    )
                    MapMarkers.draw(context, style, village, shelters)
                    android.util.Log.i("JalRakshakMap", "markers drawn, layers=${style.layers.size}")
                }

                    /**
                     * ---- CAMERA (yahan ek asli bug tha) ----
                     * Pehle hamesha `newLatLngBounds(village, shelter)` karte the.
                     * Dikkat: kuch gaon ke shelter unke BILKUL same lat/lng pe hain
                     * (Cachar → Silchar Relief Camp, Lakhimpur → North Lakhimpur Camp;
                     * dono 0 m). Do same points ka bounding box degenerate hota hai, aur
                     * fitBounds usmein zoom ko maximum tak le jaata hai — hamare tiles
                     * zoom 12 tak hain, to screen KHAALI aa jaati thi aur markers
                     * bematlab bade dikhte the. User ne Cachar chun ke yahi report kiya.
                     *
                     * Ab: max zoom clamp + coincident case pe fitBounds bilkul use hi nahi karte.
                     */
                    map.setMaxZoomPreference(15.0)   // tiles z12 tak hain; thoda overzoom theek hai
                    map.setMinZoomPreference(4.0)

                    val nearest = shelters.minByOrNull {
                        haversineKm(village.lat, village.lng, it.lat, it.lng)
                    }
                    val d = nearest?.let { haversineKm(village.lat, village.lng, it.lat, it.lng) }
                    val villagePos = LatLng(village.lat, village.lng)

                    when {
                        // Shelter gaon ke andar hi hai — bounds ka koi matlab nahi.
                        nearest == null || d == null || d < COINCIDENT_KM ->
                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(villagePos, 14.0))

                        else -> {
                            val bounds = LatLngBounds.Builder()
                                .include(villagePos)
                                .include(LatLng(nearest.lat, nearest.lng))
                                .build()
                            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 140))
                        }
                    }
                }
            },
        )

        // Tiles ready hone tak halka overlay — blank map se behtar hai batana ki kya ho raha hai.
        if (styleJson == null) {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(12.dp))
                    AppText(
                        LocalStrings.current.mapPreparing,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink2,
                    )
                }
            }
        }
    }
}
