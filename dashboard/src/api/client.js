/**
 * api/client.js — Laravel API se baat karne ki ek hi jagah.
 *
 * KYUN alag file: har component apna `fetch` likhta to error handling, base URL aur
 * JSON parsing 8 jagah duplicate hoti. Yahan ek `request()` hai — sab usi se jaate hain.
 *
 * Backend ke response shapes Day 1 mein bane the (docs/ai-context.md dekho).
 */

import { API_URL } from '../config'

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

  let res
  try {
    res = await fetch(url, {
      method,
      signal,
      headers: {
        Accept: 'application/json',
        ...(body ? { 'Content-Type': 'application/json' } : {}),
      },
      body: body ? JSON.stringify(body) : undefined,
    })
  } catch (err) {
    // Network hi nahi laga (backend band hai / CORS block). Ye sabse common dev galti hai,
    // isliye message mein seedha wahi likha hai jo karna chahiye.
    if (err.name === 'AbortError') throw err
    throw new Error('API tak nahi pahunch paye. Kya backend chal raha hai? (php artisan serve)')
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

/** Officer ki relief table. OUTPUT: { counts, count, requests[] } */
export const getRelief = (signal) => request('/relief', { signal })

/** Bheje gaye alerts (feed + KPI). OUTPUT: { count, alerts[] } */
export const getAlerts = (signal) => request('/alerts', { params: { limit: 50 }, signal })

/**
 * Targeted alert bhejo.
 * INPUT : { village_id, message_hi, message_en, sent_by }
 * OUTPUT: { message, alert, push }
 * NOTE  : `push.sent` abhi hamesha false hai — FCM Day 3 mein wire hoga. UI ise chhupata nahi.
 */
export const postAlert = (payload) => request('/alert', { method: 'POST', body: payload })
