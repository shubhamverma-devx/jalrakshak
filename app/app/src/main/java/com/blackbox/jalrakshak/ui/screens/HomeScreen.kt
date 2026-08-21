package com.blackbox.jalrakshak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackbox.jalrakshak.MainViewModel
import com.blackbox.jalrakshak.core.Lang
import com.blackbox.jalrakshak.core.LocalLang
import com.blackbox.jalrakshak.core.LocalStrings
import com.blackbox.jalrakshak.ui.components.InfoRow
import com.blackbox.jalrakshak.ui.components.LevelBadge
import com.blackbox.jalrakshak.ui.components.OfflineBanner
import com.blackbox.jalrakshak.ui.components.Panel
import com.blackbox.jalrakshak.ui.theme.isDark
import com.blackbox.jalrakshak.ui.theme.riskColor
import com.blackbox.jalrakshak.ui.theme.riskTint

/**
 * =====================================================================================
 *  HomeScreen — citizen ki main screen: "mere gaon ka kya haal hai".
 * =====================================================================================
 *  DATA: Room cache se (Repository -> observeVillage). Background mein network refresh
 *        hota rehta hai; fail ho to offline banner aata hai par data dikhta rehta hai.
 *
 *  SCREEN KA ORDER JAAN-BUJH KE AISA HAI (upar se neeche = zaroorat ke hisaab se):
 *   1. RISK CARD    — sabse bada, rangeen. "khatra hai ya nahi" ek nazar mein.
 *   2. KYA KAREIN   — RiskEngine ki advice. Sirf "khatra hai" bolna SMS bhi karta hai;
 *                     asli value "ab kya karo" batane mein hai.
 *   3. SHELTER      — "kahan jaana hai". Offline mein bhi ye dikhta hai (Room se).
 *   4. NUMBERS      — barish, nadi ka level. Ye detail chahne walon ke liye, isliye neeche.
 *
 *  App KOI RISK CALCULATE NAHI KARTI — level, reason, advice sab backend ke RiskEngine
 *  se ready-made aate hain (CLAUDE.md convention). Yahan sirf dikhaya jaata hai.
 * =====================================================================================
 */
@Composable
fun HomeScreen(vm: MainViewModel, onChangeVillage: () -> Unit) {
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
                    // Cache bhi khaali aur network bhi nahi — yahi ek case hai jahan
                    // hum sach mein kuch nahi dikha sakte. Saaf bolo, spinner mat ghumao.
                    Icon(
                        Icons.Outlined.Info, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        s.offlineNoData,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { vm.refresh() }) { Text(s.retry) }
                } else {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(s.loading, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        return
    }

    val level = v.level
    val color = riskColor(level)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (offline) OfflineBanner(v.cachedAt)

        // ---------- 1. RISK CARD ----------
        Column(
            Modifier
                .fillMaxWidth()
                .background(riskTint(level, isDark()), RoundedCornerShape(14.dp))
                .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                .padding(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(
                        s.yourVillage,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(v.name, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        v.district,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LevelBadge(level)
            }

            Spacer(Modifier.height(14.dp))

            // RiskEngine ka reason — chuni hui bhasha mein. Isme asli number hote hain
            // ("khatre ke nishaan se 1.49 m upar"), generic warning nahi.
            Text(
                if (lang == Lang.HI) v.reasonHi else v.reasonEn,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )

            val eta = if (lang == Lang.HI) v.waterEtaHi else v.waterEtaEn
            if (eta.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    eta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---------- 2. KYA KAREIN ----------
        Panel {
            Text(
                s.whatToDo,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (lang == Lang.HI) v.adviceHi else v.adviceEn,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        // ---------- 3. SHELTER ----------
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Outlined.Home, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    s.nearestShelter,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))

            val shelter = shelters.firstOrNull()
            if (shelter == null) {
                Text(s.noShelter, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    shelter.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "${s.capacity}: ${shelter.capacity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---------- 4. NUMBERS ----------
        Panel {
            InfoRow(s.rainfallToday, "%.1f mm".format(v.rainfallMm))

            // river_data false => is station ka threshold data hi nahi (decision D10).
            // Tab "नदी का डेटा नहीं" likhte hain — banaya hua number kabhi nahi dikhate.
            if (v.riverData && v.riverLevelM != null) {
                InfoRow(s.riverLevel, "%.2f m".format(v.riverLevelM))
                v.dangerLevelM?.let {
                    InfoRow(s.dangerMark, "%.2f m".format(it), valueColor = riskColor("red"))
                }
            } else {
                InfoRow(s.riverLevel, s.noRiverData)
            }
        }

        // ---------- footer ----------
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onChangeVillage) { Text(s.changeVillage) }

            TextButton(onClick = { vm.refresh() }, enabled = !refreshing) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.height(0.dp))
                Text("  ${s.refresh}")
            }
        }

        // Data honesty (DATA_NOTES.md ka rule — dashboard pe bhi yahi chip hai).
        Text(
            s.dataSourceNote,
            style = MaterialTheme.typography.labelMedium,
            // outline itna halka tha ki emulator pe padha hi nahi ja raha tha —
            // onSurfaceVariant halka to hai par readable.
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        Spacer(Modifier.height(60.dp)) // SOS button ke neeche jagah
    }
}
