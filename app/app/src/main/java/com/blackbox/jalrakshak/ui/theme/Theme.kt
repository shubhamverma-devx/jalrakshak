package com.blackbox.jalrakshak.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * JalRakshakTheme — app_mockup.html ka light theme.
 *
 * KYUN SIRF LIGHT (dark theme hata diya):
 *  Approved design light hai. Aur ye app dhoop mein, bahar, jaldi mein padhi jaati hai —
 *  wahan light + high contrast hi padhne layak hota hai. Dark theme rakhne ka matlab
 *  hota ek aur design maintain karna jo kabhi approve hi nahi hua.
 *  Phone dark mode mein ho tab bhi app light rehti hai — ye jaan-bujh ke hai.
 *
 * KYUN Material You (dynamic color) NAHI: wallpaper se rang chura ke risk ka laal/hara
 * badal dena khatarnaak hai. Rang fix hain.
 */

private val AppColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    secondary = Ink,
    background = Bg,
    onBackground = Ink,
    surface = CardBg,
    onSurface = Ink,
    surfaceVariant = Neutral,
    onSurfaceVariant = Ink2,
    outline = Line,
    outlineVariant = Line,
    error = Red,
    onError = Color.White,
)

@Composable
fun JalRakshakTheme(content: @Composable () -> Unit) {
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Status bar screen ke background mein ghul jaaye — do alag rang bure lagte hain.
            window.statusBarColor = Bg.toArgb()
            window.navigationBarColor = CardBg.toArgb()
            // Light background hai to icons DARK chahiye, warna dikhte hi nahi.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(colorScheme = AppColors, typography = AppTypography, content = content)
}
