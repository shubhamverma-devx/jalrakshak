/**
 * ReliefList — citizen app se aayi SOS requests.
 * mockup_v2.html ka `.relief-row`.
 *
 * DATA: GET /api/relief -> { counts, requests[] }
 *
 * KYUN YE PANEL DEMO MEIN ZAROORI HAI: ye "two-way" ka saboot hai. Govt ka SMS ek-taraffa
 * hai — citizen wapas kuch nahi bol sakta. Demo mein emulator se SOS bhejte hi wo yahan
 * dikhta hai (30 second ke andar, POLL_MS.ops). Judge ke saamne yahi sabse strong moment hai.
 *
 * NOTE: mockup mein bhejne wale ka naam tha (Ramen Das). Backend mein naam ka column hai
 * hi nahi (BUILD_PLAN section 7 ka schema: village_id, lat, lng, message, status) —
 * aur auth na hone se naam bharosemand bhi nahi hota. Isliye yahan GAON ka naam dikhate
 * hain, jo asli aur kaam ka hai: officer ko boat kahan bhejni hai, yehi matter karta hai.
 */

import { IconLifebuoy, IconUrgent } from '@tabler/icons-react'
import { Empty } from './Panel'
import { timeAgo } from '../utils/format'

/** Status ke badge ka text. Backend ke enum se seedha map. */
const STATUS_LABEL = { new: 'New', inprogress: 'Active', done: 'Done' }

export default function ReliefList({ relief, loading }) {
  if (loading) {
    return (
      <div style={{ padding: '4px 0' }}>
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} style={{ display: 'flex', gap: 9, padding: '9px 0', alignItems: 'center' }}>
            <div className="sk" style={{ width: 26, height: 26, borderRadius: 6, flexShrink: 0 }} />
            <div style={{ flex: 1 }}>
              <div className="sk" style={{ height: 10, width: '55%' }} />
              <div className="sk" style={{ height: 9, width: '80%', marginTop: 5 }} />
            </div>
          </div>
        ))}
      </div>
    )
  }

  const requests = relief?.requests || []

  if (!requests.length) {
    return <Empty Icon={IconLifebuoy}>Abhi koi relief request nahi</Empty>
  }

  return (
    <>
      {requests.map((r) => (
        <div key={r.id} className={`relief-row ${r.status}`}>
          <span className="rc">
            <IconUrgent className="ti" />
          </span>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div className="who">
              {r.village?.name || 'Unknown'}{' '}
              <span style={{ fontWeight: 400, color: 'var(--ink2)' }}>
                · {r.village?.district}
              </span>
            </div>
            {/* Message ek line mein — lambi SOS list mein har row ka height same rehna chahiye,
                warna officer ko scroll karke dhoondhna padta hai. */}
            <div
              className="msg"
              style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
              title={r.message}
            >
              {r.message}
            </div>
            <div style={{ fontSize: 10, color: 'var(--ink3)', marginTop: 2 }}>
              {timeAgo(r.created_at)}
            </div>
          </div>
          <span className="st">{STATUS_LABEL[r.status] || r.status}</span>
        </div>
      ))}
    </>
  )
}
