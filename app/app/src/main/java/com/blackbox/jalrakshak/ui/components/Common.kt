package com.blackbox.jalrakshak.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blackbox.jalrakshak.core.Lang
import com.blackbox.jalrakshak.core.LocalLang
import com.blackbox.jalrakshak.core.LocalStrings
import com.blackbox.jalrakshak.ui.theme.CardBg
import com.blackbox.jalrakshak.ui.theme.scriptAware
import com.blackbox.jalrakshak.ui.theme.Ink3
import com.blackbox.jalrakshak.ui.theme.Line
import com.blackbox.jalrakshak.ui.theme.OfflineBg
import com.blackbox.jalrakshak.ui.theme.OfflineInk
import com.blackbox.jalrakshak.ui.theme.OfflineLine

/**
 * Common.kt — mockup ke reusable tukde.
 * app_mockup.html ke class naam comments mein diye hain taaki design se milaana aasan rahe.
 */

/** Mockup ka `.card` — off-white surface, 1px border, 16dp radius. Koi shadow nahi. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    padding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(16.dp))
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(padding),
        content = content,
    )
}

/** Mockup ka `.sect` — 13px/700 section heading. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    AppText(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = modifier.padding(start = 2.dp, bottom = 10.dp),
    )
}

/**
 * OfflineBanner — mockup ka `.off`: amber patti sabse upar.
 *
 * KYUN ITNA ZAROORI: offline mein app PURANA risk dikhati hai. Bina is patti ke citizen
 * samjhega ye abhi ka haal hai — aur "safe" dekh ke bahar nikal jaayega, jabki 6 ghante
 * mein paani chadh chuka ho. Purana data dikhana theek hai; usse NAYA batana khatarnaak hai.
 */
@Composable
fun OfflineBanner(cachedAt: Long?) {
    val s = LocalStrings.current
    Column(Modifier.fillMaxWidth().background(OfflineBg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(Icons.Outlined.CloudOff, null, tint = OfflineInk, modifier = Modifier.size(15.dp))
            AppText(
                buildString {
                    append(s.offlineBanner)
                    if (cachedAt != null) append(" · ${s.lastUpdated}: ${timeAgo(cachedAt)}")
                },
                style = MaterialTheme.typography.labelMedium,
                color = OfflineInk,
            )
        }
        // Mockup mein `.off` pe SIRF border-bottom hai. Poore box ka border (jo pehle
        // tha) patti ko ek "card" jaisa dikha raha tha — wo full-bleed patti honi chahiye.
        Box(Modifier.fillMaxWidth().height(1.dp).background(OfflineLine))
    }
}

/** levelIcon() — risk level ka icon. Material outlined = Tabler line style ke sabse kareeb. */
fun levelIcon(level: String): ImageVector = when (level) {
    "red" -> Icons.Outlined.WarningAmber
    "yellow" -> Icons.Outlined.ErrorOutline
    else -> Icons.Outlined.CheckCircle
}

/** levelLabel() — level ka naam chuni hui bhasha mein. */
@Composable
fun levelLabel(level: String): String {
    val s = LocalStrings.current
    return when (level) {
        "red" -> s.levelRed
        "yellow" -> s.levelYellow
        else -> s.levelGreen
    }
}

/** Khaali state (alerts feed ke liye). */
@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = Ink3, modifier = Modifier.size(40.dp))
        AppText(title, style = MaterialTheme.typography.titleMedium)
        AppText(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * timeAgo() — millis -> "5 मिनट पहले" / "5 min ago".
 * KYUN relative: "14:32" se citizen ko kuch samajh nahi aata. "2 ghante pehle" se turant
 * pata chalta hai ki jaankari kitni purani hai — offline banner ka poora point yehi hai.
 */
@Composable
fun timeAgo(millis: Long): String {
    val hi = LocalLang.current == Lang.HI
    val mins = ((System.currentTimeMillis() - millis) / 60000).coerceAtLeast(0)
    return when {
        mins < 1 -> if (hi) "अभी" else "just now"
        mins < 60 -> if (hi) "$mins मिनट पहले" else "$mins min ago"
        mins < 1440 -> (mins / 60).let { if (hi) "$it घंटे पहले" else "$it hr ago" }
        else -> (mins / 1440).let { if (hi) "$it दिन पहले" else "$it days ago" }
    }
}

/** Chhota circular badge — mockup ka `.item .dot` (numbered steps ke liye). */
@Composable
fun NumberDot(text: String, bg: Color, fg: Color) {
    Box(
        Modifier.size(22.dp).background(bg, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        AppText(text, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

/**
 * AppText — poore app ka Text. Andar se `scriptAware()` lagata hai.
 *
 * KYUN WRAPPER (har call site pe scriptAware() likhne ke bajaye): ek jagah se poore
 * app ka script handling control hota hai. Koi naya screen banaye aur AppText use kare,
 * to Hindi apne aap sahi font mein aayega — bhoolne ki gunjaish nahi.
 *
 * Signature jaan-bujh ke chhota hai — sirf wahi params jo hum actually use karte hain.
 */
@Composable
fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = scriptAware(text),
        modifier = modifier,
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
        style = style,
    )
}
