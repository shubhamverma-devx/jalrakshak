/**
 * utils/format.js — display formatting. Sab ek jagah taaki poore dashboard mein
 * numbers/dates ek hi tarah dikhein.
 */

/**
 * num() — Indian grouping (1,65,700 — lakh/crore style, na ki 165,700).
 * KYUN: dashboard Assam ke officers ke liye hai. "1,65,700" wo ek nazar mein padh lenge.
 */
export const num = (n) => (n ?? 0).toLocaleString('en-IN')

/** mm value — ek decimal, hamesha unit ke saath. */
export const mm = (v) => (v === null || v === undefined ? '—' : `${Number(v).toFixed(1)} mm`)

/** meters — do decimal (river level ka farq centimetre mein matter karta hai). */
export const metres = (v) => (v === null || v === undefined ? '—' : `${Number(v).toFixed(2)} m`)

/**
 * dayLabel() — '2022-06-15' -> '15 June 2022'
 * INPUT: ISO date string | OUTPUT: readable string
 */
export function dayLabel(isoDate) {
  if (!isoDate) return '—'
  const d = new Date(isoDate + 'T00:00:00')
  return d.toLocaleDateString('en-IN', { day: 'numeric', month: 'long', year: 'numeric' })
}

/** shortDay() — '2022-06-15' -> '15 Jun' (chart ke x-axis ke liye, jagah kam hai). */
export function shortDay(isoDate) {
  if (!isoDate) return ''
  const d = new Date(isoDate + 'T00:00:00')
  return d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })
}

/**
 * timeAgo() — timestamp -> "2 min ago" / "3 hours ago".
 * INPUT: ISO datetime | OUTPUT: relative string
 * KYUN relative: activity feed mein "18:42:07" se zyada kaam ka hai "5 min ago".
 * Flood ke waqt officer ko "kitna purana" chahiye, "kab" nahi.
 */
export function timeAgo(iso) {
  if (!iso) return ''
  const secs = Math.floor((Date.now() - new Date(iso).getTime()) / 1000)

  if (secs < 45) return 'just now'
  if (secs < 3600) return `${Math.floor(secs / 60)} min ago`
  if (secs < 86400) return `${Math.floor(secs / 3600)} hr ago`
  return `${Math.floor(secs / 86400)} d ago`
}
