/**
 * TopBar — logo, ghadi, live/replay toggle, theme toggle.
 * mockup_v2.html ka `.topbar`.
 *
 * KYUN mode toggle yahan sabse upar: ye poore dashboard ka sabse bada switch hai —
 * "abhi ka asli data" vs "2022 ka replay". Demo mein judge ke saamne yahi sabse pehle dabta hai.
 *
 * KYUN "Send alert" bhi yahan: dekhna aur karna do alag cheezein hain. Poora dashboard
 * dekhne ke liye hai; alert bhejna ekmatra ASLI kaam hai jo officer yahan se karta hai.
 * Wo kaam kisi drawer ke andar chhupa nahi hona chahiye.
 */

import { IconBell, IconMoon, IconRipple, IconSun } from '@tabler/icons-react'
import { useClock } from '../hooks/useDashboardData'

export default function TopBar({ mode, onModeChange, isDark, onThemeToggle, liveStale, onSendAlert, atRiskCount }) {
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
        {/* B1 — trained SAR model. Baaki do mode rule-based RiskEngine se chalte hain,
            ye ek alag cheez hai; isliye alag tab. */}
        <button
          className={mode === 'satellite' ? 'active' : ''}
          onClick={() => onModeChange('satellite')}
        >
          Satellite
        </button>
      </div>

      {/* ---- PRIMARY ACTION ----
          Dashboard ka sabse zaroori kaam. Pehle ye sirf drawer ke andar chhupa tha
          (map ka dot dhoondho -> drawer -> neeche scroll), to naya banda kabhi dhoondh
          hi nahi paata tha. Ab accent colour mein, hamesha upar.
          Badge mein "abhi kitne gaon khatre mein hain" — button khud bata deta hai ki
          use dabane ki zaroorat hai ya nahi. */}
      <button className="btn-primary" onClick={onSendAlert} title="Send an alert to a village">
        <IconBell className="ti" />
        Send alert
        {atRiskCount > 0 && <span className="cnt">{atRiskCount}</span>}
      </button>

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
