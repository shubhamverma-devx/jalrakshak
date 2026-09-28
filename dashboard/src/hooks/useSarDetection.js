/**
 * useSarDetection — Satellite tab ka poora state.
 *
 * KYUN alag hook (useDashboardData mein nahi daala): risk map ka data har 30 sec/5 min
 * apne aap refresh hota hai. SAR detection ULTA hai — wo tabhi chalti hai jab officer
 * button dabaye, aur uska nateeja badalta nahi (same chip = same output). Dono ko ek
 * hook mein mila dena dono ko complicated kar deta.
 *
 * ============ DATA KAHAN SE (deploy hardening) ============
 * Scenes aur nateeje BUILD ke andar baked JSON se aate hain (`public/fallback/sar/`), API
 * se NAHI. KYUN: model PyTorch ka hai aur server (Railway free tier, 0.5 GB RAM) par PyTorch load hi nahi ho
 * sakta. Isliye `ml/predict.py` laptop par chaaron demo scenes pe chalta hai
 * (`php artisan dashboard:export-fallback`) aur uska ASLI output yahan padha jaata hai.
 * Nateeja wahi hai jo live chalane par aata — detection deterministic hai (same chip +
 * same threshold = same mask). SatellitePanel ye baat screen pe likhta hai.
 */

import { useCallback, useEffect, useState } from 'react'
import { getFallback } from '../api/client'

export function useSarDetection(enabled) {
  const [scenes, setScenes] = useState([])
  const [model, setModel] = useState(null)
  const [selected, setSelected] = useState(null)
  const [precomputedAt, setPrecomputedAt] = useState(null)

  const [result, setResult] = useState(null)
  const [running, setRunning] = useState(false)
  const [error, setError] = useState(null)

  /**
   * Scene list sirf tab laao jab user Satellite tab pe aaye.
   * KYUN lazy: baaki do mode mein iski zaroorat hi nahi — faltu request kyun bhejein.
   */
  useEffect(() => {
    if (!enabled || scenes.length) return

    const ac = new AbortController()
    getFallback('sar/scenes.json', ac.signal)
      .then((d) => {
        setScenes(d.scenes || [])
        setModel(d.model || null)
        setPrecomputedAt(d.generated_at || null)
        // Pehla available scene apne aap select — officer ko ek extra click na karna pade
        const first = (d.scenes || []).find((s) => s.available)
        if (first) setSelected(first.id)
      })
      .catch((e) => {
        if (e.name !== 'AbortError') setError(e.message)
      })

    return () => ac.abort()
  }, [enabled, scenes.length])

  /**
   * detect() — us scene ka predict.py output dikhao.
   * KYUN result pehle clear karte hain: purana polygon map pe pada rehta to lagta ki
   * naya scene process ho gaya, jabki wo pichhle scene ka nateeja hota. Flood dashboard
   * mein wo confusion khatarnak hai.
   */
  const detect = useCallback(async () => {
    if (!selected || running) return

    setRunning(true)
    setError(null)
    setResult(null)

    try {
      setResult(await getFallback(`sar/${selected}.json`))
    } catch (e) {
      setError(e.message)
    } finally {
      setRunning(false)
    }
  }, [selected, running])

  /** Scene badalne pe purana nateeja hatao (upar wali hi wajah). */
  const selectScene = useCallback((id) => {
    setSelected(id)
    setResult(null)
    setError(null)
  }, [])

  return { scenes, model, selected, selectScene, result, running, error, detect, precomputedAt }
}
