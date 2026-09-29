package com.blackbox.jalrakshak.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blackbox.jalrakshak.MainViewModel
import com.blackbox.jalrakshak.core.Lang
import com.blackbox.jalrakshak.core.LocalLang
import com.blackbox.jalrakshak.core.LocalStrings
import com.blackbox.jalrakshak.ui.components.AppText
import com.blackbox.jalrakshak.ui.components.AppCard
import com.blackbox.jalrakshak.ui.components.EmptyState
import com.blackbox.jalrakshak.ui.theme.Ink2
import com.blackbox.jalrakshak.ui.theme.Ink3
import com.blackbox.jalrakshak.ui.theme.Red
import com.blackbox.jalrakshak.ui.theme.RedBg

/**
 * AlertsScreen — app_mockup.html ka "Alerts" screen.
 *
 * Mockup ka `.al` card: chhota rangeen icon badge + title + timestamp (right) + message.
 *
 * DATA MAPPING (jo humare paas hai, wahi dikhate hain):
 *   badge   -> laal warning icon (alert matlab hi chetavani hai)
 *   title   -> `sent_by` (jaise "DC Barpeta") — kis officer ne bheja
 *   time    -> `sent_at`
 *   message -> chuni hui bhasha ka message
 *
 * KYUN title mein sender: mockup mein "Flood warning" / "Stay alert" jaisa title hai,
 * par hamare alerts mein per-alert severity ka koi field hai hi nahi. Wo severity
 * banana matlab data invent karna. Sender asli hai, aur officer ka naam dikhna
 * bharosa bhi badhata hai.
 */
@Composable
fun AlertsScreen(vm: MainViewModel) {
    val s = LocalStrings.current
    val lang = LocalLang.current

    val alerts by vm.alerts.collectAsStateSafe()

    if (alerts.isEmpty()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            EmptyState(Icons.Outlined.NotificationsNone, s.noAlerts, s.noAlertsSub)
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp),
    ) {
        // Mockup ka bada "Alerts" heading screen ke andar hai, top bar mein nahi.
        item {
            AppText(
                s.alerts,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 10.dp, bottom = 22.dp),
            )
        }

        items(alerts, key = { it.id }) { alert ->
            AppCard(padding = 16.dp, modifier = Modifier.padding(bottom = 12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Box(
                        Modifier.size(26.dp).background(RedBg, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.WarningAmber, null,
                            tint = Red, modifier = Modifier.size(14.dp),
                        )
                    }
                    AppText(
                        alert.sentBy,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    AppText(
                        formatSentAt(alert.sentAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink3,
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Officer ne DONO bhasha likhi thi (backend message_hi + message_en),
                // isliye runtime translation ki zaroorat nahi — jo flood mein galat ho sakti thi.
                AppText(
                    if (lang == Lang.HI) alert.messageHi else alert.messageEn,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink2,
                )
            }
        }
    }
}

/**
 * formatSentAt() — ISO timestamp -> "21 Aug, 14:00" **phone ke local time mein**.
 *
 * INPUT: "2026-08-21T14:00:15+00:00" | OUTPUT: chhota readable string
 *
 * ============ YE PEHLE TOOTA HUA THA ============
 * Pehle yahan sirf STRING KAATI jaati thi — `iso.substringAfter('T').take(5)`. Isse
 * timestamp ka offset (`+00:00`) chup-chaap ignore ho jaata tha, aur UTC ka time seedha
 * screen pe aa jaata tha.
 *
 * Backend UTC mein `sent_at` bhejta hai. Assam IST (+5:30) mein hai. Nateeja: officer
 * abhi alert bhejta aur app "05:56" dikhati jabki phone pe 11:26 baj rahe hote —
 * yaani ek abhi ka alert 5.5 ghante purana dikhta. Demo mein ye bilkul toota lagta hai.
 *
 * Ab OffsetDateTime se poora parse karke phone ke apne timezone mein badalte hain.
 * minSdk 26 hai, to java.time bina desugaring seedha available hai.
 */
private fun formatSentAt(iso: String?): String {
    if (iso == null) return ""
    return runCatching {
        val local = java.time.OffsetDateTime.parse(iso)
            .atZoneSameInstant(java.time.ZoneId.systemDefault())
        val months = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
        )
        val hh = local.hour.toString().padStart(2, '0')
        val mm = local.minute.toString().padStart(2, '0')
        "${local.dayOfMonth} ${months[local.monthValue - 1]}, $hh:$mm"
    }.getOrDefault(iso)
}
