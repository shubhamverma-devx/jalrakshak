package com.blackbox.jalrakshak.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.blackbox.jalrakshak.R

/**
 * =====================================================================================
 *  Typography — Plus Jakarta Sans (mockup ka font), app ke andar BUNDLED.
 * =====================================================================================
 *
 *  KYUN BUNDLED, CDN NAHI: ye flood app hai. Network hi wo cheez hai jo sabse pehle
 *  jaati hai — aur agar font network se aata to offline mode mein poori typography
 *  badal jaati. Font APK ke andar hai, to app hamesha waisi hi dikhti hai.
 *
 *  Font file `res/font/plus_jakarta_sans.ttf` app_mockup.html ke andar embedded
 *  base64 se seedha nikali gayi hai — yani bilkul wahi file jo design mein use hui.
 *
 *  ---- VARIABLE FONT ----
 *  Ye ek VARIABLE font hai (fvar table), weight axis 200-800. Iska matlab ek hi file
 *  se saare weights milte hain — 5 alag TTF bundle karne ki zaroorat nahi (APK chhota
 *  rehta hai). Har weight ke liye `FontVariation.Settings(weight)` dena padta hai,
 *  warna Android sirf default weight render karta hai aur bold kabhi dikhta hi nahi.
 *  Ye API 26+ pe chalta hai — hamara minSdk 26 hai, to theek hai.
 */

/**
 * Ek weight ka Font banao (variable axis set karke).
 *
 * @OptIn: FontVariation abhi experimental API hai. Ye jaan-bujh ke use kar rahe hain —
 * ek variable font se 4 weights nikalna 4 alag TTF bundle karne se behtar hai (APK
 * chhota rehta hai). API badle to sirf ye ek function badalna padega.
 */
@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: Int) = Font(
    R.font.plus_jakarta_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Jakarta = FontFamily(
    jakarta(400),
    jakarta(500),
    jakarta(600),
    jakarta(700),
)

/**
 * Noto Sans Devanagari — Hindi text ke liye, app ke andar bundled.
 *
 * KYUN CHAHIYE: Plus Jakarta Sans mein Devanagari glyphs hain hi nahi. Pehle Android
 * system font pe fallback ho raha tha — text padha to jaata tha, par uska weight,
 * x-height aur rhythm Jakarta se match nahi karta tha. Ek hi line mein do alag
 * typefaces (jaise "नदी का स्तर (33 m)") saaf bemel dikhte the.
 *
 * Ye static weights hain (variable nahi), isliye har weight ki apni file hai.
 */
val NotoDevanagari = FontFamily(
    Font(R.font.noto_sans_devanagari_regular, FontWeight.Normal),
    Font(R.font.noto_sans_devanagari_medium, FontWeight.Medium),
    Font(R.font.noto_sans_devanagari_semibold, FontWeight.SemiBold),
    Font(R.font.noto_sans_devanagari_bold, FontWeight.Bold),
)

/**
 * DEVANAGARI ke baare mein:
 * Plus Jakarta Sans mein Devanagari glyphs NAHI hain. Hindi text ke liye Android apne
 * system font (Noto Sans Devanagari, har device mein hota hai) pe FALLBACK kar leta hai —
 * to Hindi saaf dikhta hai, bas Jakarta mein nahi. Alag 250 KB ka font bundle karne se
 * behtar yehi hai, aur emulator pe verify bhi kiya gaya hai.
 */

/**
 * Type scale — mockup ke exact px se mapped (CSS px ~ Compose sp is screen pe).
 *
 * | mockup                         | style          |
 * |--------------------------------|----------------|
 * | .h1 26px/700                   | headlineMedium |
 * | .status-card .lvl 24px/700     | headlineSmall  |
 * | .metrics .mv 18px/700          | titleLarge     |
 * | .vr .vn / .btn 15px/600        | titleMedium    |
 * | .item .tx / .al .t 14px        | bodyLarge      |
 * | .al .m 13.5px / .sect 13px     | bodyMedium     |
 * | .metrics .ml 11.5px            | bodySmall      |
 * | .nav 10.5px                    | labelSmall     |
 */
val AppTypography = Typography(
    /**
     * ---- NEGATIVE LETTER-SPACING JAAN-BUJH KE HATAYA ----
     * Mockup mein headings pe tight tracking hai (-0.7px / -0.5px) — Latin display type
     * mein wo achha lagta hai. Par hamari default bhasha HINDI hai, aur Devanagari
     * Jakarta mein hai hi nahi (system font pe fallback hota hai).
     *
     * Negative tracking + font fallback + Devanagari conjuncts = text measurement
     * bigad jaati hai. Emulator pe "सुरक्षित" beech se toot ke do line mein aa gaya tha
     * ("सुरक्षि / त") — jabki English "Safe" ek line mein theek tha.
     *
     * Ek toota hua status shabd sabse pehli cheez hai jo citizen dekhta hai. Isliye
     * tracking 0 rakhi hai — Latin thoda kam tight dikhega, par dono bhasha sahi rendered
     * hoti hain. Sahi > stylish.
     */
    headlineMedium = TextStyle(
        fontFamily = Jakarta, fontSize = 26.sp, fontWeight = FontWeight.Bold,
        lineHeight = 32.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Jakarta, fontSize = 24.sp, fontWeight = FontWeight.Bold,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Jakarta, fontSize = 18.sp, fontWeight = FontWeight.Bold,
        lineHeight = 23.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Jakarta, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 20.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Jakarta, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        lineHeight = 17.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Jakarta, fontSize = 14.sp, fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Jakarta, fontSize = 13.5.sp, fontWeight = FontWeight.Normal,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Jakarta, fontSize = 11.5.sp, fontWeight = FontWeight.Normal,
        lineHeight = 15.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Jakarta, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
    ),
    labelMedium = TextStyle(
        fontFamily = Jakarta, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
    ),
    labelSmall = TextStyle(
        fontFamily = Jakarta, fontSize = 10.5.sp, fontWeight = FontWeight.Medium,
    ),
)
