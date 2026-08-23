package com.blackbox.jalrakshak.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * =====================================================================================
 *  Palette — app_mockup.html (approved design) se EXACT hex.
 * =====================================================================================
 *  Ye officer dashboard ke palette se ALAG hai, aur jaan-bujh ke:
 *   - Dashboard control-room screen hai — dark, muted, ghanton dekhna padta hai
 *   - App gaon mein, dhoop mein, jaldi mein dekhi jaati hai — light aur high contrast
 *  Risk ka MATLAB dono jagah same rehta hai (laal = khatra), bas shade alag hai.
 * =====================================================================================
 */

// --- surfaces (mockup :root) ---
val Bg = Color(0xFFEEF0F4)        // --bg   screen background
val CardBg = Color(0xFFFBFCFD)    // --card
val Line = Color(0xFFDFE3EA)      // --line borders + dividers

// --- text ---
val Ink = Color(0xFF101828)       // --ink   primary
val Ink2 = Color(0xFF667085)      // --ink2  secondary
val Ink3 = Color(0xFF98A2B3)      // --ink3  tertiary / icons

// --- risk levels + tints ---
val Red = Color(0xFFD92D20)       // --red
val RedBg = Color(0xFFFEF3F2)     // --red-bg
val Green = Color(0xFF039855)     // --green
val GreenBg = Color(0xFFECFDF3)   // --green-bg
val Amber = Color(0xFFDC6803)     // --amber
val AmberBg = Color(0xFFFFFAEB)   // --amber-bg

val Blue = Color(0xFF175CD3)      // --blue  bottom-nav active

// --- offline banner (mockup .off) ---
val OfflineBg = Color(0xFFFFFAEB)
val OfflineLine = Color(0xFFFEDF89)
val OfflineInk = Color(0xFFB54708)

/** Neutral chip/badge fill — mockup ke `.dot.n` aur `.shrow .si` mein #e9ecf1. */
val Neutral = Color(0xFFE9ECF1)

/**
 * riskColor() — backend ka level string -> rang.
 * INPUT: "red" | "yellow" | "green" | OUTPUT: Color
 * App khud kabhi level decide nahi karti — wo RiskEngine ka kaam hai. Yahan sirf naam se
 * rang mila rahe hain.
 */
fun riskColor(level: String): Color = when (level) {
    "red" -> Red
    "yellow" -> Amber
    else -> Green
}

/** riskTint() — usi level ka halka background (status circle, badge). */
fun riskTint(level: String): Color = when (level) {
    "red" -> RedBg
    "yellow" -> AmberBg
    else -> GreenBg
}
