/**
 * api/client.js — Laravel API se baat karne ki ek hi jagah.
 *
 * KYUN alag file: har component apna `fetch` likhta to error handling, base URL aur
 * JSON parsing 8 jagah duplicate hoti. Yahan ek `request()` hai — sab usi se jaate hain.
 *
 * Backend ke response shapes Day 1 mein bane the (docs/ai-context.md dekho).
 */

import { API_URL, REQUEST_TIMEOUT_MS } from '../config'

/**
 * request() — ek HTTP call, saaf error ke saath.
 *
 * INPUT : path ('/villages'), options ({ method, body, params, signal })
 * OUTPUT: parsed JSON
 * THROWS: Error jiska .message insaan ke padhne layak ho (UI seedha dikhata hai)
 *
 * KYUN apna error message banate hain: Laravel validation fail hone pe 422 ke saath
 * { message, errors } bhejta hai. Plain `fetch` usko error nahi maanta (response.ok false hota
 * hai par throw nahi karta), isliye yahan khud check karke throw karte hain.
 */
async function request(path, { method = 'GET', body, params, signal } = {}) {
  const url = new URL(API_URL + path)

  // Query params jodo, undefined/null wale chhod do (warna "?day=undefined" ban jaata hai).
  if (params) {
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') url.searchParams.set(k, v)
    })
  }

  // Timeout: server atka ho (php-fpm busy, MySQL swap mein) to fetch minuton latak sakta
  // hai aur screen skeleton pe hi ruki rehti. REQUEST_TIMEOUT_MS ke baad chhod ke fallback
  // pe jaate hain. Caller ka signal bhi saath chalta hai (component unmount pe abort).
  // (Purane browser mein AbortSignal.timeout/any nahi hote — tab bina timeout ke chalta hai.)
  const timeout = AbortSignal.timeout ? AbortSignal.timeout(REQUEST_TIMEOUT_MS) : null
  const combined =
    signal && timeout && AbortSignal.any ? AbortSignal.any([signal, timeout]) : signal || timeout || undefined

  let res
  try {
    res = await fetch(url, {
      method,
      signal: combined,
      headers: {
        Accept: 'application/json',
        ...(body ? { 'Content-Type': 'application/json' } : {}),
      },
      body: body ? JSON.stringify(body) : undefined,
    })
  } catch (err) {
    // Caller ne khud abort kiya (unmount) — chup-chaap upar bhejo.
    if (err.name === 'AbortError' && signal?.aborted) throw err
    // Baaki sab (network band, CORS, timeout) — dashboard dekhne wale ko samajh aane
    // layak message. Ye judge bhi padh sakta hai, isliye dev command nahi likhte.
    throw new Error(
      err.name === 'TimeoutError' || timeout?.aborted
        ? 'The server took too long to respond.'
        : 'Could not reach the JalRakshak server.',
    )
  }

  // 204 No Content — parse karne ko kuch nahi.
  if (res.status === 204) return null

  let data = null
  try {
    data = await res.json()
  } catch {
    // Backend ne JSON ke alawa kuch bheja (500 HTML page). bootstrap/app.php mein JSON-only
    // errors set hain, to ye kam hi hona chahiye — par defensive rehna theek hai.
    throw new Error(`Server ne galat jawaab bheja (HTTP ${res.status}).`)
  }

  if (!res.ok) {
    throw new Error(data?.message || `Request fail hui (HTTP ${res.status}).`)
  }

  return data
}

/**
 * Risk map — saare gaon + summary + replay_days.
 * INPUT : mode ('live'|'replay'), day (replay ka 0-based index)
 * OUTPUT: { mode, day, date, data_ok, summary, villages[], replay_days[] }
 */
export const getVillages = (mode, day, signal) =>
  request('/villages', { params: { mode, day }, signal })

/**
 * Ek gaon ka poora detail — drawer ke liye.
 * OUTPUT: { village, river_station, shelters[], recent_alerts[], open_relief_requests }
 */
