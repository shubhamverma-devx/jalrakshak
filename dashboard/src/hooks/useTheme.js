/**
 * useTheme — light/dark theme, <html data-theme> attribute pe.
 *
 * KYUN attribute pe, React state pe nahi: poora design CSS variables se chalta hai
 * ([data-theme="dark"] { --bg: ... }). Attribute badalte hi browser saara CSS dobara
 * apply kar deta hai — ek bhi React component ko re-render karne ki zaroorat nahi.
 * Sirf wo cheezein React ko batani padti hain jo CSS se nahi hotin (Leaflet tiles,
 * Chart.js ke tick/grid colors) — isliye hook `theme` value bhi return karta hai.
 *
 * localStorage mein save karte hain taaki officer ki pasand refresh pe na khoye.
 */

import { useCallback, useEffect, useState } from 'react'

const KEY = 'jalrakshak-theme'

export function useTheme() {
  const [theme, setTheme] = useState(() => {
    // Officer ki pichhli pasand, warna DARK.
    //
    // KYUN dark default (system preference nahi): mockup_v2.html dark mein khulta hai —
    // wahi approved design hai. Aur index.html <html data-theme="dark"> se shuru hota hai
    // taaki pehli paint sahi ho; agar React yahan system preference dekh ke light chun leta
    // to har load pe dark->light ka flash dikhta. Officer ne khud light chuna ho to
    // localStorage se wahi wapas aata hai.
    const saved = localStorage.getItem(KEY)
    return saved === 'light' || saved === 'dark' ? saved : 'dark'
  })

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme)
    localStorage.setItem(KEY, theme)
  }, [theme])

  const toggle = useCallback(() => setTheme((t) => (t === 'dark' ? 'light' : 'dark')), [])

  return { theme, toggle, isDark: theme === 'dark' }
}

/**
 * chartTheme() — Chart.js ke liye theme ke hisaab se colors.
 * INPUT : isDark (bool) | OUTPUT: { grid, tick }
 * KYUN: Chart.js canvas pe draw karta hai, CSS variables use nahi kar sakta. Isliye
 * har chart ko ye colors explicitly dene padte hain, aur theme badalne pe dobara.
 */
export const chartTheme = (isDark) => ({
  grid: isDark ? 'rgba(255,255,255,.05)' : 'rgba(0,0,0,.05)',
  tick: isDark ? '#8b97a7' : '#5d6b7a',
})
