/**
 * RainfallBar — "Rainfall by village" horizontal bar chart.
 * mockup_v2.html ka `#barChart`.
 *
 * DATA: GET /api/villages -> har village ka risk.factors.rainfall_mm
 *
 * KYUN TOP 8 (mockup mein bhi 8 the): 30 gaon ki bars 200px ki panel mein pad hi nahi
 * sakti — sab squeeze hoke unreadable ho jaate. Officer ko waise bhi "sabse zyada barish
 * kahan" chahiye, poori list nahi (poori list map pe hai).
 *
 * Bar ka rang us gaon ke RISK LEVEL se aata hai, rainfall se nahi — kyunki sabse zyada
 * barish wala gaon zaroori nahi ki sabse khatre mein ho (unchai aur nadi ka level bhi
 * matter karte hain). Ye baat chart ko dekhte hi samajh aani chahiye.
 */

import { Bar } from 'react-chartjs-2'
import './charts'
import { chartTheme } from '../hooks/useTheme'
import { levelColor } from '../utils/risk'

const TOP_N = 8

export default function RainfallBar({ villages, loading, isDark }) {
  if (loading) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 9, paddingTop: 4 }}>
        {Array.from({ length: TOP_N }).map((_, i) => (
          <div key={i} className="sk" style={{ height: 11, width: `${95 - i * 8}%` }} />
        ))}
      </div>
    )
  }

  // Sabse zyada barish wale 8 gaon.
  const top = [...(villages || [])]
    .sort((a, b) => b.risk.factors.rainfall_mm - a.risk.factors.rainfall_mm)
    .slice(0, TOP_N)

  const t = chartTheme(isDark)

  const data = {
    // Naam 9 akshar pe kaat dete hain — "Dibrugarh Town" jaisa lamba naam
    // y-axis ki poori jagah kha jaata hai aur bars patli ho jaati hain.
    labels: top.map((v) => (v.name.length > 9 ? v.name.slice(0, 8) + '…' : v.name)),
    datasets: [
      {
        data: top.map((v) => v.risk.factors.rainfall_mm),
        backgroundColor: top.map((v) => levelColor(v.risk.level)),
        borderRadius: 3,
        barThickness: 11,
      },
    ],
  }

  const options = {
    indexAxis: 'y',
    plugins: {
      legend: { display: false },
      tooltip: {
        callbacks: {
          // Tooltip mein poora naam + level, kyunki label kata hua hai.
          title: (items) => top[items[0].dataIndex].name,
          label: (item) => `${item.raw} mm · ${top[item.dataIndex].risk.level}`,
        },
      },
    },
    scales: {
      x: { grid: { color: t.grid }, ticks: { color: t.tick }, border: { display: false } },
      y: { grid: { display: false }, ticks: { color: t.tick }, border: { display: false } },
    },
    maintainAspectRatio: false,
    animation: false,
  }

  return (
    <div className="chart-wrap" style={{ height: '100%', minHeight: 170 }}>
      <Bar data={data} options={options} />
    </div>
  )
}
