/**
 * ReplaySlider — neeche ki date-slider bar (play button + range + phase pill).
 * mockup_v2.html ka `.sliderbar`.
 *
 * DEMO SCRIPT (BUILD_PLAN section 11, step 2) KA MAIN TOOL:
 * "Replay slider ghumao → baarish badhe, river upar, villages yellow→red."
 * Isliye ye instant hona chahiye — data pehle se memory mein hai (useDashboardData dekho),
 * to scrubbing pe koi network call nahi jaati.
 *
 * LIVE MODE MEIN: poori bar disable ho jaati hai (.disabled — opacity + pointer-events none),
 * kyunki live mein "din" ka koi matlab hi nahi. Chhupane ke bajaye disable karte hain taaki
 * layout na koode aur officer ko dikhe ki wo cheez wahin hai, bas abhi lagoo nahi hoti.
 */

import { useEffect, useRef } from 'react'
import { IconPlayerPause, IconPlayerPlay } from '@tabler/icons-react'
import { REPLAY_TICK_MS } from '../config'
import { dayLabel } from '../utils/format'

export default function ReplaySlider({
  days,
  day,
  onDayChange,
  playing,
  onPlayToggle,
  phase,
  disabled,
  note, // data-source chip — neeche wali comment dekho
}) {
  const timer = useRef(null)

  /**
   * Auto-play loop.
   * KYUN functional update (d => ...) aur dependency mein `day` nahi:
   * agar `day` dependency hoti to har tick pe interval clear+set hota — timing lagti-tootti.
   * Yahan interval ek hi baar banta hai aur andar se latest day nikalta hai.
   */
  useEffect(() => {
    if (!playing || disabled || days.length === 0) return

    timer.current = setInterval(() => {
      onDayChange((d) => (d + 1) % days.length)
    }, REPLAY_TICK_MS)

    return () => clearInterval(timer.current)
  }, [playing, disabled, days.length, onDayChange])

  const total = days.length || 1

  return (
    <div className={`sliderbar${disabled ? ' disabled' : ''}`}>
      <button className="play" onClick={onPlayToggle} aria-label={playing ? 'Pause' : 'Play'}>
        {playing ? <IconPlayerPause className="ti" /> : <IconPlayerPlay className="ti" />}
      </button>

      <div className="datebox">
        <div className="d">{dayLabel(days[day])}</div>
        <div className="p">
          Day {day + 1} of {total}
        </div>
      </div>

      <input
        type="range"
        min={0}
        max={Math.max(total - 1, 0)}
        value={day}
        onChange={(e) => onDayChange(Number(e.target.value))}
        aria-label="Replay day"
      />

      {/* Phase pill ka text aur rang ASLI data se derive hote hain (utils/risk.js ->
          derivePhase). Mockup mein ye hardcoded array tha — ab wo jhooth nahi bolega
          agar seed data ya thresholds badal jaayein. */}
      <span
        className="phase-pill"
        style={{ background: `var(--${phase.tone}-bg)`, color: `var(--${phase.tone})` }}
      >
        {phase.label}
      </span>

      {/* DATA HONESTY CHIP — pehle ye `position: fixed` tha (bottom-right corner).
          1440x900 pe wo theek activity feed ke aakhri item ke UPAR baith jaata tha, do
          text ek doosre pe. Ab slider bar ke andar hai, jahan khaali jagah hai — kabhi
          kisi content ko nahi dhakta, aur hamesha dikhta bhi hai.
          Slider disabled (live/satellite mode) hone pe bhi ye dim nahi hota — CSS mein
          isko chhod diya gaya hai — kyunki tab bhi ye SACH bol raha hota hai. */}
      {note && <span className="data-flag">{note}</span>}
    </div>
  )
}
