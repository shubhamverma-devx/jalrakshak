/**
 * ActivityFeed — "abhi kya ho raha hai" ki ek dhaara.
 * mockup_v2.html ka `.feed-item`.
 *
 * ============ MOCKUP SE SABSE BADA FARQ, AUR KYUN ============
 * Mockup mein feed ek hardcoded array tha ("SOS from Majuli — 2 min ago"). Ab har item
 * ASLI data se banta hai, teen source ko mila kar:
 *
 *   sos   <- GET /api/relief  (citizen ne SOS bheja)
 *   alert <- GET /api/alerts  (officer ne alert bheja)
 *   sys   <- abhi ke risk snapshot se DERIVE kiya hua (kaunsi nadi danger paar kar gayi,
 *            kis gaon mein sabse zyada barish)
 *
 * `sys` items derive karte hain kyunki backend mein "events" ka koi table nahi hai
 * (BUILD_PLAN section 7 mein 7 tables hain, events uska hissa nahi — aur scope LOCKED hai).
 * Par jo hum derive karte hain wo banaya hua nahi hai: wo ussi RiskEngine output se aata
 * hai jo map pe dikh raha hai. Jhoothi ghatnayein nahi bante — sirf jo already sach hai
 * usko padhne layak line mein likhte hain.
 * ==============================================================
 */

import { useMemo } from 'react'
import { IconActivity, IconBell, IconCloudRain, IconUrgent, IconWaveSawTool } from '@tabler/icons-react'
import { Empty } from './Panel'
import { num, timeAgo } from '../utils/format'

/** Feed ke teen type ka icon. */
const ICONS = { sos: IconUrgent, alert: IconBell, sys: IconCloudRain, river: IconWaveSawTool }

/**
 * buildFeed() — teeno source ko ek sorted list mein badalta hai.
 *
 * INPUT : relief, alerts, snapshot (abhi ka risk map), mode
 * OUTPUT: feed items ka array (naya sabse upar), max 25
 *
 * KYUN default max 25: panel scrollable hai par 500 items render karne ka koi fayda nahi —
 * officer utna neeche kabhi nahi jaata, aur DOM bhaari ho jaata hai.
 * "View all" modal Infinity bhejta hai — wahan poori list dikhani hi hoti hai.
 */
export function buildFeed(relief, alerts, snapshot, mode, limit = 25) {
  const items = []

  // --- 1. SOS (asli, timestamp ke saath) ---------------------------------------
  ;(relief?.requests || []).forEach((r) => {
    items.push({
      key: `sos-${r.id}`,
      type: 'sos',
      title: `SOS from ${r.village?.name || 'unknown village'}`,
      msg: r.message,
      at: r.created_at,
    })
  })

  // --- 2. Bheje gaye alerts (asli, timestamp ke saath) --------------------------
  ;(alerts?.alerts || []).forEach((a) => {
    items.push({
      key: `alert-${a.id}`,
      type: 'alert',
      title: `Alert sent to ${a.village?.name || 'village'}`,
      msg: `${a.sent_by} · ${a.message_en}`,
      at: a.sent_at,
    })
  })

  // Asli events time ke hisaab se — naya sabse upar.
  items.sort((a, b) => new Date(b.at) - new Date(a.at))

  // --- 3. System observations (abhi ke snapshot se derive) ----------------------
  // Ye sabse upar rehte hain kyunki ye "abhi ki soorat-e-haal" hain, purani ghatna nahi.
  const sys = []
  const villages = snapshot?.villages || []

  // (a) Sabse ooncha paani — kaunsi nadi danger mark paar kar chuki hai.
  const overDanger = villages
    .filter((v) => v.risk.factors.river_data && v.risk.level === 'red')
    .sort(
      (a, b) =>
        b.risk.factors.river_level_m - b.risk.factors.danger_level_m -
        (a.risk.factors.river_level_m - a.risk.factors.danger_level_m),
    )[0]

  if (overDanger) {
    const f = overDanger.risk.factors
    sys.push({
      key: 'sys-river',
      type: 'river',
      title: 'River above danger mark',
      msg: `${overDanger.name} — ${f.river_level_m} m vs danger ${f.danger_level_m} m`,
      at: snapshot?.generated_at,
      stamp: mode === 'replay' ? snapshot?.date : null,
    })
  }

  // (b) Sabse zyada barish wala gaon.
  const wettest = [...villages].sort(
    (a, b) => b.risk.factors.rainfall_mm - a.risk.factors.rainfall_mm,
  )[0]

  if (wettest && wettest.risk.factors.rainfall_mm > 0) {
    sys.push({
      key: 'sys-rain',
      type: 'sys',
      title: 'Heaviest rainfall',
      msg: `${wettest.name} — ${wettest.risk.factors.rainfall_mm} mm in 24h (${wettest.risk.factors.rainfall_category.replace('_', ' ')})`,
      at: snapshot?.generated_at,
      stamp: mode === 'replay' ? snapshot?.date : null,
    })
  }

  // (c) Kitne log khatre mein — ek line ka summary.
  if (snapshot?.summary?.affected_population > 0) {
    sys.push({
      key: 'sys-affected',
      type: 'sys',
      title: 'Population at risk updated',
      msg: `${num(snapshot.summary.affected_population)} people across ${snapshot.summary.by_level.red + snapshot.summary.by_level.yellow} villages`,
      at: snapshot?.generated_at,
      stamp: mode === 'replay' ? snapshot?.date : null,
    })
  }

  const all = [...sys, ...items]
  return limit === Infinity ? all : all.slice(0, limit)
}

export default function ActivityFeed({ relief, alerts, snapshot, mode, loading, limit }) {
  const feed = useMemo(
    () => buildFeed(relief, alerts, snapshot, mode, limit),
    [relief, alerts, snapshot, mode, limit],
  )

  if (loading) {
    return (
      <div>
        {Array.from({ length: 4 }).map((_, i) => (
          <div key={i} className="feed-item">
            <div className="sk" style={{ width: 26, height: 26, borderRadius: 6, flexShrink: 0 }} />
            <div style={{ flex: 1 }}>
              <div className="sk" style={{ height: 10, width: '60%' }} />
              <div className="sk" style={{ height: 9, width: '85%', marginTop: 5 }} />
            </div>
          </div>
        ))}
      </div>
    )
  }

  if (!feed.length) return <Empty Icon={IconActivity}>Abhi koi activity nahi</Empty>

  return (
    <>
      {feed.map((f) => (
        <FeedItem key={f.key} f={f} />
      ))}
    </>
  )
}

/**
 * FeedItem — feed ka ek item.
 * KYUN alag component: panel aur "View all" modal dono yahi markup use karte hain.
 * Do jagah copy karte to ek jagah style badalne pe doosri peeche reh jaati.
 */
export function FeedItem({ f }) {
  const Icon = ICONS[f.type] || IconActivity
  // 'river' bhi visually 'sys' hi hai (amber) — sirf icon alag.
  const cls = f.type === 'river' ? 'sys' : f.type

  return (
    <div className={`feed-item ${cls}`}>
      <div className="fic">
        <Icon className="ti" />
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div className="ft">{f.title}</div>
        <div className="fm">{f.msg}</div>
        {/* Replay ke derived items pe 2022 ki DATE likhte hain, "2 min ago" nahi.
            Warna officer ko lagega ki nadi abhi, is waqt danger paar kar rahi hai —
            jabki wo 2022 ka replay hai. Ye galatfehmi flood dashboard mein bhaari padegi. */}
        <div className="ftime">{f.stamp ? `Replay · ${f.stamp}` : timeAgo(f.at)}</div>
      </div>
    </div>
  )
}
