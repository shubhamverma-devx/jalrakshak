/**
 * TrendChart — 12-din ka "average rainfall + people at risk" combo chart.
 * mockup_v2.html ka `#trend`.
 *
 * DATA: SAARE 12 replay snapshots (useDashboardData ne pehle hi fetch kar liye).
 *       Line  = us din ka average rainfall (villages ka mean)
 *       Bars  = us din ki affected population (summary.affected_population)
 *
 * KYUN DO AXIS: mm aur log ki ginti ka scale bilkul alag hai (200 vs 1,65,000).
 * Ek hi axis pe rainfall line bilkul flat dikhti. Alag axis se dono ka SHAPE dikhta hai —
 * aur asli kahani shape mein hai: barish pehle chadti hai, affected log uske BAAD.
 * Yehi "early-warning" ka poora point hai, aur ye chart usko ek nazar mein dikha deta hai.
 *
 * LIVE MODE: 12-din ka trend replay ka concept hai. Live mein sirf aaj ka data hota hai,
 * isliye wahan honest empty-state dikhate hain — jhoothi line banane se behtar.
 */

import { Chart as ReactChart } from 'react-chartjs-2'
import { IconChartLine } from '@tabler/icons-react'
import './charts'
import { Empty } from './Panel'
import { chartTheme } from '../hooks/useTheme'
import { shortDay } from '../utils/format'

export default function TrendChart({ snapshots, days, loading, isDark, mode, currentDay }) {
  if (mode === 'satellite') {
    // KYUN empty state: ye chart village-level RAINFALL trend hai (rule-based data se).
    // SAR detection ek satellite image ka snapshot hai — dono ka koi rishta nahi.
    // Purana replay trend yahan chhoda rehne dena galat impression deta ki ye
    // detection se juda hai.
    return (
      <Empty Icon={IconChartLine}>
        The rainfall trend is unrelated to SAR detection.
        <br />
        See this chart in Replay 2022 mode.
      </Empty>
    )
  }

  if (mode === 'live') {
    return (
      <Empty Icon={IconChartLine}>
        Live mode shows only today's data.
        <br />
        The 12-day trend is in Replay 2022.
      </Empty>
    )
  }

  if (loading || !snapshots?.length) {
    return <div className="sk" style={{ height: '100%', minHeight: 110 }} />
  }

  const t = chartTheme(isDark)

  // Har din ka average rainfall — asli village-wise numbers se nikalta hai.
  const rainSeries = snapshots.map((s) => {
    const vs = s.villages
    if (!vs.length) return 0
    return Math.round(vs.reduce((sum, v) => sum + v.risk.factors.rainfall_mm, 0) / vs.length)
  })

  const affectedSeries = snapshots.map((s) => s.summary.affected_population)

  const data = {
    labels: days.map(shortDay),
    datasets: [
      {
        type: 'line',
        label: 'Avg rainfall (mm)',
        data: rainSeries,
        borderColor: '#4b82be',
        backgroundColor: 'rgba(75,130,190,.08)',
        fill: true,
        tension: 0.4,
        yAxisID: 'y',
        pointRadius: 0,
        borderWidth: 2,
      },
      {
        type: 'bar',
        label: 'People at risk',
        data: affectedSeries,
        // Abhi jo din slider pe chuna hua hai, uski bar gehri — baaki halki.
        // Isse officer ko turant pata rehta hai ki wo 12 din ki kahani mein kahan khada hai.
        backgroundColor: affectedSeries.map((_, i) =>
          i === currentDay ? 'rgba(200,84,80,.75)' : 'rgba(200,84,80,.3)',
        ),
        yAxisID: 'y1',
        borderRadius: 2,
        barThickness: 9,
      },
    ],
  }

  const options = {
    plugins: {
      legend: { labels: { color: t.tick, boxWidth: 9, font: { size: 10 } } },
      tooltip: { mode: 'index', intersect: false },
    },
    scales: {
      x: { grid: { display: false }, ticks: { color: t.tick }, border: { display: false } },
      y: { grid: { color: t.grid }, ticks: { color: t.tick }, border: { display: false } },
      y1: {
        position: 'right',
        grid: { display: false },
        // Lakh ke numbers axis pe bahut jagah lete hain — "1.6L" chhota aur padhne layak hai.
        ticks: {
          color: t.tick,
          callback: (v) => (v >= 1000 ? `${(v / 100000).toFixed(1)}L` : v),
        },
        border: { display: false },
      },
    },
    maintainAspectRatio: false,
    animation: false,
  }

  return (
    <div className="chart-wrap" style={{ height: 120 }}>
      <ReactChart type="bar" data={data} options={options} />
    </div>
  )
}
