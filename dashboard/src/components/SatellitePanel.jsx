/**
 * SatellitePanel — B1 (trained SAR model) ka control panel.
 *
 * DATA: public/fallback/sar/*.json  <-  ml/predict.py (laptop pe chala, build mein baked)
 *       Demo server (Railway free, 0.5 GB) par PyTorch nahi chalta — isliye "pre-computed" line hamesha dikhti hai.
 *
 * ============ IS PANEL KA SABSE ZAROORI HISSA: PROVENANCE ============
 *  Dashboard ka baaki sab kuch RULE-BASED hai (RiskEngine — IMD thresholds pe if-else).
 *  Sirf ye ek cheez TRAINED MODEL hai. Judge ko ye farq turant dikhna chahiye, warna
 *  ya to wo poore product ko "AI" samajh lenge (jo jhooth hai), ya is asli model ko
 *  bhi rule-based samajh lenge (jo mehnat ko chhupata hai).
 *
 *  Isliye panel mein hamesha dikhta hai: dataset, kitne chips, IoU (JIS THRESHOLD PE
 *  CHAL RAHA HAI ussi ka), aur "detection, not forecast" wali line.
 * ====================================================================
 */

import {
  IconAlertTriangle,
  IconCircleCheck,
  IconInfoCircle,
  IconLoader2,
  IconPlayerPlay,
  IconSatellite,
} from '@tabler/icons-react'
import { num } from '../utils/format'

/** Ek metric tile — bade number + chhota label. */
function Stat({ label, value, sub, tone }) {
  return (
    <div
      style={{
        background: 'var(--panel2)',
        border: '1px solid var(--line)',
        borderRadius: 8,
        padding: '9px 11px',
        flex: 1,
        minWidth: 0,
      }}
    >
      <div style={{ fontSize: 10.5, color: 'var(--ink2)', letterSpacing: 0.2 }}>{label}</div>
      <div
        className="mono"
        style={{
          fontSize: 19,
          fontWeight: 600,
          letterSpacing: -0.5,
          marginTop: 3,
          color: tone || 'var(--ink)',
        }}
      >
        {value}
      </div>
      {sub && <div style={{ fontSize: 10, color: 'var(--ink3)', marginTop: 2 }}>{sub}</div>}
    </div>
  )
}

