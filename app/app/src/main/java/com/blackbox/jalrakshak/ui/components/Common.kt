package com.blackbox.jalrakshak.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackbox.jalrakshak.core.LocalStrings
import com.blackbox.jalrakshak.ui.theme.isDark
import com.blackbox.jalrakshak.ui.theme.riskColor
import com.blackbox.jalrakshak.ui.theme.riskTint

/**
 * Common.kt — chhote reusable UI tukde.
 * KYUN alag file: teen screens mein wahi card/banner/row chahiye. Ek jagah rakhne se
 * design consistent rehta hai (dashboard ke panels jaisa flat look).
 */

/**
 * levelIcon() — risk level ka icon.
 * Material ke OUTLINED icons use kar rahe hain — ye Tabler ki line-style ke sabse kareeb
 * hain, jo dashboard mein use hui hai. Filled icons bhaari aur alag dikhte.
 */
fun levelIcon(level: String): ImageVector = when (level) {
    "red" -> Icons.Outlined.WarningAmber
    "yellow" -> Icons.Outlined.ErrorOutline
    else -> Icons.Outlined.CheckCircle
}

/** levelLabel() — level ka naam, chuni hui bhasha mein. */
@Composable
fun levelLabel(level: String): String {
    val s = LocalStrings.current
    return when (level) {
        "red" -> s.levelRed
        "yellow" -> s.levelYellow
        else -> s.levelGreen
    }
}

/**
 * Panel — dashboard ke `.panel` jaisa flat card.
 * Koi shadow/elevation nahi — sirf border. Yehi mockup ka look hai (flat surfaces).
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(14.dp),
        content = content,
    )
}

/**
 * OfflineBanner — "ऑफ़लाइन — पिछली जानकारी दिखाई जा रही है" + kitna purana.
 *
 * INPUT: cachedAt (millis) — data kab save hua tha
 *
 * KYUN ye banner ITNA ZAROORI HAI: offline mein app purana risk dikhati hai. Bina banner
 * ke citizen samjhega ye ABHI ka haal hai — aur "green" dekh ke bahar nikal jaayega,
 * jabki 6 ghante mein paani chadh chuka ho. Purana data dikhana theek hai; usse NAYA
 * batana khatarnak hai. Isliye banner mein umar bhi likhi hai.
 */
@Composable
fun OfflineBanner(cachedAt: Long?) {
    val s = LocalStrings.current
    val amber = MaterialTheme.colorScheme.let { riskColor("yellow") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(riskTint("yellow", isDark()), RoundedCornerShape(10.dp))
            .border(1.dp, amber.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.CloudOff, null, tint = amber, modifier = Modifier.size(18.dp))
        Column {
            Text(
                s.offlineBanner,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (cachedAt != null) {
                Text(
                    "${s.lastUpdated}: ${timeAgo(cachedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * InfoRow — "label ......... value" ki ek line (dashboard ke drawer `.drow` jaisa).
 */
@Composable
fun InfoRow(label: String, value: String, valueColor: Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** EmptyState — "abhi kuch nahi hai" (alerts feed ke liye). */
@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            icon, null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(40.dp),
        )
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/**
 * timeAgo() — millis ko "5 मिनट पहले" jaisa banata hai.
 *
 * INPUT : timestamp (millis) | OUTPUT: readable string
 * KYUN relative: "14:32" se citizen ko kuch samajh nahi aata. "2 ghante pehle" se
 * turant pata chalta hai ki jaankari kitni purani hai — offline banner ka poora point yehi hai.
 *
 * NOTE: ye chhota sa formatting hai, isliye Strings.kt mein nahi daala; dono bhasha ke
 * shabd yahin inline hain (Compose se bhasha padh ke).
 */
@Composable
fun timeAgo(millis: Long): String {
    val hi = com.blackbox.jalrakshak.core.LocalLang.current == com.blackbox.jalrakshak.core.Lang.HI
    val mins = ((System.currentTimeMillis() - millis) / 60000).coerceAtLeast(0)

    return when {
        mins < 1 -> if (hi) "अभी" else "just now"
        mins < 60 -> if (hi) "$mins मिनट पहले" else "$mins min ago"
        mins < 1440 -> (mins / 60).let { if (hi) "$it घंटे पहले" else "$it hr ago" }
        else -> (mins / 1440).let { if (hi) "$it दिन पहले" else "$it days ago" }
    }
}

/** Chhota rangeen badge (risk level ke liye) — dashboard ke `.dbadge` jaisa. */
@Composable
fun LevelBadge(level: String) {
    val color = riskColor(level)
    Row(
        modifier = Modifier
            .background(riskTint(level, isDark()), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(levelIcon(level), null, tint = color, modifier = Modifier.size(16.dp))
        Text(
            levelLabel(level).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Box jo poori jagah leke beech mein content rakhta hai (loading/error ke liye). */
@Composable
fun CenterBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { content() }
}
