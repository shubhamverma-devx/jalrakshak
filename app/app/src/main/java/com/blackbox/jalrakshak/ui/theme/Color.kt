package com.blackbox.jalrakshak.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * =====================================================================================
 *  Palette — officer dashboard (mockup_v2.html) se EXACT match.
 * =====================================================================================
 *  KYUN bilkul wahi rang: officer dashboard pe gaon RED dekhta hai aur citizen apne
 *  phone pe bhi wahi RED dekhta hai. Ek hi rang ka matlab dono taraf ek jaisa hona
 *  chahiye — warna phone pe baat karte waqt confusion hoti hai ("mujhe to peela dikh
 *  raha hai"). Flood mein ye confusion mehngi padti hai.
 *
 *  Muted, professional, flat — koi gradient nahi, koi chamakdaar rang nahi.
 * =====================================================================================
 */

// --- Risk levels (dono theme mein same — inka matlab fix hai) ---
val RiskRed = Color(0xFFC85450)
val RiskAmber = Color(0xFFC79445)
val RiskGreen = Color(0xFF5B9A6B)

// --- Accent ---
val Accent = Color(0xFF3B6EA5)
val Accent2 = Color(0xFF4B82BE)

// --- Light theme (dashboard ka [data-theme="light"]) ---
val LightBg = Color(0xFFE4E8EE)
val LightPanel = Color(0xFFF4F6F9)
val LightPanel2 = Color(0xFFEAEEF3)
val LightInk = Color(0xFF1C2430)
val LightInk2 = Color(0xFF5A6675)
val LightInk3 = Color(0xFF8794A4)
val LightLine = Color(0xFFD3DAE3)

// --- Dark theme (dashboard ka [data-theme="dark"]) ---
val DarkBg = Color(0xFF0D1117)
val DarkPanel = Color(0xFF141A22)
val DarkPanel2 = Color(0xFF10151C)
val DarkInk = Color(0xFFDFE5EC)
val DarkInk2 = Color(0xFF8B97A7)
val DarkInk3 = Color(0xFF5B6675)
val DarkLine = Color(0xFF232C38)

/**
 * Risk level ka background tint (dashboard ke --red-bg / --amber-bg / --green-bg).
 * Alpha alag hai light aur dark ke liye — dark mein zyada alpha chahiye warna tint
 * dikhta hi nahi.
 */
fun riskTint(level: String, isDark: Boolean): Color {
    val base = riskColor(level)
    return base.copy(alpha = if (isDark) 0.13f else 0.11f)
}

/**
 * riskColor() — backend ka level string -> rang.
 * INPUT : "red" | "yellow" | "green" | OUTPUT: Color
 * KYUN yahan mapping: app kahin bhi apna faisla nahi karti ki kaunsa level hai —
 * wo backend ka kaam hai (RiskEngine). Yahan sirf naam se rang mila rahe hain.
 */
fun riskColor(level: String): Color = when (level) {
    "red" -> RiskRed
    "yellow" -> RiskAmber
    else -> RiskGreen
}