export default function SatellitePanel({
  scenes,
  model,
  selected,
  onSelectScene,
  onDetect,
  running,
  result,
  error,
  precomputedAt,
}) {
  const d = result?.detection
  const m = model?.metrics_at_threshold

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {/* ---------- scene picker ---------- */}
      <div>
        <div style={{ fontSize: 10.5, color: 'var(--ink2)', marginBottom: 7, letterSpacing: 0.3 }}>
          SENTINEL-1 SCENE
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
          {scenes.map((s) => {
            const active = s.id === selected
            return (
              <button
                key={s.id}
                onClick={() => onSelectScene(s.id)}
                disabled={!s.available || running}
                style={{
                  textAlign: 'left',
                  background: active ? 'var(--accent-bg)' : 'var(--panel2)',
                  border: `1px solid ${active ? 'var(--accent)' : 'var(--line)'}`,
                  borderRadius: 8,
                  padding: '8px 10px',
                  cursor: s.available && !running ? 'pointer' : 'not-allowed',
                  opacity: s.available ? 1 : 0.5,
                  fontFamily: 'inherit',
                  color: 'var(--ink)',
                }}
              >
                <div style={{ fontSize: 12, fontWeight: 500 }}>{s.label}</div>
                <div style={{ fontSize: 10, color: 'var(--ink3)', marginTop: 2 }}>
                  {s.id} · ground truth {s.ground_truth_water_pct}% water
                </div>
              </button>
            )
          })}
        </div>
      </div>

      {/* ---------- run ---------- */}
      <button
        onClick={onDetect}
        disabled={running || !selected}
        style={{
          background: 'var(--accent)',
          color: '#fff',
          border: 0,
          borderRadius: 8,
          padding: '10px 12px',
          fontFamily: 'inherit',
          fontSize: 13,
          fontWeight: 600,
          cursor: running ? 'wait' : 'pointer',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 8,
          opacity: running ? 0.75 : 1,
        }}
      >
        {running ? (
          <>
            <IconLoader2 className="ti" style={{ animation: 'spin 1s linear infinite' }} />
            Loading result…
          </>
        ) : (
          <>
            <IconPlayerPlay className="ti" />
            Show flood detection
          </>
        )}
      </button>

      {error && (
        <div
          style={{
            background: 'var(--red-bg)',
            border: '1px solid var(--red)',
            borderRadius: 8,
            padding: '9px 11px',
            fontSize: 11.5,
            display: 'flex',
            gap: 8,
          }}
        >
          <IconAlertTriangle className="ti" style={{ color: 'var(--red)', flexShrink: 0 }} />
          <span>{error}</span>
        </div>
      )}

      {/* ---------- result ---------- */}
      {d && (
        <>
          <div style={{ display: 'flex', gap: 8 }}>
            <Stat
              label="Flooded area"
              value={d.flooded_area_sq_km}
              sub={`sq km of ${d.scene_area_sq_km} scene`}
              tone="var(--accent-2)"
            />
            <Stat
              label="Water cover"
              value={`${(d.water_fraction * 100).toFixed(1)}%`}
              sub={`confidence ${d.mean_confidence}`}
            />
          </div>

          {/* Model ne kya kaha vs asli label — ye tulna hi imaandaari hai.
              Chhupane ke bajaye saath dikhate hain. */}
          {(() => {
            const gt = scenes.find((s) => s.id === result.scene)?.ground_truth_water_pct
            if (gt == null) return null
            const got = d.water_fraction * 100
            const diff = got - gt
            const close = Math.abs(diff) <= 5
            return (
              <div
                style={{
                  background: 'var(--panel2)',
                  border: '1px solid var(--line)',
                  borderRadius: 8,
                  padding: '9px 11px',
                  fontSize: 11.5,
                  display: 'flex',
                  gap: 8,
                  alignItems: 'flex-start',
                }}
              >
                {close ? (
                  <IconCircleCheck
                    className="ti"
                    style={{ color: 'var(--green)', flexShrink: 0, marginTop: 1 }}
                  />
                ) : (
                  <IconInfoCircle
                    className="ti"
                    style={{ color: 'var(--amber)', flexShrink: 0, marginTop: 1 }}
                  />
                )}
                <span style={{ color: 'var(--ink2)', lineHeight: 1.5 }}>
                  Model said <b style={{ color: 'var(--ink)' }}>{got.toFixed(1)}%</b>, hand label
                  says <b style={{ color: 'var(--ink)' }}>{gt}%</b>{' '}
                  <span style={{ color: 'var(--ink3)' }}>
                    ({diff > 0 ? '+' : ''}
                    {diff.toFixed(1)} pp)
                  </span>
                </span>
              </div>
            )
          })()}

          {/* KYUN "nearest", "affected" nahi: chip sirf ~5x5 km ka hai aur hamare seeded
              gaon 7-33 km door hain — ek bhi gaon ka centroid chip ke andar nahi aata.
              "Affected" bolna seedha jhooth hota. Doori hamesha saath dikhti hai. */}
          {result.nearest_villages?.length > 0 && (
            <div>
              <div
                style={{ fontSize: 10.5, color: 'var(--ink2)', marginBottom: 6, letterSpacing: 0.3 }}
              >
                NEAREST VILLAGES TO SCENE CENTRE
              </div>
              {result.nearest_villages.slice(0, 3).map((v) => (
                <div
                  key={v.id}
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    padding: '5px 0',
                    borderBottom: '1px solid var(--line)',
                    fontSize: 11.5,
                  }}
                >
                  <span>
                    {v.name}{' '}
                    <span style={{ color: 'var(--ink3)' }}>· {num(v.population)} people</span>
                  </span>
                  <span className="mono" style={{ color: 'var(--ink2)' }}>
                    {v.distance_km} km
                  </span>
                </div>
              ))}
              <div style={{ fontSize: 10, color: 'var(--ink3)', marginTop: 6, lineHeight: 1.5 }}>
                Distance from scene centre — not a claim that these villages are flooded.
              </div>
            </div>
          )}
        </>
      )}

      {/* ---------- provenance (HAMESHA dikhta hai, result ho ya na ho) ---------- */}
      {model && (
        <div
          style={{
            background: 'var(--panel2)',
            border: '1px solid var(--line)',
            borderLeft: '2px solid var(--accent)',
            borderRadius: 8,
            padding: '9px 10px',
            fontSize: 10.5,
            lineHeight: 1.5,
            color: 'var(--ink2)',
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              color: 'var(--ink)',
              fontWeight: 600,
              marginBottom: 4,
            }}
          >
            <IconSatellite className="ti" style={{ color: 'var(--accent-2)' }} />
            Trained model
          </div>
          {/* Disclaimer sabse upar hai, details neeche.
              KYUN: panel scrollable hai. Agar ye aakhir mein hota to fold ke neeche
              chala jaata — aur yahi wo ek line hai jo galatfehmi rokti hai. */}
          <span style={{ color: 'var(--amber)', fontWeight: 600 }}>Detection, not forecast</span>
          <span style={{ color: 'var(--ink3)' }}> — is image mein paani KAHAN tha, aage kya hoga wo nahi.</span>
          <br />
          U-Net (ResNet34) · <b style={{ color: 'var(--ink)' }}>Sen1Floods11, {model.trained_chips} chips</b>{' '}
          (hand-labeled, CC-BY 4.0)
          {m && (
            <>
              <br />
              Test IoU <b style={{ color: 'var(--ink)' }}>{m.iou}</b> · recall {m.recall} @ thr{' '}
              {model.threshold} · India-region IoU{' '}
              <b style={{ color: 'var(--ink)' }}>{model.india_region_iou}</b>
            </>
          )}
          {/* KYUN ye line: button dabane par model yahan LIVE nahi chalta. Result
              ml/predict.py ka asli output hai jo laptop par chala — demo server free tier
              (0.5 GB) pe hai aur PyTorch load nahi kar sakta. Ye na likhte to "Show detection" ko live
              inference samjha jaata. */}
          <br />
          <span style={{ color: 'var(--ink3)' }}>
            Results pre-computed with <span className="mono">ml/predict.py</span>
            {precomputedAt ? ` on ${precomputedAt.slice(0, 10)}` : ''} — the demo server runs on a free tier
            and does not load PyTorch. Same chip + threshold always gives the same mask.
          </span>
        </div>
      )}
    </div>
  )
}
