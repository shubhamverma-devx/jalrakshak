/**
 * SendAlertDialog — "Send alert" ka poora flow ek dialog mein.
 *
 * ============ YE KYUN BANA (sabse important baat) ============
 * Pehle alert bhejne ka SIRF EK rasta tha: map pe sahi dot dhoondho -> drawer khulao ->
 * neeche tak scroll karo -> button. Jo banda dashboard pehli baar dekh raha hai (judge,
 * naya officer) usko ye kabhi pata hi nahi chalta ki alert bhi bhej sakte hain. Ek command
 * center ka sabse zaroori kaam chhupa hua nahi hona chahiye.
 *
 * Ab topbar mein primary button hai, aur poora kaam ek jagah dikhta hai:
 *   gaon chuno (khatre wale sabse upar) -> message padho (Hindi + English) -> zaroorat ho
 *   to edit karo -> bhejo -> kitne device tak pahuncha, wo saaf dikhta hai.
 *
 * DRAWER WALA BUTTON HATA NAHI HAI. Wo map se seedha bhejne ka fast rasta hai (dot pe
 * click kiya, wahi gaon already select hai). Do raste ek hi API pe jaate hain.
 *
 * ============ MESSAGE KAHAN SE AATA HAI ============
 * RiskEngine ka apna `reason` + `advice`, Hindi aur English dono. Wahi text jo drawer mein
 * dikhta hai aur wahi jo citizen ke phone pe jaata hai. Isme asli numbers hote hain
 * ("85.4 m, danger mark 85.14 m") — generic warning nahi.
 * Officer edit kar sakta hai (naya): kabhi-kabhi zameen ki baat engine ko nahi pata hoti
 * ("NH-37 band hai"). Edit karne pe "auto-generated pe wapas jao" ka link rehta hai.
 *
 * DATA: villages prop se aate hain (wahi snapshot jo map dikha raha hai) — koi alag API
 * call nahi. POST /api/alert par jaata hai, response ka `push` block hi success screen
 * ke numbers banata hai — hum khud kuch calculate nahi karte.
 */

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  IconAlertTriangle,
  IconBell,
  IconCheck,
  IconChevronDown,
  IconLoader2,
  IconRotate,
  IconSearch,
  IconSend,
  IconX,
} from '@tabler/icons-react'

import { postAlert } from '../api/client'
import { OFFICER_NAME } from '../config'
import { levelClass, levelLabel } from '../utils/risk'
import { mm } from '../utils/format'

/** Risk order — red pehle, phir yellow, phir green. Sort ke liye. */
const LEVEL_RANK = { red: 0, yellow: 1, green: 2 }

/**
 * autoMessage() — ek gaon ke risk se do message banata hai.
 * INPUT : village row (risk.reason_* aur risk.advice_* ke saath)
 * OUTPUT: { hi, en }
 */
function autoMessage(v) {
  if (!v) return { hi: '', en: '' }
  const r = v.risk
  return {
    hi: `${r.reason_hi} ${r.advice_hi}`.trim(),
    en: `${r.reason_en} ${r.advice_en}`.trim(),
  }
}

