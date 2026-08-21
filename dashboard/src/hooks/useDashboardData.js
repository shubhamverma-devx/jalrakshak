/**
 * useDashboardData — dashboard ka poora data layer, ek hook mein.
 *
 * KYA KARTA HAI:
 *   1. Mount pe replay ke SAARE 12 din ek saath fetch karta hai (parallel)
 *   2. Live mode ka snapshot alag fetch karta hai (aur time-time pe refresh)
 *   3. Relief requests + alerts poll karta hai
 *   4. Loading / error state sambhalta hai
 *
 * ============ KYUN SAARE 12 DIN EK SAATH? (ye sabse important decision hai) ============
 *  Do wajah:
 *
 *  (a) SLIDER INSTANT CHAHIYE. Demo mein judge ke saamne slider ghumega ya auto-play chalega
 *      (har 950ms ek din). Har din pe naya HTTP call karte to slider laggy dikhta — poore
 *      demo ka sabse dramatic moment kharab ho jaata. Sab pehle se memory mein ho to
 *      scrubbing bilkul instant hai.
 *
 *  (b) 12-DIN KA TREND CHART ASLI DATA SE BANE. Mockup mein trend dummy formula se banta tha.
 *      Backend ek request mein ek hi din deta hai. Saare din ho to trend (average rainfall +
 *      affected population per day) ASLI RiskEngine output se banta hai, banaya hua nahi.
 *      Drawer ka mini rainfall chart bhi isi se banta hai — us gaon ka 12-din ka asli data.
 *
 *  Ye mehnga nahi hai: backend replay ko 24 ghante cache karta hai (RiskMapService), to
 *  12 request bijli ki tarah aati hain, aur poore session mein sirf EK BAAR jaati hain.
 * =======================================================================================
 */

import { useCallback, useEffect, useRef, useState } from 'react'
import { getAlerts, getRelief, getVillages } from '../api/client'
import { POLL_MS } from '../config'

export function useDashboardData(mode) {
  // --- Replay: saare din ke snapshots (ek baar load hote hain) ---------------------
  const [replay, setReplay] = useState({ days: [], snapshots: [], loading: true, error: null })

  // --- Live: ek snapshot (mode 'live' hone pe load + refresh hota hai) -------------
  const [live, setLive] = useState({ snapshot: null, loading: false, error: null })

  // --- Officer ka operational data (dono mode mein same — ye asli, abhi ka data hai) -
  const [ops, setOps] = useState({ relief: null, alerts: null, error: null })

  /**
   * loadReplay() — 12 din ek saath.
   *
   * Pehle day 0 mangwaate hain sirf `replay_days` ki list lene ke liye (kitne din hain ye
   * backend batata hai, hum hardcode nahi karte — seed data badla to bhi sahi chalega),
   * phir baaki din parallel mein.
   */
  const loadReplay = useCallback(async (signal) => {
    setReplay((r) => ({ ...r, loading: true, error: null }))
    try {
      const first = await getVillages('replay', 0, signal)
      const days = first.replay_days || []

      // Baaki din parallel. Promise.all isliye ki 12 sequential call ~12x slow hote.
      const rest = await Promise.all(
        days.slice(1).map((_, i) => getVillages('replay', i + 1, signal)),
      )

      setReplay({ days, snapshots: [first, ...rest], loading: false, error: null })
    } catch (err) {
      if (err.name === 'AbortError') return
      setReplay({ days: [], snapshots: [], loading: false, error: err.message })
    }
  }, [])

  /** loadLive() — Open-Meteo wala abhi ka snapshot. */
  const loadLive = useCallback(async (signal) => {
    setLive((l) => ({ ...l, loading: true, error: null }))
    try {
      const snapshot = await getVillages('live', null, signal)
      setLive({ snapshot, loading: false, error: null })
    } catch (err) {
      if (err.name === 'AbortError') return
      setLive({ snapshot: null, loading: false, error: err.message })
    }
  }, [])

  /**
   * loadOps() — relief requests + alerts.
   * Promise.all se dono ek saath. Ek fail ho to dono ko fail maanna galat hoga, par
   * dono chhoti queries hain aur ek hi backend se aati hain — practically saath hi failenge.
   */
  const loadOps = useCallback(async (signal) => {
    try {
      const [relief, alerts] = await Promise.all([getRelief(signal), getAlerts(signal)])
      setOps({ relief, alerts, error: null })
    } catch (err) {
      if (err.name === 'AbortError') return
      // Ops fail hone pe poora dashboard mat giraao — map/risk phir bhi kaam ka hai.
      setOps((o) => ({ ...o, error: err.message }))
    }
  }, [])

  // --- Mount: replay + ops ek baar ------------------------------------------------
  useEffect(() => {
    const ac = new AbortController()
    loadReplay(ac.signal)
    loadOps(ac.signal)
    return () => ac.abort()
  }, [loadReplay, loadOps])

  // --- Live snapshot: sirf tab load karo jab user live mode mein ho -----------------
  // KYUN lazy: live mode Open-Meteo ko chhoo sakta hai (agar backend cache thanda ho).
  // Jab tak officer live nahi dekhna chahta, wo call karne ka koi matlab nahi.
  useEffect(() => {
    if (mode !== 'live') return
    const ac = new AbortController()
    loadLive(ac.signal)

    const t = setInterval(() => loadLive(ac.signal), POLL_MS.live)
    return () => {
      ac.abort()
      clearInterval(t)
    }
  }, [mode, loadLive])

  // --- Ops polling: dono mode mein chalta rehta hai ---------------------------------
  // KYUN dono mode mein: SOS aur alerts ASLI hain, replay ke 2022 data se unka koi rishta
  // nahi. Officer replay dekh raha ho tab bhi nayi SOS aa sakti hai — wo miss nahi honi chahiye.
  useEffect(() => {
    const ac = new AbortController()
    const t = setInterval(() => loadOps(ac.signal), POLL_MS.ops)
    return () => {
      ac.abort()
      clearInterval(t)
    }
  }, [loadOps])

  /**
   * refreshOps() — turant relief/alerts dobara laao.
   * KYUN chahiye: alert bhejne ke baad 30 second wait karna bura lagta hai. Alert bhejte hi
   * ye call hota hai to feed aur KPI turant update dikhte hain.
   */
  const refreshOps = useCallback(() => loadOps(), [loadOps])

  /** retry() — error box ke "Retry" button ke liye. */
  const retry = useCallback(() => {
    loadReplay()
    loadOps()
    if (mode === 'live') loadLive()
  }, [loadReplay, loadOps, loadLive, mode])

  return { replay, live, ops, refreshOps, retry }
}

/**
 * useClock() — topbar ka HH:MM:SS clock.
 * KYUN alag hook: har second state badalta hai. Agar ye App mein hota to poora dashboard
 * (map, charts, sab) har second re-render hota. Alag component + alag hook se sirf clock
 * ka chhota sa <div> update hota hai.
 */
export function useClock() {
  const [time, setTime] = useState(() => new Date().toLocaleTimeString('en-IN', { hour12: false }))
  const ref = useRef()

  useEffect(() => {
    ref.current = setInterval(
      () => setTime(new Date().toLocaleTimeString('en-IN', { hour12: false })),
      1000,
    )
    return () => clearInterval(ref.current)
  }, [])

  return time
}