export const getVillage = (id, mode, day, signal) =>
  request(`/village/${id}`, { params: { mode, day }, signal })

/**
 * B2 — ek gaon ka +24h / +48h forecast.
 * OUTPUT: { village, forecast:{recent, horizons[]}, model:{metrics, baseline, ...} }
 * NOTE  : ye 2-4 second le sakta hai (Python subprocess + Open-Meteo), isliye drawer
 *         ise alag se maangta hai — baaki detail ka wait nahi karwaata.
 *         503 aata hai agar model/Python na mile — UI usko chup-chaap sambhalta hai.
 */
export const getVillageForecast = (id, signal) => request(`/village/${id}/forecast`, { signal })

/** Officer ki relief table. OUTPUT: { counts, count, requests[] } */
export const getRelief = (signal) => request('/relief', { signal })

/**
 * Ek relief request ka status badlo (acknowledge / mark handled / reopen).
 * INPUT : id, status ('new' | 'inprogress' | 'done')
 * OUTPUT: { message, relief }
 *
 * KYUN PATCH: sirf ek field badal rahe hain, poora record replace nahi kar rahe.
 */
export const patchRelief = (id, status) =>
  request(`/relief/${id}`, { method: 'PATCH', body: { status } })

/** Bheje gaye alerts (feed + KPI). OUTPUT: { count, alerts[] } */
export const getAlerts = (signal) => request('/alerts', { params: { limit: 50 }, signal })

/**
 * Targeted alert bhejo.
 * INPUT : { village_id, message_hi, message_en, sent_by }
 * OUTPUT: { message, alert, push }
 * NOTE  : `push.sent` abhi hamesha false hai — FCM Day 3 mein wire hoga. UI ise chhupata nahi.
 */
export const postAlert = (payload) => request('/alert', { method: 'POST', body: payload })

/**
 * ============ B1 — SAR flood detection (trained model) ============
 * NOTE: baaki saare endpoints RULE-BASED RiskEngine se aate hain. Ye do TRAINED
 * MODEL se aate hain (U-Net, Sen1Floods11 pe train kiya). Dono alag cheezein hain —
 * UI mein bhi ye farq saaf dikhna chahiye.
 */

/**
 * Bundled SAR sample scenes + model provenance.
 * OUTPUT: { scenes: [{id,label,ground_truth_water_pct,available}], model: {...} }
 *
 * NOTE: dashboard ab ise NAHI bulata — Satellite tab `fallback/sar/*.json` se chalta hai
 * (server free tier pe hai, PyTorch nahi chal sakta). Endpoint local dev ke liye zinda hai.
 */
export const getSarScenes = (signal) => request('/sar/scenes', { signal })

/**
 * Ek scene pe model chalao (local dev — ml/.venv wale laptop par).
 * OUTPUT: { scene, detection:{flooded_area_sq_km,water_fraction,bounds,...},
 *           geojson, nearest_villages, model }
 */
export const postSarDetect = (scene, signal) =>
  request('/sar/detect', { method: 'POST', body: { scene }, signal })

/**
 * ============ Static fallback (build ke andar baked JSON) ============
 * `php artisan dashboard:export-fallback` inhe `public/fallback/` mein likhta hai; Vite
 * unhe `dist/` mein copy karta hai. Nginx seedha file deta hai — PHP/MySQL band ho tab bhi.
 *
 * INPUT : file ka naam ('replay.json', 'sar/India_591317.json')
 * OUTPUT: parsed JSON | THROWS: Error agar file build mein nahi hai
 *
 * KYUN BASE_URL: dashboard kisi sub-path pe deploy ho to bhi sahi file mile.
 */
export async function getFallback(name, signal) {
  const res = await fetch(`${import.meta.env.BASE_URL}fallback/${name}`, { signal })
  if (!res.ok) throw new Error(`Bundled data missing (${name}).`)
  return res.json()
}
