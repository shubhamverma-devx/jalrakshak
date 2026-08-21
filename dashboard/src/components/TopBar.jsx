/**
 * TopBar — logo, ghadi, live/replay toggle, theme toggle.
 * mockup_v2.html ka `.topbar`.
 *
 * KYUN mode toggle yahan sabse upar: ye poore dashboard ka sabse bada switch hai —
 * "abhi ka asli data" vs "2022 ka replay". Demo mein judge ke saamne yahi sabse pehle dabta hai.
 */

import { IconMoon, IconRipple, IconSun } from '@tabler/icons-react'
import { useClock } from '../hooks/useDashboardData'

export default function TopBar({ mode, onModeChange, isDark, onThemeToggle, liveStale }) {
  const clock = useClock()

  return (
    <div className="topbar">
      <div className="logo">
        <span className="mk">
          <IconRipple className="ti" />
        </span>
        JalRakshak
        <span className="tag">COMMAND CENTER</span>
      </div>

      <div className="spacer" />

      <div className="clock mono">{clock}</div>

      {/* Live badge sirf live mode mein. Pulse = "data abhi aa raha hai".
          Agar Open-Meteo se data na mila ho (data_ok false) to badge amber ho jaata hai
          aur pulse ruk jaata hai — chup-chaap "Live" dikhate rehna jhooth hota. */}
      {mode === 'live' && (
        <div className={`live-badge${liveStale ? ' stale' : ''}`}>
          <span className="pulse" />
          {liveStale ? 'Live · data unavailable' : 'Live · Open-Meteo'}
        </div>
      )}

      <div className="seg">
        <button
          className={mode === 'replay' ? 'active' : ''}
          onClick={() => onModeChange('replay')}
        >
          Replay 2022
        </button>
        <button className={mode === 'live' ? 'active' : ''} onClick={() => onModeChange('live')}>
          Live
        </button>
      </div>

      <button
        className="iconbtn"
        onClick={onThemeToggle}
        title={isDark ? 'Light theme' : 'Dark theme'}
        aria-label="Toggle theme"
      >
        {isDark ? <IconSun className="ti" /> : <IconMoon className="ti" />}
      </button>
    </div>
  )
}
