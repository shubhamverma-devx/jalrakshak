/**
 * KpiStrip — upar ke 5 numbers. mockup_v2.html ka `.kpis`.
 *
 * KYUN ye 5 hi: officer ke pehle paanch sawaal —
 *   "kitne gaon khatre mein?" · "kitne warning pe?" · "kitne log?" ·
 *   "kitni madad maangi gayi?" · "kitne alert gaye?"
 * Har card ka number ASLI API se aata hai, koi hardcoded value nahi.
 */

import {
  IconAlertCircle,
  IconAlertTriangle,
  IconBell,
  IconLifebuoy,
  IconUsers,
} from '@tabler/icons-react'
import { num } from '../utils/format'

/**
 * Kpi — ek card.
 * INPUT: tone (css class), label, value, delta (chhoti explanation line), Icon, loading
 * KYUN `loading` prop: pehli load pe "0" dikhana galat hai — 0 ka matlab hota hai
 * "koi gaon khatre mein nahi", jo hum abhi jaante hi nahi. Isliye skeleton dikhate hain.
 */
function Kpi({ tone, label, value, delta, Icon, loading }) {
  return (
    <div className={`kpi ${tone}`}>
      <div className="top">
        <span className="lbl">{label}</span>
        <span className="ic">
          <Icon className="ti" />
        </span>
      </div>
      {loading ? (
        <div className="sk" style={{ height: 26, width: '55%' }} />
      ) : (
        <div className="val">{value}</div>
      )}
      <div className="delta">{loading ? ' ' : delta}</div>
    </div>
  )
}

export default function KpiStrip({ snapshot, relief, alerts, sessionAlerts, loading }) {
  const s = snapshot?.summary
  const reliefPending = relief ? relief.counts.new + relief.counts.inprogress : 0

  return (
    <div className="kpis">
      <Kpi
        tone="red"
        label="Danger zones"
        Icon={IconAlertTriangle}
        loading={loading}
        value={s?.by_level.red ?? 0}
        delta="villages above danger mark"
      />
      <Kpi
        tone="amber"
        label="Warning zones"
        Icon={IconAlertCircle}
        loading={loading}
        value={s?.by_level.yellow ?? 0}
        delta="river or rainfall warning"
      />
      <Kpi
        tone="accent"
        label="People at risk"
        Icon={IconUsers}
        loading={loading}
        value={num(s?.affected_population)}
        delta="across danger + warning zones"
      />
      {/* Relief aur alerts ASLI hain — replay mode mein bhi ye 2022 ke nahi, aaj ke hain.
          Isliye inka loading state risk snapshot se alag hai. */}
      <Kpi
        tone="neutral"
        label="Relief requests"
        Icon={IconLifebuoy}
        loading={!relief}
        value={num(relief?.count)}
        delta={`${reliefPending} pending response`}
      />
      <Kpi
        tone="green"
        label="Alerts sent"
        Icon={IconBell}
        loading={!alerts}
        value={num(alerts?.count)}
        delta={sessionAlerts > 0 ? `${sessionAlerts} this session` : 'total dispatched'}
      />
    </div>
  )
}
