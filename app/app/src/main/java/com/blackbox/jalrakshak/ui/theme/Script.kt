package com.blackbox.jalrakshak.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * =====================================================================================
 *  Script.kt — ek hi string mein Latin = Jakarta, Devanagari = Noto Sans Devanagari
 * =====================================================================================
 *
 *  DIKKAT: Compose ka `FontFamily` weight/style ke hisaab se font chunta hai —
 *  SCRIPT ke hisaab se nahi. Yani aap `fontFamily = Jakarta` de do, to Devanagari
 *  glyphs Jakarta mein hain hi nahi, aur Android chup-chaap SYSTEM font pe fallback
 *  kar deta hai. Text padha jaata hai, par:
 *    - uska weight Jakarta se match nahi karta
 *    - x-height aur rhythm alag hota hai
 *    - mixed line ("नदी का स्तर (33 m)") mein do alag typefaces saaf dikhte hain
 *
 *  HAL: string ko script ke hisaab se TUKDON mein todo aur har tukde pe sahi family
 *  lagao — AnnotatedString ke SpanStyle se. Ek Text call, do fonts, sahi jagah.
 *
 *  KYUN poore app ki family Noto nahi kar di (aasan hota): Noto Sans Devanagari mein
 *  Latin glyphs bhi hain, to sab kuch Noto mein chal jaata — par phir English mode
 *  bhi Noto mein dikhta, aur approved design Jakarta ka hai. Numbers (`33.0`, `4 mm`)
 *  bhi Noto mein dikhte, jo mockup ke bold numeric look se match nahi karta.
 * =====================================================================================
 */

/** Devanagari Unicode block: U+0900–U+097F (plus extended U+A8E0–U+A8FF). */
private fun Char.isDevanagari(): Boolean =
    this in 'ऀ'..'ॿ' || this in '꣠'..'ꣿ'

/**
 * Neutral characters — space, digits, punctuation, brackets.
 *
 * KYUN ALAG SE HANDLE KARTE HAIN: agar space/punctuation ko apna run maan lein to
 * "नदी का स्तर" 5 alag runs mein toot jaata (word, space, word, space, word) aur
 * har run ka shaping alag hota — Devanagari mein ye conjuncts aur matras tod deta hai.
 * Isliye neutral chars PICHHLE run ke saath chipke rehte hain.
 */
private fun Char.isNeutral(): Boolean = !isLetter() || isDigit()

/**
 * scriptAware() — string ko Latin/Devanagari runs mein todta hai.
 *
 * INPUT : plain string ("नदी का स्तर (33 m)")
 * OUTPUT: AnnotatedString jisme Devanagari tukde Noto mein aur baaki Jakarta mein
 *
 * remember(text) se ye har recomposition pe dobara nahi banta.
 */
@Composable
fun scriptAware(text: String): AnnotatedString = remember(text) { buildScriptAware(text) }

/** Non-composable version — jahan remember available na ho. */
fun buildScriptAware(text: String): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")

    // Pehla decisive character dhoondo (neutral chars script decide nahi karte).
    val firstScript = text.firstOrNull { !it.isNeutral() }?.isDevanagari() ?: false

    return buildAnnotatedString {
        var runIsDev = firstScript
        val run = StringBuilder()

        fun flush() {
            if (run.isEmpty()) return
            withStyle(SpanStyle(fontFamily = if (runIsDev) NotoDevanagari else Jakarta)) {
                append(run.toString())
            }
            run.clear()
        }

        for (ch in text) {
            if (ch.isNeutral()) {
                // Neutral: jis run mein chal rahe hain usi mein rehne do.
                run.append(ch)
                continue
            }
            val dev = ch.isDevanagari()
            if (dev != runIsDev) {
                flush()
                runIsDev = dev
            }
            run.append(ch)
        }
        flush()
    }
}
