/**
 * ReliefPanel — citizen app se aayi SOS requests, jinpe officer KAAM kar sakta hai.
 *
 * DATA: GET /api/relief -> { counts, requests[] }
 *       PATCH /api/relief/{id} { status } -> acknowledge / mark handled / reopen
 *
 * ============ YE PANEL DEMO MEIN KYUN SABSE ZAROORI HAI ============
 * Ye "two-way" ka saboot hai. Govt ka SMS ek-taraffa hai — citizen wapas kuch nahi bol
 * sakta. Emulator se SOS bhejte hi wo yahan dikhta hai (30 sec ke andar, POLL_MS.ops).
 *
 * ============ PURANE VERSION SE KYA BADLA, AUR KYUN ============
 * 1. Message ab 2 line tak dikhta hai (pehle ek line mein `...` se kat jaata tha).
 *    SOS ka poora matlab ussi line mein hota hai — "chhat par 5 log fase hain, paani gale
 *    tak". Usko kaat dena is panel ka poora point maar deta hai. Poora text "View all"
 *    mein bina kate milta hai.
 * 2. Status filter chips — 20 request mein se "kaunsi abhi tak nayi hai" ek click mein.
 * 3. ACTION BUTTONS. Pehle list sirf dikhti thi; officer usme kuch kar nahi sakta tha.
 *    Do boat aur bees request ho to use yaad rakhna padta ki kis pe team bhej di. Ab wo
 *    Acknowledge -> Mark handled kar sakta hai, aur status DB mein save hota hai.
 * 4. "View all" footer — panel chhota hai, list lambi ho sakti hai. Chup-chaap kaat dene
 *    ke bajaye saaf likha hai ki kitni hain aur poori list kahan milegi.
 *
 * NOTE: bhejne wale ka NAAM nahi dikhate — backend mein wo column hai hi nahi
 * (BUILD_PLAN section 7: village_id, lat, lng, message, status) aur auth ke bina naam
 * bharosemand bhi nahi hota. GAON dikhate hain — officer ko boat kahan bhejni hai, wahi
 * kaam ka sach hai.
 */

import { useMemo, useRef, useState } from 'react'
import {
  IconArrowBackUp,
  IconCheck,
  IconLifebuoy,
  IconListDetails,
  IconLoader2,
  IconProgressCheck,
  IconUrgent,
  IconX,
} from '@tabler/icons-react'

import Panel, { Empty } from './Panel'
import { timeAgo } from '../utils/format'
import { useOverflowCount } from '../hooks/useOverflowCount'
import { useEscapeKey } from '../hooks/useEscapeKey'

const STATUS_LABEL = { new: 'New', inprogress: 'In progress', done: 'Handled' }

/** Filter chips. `key` null = sab. */
const FILTERS = [
  { key: null, label: 'All' },
  { key: 'new', label: 'New' },
  { key: 'inprogress', label: 'In progress' },
  { key: 'done', label: 'Handled' },
]

/**
 * ReliefCard — ek request.
 * INPUT : r (request), onStatus(id,status), busy (is id pe abhi call chal rahi hai?), full (message pura dikhao)
 */
function ReliefCard({ r, onStatus, busy, full }) {
  return (
    <div className={`relief-card ${r.status}${full ? ' full' : ''}`}>
      <span className="rc">
        {r.status === 'done' ? <IconCheck className="ti" /> : <IconUrgent className="ti" />}
      </span>

      <div className="bd">
        <div className="who">
          {r.village?.name || 'Unknown village'}
          <span className="dist">{r.village?.district}</span>
        </div>

        {/* title= mein poora text — hover pe clipped message bhi padha ja sakta hai. */}
        <div className="msg" title={r.message}>
          {r.message}
        </div>

        {/* Ek waqt pe ek hi aage badhne ka button — officer ko sochna na pade ki kya dabaye.
            'done' pe reopen rehta hai kyunki galti se mark ho jaana aam baat hai. */}
        <div className="relief-acts">
          {r.status === 'new' && (
            <button className="rbtn go" onClick={() => onStatus(r.id, 'inprogress')} disabled={busy}>
              {busy ? <IconLoader2 className="ti" style={{ animation: 'spin 1s linear infinite' }} /> : <IconProgressCheck className="ti" />}
              Acknowledge
            </button>
          )}
          {r.status === 'inprogress' && (
            <button className="rbtn ok" onClick={() => onStatus(r.id, 'done')} disabled={busy}>
              {busy ? <IconLoader2 className="ti" style={{ animation: 'spin 1s linear infinite' }} /> : <IconCheck className="ti" />}
              Mark handled
            </button>
          )}
          {r.status === 'done' && (
            <button className="rbtn" onClick={() => onStatus(r.id, 'new')} disabled={busy}>
              <IconArrowBackUp className="ti" />
              Reopen
            </button>
          )}
          {/* Time button ke saath ek hi line mein — alag line dene se har card 18px lamba
              ho jaata tha aur panel mein ek card kam dikhta. */}
          <span className="tm">{timeAgo(r.created_at)}</span>
        </div>
      </div>

      <span className="st">{STATUS_LABEL[r.status] || r.status}</span>
    </div>
  )
}

