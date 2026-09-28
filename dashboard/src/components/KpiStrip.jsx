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
  IconRulerMeasure,
  IconSatellite,
  IconTargetArrow,
  IconUsers,
  IconWaveSine,
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

export default function KpiStrip({ snapshot, relief, alerts, sessionAlerts, loading, sar, opsDown, riskDown }) {
  /**
   * Satellite mode: KPI strip poori tarah SAR ke numbers dikhata hai.
   *
   * KYUN swap karte hain (village risk ke saath mix nahi karte): us tab pe officer ek
   * satellite scene dekh raha hai. Wahan "Danger zones 0" (live risk se) na sirf
   * irrelevant hai, balki khatarnak bhi — koi samajh sakta hai ki scene mein khatra
   * nahi hai, jabki wo number scene se aaya hi nahi.
   *
   * Aakhri card model ka IoU hai — provenance hamesha saamne rehta hai, chhupa hua nahi.
   */
  if (sar) {
    const d = sar.result?.detection
    const m = sar.model?.metrics_at_threshold
    const nearest = sar.result?.nearest_villages?.[0]
    const busy = sar.running

    return (
      <div className="kpis">
        <Kpi
          tone="accent"
          label="Flooded area"
          Icon={IconWaveSine}
          loading={busy}
          value={d ? d.flooded_area_sq_km : '—'}
          delta={d ? `sq km · scene is ${d.scene_area_sq_km} sq km` : 'run detection'}
        />
        <Kpi
          tone="neutral"
          label="Water coverage"
          Icon={IconRulerMeasure}
          loading={busy}
          value={d ? `${(d.water_fraction * 100).toFixed(1)}%` : '—'}
          delta={d ? `${num(d.water_pixels)} pixels at 10 m` : 'of scene'}
        />
        <Kpi
          tone="green"
          label="Mean confidence"
          Icon={IconTargetArrow}
          loading={busy}
          value={d ? d.mean_confidence : '—'}
          delta={d ? `threshold ${d.threshold}` : 'over water pixels'}
        />
        <Kpi
          tone="neutral"
          label="Nearest village"
          Icon={IconUsers}
          loading={busy}
          value={nearest ? `${nearest.distance_km} km` : '—'}
          delta={nearest ? `${nearest.name} · not a flood claim` : 'from scene centre'}
        />
        {/* Provenance KPI — jaan-bujh ke strip mein rakha hai, footnote mein nahi. */}
        <Kpi
          tone="amber"
          label="Model IoU (test)"
          Icon={IconSatellite}
          loading={false}
          value={m ? m.iou : '—'}
          delta={sar.model ? `Sen1Floods11 · ${sar.model.trained_chips} chips` : 'trained model'}
        />
      </div>
    )
  }

  const s = snapshot?.summary
  // riskDown: snapshot aaya hi nahi (live fail). "0 danger zones" dikhana jhootha all-clear
  // hota, aur skeleton hamesha ghoomta — dono galat. "—" + wajah.
  const na = riskDown && !s
  const reliefPending = relief ? relief.counts.new + relief.counts.inprogress : 0

  return (
    <div className="kpis">
      <Kpi
        tone="red"
        label="Danger zones"
        Icon={IconAlertTriangle}
        loading={loading && !na}
        value={na ? '—' : s?.by_level.red ?? 0}
        delta={na ? 'live data unavailable' : 'villages above danger mark'}
      />
      <Kpi
        tone="amber"
        label="Warning zones"
        Icon={IconAlertCircle}
        loading={loading && !na}
        value={na ? '—' : s?.by_level.yellow ?? 0}
        delta={na ? 'live data unavailable' : 'river or rainfall warning'}
      />
      <Kpi
        tone="accent"
        label="People at risk"
        Icon={IconUsers}
        loading={loading && !na}
        value={na ? '—' : num(s?.affected_population)}
        delta={na ? 'live data unavailable' : 'across danger + warning zones'}
      />
      {/* Relief aur alerts ASLI hain — replay mode mein bhi ye 2022 ke nahi, aaj ke hain.
          Isliye inka loading state risk snapshot se alag hai. */}
      <Kpi
        tone="neutral"
        label="Relief requests"
        Icon={IconLifebuoy}
        // opsDown: server nahi mila — skeleton hamesha ghoomta rehta, "—" + wajah likho.
        loading={!relief && !opsDown}
        value={relief ? num(relief.count) : '—'}
        delta={relief ? `${reliefPending} pending response` : 'needs the server'}
      />
      <Kpi
        tone="green"
        label="Alerts sent"
        Icon={IconBell}
        loading={!alerts && !opsDown}
        value={alerts ? num(alerts.count) : '—'}
        delta={
          !alerts ? 'needs the server' : sessionAlerts > 0 ? `${sessionAlerts} this session` : 'total dispatched'
        }
      />
    </div>
  )
}
