/**
 * utils/risk.js — backend ke data ko UI ki bhasha mein badalne wale chhote helpers.
 *
 * KYUN alag file: ye pure functions hain (input do, output lo, koi side effect nahi).
 * Components mein rakhte to har jagah duplicate hote aur test karna mushkil hota.
 */

import { LEVEL_COLORS } from '../config'

/**
 * levelClass() — backend ka level -> design ka CSS class.
 *
 * INPUT : 'red' | 'yellow' | 'green'  | OUTPUT: 'red' | 'amber' | 'green'
 *
 * KYUN ye mapping zaroori hai: backend RiskEngine 'yellow' bolta hai (BUILD_PLAN section 8
 * ki bhasha), par approved design mockup_v2.html 'amber' class use karta hai. Dono theek hain;
 * translation ek hi jagah honi chahiye — yahan. Kahin aur `=== 'yellow'` mat likhna.
 */
export const levelClass = (level) => (level === 'yellow' ? 'amber' : level)

/** Level ka hex color (map dots + charts). Unknown level pe green (safe default). */
export const levelColor = (level) => LEVEL_COLORS[level] || LEVEL_COLORS.green

/** Level ka angrezi naam badge/legend ke liye. */
export const levelLabel = (level) =>
  ({ red: 'Danger', yellow: 'Warning', green: 'Safe' })[level] || 'Unknown'

/**
 * Map pe dot ka size (px) — jitna khatra utna bada.
 * KYUN: 30 dots ek saath dikhte hain. Sirf rang se RED dhoondhna padta hai; size badhane se
 * khatre wale gaon turant aankh mein aate hain. Mockup ke exact numbers (16/12/9).
 */
export const dotSize = (level) => (level === 'red' ? 16 : level === 'yellow' ? 12 : 9)

/**
 * derivePhase() — replay ke din ka "phase" (Normal / Rising / Peak / Receding ...).
 *
 * INPUT : saare din ke snapshots (array), abhi ka din index
 * OUTPUT: { label, tone }  tone = 'red' | 'amber' | 'green'
 *
 * KYUN DERIVE KARTE HAIN, HARDCODE NAHI: mockup mein phase naam ek fixed array tha
 * (['Normal','Normal','Rising',...]). Wo dummy data ke saath hi sach tha. Ab data asli
 * RiskEngine se aata hai — agar thresholds ya seed data badla to hardcoded labels jhooth
 * bol dete. Isliye phase har baar asli numbers se nikaalte hain: kitne RED hain, aur
 * affected aabadi kal se badhi ya ghati.
 */
export function derivePhase(snapshots, dayIndex) {
  const cur = snapshots?.[dayIndex]
  if (!cur) return { label: '—', tone: 'green' }

  const red = cur.summary.by_level.red
  const yellow = cur.summary.by_level.yellow
  const affected = cur.summary.affected_population

  // Sab saaf — koi khatra nahi.
  if (red === 0 && yellow === 0) return { label: 'Normal', tone: 'green' }

  const prev = snapshots[dayIndex - 1]
  const rising = !prev || affected >= prev.summary.affected_population

  // Peak = jis din sabse zyada log affected the. Poore data se nikalta hai.
  const maxAffected = Math.max(...snapshots.map((s) => s.summary.affected_population))
  if (red > 0 && affected === maxAffected) return { label: 'Peak', tone: 'red' }

  if (red > 0) return rising ? { label: 'Flooding', tone: 'red' } : { label: 'Receding', tone: 'amber' }

  return rising ? { label: 'Rising', tone: 'amber' } : { label: 'Improving', tone: 'green' }
}
