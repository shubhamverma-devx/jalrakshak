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
  IconSatellite,
  IconAlertTriangle,
  IconChartBar,
  IconChartDonut,
  IconChartLine,
} from '@tabler/icons-react'

import './styles/global.css'

import TopBar from './components/TopBar'
import KpiStrip from './components/KpiStrip'
import Panel from './components/Panel'
import RiskDonut from './components/RiskDonut'
import RainfallBar from './components/RainfallBar'
import RiskMap from './components/RiskMap'
import TrendChart from './components/TrendChart'
import ReliefPanel from './components/ReliefPanel'
import ActivityPanel from './components/ActivityPanel'
import ReplaySlider from './components/ReplaySlider'
import SatellitePanel from './components/SatellitePanel'
import VillageDrawer from './components/VillageDrawer'
import SendAlertDialog from './components/SendAlertDialog'
import Toast from './components/Toast'

import { useDashboardData } from './hooks/useDashboardData'
import { useSarDetection } from './hooks/useSarDetection'
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
  const [alertOpen, setAlertOpen] = useState(false) // topbar wala Send-alert dialog

  const { toggle, isDark } = useTheme()
  const { replay, live, ops, refreshOps, retry, setReliefStatus } = useDashboardData(mode)

  // B1 — SAR detection. Sirf Satellite tab pe active hota hai (lazy).
  const sar = useSarDetection(mode === 'satellite')
  const isSat = mode === 'satellite'

  // --- Abhi kaunsa snapshot dikh raha hai ------------------------------------------
  // Replay mein slider ka din, live mein Open-Meteo wala. Poora dashboard isi ek
  // object se chalta hai — isliye map, KPI aur charts kabhi alag baat nahi bolte.
  // Satellite mode mein bhi village dots dikhte rehte hain (live risk se) — taaki
  // detected paani aur gaon ek hi map pe saath dikhein. Yehi to poora point hai.
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
   * SOS ka status badalna (Acknowledge / Mark handled / Reopen).
   * Hook optimistic update karta hai; yahan sirf fail hone pe toast dikhate hain —
   * chup-chaap fail hona sabse bura hota, officer samajhta ki kaam ho gaya.
   */
  const handleReliefStatus = useCallback(
    async (id, status) => {
      try {
        await setReliefStatus(id, status)
        showToast({
          text:
            status === 'inprogress'
              ? 'Marked in progress'
              : status === 'done'
                ? 'Marked handled'
                : 'Request reopened',
          type: 'ok',
        })
      } catch (err) {
        showToast({ text: err.message, type: 'error' })
      }
    },
    [setReliefStatus, showToast],
  )

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
  //
  // Koi bhi modal khula ho to drawer ko haath mat lagao: Escape sabse UPAR wali cheez
  // band karta hai, ye expected behaviour hai. Har modal apna Escape khud sunta hai
  // (useEscapeKey), yahan hum sirf DOM se pooch lete hain ki koi overlay khula to nahi.
  // Har modal ka state App tak laane se ek naya modal banate waqt wo connection jodna
  // bhool jaana aasan hota — DOM check kabhi purana nahi padta.
  useEffect(() => {
    const onKey = (e) => {
      if (e.key !== 'Escape') return
      if (document.querySelector('.modal-ov')) return
      setSelected(null)
    }
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
    () =>
      mode === 'replay'
        ? derivePhase(replay.snapshots, day)
        : { label: isSat ? 'Satellite' : 'Live', tone: 'green' },
    [replay.snapshots, day, mode, isSat],
  )

  /** Map ke upar ka subtitle. */
  const mapSubtitle = isSat
    ? sar.result
      ? `Sentinel-1 SAR · ${sar.result.scene} · ${sar.result.detection.flooded_area_sq_km} sq km detected`
      : 'Sentinel-1 SAR · select a scene and run detection'
    : mode === 'replay'
      ? `Replay · ${dayLabel(replay.days[day])}`
      : snapshot?.data_ok === false
        ? 'Live · rainfall data unavailable'
        : 'Live · Open-Meteo'

  /**
   * Send-alert dialog ke liye gaon ki list.
   *
   * Normally wahi snapshot jo map dikha raha hai — dono ek hi baat bolne chahiye.
   * Satellite tab pe live snapshot lazy hai (useDashboardData sirf mode==='live' pe
   * fetch karta hai), to wahan replay ke current din pe girte hain — warna dialog
   * khaali khulta.
   *
   * riskSource snapshot ke APNE `mode` field se banta hai, hamare UI mode se nahi.
   * KYUN: officer ko dikhna chahiye ki jo risk level wo dialog mein dekh raha hai wo
   * KIS data ka hai. Replay ka risk dekh ke asli alert bhejna ek asli galti hai —
   * usko chhupana nahi chahiye.
   */
  const alertSnapshot = snapshot || replay.snapshots[day] || replay.snapshots[0] || null
  const alertVillages = alertSnapshot?.villages || []
  const riskSource = alertSnapshot
    ? alertSnapshot.mode === 'replay'
      ? `Replay 2022 · ${dayLabel(alertSnapshot.date)}`
      : 'Live · Open-Meteo'
    : 'no data yet'
  const atRiskCount = alertVillages.filter((v) => v.risk.level !== 'green').length


  return (
    <>
      <TopBar
        mode={mode}
        onModeChange={handleModeChange}
        isDark={isDark}
        onThemeToggle={toggle}
        // data_ok false => Open-Meteo se data nahi mila. Badge amber ho jaata hai.
        liveStale={mode === 'live' && snapshot?.data_ok === false}
        onSendAlert={() => setAlertOpen(true)}
        atRiskCount={atRiskCount}
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
        // Satellite mode mein KPI strip SAR ke numbers dikhata hai.
        // KYUN: us tab pe "Danger zones 0" (live risk se) irrelevant aur confusing hai —
        // officer us waqt scene dekh raha hai, village risk nahi.
        sar={isSat ? { result: sar.result, model: sar.model, running: sar.running } : null}
      />

      <div className="grid">
        {/* ---------------- LEFT: risk distribution + rainfall bars ---------------- */}
        <div className="col">
          {isSat ? (
            <Panel title="SAR flood detection" Icon={IconSatellite} style={{ flex: 1 }}>
              <SatellitePanel
                scenes={sar.scenes}
                model={sar.model}
                selected={sar.selected}
                onSelectScene={sar.selectScene}
                onDetect={sar.detect}
                running={sar.running}
                result={sar.result}
                error={sar.error}
              />
            </Panel>
          ) : (
            <>
              <Panel
                title="Risk distribution"
                subtitle="How many villages sit at each level right now"
                Icon={IconChartDonut}
                style={{ flex: '0 0 auto' }}
              >
                <RiskDonut summary={snapshot?.summary} loading={loading || !snapshot} />
              </Panel>

              <Panel
                title="Rainfall by village"
                subtitle="Last 24 hours · heaviest first"
                Icon={IconChartBar}
                style={{ flex: 1, minHeight: 200 }}
              >
                <RainfallBar
                  villages={snapshot?.villages}
                  loading={loading || !snapshot}
                  isDark={isDark}
                />
              </Panel>
            </>
          )}
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
            waterGeoJson={isSat ? sar.result?.geojson : null}
            fitBounds={isSat ? sar.result?.detection?.bounds : null}
          />

          <Panel
            title="Rainfall & people at risk · 12-day trend"
            Icon={IconChartLine}
            style={{ flex: '0 0 180px' }}
            /* Chart canvas kabhi-kabhi 1px zyada naapta hai aur poore panel pe scrollbar
               aa jaata hai. Yahan scroll karne ko kuch hai hi nahi — chart body ke saath
               resize hota hai. */
            bodyStyle={{ overflow: 'hidden' }}
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

        {/* ---------------- RIGHT: relief (priority) + activity ----------------

            LAYOUT KYUN BADLA: pehle relief `maxHeight: 44%` pe tha aur activity `flex:1`.
            Do chhote scroll box ek doosre ke upar — dono mein cards beech se kat rahe the,
            aur officer ko dono mein alag-alag scroll karna padta tha.

            Ab dono ko flex-basis se hissa milta hai (57/43) aur dono ka min-height fix hai,
            to koi bhi panel itna chhota nahi ho sakta ki ek poora card na sama sake.
            Relief ko zyada hissa isliye ki wo ACTION panel hai — activity sirf padhne ke
            liye hai. Jo cheez panel mein nahi samati, uske liye relief ka "View all" hai;
            chup-chaap kaat dena sabse bura option tha. */}
        <div className="col">
          <ReliefPanel
            relief={ops.relief}
            loading={!ops.relief && !ops.error}
            onStatus={handleReliefStatus}
            style={{ flex: '1 1 57%', minHeight: 190 }}
          />

          <ActivityPanel
            relief={ops.relief}
            alerts={ops.alerts}
            snapshot={snapshot}
            mode={mode}
            loading={!ops.alerts && !ops.error}
            style={{ flex: '1 1 43%', minHeight: 150 }}
          />
        </div>
      </div>

      <ReplaySlider
        days={replay.days}
        day={day}
        onDayChange={setDay}
        playing={playing}
        onPlayToggle={() => setPlaying((p) => !p)}
        phase={phase}
        disabled={mode !== 'replay' || replay.loading}
        /* Data honesty chip (data/DATA_NOTES.md ka rule). Mockup mein yahan "MOCKUP ·
           dummy data" likha tha. Ab data asli API se aata hai, par replay ka 2022 data
           representative hai — isliye source hamesha screen pe likha rehta hai. */
        note={
          isSat
            ? 'Sentinel-1 SAR · Sen1Floods11 test split (unseen in training)'
            : mode === 'replay'
              ? 'Assam 2022 replay · representative demo data'
              : 'Live rainfall · Open-Meteo'
        }
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

      <SendAlertDialog
        open={alertOpen}
        onClose={() => setAlertOpen(false)}
        villages={alertVillages}
        riskSource={riskSource}
        onSent={handleAlertSent}
        onToast={showToast}
      />

      <Toast toast={toast} />
    </>
  )
}
