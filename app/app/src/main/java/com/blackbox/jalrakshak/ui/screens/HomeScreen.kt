package com.blackbox.jalrakshak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackbox.jalrakshak.MainViewModel
import com.blackbox.jalrakshak.core.Lang
import com.blackbox.jalrakshak.core.LocalLang
import com.blackbox.jalrakshak.core.LocalStrings
import com.blackbox.jalrakshak.data.local.CachedShelter
import com.blackbox.jalrakshak.data.local.CachedVillage
import com.blackbox.jalrakshak.ui.components.AppText
import com.blackbox.jalrakshak.ui.components.AppCard
import com.blackbox.jalrakshak.ui.components.NumberDot
import com.blackbox.jalrakshak.ui.components.SectionTitle
import com.blackbox.jalrakshak.ui.components.levelIcon
import com.blackbox.jalrakshak.ui.components.levelLabel
import com.blackbox.jalrakshak.ui.theme.Blue
import com.blackbox.jalrakshak.ui.theme.CardBg
import com.blackbox.jalrakshak.ui.theme.Ink2
import com.blackbox.jalrakshak.ui.theme.Ink3
import com.blackbox.jalrakshak.ui.theme.Line
import com.blackbox.jalrakshak.ui.theme.Neutral
import com.blackbox.jalrakshak.ui.theme.Red
import com.blackbox.jalrakshak.ui.theme.riskColor
import com.blackbox.jalrakshak.ui.theme.riskTint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * =====================================================================================
 *  HomeScreen — app_mockup.html ka "Home · Danger" / "Home · Safe" screen
 * =====================================================================================
 *  Mockup ka order (upar se neeche = zaroorat ke hisaab se):
 *    .top          gaon ka naam + chevron (tap = gaon badlo) · bhasha toggle
 *    .status-card  circular icon + status shabd + ek line
 *    .metrics      rainfall | river level | to rise (3 columns, divider ke saath)
 *    .sect + .list "What to do" — numbered circular badges
 *    .shrow        nazdeeki shelter
 *    .btn red      "Ask for help" (SOS)
 *
 *  App KOI RISK CALCULATE NAHI KARTI — level, reason, advice, hours sab backend ke
 *  RiskEngine se ready-made aate hain. Yahan sirf dikhaya jaata hai.
 * =====================================================================================
 */
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onChangeVillage: () -> Unit,
    onSos: () -> Unit,
    onOpenMap: () -> Unit,
) {
    val s = LocalStrings.current
    val lang = LocalLang.current

    val village by vm.village.collectAsStateSafe()
    val shelters by vm.shelters.collectAsStateSafe()
    val offline by vm.offline.collectAsStateSafe()
    val refreshing by vm.refreshing.collectAsStateSafe()

    val v = village

    // Pehli baar: cache khaali hai aur network abhi chal raha hai.
    if (v == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (offline) {
                    AppText(
                        s.offlineNoData,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Ink2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { vm.refresh() }) { AppText(s.retry) }
                } else {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(14.dp))
                    AppText(s.loading, style = MaterialTheme.typography.bodyMedium, color = Ink2)
                }
            }
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
    ) {
        // ---------- .top : gaon + bhasha ----------
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            /**
             * Gaon ka naam = "gaon badlo" ka button.
             * Pehle ye plain text tha aur sirf chevron se pata chalta tha ki tappable hai.
             * Ab iske peeche halka pill background hai — tap karne layak dikhta hai.
             */
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Neutral)
                    .clickable { onChangeVillage() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Outlined.LocationOn, null, tint = Ink2, modifier = Modifier.size(16.dp))
                AppText(v.name, style = MaterialTheme.typography.titleMedium)
                Icon(
                    Icons.Outlined.KeyboardArrowDown, null,
                    tint = Ink2, modifier = Modifier.size(16.dp),
                )
            }

            /**
             * Bhasha toggle — ab ek saaf control hai.
             * Pehle bas "EN" likha tha jo label jaisa lagta tha, button jaisa nahi.
             * Ab bordered pill + globe icon: dikhta hai ki dabaya ja sakta hai, aur
             * batata hai ki dabane pe KYA milega (dusri bhasha ka naam).
             */
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Line, RoundedCornerShape(10.dp))
                    .clickable { vm.setLang(if (lang == Lang.HI) Lang.EN else Lang.HI) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(Icons.Outlined.Language, null, tint = Ink2, modifier = Modifier.size(15.dp))
                AppText(
                    if (lang == Lang.HI) "English" else "हिंदी",
                    style = MaterialTheme.typography.labelMedium,
                    color = Ink2,
                )
            }
        }

        // ---------- .status-card ----------
        StatusCard(v, lang)
        Spacer(Modifier.height(14.dp))

        // ---------- .metrics ----------
        MetricsRow(v, lang)
        Spacer(Modifier.height(14.dp))

        // ---------- .sect + .list : what to do ----------
        SectionTitle(s.whatToDo)
        AdviceList(v, lang)
        Spacer(Modifier.height(14.dp))

        // ---------- .shrow : shelter ----------
        ShelterRow(shelters.firstOrNull(), v, onOpenMap)
        Spacer(Modifier.height(18.dp))

        // ---------- .btn red : SOS ----------
        Button(
            onClick = onSos,
            colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = androidx.compose.ui.graphics.Color.White),
            shape = RoundedCornerShape(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 15.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.NotificationsActive, null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            AppText(s.sos, style = MaterialTheme.typography.labelLarge)
        }

        // Refresh — mockup mein nahi hai, par functionality thi. Isliye sabse neeche,
        // chhota aur halka: design ka rhythm nahi todta, aur feature bhi nahi khoya.
        Spacer(Modifier.height(6.dp))
        TextButton(
            onClick = { vm.refresh() },
            enabled = !refreshing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (refreshing) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
            } else {
                Icon(Icons.Outlined.Refresh, null, tint = Ink3, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(7.dp))
            AppText(s.refresh, style = MaterialTheme.typography.bodySmall, color = Ink3)
        }

        AppText(
            s.dataSourceNote,
            style = MaterialTheme.typography.bodySmall,
            color = Ink3,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

/** Mockup `.status-card` — 64dp circle + 24px status shabd + ek line explanation. */
@Composable
private fun StatusCard(v: CachedVillage, lang: Lang) {
    val color = riskColor(v.level)

    AppCard(padding = 20.dp) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(64.dp).background(riskTint(v.level), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(levelIcon(v.level), null, tint = color, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(16.dp))
            // maxLines=1: status shabd kabhi toot ke do line mein nahi jaana chahiye —
            // yehi wo ek cheez hai jo citizen sabse pehle padhta hai.
            AppText(
                levelLabel(v.level),
                style = MaterialTheme.typography.headlineSmall,
                color = color,
                maxLines = 1,
            )
            Spacer(Modifier.height(8.dp))
            // RiskEngine ka reason — ismein asli number hote hain, generic warning nahi.
            AppText(
                if (lang == Lang.HI) v.reasonHi else v.reasonEn,
                style = MaterialTheme.typography.bodyLarge,
                color = Ink2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Mockup `.metrics` — teen column, beech mein 1px divider.
 * Value bold + chhoti unit, neeche label.
 */
@Composable
private fun MetricsRow(v: CachedVillage, lang: Lang) {
    val s = LocalStrings.current

    Row(
        Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(16.dp))
            .border(1.dp, Line, RoundedCornerShape(16.dp)),
    ) {
        Metric(
            value = "%.0f".format(v.rainfallMm),
            unit = "mm",
            label = s.rainfallToday,
            modifier = Modifier.weight(1f),
        )
        Divider()
        // river_data false => is station ka threshold hi nahi (decision D10).
        // Tab number banane ke bajaye "—" dikhate hain.
        Metric(
            value = if (v.riverData && v.riverLevelM != null) "%.1f".format(v.riverLevelM) else "—",
            unit = if (v.riverData && v.riverLevelM != null) "m" else null,
            label = s.riverLevel,
            valueColor = if (v.riverData) riskColor(v.level) else Ink3,
            modifier = Modifier.weight(1f),
        )
        Divider()
        Metric(
            value = v.hoursToDanger?.let { "~$it" } ?: "—",
            unit = if (v.hoursToDanger != null) "hr" else null,
            label = if (v.hoursToDanger != null) s.toRise else s.noAlertShort,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Divider() {
    Box(Modifier.width(1.dp).height(64.dp).background(Line))
}

@Composable
private fun Metric(
    value: String,
    unit: String?,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        modifier.padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            AppText(value, style = MaterialTheme.typography.titleLarge, color = valueColor)
            if (unit != null) {
                AppText(
                    unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink3,
                    modifier = Modifier.padding(start = 1.dp, bottom = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        AppText(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Ink2,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Mockup `.list` with numbered `.dot` badges.
 *
 * KYUN SPLIT KARTE HAIN: backend `advice` ek paragraph deta hai
 * ("तुरंत नज़दीकी शरण स्थल जाएँ। ज़रूरी काग़ज़... साथ लें। बहते पानी में न चलें।")
 * Mockup usse numbered steps mein dikhata hai. Ye sirf DISPLAY ka farq hai — text
 * wahi RiskEngine ka hai, hum kuch jodte ya badalte nahi. Devanagari danda (।) aur
 * English full-stop dono pe todte hain.
 */
@Composable
private fun AdviceList(v: CachedVillage, lang: Lang) {
    val advice = if (lang == Lang.HI) v.adviceHi else v.adviceEn
    val steps = advice
        .split('।', '.')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    Column(
        Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(16.dp))
            .border(1.dp, Line, RoundedCornerShape(16.dp)),
    ) {
        steps.forEachIndexed { i, step ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Pehla step level ke rang mein (mockup: `.dot` red), baaki neutral (`.dot.n`)
                if (i == 0) {
                    NumberDot("${i + 1}", riskTint(v.level), riskColor(v.level))
                } else {
                    NumberDot("${i + 1}", Neutral, Ink2)
                }
                AppText(step, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Mockup `.shrow` — icon tile + naam + doori/capacity + chevron. */
@Composable
private fun ShelterRow(shelter: CachedShelter?, village: CachedVillage, onOpenMap: () -> Unit) {
    val s = LocalStrings.current

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            // Poori row tappable — chevron pehle se ye ishara kar raha tha, par kuch hota nahi tha.
            .clickable(enabled = shelter != null) { onOpenMap() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(38.dp).background(Neutral, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Home, null, tint = Ink2, modifier = Modifier.size(18.dp))
        }

        Column(Modifier.weight(1f)) {
            if (shelter == null) {
                AppText(s.noShelter, style = MaterialTheme.typography.titleMedium)
            } else {
                AppText(shelter.name, style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                ))
                Spacer(Modifier.height(2.dp))
                // Doori gaon ke centre se hai (dono lat/lng already cache mein hain).
                // Seedhi doori hai, road distance nahi — isliye "~".
                val km = haversineKm(village.lat, village.lng, shelter.lat, shelter.lng)
                AppText(
                    "~%.1f km · %s %,d".format(km, s.capacity, shelter.capacity),
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink2,
                )
            }
        }

        if (shelter != null) {
            // "Directions" — offline map kholta hai (disha + doori). Sirf chevron se
            // pata nahi chalta tha ki tap karne pe kya milega; ab likha hua hai.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(Icons.Outlined.Map, null, tint = Blue, modifier = Modifier.size(16.dp))
                AppText(s.directions, style = MaterialTheme.typography.labelMedium, color = Blue)
            }
        }
    }
}

/**
 * haversineKm() — do lat/lng ke beech seedhi doori.
 * INPUT: lat1,lng1,lat2,lng2 | OUTPUT: km
 * KYUN yahan: shelter aur gaon dono ke coords pehle se Room mein hain, to ye offline bhi
 * chalta hai. Routing API paise/key maangti hai aur network chahiye — flood mein dono nahi.
 */
private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
    return r * 2 * asin(min(1.0, sqrt(a)))
}
