/**
 * RiskMap — Assam ka Leaflet map, har gaon ek colored dot.
 * mockup_v2.html ka `#map` + `.map-title` + `.map-legend`.
 *
 * DATA: GET /api/villages -> har village ka lat/lng + risk.level
 *
 * YE DASHBOARD KA DIL HAI. Poore product ka differentiator yahi ek screen hai:
 * govt ka system poore zile ko ek rang mein dikhata hai, yahan 30 alag-alag gaon
 * alag-alag rang mein hain. Judge ko yahi dikhana hai.
 *
 * KYUN divIcon (default Leaflet pin nahi):
 *   Pin images bhaari hote hain aur unka rang badalne ke liye 3 alag PNG chahiye hote.
 *   divIcon ek simple <div> hai — rang aur size dono inline style se, turant.
 *   Size bhi risk ke hisaab se badalta hai (RED sabse bada) — 30 dots mein khatra
 *   turant aankh mein aa jaata hai.
 */

import { useEffect, useMemo } from 'react'
import { MapContainer, Marker, TileLayer, ZoomControl, useMap } from 'react-leaflet'
import L from 'leaflet'
import { LEVEL_COLORS } from '../config'
import { dotSize, levelColor } from '../utils/risk'

// Assam ka center + zoom (mockup ke exact values).
const CENTER = [26.3, 92.6]
const ZOOM = 7

/**
 * Carto ke basemaps — theme ke hisaab se dark/light.
 * KYUN Carto: muted, grey basemap hai. Normal OSM ke rangeen roads/parks ke upar
 * hamare red/amber/green dots gum ho jaate. Yahan map background chup rehta hai
 * aur data bolta hai — yehi mockup ka bhi look hai.
 */
const TILES = {
  dark: 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png',
  light: 'https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png',
}

/**
 * makeIcon() — ek village dot ka Leaflet icon.
 * INPUT : level, selected (drawer mein khula hai ya nahi)
 * OUTPUT: L.divIcon
 */
function makeIcon(level, selected) {
  const s = dotSize(level)
  return L.divIcon({
    className: '',
    html: `<div class="vdot${selected ? ' selected' : ''}" style="width:${s}px;height:${s}px;background:${levelColor(level)};"></div>`,
    iconSize: [s, s],
    iconAnchor: [s / 2, s / 2],
  })
}

/**
 * ResizeFix — Leaflet ko batata hai ki container ka size badal gaya.
 * KYUN CHAHIYE: map ek flex panel ke andar hai. Jab drawer khulta hai ya window resize
 * hoti hai, Leaflet ko khud pata nahi chalta aur tiles aadhi grey reh jaati hain.
 * invalidateSize() usse dobara naapne ko kehta hai.
 */
function ResizeFix({ trigger }) {
  const map = useMap()
  useEffect(() => {
    // setTimeout isliye ki CSS transition (drawer .28s) khatam hone ke BAAD naapna hai.
    const t = setTimeout(() => map.invalidateSize(), 320)
    return () => clearTimeout(t)
  }, [map, trigger])
  return null
}

export default function RiskMap({ villages, subtitle, selectedId, onSelect, isDark, drawerOpen }) {
  // Markers ko memo karte hain — warna har render pe 30 divIcon dobara bante hain
  // (aur replay auto-play mein render har 950ms hota hai).
  const markers = useMemo(
    () =>
      (villages || []).map((v) => ({
        id: v.id,
        pos: [v.lat, v.lng],
        icon: makeIcon(v.risk.level, v.id === selectedId),
        village: v,
      })),
    [villages, selectedId],
  )

  return (
    <div className="panel map-panel">
      <MapContainer
        center={CENTER}
        zoom={ZOOM}
        /* Default topleft zoom control "Assam flood grid" wale title chip ke neeche chhup
           jaata tha (pehle screenshot mein title ka "As" dhak gaya tha). Isliye khud add
           karte hain bottom-right pe — wahan sirf khaali map hai (legend bottom-LEFT pe hai). */
        zoomControl={false}
        attributionControl={false}
        style={{ position: 'absolute', inset: 0 }}
      >
        <ZoomControl position="bottomright" />
        {/* key={isDark} — theme badalte hi TileLayer dobara banta hai aur naye tiles aate hain.
            React-leaflet url prop badalne pe apne aap reload nahi karta, isliye key trick. */}
        <TileLayer key={isDark ? 'dark' : 'light'} url={isDark ? TILES.dark : TILES.light} maxZoom={12} />

        {markers.map((m) => (
          <Marker
            key={m.id}
            position={m.pos}
            icon={m.icon}
            eventHandlers={{ click: () => onSelect(m.village) }}
          />
        ))}

        <ResizeFix trigger={drawerOpen} />
      </MapContainer>

      <div className="map-chip map-title">
        <b>Assam flood grid</b>
        <span>{subtitle}</span>
      </div>

      <div className="map-chip map-legend">
        <div>
          <i style={{ background: LEVEL_COLORS.red }} /> Danger
        </div>
        <div>
          <i style={{ background: LEVEL_COLORS.yellow }} /> Warning
        </div>
        <div>
          <i style={{ background: LEVEL_COLORS.green }} /> Safe
        </div>
      </div>
    </div>
  )
}
