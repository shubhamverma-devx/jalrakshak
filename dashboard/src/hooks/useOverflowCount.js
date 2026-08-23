/**
 * useOverflowCount — "is scroll box mein kitne item neeche kat rahe hain?"
 *
 * INPUT : ref (scrollable element ka ref), selector (item ka CSS selector), deps
 * OUTPUT: { total, below }  — kitne item hain, aur unme se kitne poori tarah nahi dikhte
 *
 * ============ YE KYUN CHAHIYE ============
 * Panel ke footer mein "4 of 4 shown" likhna JHOOTH tha jab panel mein sirf 2 hi dikh
 * rahe the. Aur bina kuch likhe kaat dena usse bhi bura — officer ko pata hi nahi chalta
 * ki neeche aur bhi SOS pade hain.
 *
 * Number hardcode nahi kar sakte: panel ki height screen ke saath badalti hai (1440 vs
 * 1920), aur card ki height message ki lambai se badalti hai. Isliye asli DOM naapte
 * hain — jis item ka neecha kinara box ke kinare se neeche hai, wo kata hua hai.
 *
 * ResizeObserver + scroll dono sunte hain: window resize pe bhi jawaab badalta hai, aur
 * scroll karne pe bhi ("2 more below" -> "0 more below").
 */

import { useEffect, useState } from 'react'

export function useOverflowCount(ref, selector, deps = []) {
  const [state, setState] = useState({ total: 0, below: 0 })

  useEffect(() => {
    const el = ref.current
    if (!el) return

    const measure = () => {
      const box = el.getBoundingClientRect()
      const items = [...el.querySelectorAll(selector)]
      const visible = items.filter((i) => i.getBoundingClientRect().bottom <= box.bottom + 1).length
      setState({ total: items.length, below: Math.max(0, items.length - visible) })
    }

    measure()
    const ro = new ResizeObserver(measure)
    ro.observe(el)
    el.addEventListener('scroll', measure, { passive: true })

    return () => {
      ro.disconnect()
      el.removeEventListener('scroll', measure)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ref, selector, ...deps])

  return state
}
