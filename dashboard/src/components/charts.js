/**
 * charts.js — Chart.js ka ek hi jagah registration.
 *
 * KYUN: Chart.js v4 tree-shakeable hai — jo controller/element/scale use karna ho wo
 * explicitly register karna padta hai. Har chart component mein alag register karte to
 * duplicate hota. Yahan ek baar, sab import karte hain.
 */

import {
  ArcElement,
  BarController,
  BarElement,
  CategoryScale,
  Chart,
  Filler,
  Legend,
  LineController,
  LineElement,
  LinearScale,
  PointElement,
  Tooltip,
} from 'chart.js'

Chart.register(
  ArcElement,
  BarController,
  BarElement,
  CategoryScale,
  Filler,
  Legend,
  LineController,
  LineElement,
  LinearScale,
  PointElement,
  Tooltip,
)

// Mockup ke defaults — Inter 10px. Har chart mein dobara likhne ki zaroorat nahi.
Chart.defaults.font.family = 'Inter'
Chart.defaults.font.size = 10
Chart.defaults.animation.duration = 300

export default Chart
