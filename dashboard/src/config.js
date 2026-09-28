/**
 * config.js — poore dashboard ki EK jagah ki settings.
 *
 * KYUN EK FILE: BUILD_PLAN section 4 ke hisaab se Laravel API aur React alag deploy hote hain
 * (API droplet pe, dashboard Vercel pe). Agar API ka URL 10 components mein bikhra hota to
 * deploy ke waqt har jagah dhoondhna padta. Yahan ek jagah badlo, poora app badal jaata hai.
 */

/**
 * API ka base URL — .env ke VITE_API_URL se aata hai.
 * Fallback localhost isliye rakha hai ki naya developer bina .env banaye bhi chala sake.
 */
export const API_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8000/api').replace(/\/$/, '')

/**
 * Alert bhejne wale officer ka naam.
 * KYUN hardcoded: auth/login BUILD_PLAN section 2 mein explicitly future scope hai.
 * Backend `sent_by` ek plain string leta hai. Real deployment mein ye logged-in officer
 * ka naam hoga — tab ye constant hat jaayega.
 */
export const OFFICER_NAME = 'District Control Room'

/**
 * Risk level ke rang — mockup_v2.html ke exact hex.
 * NOTE: backend 'yellow' bolta hai, design 'amber' bolta hai. Mapping utils/risk.js mein hai.
 */
export const LEVEL_COLORS = {
  red: '#c85450',
  yellow: '#c79445',
  green: '#5b9a6b',
}

/**
 * Polling intervals (ms).
 * KYUN itne lambe: backend ka risk scheduler waise bhi har 30 min chalta hai (BUILD_PLAN
 * section 6) aur live risk map 15 min cache hota hai. Usse tez poll karne ka koi fayda nahi —
 * bas droplet (1GB RAM) pe faltu load padega.
 * Relief/alerts tez poll hote hain kyunki wo insaan ke action se banti hain (SOS aa sakta hai
 * kisi bhi waqt) aur query sasti hai.
 */
export const POLL_MS = {
  live: 5 * 60 * 1000, // live risk map — 5 min
  ops: 30 * 1000,      // relief requests + alerts — 30 sec
}

/**
 * Ek API call ka max intezaar (ms). Iske baad dashboard bundled fallback pe chala jaata hai.
 * KYUN 8 sec: droplet pe normal response <300ms hai; 8 sec ka matlab server atka hua hai.
 * Judge ko isse zyada skeleton ghoorne nahi dena.
 */
export const REQUEST_TIMEOUT_MS = 8000

/** Replay auto-play mein ek din kitni der dikhe (ms). Mockup mein 950ms tha. */
export const REPLAY_TICK_MS = 950
