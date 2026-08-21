package com.blackbox.jalrakshak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.blackbox.jalrakshak.ui.components.EmptyState
import com.blackbox.jalrakshak.ui.components.OfflineBanner
import com.blackbox.jalrakshak.ui.components.Panel

/**
 * AlertsScreen — officer ne is gaon ko jo chetavaniyan bheji, unki list.
 *
 * DATA: Room cache (Repository.observeAlerts) — jo /api/alerts se bhara jaata hai.
 *
 * KYUN YE SCREEN, JAB PUSH NOTIFICATION AATA HI HAI:
 *  Notification swipe ho jaata hai, ya phone silent tha, ya battery band thi. Alert
 *  KAHIN reh jaana chahiye jahan citizen use dobara padh sake. Aur ye list OFFLINE bhi
 *  kaam karti hai (Room se) — network jaane ke baad bhi aakhri chetavani padhi ja sakti
 *  hai. Yehi "SMS se aage" ka ek hissa hai: SMS bhi rehta hai, par usmein "kya karo"
 *  aur shelter nahi hota.
 *
 * Push tap karke aane pe app seedha isi tab pe khulti hai (MainActivity dekho).
 */
@Composable
fun AlertsScreen(vm: MainViewModel) {
    val s = LocalStrings.current
    val lang = LocalLang.current

    val alerts by vm.alerts.collectAsStateSafe()
    val offline by vm.offline.collectAsStateSafe()
    val village by vm.village.collectAsStateSafe()

    if (alerts.isEmpty()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            EmptyState(Icons.Outlined.NotificationsNone, s.noAlerts, s.noAlertsSub)
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (offline) {
            item { OfflineBanner(village?.cachedAt) }
        }

        items(alerts, key = { it.id }) { alert ->
            Panel {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    // Alert icon — accent tint (dashboard ke feed `.alert` item jaisa).
                    Icon(
                        Icons.Outlined.NotificationsNone, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                RoundedCornerShape(8.dp),
                            )
                            .padding(6.dp)
                            .size(18.dp),
                    )

                    Column {
                        // Alert ka text chuni hui bhasha mein. Officer ne DONO bhasha likhi
                        // thi (backend `message_hi` + `message_en`) — isliye yahan runtime
                        // translation ki zaroorat hi nahi, jo flood mein galat ho sakti thi.
                        Text(
                            if (lang == Lang.HI) alert.messageHi else alert.messageEn,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            "${s.from}: ${alert.sentBy}${alert.sentAt?.let { " · " + formatSentAt(it) } ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * formatSentAt() — ISO timestamp ko chhote readable roop mein.
 * INPUT : "2026-08-21T14:00:15+00:00" | OUTPUT: "21 Aug, 14:00"
 * KYUN itna simple: yahan sirf "kab bheja tha" dikhana hai. Poora date-time library
 * (java.time formatting + locale) is ek line ke liye over-engineering hoti.
 */
private fun formatSentAt(iso: String): String = runCatching {
    val date = iso.substringBefore('T')          // 2026-08-21
    val time = iso.substringAfter('T').take(5)   // 14:00
    val (_, m, d) = date.split("-")
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    "${d.toInt()} ${months[m.toInt() - 1]}, $time"
}.getOrDefault(iso)
