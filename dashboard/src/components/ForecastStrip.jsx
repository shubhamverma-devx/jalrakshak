/**
 * ForecastStrip — village drawer mein "agle 48 ghante" ki patti. (B2)
 *
 * DATA: GET /api/village/{id}/forecast
 *
 * ============ YE PATTI IMANDAAR KYUN HONI CHAHIYE ============
 * Baaki poora dashboard ABHI ka haal dikhata hai — wo rule-based hai aur uske peeche
 * IMD ke official thresholds hain. Ye ek hi jagah hai jahan ek TRAINED MODEL kuch keh
 * raha hai, aur wo model apne test set pe ek trivial baseline se behtar NAHI hai:
 *
 *   +24h macro-F1 0.609  vs  "aaj wala hi level" ka 0.600   (+0.009 — jeet nahi kehte)
 *   +48h macro-F1 0.483  vs  0.511                          (peeche)
 *   RED recall: +24h 0.24 (11/45) · +48h 0.09 (4/45)
 *
 * NWP atmospheric features bhi try kiye — test par sudhaar nahi hua.
 *
 * Isliye patti ke neeche accuracy ki line HAMESHA dikhti hai, aur numbers backend se
 * aate hain (hardcode nahi) taaki model dobara train ho to UI apne aap sach bole.
 * Agar kabhi ye line hatane ka man kare — mat hatana. Yehi is feature ko theek rakhti hai.
 */

import { useEffect, useState } from 'react'
import { IconAlertTriangle, IconChevronDown, IconClock } from '@tabler/icons-react'

import { getVillageForecast } from '../api/client'
import { levelClass, levelLabel } from '../utils/risk'

/** Ek khaana — abhi / +24h / +48h. */
function Cell({ title, level, sub, confidence }) {
  const cls = level ? levelClass(level) : null
  return (
    <div className="fc-cell">
      <div className="fc-when">{title}</div>
      <div className={`fc-level ${cls || ''}`}>{level ? levelLabel(level) : '—'}</div>
      {sub && <div className="fc-sub">{sub}</div>}
      {confidence !== undefined && (
        <div className="fc-conf" title={`${Math.round(confidence * 100)}% of rainfall scenarios agree`}>
          {/* Confidence banayi hui nahi hai: model 6 alag rainfall scenarios deta hai,
              ye batata hai unme se kitne isi level pe pahunche. */}
          {Math.round(confidence * 100)}% agree
        </div>
      )}
    </div>
  )
}

export default function ForecastStrip({ villageId }) {
  const [state, setState] = useState({ loading: true, data: null, error: null })
  const [open, setOpen] = useState(false)

  useEffect(() => {
    if (!villageId) return
    const ac = new AbortController()
    setState({ loading: true, data: null, error: null })

    getVillageForecast(villageId, ac.signal)
      .then((data) => setState({ loading: false, data, error: null }))
      .catch((err) => {
        if (err.name === 'AbortError') return
        setState({ loading: false, data: null, error: err.message })
      })

    return () => ac.abort()
  }, [villageId])

  if (state.loading) {
    return (
      <div className="fc-wrap">
        <div className="fc-head">
          <IconClock className="ti" /> Next 48 hours
        </div>
        <div className="fc-grid">
          {[0, 1, 2].map((i) => (
            <div key={i} className="fc-cell">
              <div className="sk" style={{ height: 9, width: '60%', margin: '0 auto' }} />
              <div className="sk" style={{ height: 14, width: '80%', margin: '7px auto 0' }} />
            </div>
          ))}
        </div>
      </div>
    )
  }

  /* Forecast na mile to poora block chhupa dete hain — drawer ka baaki detail (jo
     rule-based aur bharosemand hai) uske bina bhi poora kaam ka hai. Ek laal error box
     yahan officer ko sirf darayega, dega kuch nahi. */
  if (state.error || !state.data) return null

  const { forecast, model } = state.data
  const h24 = forecast.horizons.find((h) => h.hours === 24)
  const h48 = forecast.horizons.find((h) => h.hours === 48)
  const m = model?.metrics
  const base = model?.baseline

  return (
    <div className="fc-wrap">
      <div className="fc-head">
        <IconClock className="ti" /> Next 48 hours
        <span className="fc-tag">forecast</span>
      </div>

      <div className="fc-grid">
        <Cell title="NOW" level={forecast.recent.level_now} sub={`${forecast.recent.rain_today_mm} mm`} />
        <Cell title="+24H" level={h24?.level} sub={`${h24?.rain_mm} mm`} confidence={h24?.confidence} />
        <Cell title="+48H" level={h48?.level} sub={`${h48?.rain_mm} mm`} confidence={h48?.confidence} />
      </div>

      {/* ---- Accuracy line. Ye hamesha dikhti hai, chhupi nahi. ---- */}
      <button className="fc-note" onClick={() => setOpen((o) => !o)}>
        <IconAlertTriangle className="ti" />
        <span>
          Roughly matches a “same as today” baseline — misses most red days
        </span>
        <IconChevronDown className="ti chev" style={{ transform: open ? 'rotate(180deg)' : '' }} />
      </button>

      {open && (
        <div className="fc-detail">
          <div className="fc-row">
            <span>+24h macro-F1</span>
            <b>
              {m?.h24?.macro_f1?.toFixed(3)}{' '}
              <span className="vs">vs {base?.h24_macro_f1?.toFixed(3)} baseline</span>
            </b>
          </div>
          <div className="fc-row">
            <span>+48h macro-F1</span>
            <b>
              {m?.h48?.macro_f1?.toFixed(3)}{' '}
              <span className="vs">vs {base?.h48_macro_f1?.toFixed(3)} baseline</span>
            </b>
          </div>
          <div className="fc-row">
            <span>Red days caught</span>
            <b>
              {Math.round((m?.h24?.red_recall ?? 0) * 100)}% at +24h ·{' '}
              {Math.round((m?.h48?.red_recall ?? 0) * 100)}% at +48h
            </b>
          </div>
          <p className="fc-p">
            Trained on {model?.dataset}. Target is this dashboard’s own risk rule applied to the
            future day — not observed flooding. Held-out test {model?.test_period}, only{' '}
            {m?.h24?.red_support} red days in it.
          </p>
          <p className="fc-p strong">
            Do not use this alone to order an evacuation. Current risk on the left is rule-based
            and is what alerts are sent on.
          </p>
        </div>
      )}
    </div>
  )
}
