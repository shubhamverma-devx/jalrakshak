/**
 * VillageDrawer — map ke dot pe click karne se khulne wala side panel.
 * mockup_v2.html ka `.drawer`.
 *
 * DATA: GET /api/village/{id}?mode=&day=  -> village + river_station + shelters + alerts
 *       Mini rainfall chart preloaded replay snapshots se banta hai (12 din ka asli series).
 *
 * KYUN ALAG API CALL (jabki village row map wale response mein already hai):
 *   Drawer ko wo cheezein chahiye jo list response mein nahi hain — nearest shelter,
 *   river station ke thresholds, is gaon ke purane alerts. 30 gaon ki poori list ke saath
 *   ye sab bhejna payload ko kaafi bada kar deta, jabki ek waqt mein ek hi gaon khulta hai.
 *
 * KYUN reason_hi (Hindi) dikhate hain: BUILD_PLAN section 2 — Hindi + English dono locked hain.
 * Officer ko wahi text dikhna chahiye jo citizen ke phone pe jaayega, taaki alert bhejne se
 * pehle wo padh ke check kar sake ki message sahi hai.
 */

import { useCallback, useEffect, useMemo, useState } from 'react'
import { Line } from 'react-chartjs-2'
import {
  IconAlertCircle,
  IconAlertTriangle,
  IconBell,
  IconCircleCheck,
  IconLoader2,
  IconX,
} from '@tabler/icons-react'
import './charts'
import { getVillage, postAlert } from '../api/client'
import { OFFICER_NAME } from '../config'
import { metres, mm, num, shortDay } from '../utils/format'
import { levelClass, levelColor, levelLabel } from '../utils/risk'

/** Level ke hisaab se badge ka icon. */
const LEVEL_ICON = { red: IconAlertTriangle, yellow: IconAlertCircle, green: IconCircleCheck }

/** Ek detail row (label : value). */
function Row({ k, v }) {
  return (
    <div className="drow">
      <span className="k">{k}</span>
      <span className="v">{v}</span>
    </div>
  )
}

