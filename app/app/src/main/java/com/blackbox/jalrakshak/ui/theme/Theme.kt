package com.blackbox.jalrakshak.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * JalRakshakTheme — poore app ka rang aur typography.
 *
 * KYUN Material You (dynamic color) NAHI:
 *  Android 12+ wallpaper se app ke rang chura leta hai. Ye normal app ke liye achha hai,
 *  par yahan KHATARNAK: agar user ka wallpaper laal hai to poori app laal ho jaayegi
 *  aur "safe/green" bhi laal dikhega. Risk ka rang kabhi wallpaper pe depend nahi karna
 *  chahiye. Isliye rang fix hain — wahi jo officer dashboard pe hain.
 *
 * Dark/light PHONE ki setting follow karta hai (dashboard ka toggle yahan nahi hai —
 * app mein toggle bhasha ke liye hai, jo zyada zaroori hai).
 */

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Accent2,
    background = LightBg,
    onBackground = LightInk,
    surface = LightPanel,
    onSurface = LightInk,
    surfaceVariant = LightPanel2,
    onSurfaceVariant = LightInk2,
    outline = LightLine,
    error = RiskRed,
)

private val DarkColors = darkColorScheme(
    primary = Accent2,
    onPrimary = Color.White,
    secondary = Accent,
    background = DarkBg,
    onBackground = DarkInk,
    surface = DarkPanel,
    onSurface = DarkInk,
    surfaceVariant = DarkPanel2,
    onSurfaceVariant = DarkInk2,
    outline = DarkLine,
    error = RiskRed,
)

/**
 * Typography — thoda BADA default size.
 * KYUN: app gaon ke logon ke liye hai, aksar dhoop mein aur jaldi mein padhi jaayegi.
 * Material ka default 14sp body yahan chhota padta hai. 15-16sp rakha hai.
 */
private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun JalRakshakTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Status bar app ke background ke saath ghul jaaye — do alag rang bure lagte hain.
            window.statusBarColor = colors.background.toArgb()
            // Dark theme mein safed icons, light mein kaale — warna icons dikhte hi nahi.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content,
    )
}

/** Kya abhi dark theme hai — riskTint() ko batane ke liye. */
@Composable
fun isDark(): Boolean = isSystemInDarkTheme()
