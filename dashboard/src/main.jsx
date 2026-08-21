/**
 * main.jsx — React app ka entry point.
 *
 * NOTE: StrictMode jaan-bujh ke ON hai. Development mein ye har effect ko do baar chalata
 * hai — jisse pata chalta hai ki cleanup sahi likha hai ya nahi. Hamare data hook mein
 * AbortController + clearInterval har effect mein hai, isliye double-run se koi duplicate
 * request ya leak nahi hoti. Production build mein ye double-run hota hi nahi.
 */

import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App.jsx'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