export default function VillageDrawer({
  village, // map se aaya row (turant dikhane ke liye — risk pehle se paas hai)
  mode,
  day,
  snapshots, // saare replay din (mini chart ke liye)
  days,
  onClose,
  onAlertSent,
  onToast,
}) {
  const [detail, setDetail] = useState(null) // API se aaya extra data
  const [loading, setLoading] = useState(false)
  const [sending, setSending] = useState(false)

  const villageId = village?.id

  /**
   * Detail fetch — jab bhi gaon ya din badle.
   * KYUN day dependency mein hai: replay slider ghumane pe drawer khula reh sakta hai,
   * aur us gaon ka risk har din badalta hai. Drawer ko map ke saath sync rehna hi chahiye —
   * warna map RED dikhaye aur drawer GREEN, jo flood mein khatarnaak confusion hai.
   */
  useEffect(() => {
    if (!villageId) return

    const ac = new AbortController()
    setLoading(true)

    getVillage(villageId, mode, mode === 'replay' ? day : null, ac.signal)
      .then((d) => setDetail(d))
      .catch((err) => {
        if (err.name !== 'AbortError') onToast({ text: err.message, type: 'error' })
      })
      .finally(() => setLoading(false))

    return () => ac.abort()
  }, [villageId, mode, day, onToast])

  /**
   * Mini chart ka data — is gaon ka 12-din ka rainfall.
   * Preloaded snapshots se nikalta hai, isliye koi extra API call nahi. Asli RiskEngine data.
   */
  const series = useMemo(() => {
    if (mode !== 'replay' || !snapshots?.length || !villageId) return null
    return snapshots.map(
      (s) => s.villages.find((v) => v.id === villageId)?.risk.factors.rainfall_mm ?? 0,
    )
  }, [snapshots, villageId, mode])

  /**
   * sendAlert() — POST /api/alert.
   *
   * Message kya bhejte hain: RiskEngine ka apna `reason` + `advice`. KYUN:
   *  - Wo text pehle hi Hindi aur English dono mein maujood hai (backend ne banaya)
   *  - Usmein asli numbers hote hain ("85.4 m, danger mark 85.14 m") — generic warning nahi
   *  - Officer ko wahi text drawer mein dikh chuka hai, to koi surprise nahi
   * Deployment mein officer isko edit kar sakega; abhi scope LOCKED hai (koi editor nahi).
   */
  const sendAlert = useCallback(async () => {
    if (!village || sending) return

    const r = village.risk
    setSending(true)

    try {
      await postAlert({
        village_id: village.id,
        message_hi: `${r.reason_hi} ${r.advice_hi}`,
        message_en: `${r.reason_en} ${r.advice_en}`,
        sent_by: OFFICER_NAME,
      })

      onToast({ text: `Alert sent to ${village.name}`, type: 'ok' })
      onAlertSent() // KPI + feed turant refresh, 30-sec poll ka wait nahi
    } catch (err) {
      onToast({ text: err.message, type: 'error' })
    } finally {
      setSending(false)
    }
  }, [village, sending, onToast, onAlertSent])

  // Gaon select nahi hai — drawer band (par DOM mein rehta hai taaki slide animation chale).
  if (!village) return <div className="drawer" />

  const risk = village.risk
  const cls = levelClass(risk.level)
  const Icon = LEVEL_ICON[risk.level] || IconCircleCheck
  const color = levelColor(risk.level)

  // Nearest shelter — API `shelters` us gaon se jude camps deta hai.
  const shelter = detail?.shelters?.[0]

  return (
    <div className="drawer show">
      <div className={`dhead ${cls}`}>
        <button className="dclose" onClick={onClose} aria-label="Close">
          <IconX className="ti" />
        </button>
        <span className="dbadge">
          <Icon className="ti" />
          {levelLabel(risk.level)}
        </span>
        <div className="dname">{village.name}</div>
        <div className="ddist">{village.district} district</div>
      </div>

      <Row k="Rainfall today" v={mm(risk.factors.rainfall_mm)} />
      <Row k="3-day rainfall" v={mm(risk.factors.rainfall_3day_mm)} />

      {/* River level "(est.)" likha hai jaan-bujh ke — ye derived proxy hai, asli gauge
          reading nahi (decision D9). Officer ko ye farq dikhna chahiye. Jin stations ka
          threshold data hi nahi, wahan "No river data" — jhoota number nahi. */}
      <Row
        k="River level (est.)"
        v={risk.factors.river_data ? metres(risk.factors.river_level_m) : 'No river data'}
      />
      <Row
        k="Danger mark"
        v={risk.factors.danger_level_m ? metres(risk.factors.danger_level_m) : '—'}
      />
      <Row k="Elevation" v={`${village.elevation_m} m`} />
      <Row k="Population" v={num(village.population)} />
      <Row
        k="Nearest shelter"
        v={loading ? '…' : shelter ? shelter.name : 'None mapped'}
      />
      {shelter && <Row k="Shelter capacity" v={num(shelter.capacity)} />}

      {/* --- Mini rainfall trend (sirf replay mein — live mein series hai hi nahi) --- */}
      {series && (
        <>
          <div className="dmini-lbl" style={{ marginTop: 12 }}>
            Rainfall trend · 12 days
          </div>
          <div className="dmini">
            <Line
              data={{
                labels: days.map(shortDay),
                datasets: [
                  {
                    data: series,
                    borderColor: color,
                    backgroundColor: color + '1a',
                    fill: true,
                    tension: 0.4,
                    pointRadius: 0,
                    borderWidth: 2,
                  },
                ],
              }}
              options={{
                plugins: { legend: { display: false }, tooltip: { enabled: true } },
                scales: { x: { display: false }, y: { display: false } },
                maintainAspectRatio: false,
                animation: false,
              }}
            />
          </div>
        </>
      )}

      {/* --- RiskEngine ka Hindi reason — border ka rang level ke hisaab se --- */}
      <div className="dreason hindi" style={{ borderLeftColor: color }}>
        {risk.reason_hi}
        <div className="deta">{risk.water_eta_hi}</div>
      </div>

      <button className="dalert" onClick={sendAlert} disabled={sending}>
        {sending ? (
          <>
            <IconLoader2 className="ti" style={{ animation: 'spin 1s linear infinite' }} />
            Sending…
          </>
        ) : (
          <>
            <IconBell className="ti" />
            Send alert to village
          </>
        )}
      </button>
    </div>
  )
}
