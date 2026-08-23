/**
 * ActivityPanel — "abhi kya ho raha hai" wala panel + uska "View all".
 *
 * KYUN YE WRAPPER BANA: `ActivityFeed` sirf list render karta hai. Panel ka footer
 * ("kitne item neeche kat rahe hain") aur "View all" modal ko feed ki GINTI chahiye —
 * wo ginti App tak le jaane se App ko feed ki internal baat pata karni padti. Isliye
 * panel, footer, modal aur feed — sab ek jagah, bilkul ReliefPanel ki tarah.
 *
 * ============ "VIEW ALL" KYUN CHAHIYE ============
 * Panel mein ~4 item dikhte hain, feed 25 tak rakhti hai, aur DB mein isse zyada pade
 * hote hain. Officer ko 10-15 minute purani ghatna dekhni ho to chhote panel mein scroll
 * karna taklif hai. Modal poori list deti hai — bina 25 wali cap ke, poori chaudai mein.
 *
 * IMANDAARI: modal ke footer mein saaf likha hai ki data kahan tak ka hai — alerts API
 * se sirf newest 50 aate hain (client.js -> getAlerts limit 50). "All activity" likh ke
 * chup ho jaana galat hota, kyunki wo 51-waan alert dikhata hi nahi.
 */

import { useMemo, useRef, useState } from 'react'
import { IconActivity, IconListDetails, IconX } from '@tabler/icons-react'

import Panel from './Panel'
import ActivityFeed, { buildFeed, FeedItem } from './ActivityFeed'
import { useOverflowCount } from '../hooks/useOverflowCount'
import { useEscapeKey } from '../hooks/useEscapeKey'

export default function ActivityPanel({ relief, alerts, snapshot, mode, loading, style }) {
  const [showAll, setShowAll] = useState(false)
  const bodyRef = useRef(null)
  const modalRef = useRef(null)

  useEscapeKey(showAll, () => setShowAll(false))

  /** Kitne item panel ki tali ke neeche chhup gaye — footer mein sach likhne ke liye. */
  const { total, below } = useOverflowCount(bodyRef, '.feed-item', [
    relief,
    alerts,
    snapshot,
    mode,
    loading,
  ])

  /**
   * Poori feed — sirf modal ke liye, aur sirf tab banti hai jab modal khula ho.
   * KYUN lazy: panel har 30 second pe naya data leta hai. Har refresh pe 500 items ki
   * list banana bekaar hai jab modal band pada ho.
   */
  /** Modal bhi utna hi imandaar — neeche content bacha ho to wahan bhi fade. */
  const modal = useOverflowCount(modalRef, '.feed-item', [showAll, relief, alerts])

  const fullFeed = useMemo(
    () => (showAll ? buildFeed(relief, alerts, snapshot, mode, Infinity) : []),
    [showAll, relief, alerts, snapshot, mode],
  )

  return (
    <>
      <Panel
        title="Activity feed"
        subtitle="Everything that happened, newest first"
        Icon={IconActivity}
        style={style}
        bodyStyle={{ padding: 0 }}
        bodyClass={below > 0 ? 'faded' : undefined}
        bodyRef={bodyRef}
        /* Relief panel jaisa hi imandaar footer — kata hua content ginti ke saath. */
        footer={
          total > 0 ? (
            <div className="panel-foot">
              <span>
                {below > 0
                  ? `${below} more below — scroll`
                  : `${total} event${total === 1 ? '' : 's'}`}
              </span>
              <button className="linkbtn" onClick={() => setShowAll(true)}>
                View all →
              </button>
            </div>
          ) : null
        }
      >
        <ActivityFeed
          relief={relief}
          alerts={alerts}
          snapshot={snapshot}
          mode={mode}
          loading={loading}
        />
      </Panel>

      {showAll && (
        <div
          className="modal-ov"
          onMouseDown={(e) => e.target === e.currentTarget && setShowAll(false)}
        >
          <div className="modal wide" role="dialog" aria-modal="true" aria-label="All activity">
            <div className="modal-head">
              <div className="mt">
                <IconListDetails className="ti" />
                All activity
              </div>
              <div className="ms">
                {fullFeed.length} event{fullFeed.length === 1 ? '' : 's'} — SOS from the citizen
                app, alerts your team sent, and observations from the current risk snapshot.
                Newest first.
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
              {fullFeed.map((f) => (
                <FeedItem key={f.key} f={f} />
              ))}
            </div>

            {/* Ye line hatana mat — "All activity" ka matlab yahan tak hi hai. */}
            <div className="modal-foot">
              <div className="note">
                Alerts are the newest 50 from the API; relief requests are all open ones.
              </div>
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