export default function SendAlertDialog({ open, onClose, villages, riskSource, onSent, onToast }) {
  const [query, setQuery] = useState('')
  const [pickerOpen, setPickerOpen] = useState(false)
  const [villageId, setVillageId] = useState(null)
  const [hi, setHi] = useState('')
  const [en, setEn] = useState('')
  const [edited, setEdited] = useState(false)
  const [sending, setSending] = useState(false)
  const [result, setResult] = useState(null) // { village, push } — success screen

  const searchRef = useRef(null)

  const village = useMemo(
    () => villages.find((v) => v.id === villageId) || null,
    [villages, villageId],
  )

  /**
   * Gaon ki list — DO GROUP mein.
   * KYUN grouping: 30 gaon ki flat alphabetical list mein officer ko wahi dhoondhna padta
   * hai jo use pehle se pata ho. Flood mein sawaal ulta hota hai — "abhi kaun khatre mein
   * hai?". Isliye red/yellow sabse upar, red-first aur usme sabse zyada barish wala pehle.
   */
  const { atRisk, normal } = useMemo(() => {
    const q = query.trim().toLowerCase()
    const match = (v) =>
      !q || v.name.toLowerCase().includes(q) || (v.district || '').toLowerCase().includes(q)

    const sorted = [...villages].filter(match).sort((a, b) => {
      const d = LEVEL_RANK[a.risk.level] - LEVEL_RANK[b.risk.level]
      if (d !== 0) return d
      return b.risk.factors.rainfall_mm - a.risk.factors.rainfall_mm
    })

    return {
      atRisk: sorted.filter((v) => v.risk.level !== 'green'),
      normal: sorted.filter((v) => v.risk.level === 'green'),
    }
  }, [villages, query])

  /** Dialog band/khulne pe sab reset — purana gaon ya adha likha message dobara na dikhe. */
  useEffect(() => {
    if (!open) return
    setQuery('')
    setVillageId(null)
    setHi('')
    setEn('')
    setEdited(false)
    setResult(null)
    setPickerOpen(true) // khulte hi picker khula — pehla kaam yehi hai
  }, [open])

  /** Picker khulte hi search box mein cursor — officer seedha type kar sakta hai. */
  useEffect(() => {
    if (pickerOpen) searchRef.current?.focus()
  }, [pickerOpen])

  /** Escape se band. */
  useEffect(() => {
    if (!open) return
    const onKey = (e) => {
      if (e.key !== 'Escape') return
      if (pickerOpen) setPickerOpen(false)
      else onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, pickerOpen, onClose])

  /**
   * Gaon select — message turant auto-generate.
   * Agar officer ne pehle kuch edit kiya tha to wo yahan overwrite hota hai, kyunki naye
   * gaon ka purana message bhejna sabse buri galti hogi.
   */
  const pick = useCallback((v) => {
    setVillageId(v.id)
    const m = autoMessage(v)
    setHi(m.hi)
    setEn(m.en)
    setEdited(false)
    setPickerOpen(false)
    setQuery('')
  }, [])

  /** Edit ke baad wapas auto-generated text pe. */
  const resetMessage = useCallback(() => {
    const m = autoMessage(village)
    setHi(m.hi)
    setEn(m.en)
    setEdited(false)
  }, [village])

  const canSend = !!village && hi.trim() !== '' && en.trim() !== '' && !sending

  const send = useCallback(async () => {
    if (!canSend) return
    setSending(true)
    try {
      const res = await postAlert({
        village_id: village.id,
        message_hi: hi.trim(),
        message_en: en.trim(),
        sent_by: OFFICER_NAME,
      })
      // Success screen backend ke apne `push` block se banti hai — hum kuch maan kar
      // nahi likhte. devices 0 ho to wo bhi saaf dikhta hai (neeche warning).
      setResult({ village, push: res.push })
      onSent()
    } catch (err) {
      onToast({ text: err.message, type: 'error' })
    } finally {
      setSending(false)
    }
  }, [canSend, village, hi, en, onSent, onToast])

  /** "Send another" — success screen se wapas form pe, khaali. */
  const again = useCallback(() => {
    setResult(null)
    setVillageId(null)
    setHi('')
    setEn('')
    setEdited(false)
    setPickerOpen(true)
  }, [])

  if (!open) return null

  const cls = village ? levelClass(village.risk.level) : null

  return (
    <div className="modal-ov" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" role="dialog" aria-modal="true" aria-label="Send alert">
        <div className="modal-head">
          <div className="mt">
            <IconBell className="ti" />
            {result ? 'Alert sent' : 'Send alert to a village'}
          </div>
          {!result && (
            <div className="ms">
              Every citizen app registered in the village gets a push notification. Risk levels
              below are from <b>{riskSource}</b>.
            </div>
          )}
          <button className="dclose" onClick={onClose} aria-label="Close">
            <IconX className="ti" />
          </button>
        </div>

        {result ? (
          /* ---------------- SUCCESS ---------------- */
          <>
            <div className="modal-body">
              <div className="sent-ok">
                <div className="ring">
                  <IconCheck className="ti" />
                </div>
                <div className="h">Alert sent to {result.village.name}</div>
                <div className="s">
                  Saved to the alert log and pushed to registered devices in{' '}
                  {result.village.district} district.
                </div>

                <div className="sent-stats">
                  <div className="sent-stat">
                    <div className="n">{result.push.devices}</div>
                    <div className="l">Devices registered</div>
                  </div>
                  <div className={`sent-stat${result.push.success > 0 ? ' ok' : ''}`}>
                    <div className="n">{result.push.success}</div>
                    <div className="l">Delivered</div>
                  </div>
                  <div className={`sent-stat${result.push.failed > 0 ? ' bad' : ''}`}>
                    <div className="n">{result.push.failed}</div>
                    <div className="l">Failed</div>
                  </div>
                </div>

                {/* IMANDAARI: device 0 hone pa "sent!" dikha dena jhooth hai — alert DB mein
                    hai par kisi phone tak nahi gaya. Ye farq officer ko dikhna hi chahiye. */}
                {result.push.devices === 0 && (
                  <div className="sent-warn">
                    <IconAlertTriangle className="ti" />
                    <span>
                      No devices are registered in {result.village.name} yet, so this alert
                      reached no one. It is saved in the alert log and will be delivered to
                      anyone who installs the citizen app and selects this village.
                    </span>
                  </div>
                )}
                {result.push.error && (
                  <div className="sent-warn">
                    <IconAlertTriangle className="ti" />
                    <span>Push error: {result.push.error}</span>
                  </div>
                )}
              </div>
            </div>

            <div className="modal-foot">
              <div className="note" />
              <button className="btn-ghost" onClick={again}>
                Send another
              </button>
              <button className="btn-primary" onClick={onClose}>
                Done
              </button>
            </div>
          </>
        ) : (
          /* ---------------- FORM ---------------- */
          <>
            <div className="modal-body">
              {/* ---- 1. Village ---- */}
              <div className="fld">
                <div className="fld-lbl">
                  <span className="step">1</span> Village
                </div>

                <button
                  className={`combo${pickerOpen ? ' open' : ''}`}
                  onClick={() => setPickerOpen((o) => !o)}
                >
                  {village ? (
                    <>
                      <span className={`rdot ${cls}`} />
                      <span>
                        <b style={{ fontWeight: 600 }}>{village.name}</b>
                        <span style={{ color: 'var(--ink3)', fontSize: 11.5 }}>
                          {' '}
                          · {village.district}
                        </span>
                      </span>
                      <span className={`rpill ${cls}`} style={{ marginLeft: 8 }}>
                        {levelLabel(village.risk.level)}
                      </span>
                    </>
                  ) : (
                    <span className="ph">Choose a village…</span>
                  )}
                  <IconChevronDown className="ti chev" />
                </button>

                {pickerOpen && (
                  <div className="combo-panel">
                    <div className="combo-search">
                      <IconSearch className="ti" />
                      <input
                        ref={searchRef}
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                        placeholder="Search village or district…"
                      />
                    </div>

                    <div className="combo-list">
                      {atRisk.length === 0 && normal.length === 0 && (
                        <div style={{ padding: '14px 12px', fontSize: 11.5, color: 'var(--ink3)' }}>
                          No village matches “{query}”.
                        </div>
                      )}

                      {atRisk.length > 0 && (
                        <>
                          <div className="combo-group">At risk right now · {atRisk.length}</div>
                          {atRisk.map((v) => (
                            <VillageOption key={v.id} v={v} onPick={pick} active={v.id === villageId} />
                          ))}
                        </>
                      )}

                      {normal.length > 0 && (
                        <>
                          <div className="combo-group">Normal · {normal.length}</div>
                          {normal.map((v) => (
                            <VillageOption key={v.id} v={v} onPick={pick} active={v.id === villageId} />
                          ))}
                        </>
                      )}
                    </div>
                  </div>
                )}

                {/* Chuna hua gaon kyun khatre mein hai — English reason, ek line.
                    Officer ko bhejne se pehle wajah dikhni chahiye. */}
                {village && !pickerOpen && (
                  <div className="reason-note">{village.risk.reason_en}</div>
                )}
              </div>

              {/* ---- 2. Message ---- */}
              <div className="fld">
                <div className="fld-lbl">
                  <span className="step">2</span> Message
                </div>
                <div className="fld-help">
                  Written automatically from this village’s current rainfall and river reading.
                  Edit either language if you need to add something the system cannot know.
                </div>

                {!village ? (
                  <div className="reason-note" style={{ borderLeftColor: 'var(--line2)' }}>
                    Pick a village first — the message is built from its risk reading.
                  </div>
                ) : (
                  <>
                    <div className="ta-lbl">
                      <span>HINDI · this is what appears on the phone</span>
                      {edited && (
                        <button className="linkbtn" onClick={resetMessage}>
                          <IconRotate className="ti" style={{ width: 11, height: 11 }} /> Reset to
                          auto-generated
                        </button>
                      )}
                    </div>
                    <textarea
                      className="ta hindi"
                      value={hi}
                      rows={3}
                      onChange={(e) => {
                        setHi(e.target.value)
                        setEdited(true)
                      }}
                    />

                    <div className="ta-lbl" style={{ marginTop: 10 }}>
                      <span>ENGLISH</span>
                    </div>
                    <textarea
                      className="ta"
                      value={en}
                      rows={3}
                      onChange={(e) => {
                        setEn(e.target.value)
                        setEdited(true)
                      }}
                    />
                  </>
                )}
              </div>
            </div>

            <div className="modal-foot">
              <div className="note">
                {village
                  ? `Sent as ${OFFICER_NAME} · logged in the alert history`
                  : 'At-risk villages are listed first.'}
              </div>
              <button className="btn-ghost" onClick={onClose}>
                Cancel
              </button>
              <button className="btn-primary" onClick={send} disabled={!canSend}>
                {sending ? (
                  <>
                    <IconLoader2 className="ti" style={{ animation: 'spin 1s linear infinite' }} />
                    Sending…
                  </>
                ) : (
                  <>
                    <IconSend className="ti" />
                    Send alert
                  </>
                )}
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}

/** Ek gaon ka option row — naam, district, risk pill, aaj ki barish. */
function VillageOption({ v, onPick, active }) {
  const cls = levelClass(v.risk.level)
  return (
    <button className={`vopt${active ? ' active' : ''}`} onClick={() => onPick(v)}>
      <span className={`rdot ${cls}`} />
      <span style={{ minWidth: 0 }}>
        <div className="nm">{v.name}</div>
        <div className="ds">{v.district}</div>
      </span>
      <span className="rain">{mm(v.risk.factors.rainfall_mm)}</span>
      <span className={`rpill ${cls}`}>{levelLabel(v.risk.level)}</span>
    </button>
  )
}
