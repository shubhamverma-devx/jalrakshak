/**
 * useEscapeKey — jab tak `active` hai, Escape dabane pe `onEscape()` chalao.
 *
 * INPUT : active (boolean — modal khula hai ya nahi), onEscape (callback)
 *
 * KYUN: har modal apna Escape khud sunta hai. Ek jagah (App) se sabko handle karne ke
 * liye App ko har modal ka state pata hona padta — aur ek naya modal banate hi wo
 * connection jodna bhool jaana sabse aasan galti hai. Modal khud sunta hai to wo galti
 * ho hi nahi sakti.
 *
 * NOTE: listener sirf tab lagta hai jab modal khula ho, isliye "sabse upar wali cheez
 * pehle band ho" apne aap sahi rehta hai — band modal kuch sunta hi nahi.
 */

import { useEffect } from 'react'

export function useEscapeKey(active, onEscape) {
  useEffect(() => {
    if (!active) return
    const onKey = (e) => e.key === 'Escape' && onEscape()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [active, onEscape])
}
