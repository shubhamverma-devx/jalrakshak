/**
 * =====================================================================================
 *  App — JalRakshak Officer Dashboard (mockup_v2.html ka React version)
 * =====================================================================================
 *
 *  YE KYA HAI: Assam ke flood officer ka command center. Ek screen pe —
 *    kaunsa gaon khatre mein (map + KPI), kitni barish (charts), kisne madad maangi
 *    (relief), aur ek click mein us gaon ko alert.
 *
 *  SAARA DATA LARAVEL API SE AATA HAI. Poore dashboard mein ek bhi hardcoded village,
 *  rainfall ya risk number nahi hai. Risk ka faisla backend ke RiskEngine ka hai —
 *  frontend uska sirf रंग chunta hai. Ye jaan-bujh ke hai (CLAUDE.md convention):
 *  risk logic ek hi jagah, warna dashboard aur app alag-alag jawaab dene lagenge.
 *
 *  LAYOUT (mockup_v2.html ka exact):
 *    TopBar  ->  KPI strip (5)  ->  3-column grid  ->  ReplaySlider (fixed bottom)
 *    Grid: [donut + rainfall bars] | [map + 12-day trend] | [relief + activity feed]
 * =====================================================================================
 */

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  IconActivity,
  IconAlertTriangle,
  IconChartBar,
  IconChartDonut,
  IconChartLine,
  IconLifebuoy,
} from '@tabler/icons-react'

import './styles/global.css'

import TopBar from './components/TopBar'
import KpiStrip from './components/KpiStrip'
import Panel from './components/Panel'
import RiskDonut from './components/RiskDonut'
import RainfallBar from './components/RainfallBar'
import RiskMap from './components/RiskMap'
import TrendChart from './components/TrendChart'
import ReliefList from './components/ReliefList'
import ActivityFeed from './components/ActivityFeed'
import ReplaySlider from './components/ReplaySlider'
import VillageDrawer from './components/VillageDrawer'
import Toast from './components/Toast'

import { useDashboardData } from './hooks/useDashboardData'
import { useTheme } from './hooks/useTheme'
import { dayLabel } from './utils/format'
import { derivePhase } from './utils/risk'

