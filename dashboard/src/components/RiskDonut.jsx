/**
 * RiskDonut — risk distribution (danger / warning / safe).
 * mockup_v2.html ka `#donut` + `.donut-center` + `.leg`.
 *
 * DATA: GET /api/villages -> summary.by_level (RiskEngine ka asli output, koi hisaab yahan nahi).
 * KYUN donut: officer ko exact number se zyada "kitna hissa laal hai" ka anupat dikhta hai.
 * Beech mein total villages likha hai taaki anupat ka denominator bhi saaf rahe.
 */

import { Doughnut } from 'react-chartjs-2'
import './charts'
import { LEVEL_COLORS } from '../config'
import { IconChartDonut } from '@tabler/icons-react'
import { Empty } from './Panel'

export default function RiskDonut({ summary, loading, unavailable }) {
  // Data aaya hi nahi (live fail). "0 villages" ka donut jhootha all-clear hota —
  // saaf likho ki data nahi hai. Skeleton bhi nahi: wo "aa raha hai" bolta hai, jo sach nahi.
  if (unavailable) {
    return <Empty Icon={IconChartDonut}>No risk data: the live server is unreachable.</Empty>
  }
  if (loading) {
    return (
      <>
        <div className="donut-wrap">
          <div
            className="sk"
            style={{ width: 130, height: 130, borderRadius: '50%' }}
          />
        </div>
        <div className="leg">
          <div className="sk" style={{ height: 11, width: 180 }} />
        </div>
      </>
    )
  }

  const c = summary?.by_level || { red: 0, yellow: 0, green: 0 }
  const total = summary?.total_villages ?? 0

  const data = {
    labels: ['Danger', 'Warning', 'Safe'],
    datasets: [
      {
        data: [c.red, c.yellow, c.green],
        backgroundColor: [LEVEL_COLORS.red, LEVEL_COLORS.yellow, LEVEL_COLORS.green],
        borderWidth: 0,
      },
    ],
  }

  const options = {
    cutout: '72%',
    plugins: { legend: { display: false }, tooltip: { enabled: true } },
    maintainAspectRatio: false,
    // KYUN animation off: replay slider ghumte waqt donut har din redraw hota hai.
    // Animation ke saath wo "phadakta" dikhta hai. Sthir number zyada professional lagta hai.
    animation: false,
  }

  return (
    <>
      <div className="donut-wrap">
        <Doughnut data={data} options={options} />
        <div className="donut-center">
          <div className="n">{total}</div>
          <div className="l">villages</div>
        </div>
      </div>
      <div className="leg">
        <span>
          <i style={{ background: LEVEL_COLORS.red }} />
          Danger
        </span>
        <span>
          <i style={{ background: LEVEL_COLORS.yellow }} />
          Warning
        </span>
        <span>
          <i style={{ background: LEVEL_COLORS.green }} />
          Safe
        </span>
      </div>
    </>
  )
}