export default function ReliefPanel({ relief, loading, onStatus, style }) {
  const [filter, setFilter] = useState(null)
  const [busyId, setBusyId] = useState(null)
  const [showAll, setShowAll] = useState(false)
  const bodyRef = useRef(null)
  const modalRef = useRef(null)

  const counts = relief?.counts || { new: 0, inprogress: 0, done: 0 }
  const all = relief?.requests || []
  const total = all.length

  const shown = useMemo(
    () => (filter ? all.filter((r) => r.status === filter) : all),
    [all, filter],
  )

  /**
   * Status badalna. Busy state per-id hai (poori list nahi) — do officer alag-alag
   * request pe ek saath kaam kar sakte hain, aur ek button dabane se baaki freeze na ho.
   */
  const handleStatus = async (id, status) => {
    setBusyId(id)
    try {
      await onStatus(id, status)
    } finally {
      setBusyId(null)
    }
  }

  const chipCount = (key) => (key === null ? total : counts[key] ?? 0)

  useEscapeKey(showAll, () => setShowAll(false))

  /** Kitne card panel ki tali ke neeche chhup gaye — footer mein sach likhne ke liye. */
  const { below } = useOverflowCount(bodyRef, '.relief-card', [shown.length, loading])

  /** Modal bhi utna hi imandaar — neeche content bacha ho to wahan bhi fade. */
  const modal = useOverflowCount(modalRef, '.relief-card', [showAll, total])

  const body = loading ? (
    <div style={{ padding: '4px 0' }}>
      {Array.from({ length: 3 }).map((_, i) => (
        <div key={i} style={{ display: 'flex', gap: 9, padding: '11px 12px' }}>
          <div className="sk" style={{ width: 26, height: 26, borderRadius: 6, flexShrink: 0 }} />
          <div style={{ flex: 1 }}>
            <div className="sk" style={{ height: 10, width: '55%' }} />
            <div className="sk" style={{ height: 9, width: '85%', marginTop: 5 }} />
            <div className="sk" style={{ height: 9, width: '40%', marginTop: 5 }} />
          </div>
        </div>
      ))}
    </div>
  ) : shown.length === 0 ? (
    <Empty Icon={IconLifebuoy}>
      {total === 0
        ? 'No relief requests yet. SOS sent from the citizen app appears here within 30 seconds.'
        : `No ${STATUS_LABEL[filter]?.toLowerCase()} requests.`}
    </Empty>
  ) : (
    shown.map((r) => (
      <ReliefCard key={r.id} r={r} onStatus={handleStatus} busy={busyId === r.id} />
    ))
  )

  return (
    <>
      <Panel
        title="Relief requests"
        subtitle="SOS sent by citizens from the app — act on each one"
        Icon={IconLifebuoy}
        style={style}
        bodyStyle={{ padding: 0 }}
        bodyClass={below > 0 ? 'faded' : undefined}
        right={
          counts.new > 0 ? (
            <span
              className="rpill red"
              style={{ fontSize: 10, padding: '3px 8px' }}
              title={`${counts.new} request${counts.new > 1 ? 's' : ''} not yet acknowledged`}
            >
              {counts.new} new
            </span>
          ) : total > 0 ? (
            <span className="rpill green" style={{ fontSize: 10, padding: '3px 8px' }}>
              All seen
            </span>
          ) : null
        }
        /* Chips header ke neeche fix rehte hain, body ke saath scroll nahi hote —
           warna scroll karte hi filter gayab ho jaata. */
        beforeBody={
          total > 0 ? (
            <div className="chips">
              {FILTERS.map((f) => (
                <button
                  key={f.label}
                  className={`chip${filter === f.key ? ' on' : ''}`}
                  onClick={() => setFilter(f.key)}
                >
                  {f.label} <span className="n">{chipCount(f.key)}</span>
                </button>
              ))}
            </div>
          ) : null
        }
        footer={
          total > 0 ? (
            <div className="panel-foot">
              {/* Kata hua content chup-chaap kaatne ke bajaye ginti ke saath likha hai. */}
              <span>
                {below > 0
                  ? `${below} more below — scroll`
                  : filter
                    ? `${shown.length} ${STATUS_LABEL[filter].toLowerCase()}`
                    : `${total} request${total === 1 ? '' : 's'}`}
              </span>
              <button className="linkbtn" onClick={() => setShowAll(true)}>
                View all {total} →
              </button>
            </div>
          ) : null
        }
        bodyRef={bodyRef}
      >
        {body}
      </Panel>

      {/* ---- "View all" — poori list, message bina kate ---- */}
      {showAll && (
        <div className="modal-ov" onMouseDown={(e) => e.target === e.currentTarget && setShowAll(false)}>
          <div className="modal wide" role="dialog" aria-modal="true" aria-label="All relief requests">
            <div className="modal-head">
              <div className="mt">
                <IconListDetails className="ti" />
                All relief requests
              </div>
              <div className="ms">
                {total} request{total === 1 ? '' : 's'} from the citizen app · {counts.new} new,{' '}
                {counts.inprogress} in progress, {counts.done} handled. Full message text, nothing
                truncated.
              </div>
              <button className="dclose" onClick={() => setShowAll(false)} aria-label="Close">
                <IconX className="ti" />
              </button>
            </div>

            <div
              ref={modalRef}
              className={`modal-body${modal.below > 0 ? ' faded' : ''}`}
              style={{ padding: 0 }}
            >
              {all.map((r) => (
                <ReliefCard key={r.id} r={r} onStatus={handleStatus} busy={busyId === r.id} full />
              ))}
            </div>

            <div className="modal-foot">
              <div className="note">Status changes save immediately.</div>
              <button className="btn-ghost" onClick={() => setShowAll(false)}>
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