export default function App() {
  // --- UI state --------------------------------------------------------------------
  const [mode, setMode] = useState('replay') // demo replay se shuru hota hai (BUILD_PLAN section 11)
  const [day, setDay] = useState(0)
  const [playing, setPlaying] = useState(false)
  const [selected, setSelected] = useState(null) // drawer mein khula gaon
  const [toast, setToast] = useState(null)
  const [sessionAlerts, setSessionAlerts] = useState(0) // is session mein kitne alert gaye

  const { toggle, isDark } = useTheme()
  const { replay, live, ops, refreshOps, retry } = useDashboardData(mode)

  // --- Abhi kaunsa snapshot dikh raha hai ------------------------------------------
  // Replay mein slider ka din, live mein Open-Meteo wala. Poora dashboard isi ek
  // object se chalta hai — isliye map, KPI aur charts kabhi alag baat nahi bolte.
  const snapshot = mode === 'replay' ? replay.snapshots[day] : live.snapshot

  const loading = mode === 'replay' ? replay.loading : live.loading
  const error = mode === 'replay' ? replay.error : live.error

  /**
   * toast dikhao aur ~2 second mein hata do.
   * Timer ref mein rakha hai taaki do alert jaldi-jaldi bhejne pe pehla timer
   * doosre ka toast na kaat de.
   */
  const toastTimer = useRef(null)
  const showToast = useCallback((t) => {
    setToast(t)
    clearTimeout(toastTimer.current)
    toastTimer.current = setTimeout(() => setToast(null), 2200)
  }, [])

  /** Alert bhejne ke baad — KPI/feed refresh + session counter. */
  const handleAlertSent = useCallback(() => {
    setSessionAlerts((n) => n + 1)
    refreshOps()
  }, [refreshOps])

  /**
   * Mode badalna.
   * Live mein jaate hi auto-play band karte hain — warna background mein timer chalta
   * rehta aur wapas replay pe aane pe din achanak kood jaata.
   */
  const handleModeChange = useCallback((m) => {
    setMode(m)
    setPlaying(false)
  }, [])

  // Escape se drawer band — keyboard se chalane wale officer ke liye.
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && setSelected(null)
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  /**
   * Drawer khula ho aur din/mode badle to selected village ka DATA purana ho jaata hai
   * (wo map ke us waqt ke snapshot se aaya tha). Yahan use naye snapshot se dobara
   * uthate hain, taaki drawer aur map hamesha ek hi baat bolein.
   */
  const selectedVillage = useMemo(() => {
    if (!selected || !snapshot) return null
    return snapshot.villages.find((v) => v.id === selected.id) || null
  }, [selected, snapshot])

  /** Replay ka phase (Normal / Rising / Peak / Receding) — asli data se derive. */
  const phase = useMemo(
    () => (mode === 'replay' ? derivePhase(replay.snapshots, day) : { label: 'Live', tone: 'green' }),
    [replay.snapshots, day, mode],
  )

  /** Map ke upar ka subtitle. */
  const mapSubtitle =
    mode === 'replay'
      ? `Replay · ${dayLabel(replay.days[day])}`
      : snapshot?.data_ok === false
        ? 'Live · rainfall data unavailable'
        : 'Live · Open-Meteo'

  const reliefNew = ops.relief?.counts.new ?? 0

  return (
    <>
      <TopBar
        mode={mode}
        onModeChange={handleModeChange}
        isDark={isDark}
        onThemeToggle={toggle}
        // data_ok false => Open-Meteo se data nahi mila. Badge amber ho jaata hai.
        liveStale={mode === 'live' && snapshot?.data_ok === false}
      />

      {/* Error dikhane ka tareeka: poora dashboard blank karne ke bajaye ek patti upar.
          KYUN: agar sirf live fail hua hai to replay ka data phir bhi kaam ka hai;
          poora screen error se dhak dena officer se zyada cheen leta hai. */}
      {error && (
        <div className="errbox">
          <IconAlertTriangle className="ti" />
          <span>{error}</span>
          <button onClick={retry}>Retry</button>
        </div>
      )}

      <KpiStrip
        snapshot={snapshot}
        relief={ops.relief}
        alerts={ops.alerts}
        sessionAlerts={sessionAlerts}
        loading={loading || !snapshot}
      />

      <div className="grid">
        {/* ---------------- LEFT: risk distribution + rainfall bars ---------------- */}
        <div className="col">
          <Panel title="Risk distribution" Icon={IconChartDonut} style={{ flex: '0 0 auto' }}>
            <RiskDonut summary={snapshot?.summary} loading={loading || !snapshot} />
          </Panel>

          <Panel title="Rainfall by village" Icon={IconChartBar} style={{ flex: 1 }}>
            <RainfallBar villages={snapshot?.villages} loading={loading || !snapshot} isDark={isDark} />
          </Panel>
        </div>

        {/* ---------------- CENTER: map + 12-day trend ---------------- */}
        <div className="col">
          <RiskMap
            villages={snapshot?.villages}
            subtitle={mapSubtitle}
            selectedId={selectedVillage?.id}
            onSelect={setSelected}
            isDark={isDark}
            drawerOpen={!!selectedVillage}
          />

          <Panel
            title="Rainfall & people at risk · 12-day trend"
            Icon={IconChartLine}
            style={{ flex: '0 0 180px' }}
          >
            <TrendChart
              snapshots={replay.snapshots}
              days={replay.days}
              loading={replay.loading}
              isDark={isDark}
              mode={mode}
              currentDay={day}
            />
          </Panel>
        </div>

        {/* ---------------- RIGHT: relief + activity ---------------- */}
        <div className="col">
          <Panel
            title="Relief requests"
            Icon={IconLifebuoy}
            style={{ flex: '0 0 auto', maxHeight: '44%' }}
            bodyStyle={{ padding: '4px 13px' }}
            right={
              reliefNew > 0 ? (
                <span style={{ fontSize: 10.5, color: 'var(--red)', fontWeight: 600 }}>
                  {reliefNew} new
                </span>
              ) : null
            }
          >
            <ReliefList relief={ops.relief} loading={!ops.relief && !ops.error} />
          </Panel>

          <Panel
            title="Activity feed"
            Icon={IconActivity}
            style={{ flex: 1 }}
            bodyStyle={{ padding: 0 }}
          >
            <ActivityFeed
              relief={ops.relief}
              alerts={ops.alerts}
              snapshot={snapshot}
              mode={mode}
              loading={!ops.alerts && !ops.error}
            />
          </Panel>
        </div>
      </div>

      <ReplaySlider
        days={replay.days}
        day={day}
        onDayChange={setDay}
        playing={playing}
        onPlayToggle={() => setPlaying((p) => !p)}
        phase={phase}
        disabled={mode === 'live' || replay.loading}
      />

      <VillageDrawer
        village={selectedVillage}
        mode={mode}
        day={day}
        snapshots={replay.snapshots}
        days={replay.days}
        onClose={() => setSelected(null)}
        onAlertSent={handleAlertSent}
        onToast={showToast}
      />

      <Toast toast={toast} />

      {/* Data honesty chip (data/DATA_NOTES.md ka rule).
          Mockup mein yahan "MOCKUP · dummy data" likha tha. Ab data asli API se aata hai,
          par replay ka 2022 data representative hai — isliye source saaf likha rehta hai.
          Judge ke saamne screen pe hi likha ho, ye sabse imandaar tareeka hai. */}
      <div className="data-flag">
        {mode === 'replay'
          ? 'Assam 2022 replay · representative demo data'
          : 'Live rainfall · Open-Meteo'}
      </div>
    </>
  )
}
