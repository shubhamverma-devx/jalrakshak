package com.blackbox.jalrakshak.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import com.blackbox.jalrakshak.data.local.CachedShelter
import com.blackbox.jalrakshak.data.local.CachedVillage
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * MapMarkers — gaon, shelters aur unke beech ki seedhi line map pe daalta hai.
 *
 * KYUN CIRCLE LAYERS (SymbolLayer/icons nahi): SymbolLayer text aur sprite icons
 * maangta hai. Text ke liye glyph files chahiye aur icons ke liye sprite sheet —
 * dono aam taur pe network se aate hain. CircleLayer purely vector shape hai,
 * kuch download nahi karta. Poori tarah offline.
 *
 * Naam/labels hum Compose overlay mein dikhate hain (bundled font ke saath), map ke
 * andar nahi — isse offline bhi labels sahi font mein aate hain.
 */
object MapMarkers {

    private const val SRC_VILLAGE = "jr-village"
    private const val SRC_SHELTERS = "jr-shelters"
    private const val SRC_LINE = "jr-line"

    // Palette app ke theme se — map aur baaki app ek jaise dikhein.
    private const val BLUE = "#175CD3"
    private const val GREEN = "#039855"
    private const val INK3 = "#98A2B3"

    /**
     * draw() — style ready hone ke baad markers add karo.
     *
     * INPUT : style, chuna hua gaon, uske shelters
     * OUTPUT: kuch nahi — style mein sources + layers jud jaate hain
     *
     * Order maayne rakhta hai: line sabse neeche, phir shelters, phir gaon sabse upar
     * (taaki overlap mein "aap yahan hain" hamesha dikhe).
     */
    fun draw(
        context: Context,
        style: org.maplibre.android.maps.Style,
        village: CachedVillage,
        shelters: List<CachedShelter>,
    ) {
        val nearest = shelters.minByOrNull { haversineKm(village.lat, village.lng, it.lat, it.lng) }
        val dist = nearest?.let { haversineKm(village.lat, village.lng, it.lat, it.lng) }

        // ---- 1. gaon se nazdeeki shelter tak seedhi line ----
        // Coincident (doori ~0) pe line skip — do same points ke beech rekha ka koi
        // matlab nahi, aur zoom karne pe wo ajeeb dikhti hai.
        if (nearest != null && dist != null && dist >= COINCIDENT_KM) {
            val line = LineString.fromLngLats(
                listOf(
                    Point.fromLngLat(village.lng, village.lat),
                    Point.fromLngLat(nearest.lng, nearest.lat),
                ),
            )
            style.addSource(GeoJsonSource(SRC_LINE, line))
            style.addLayer(
                LineLayer("jr-line-layer", SRC_LINE).withProperties(
                    PropertyFactory.lineColor(GREEN),
                    PropertyFactory.lineWidth(3f),
                    // Dashed — jaan-bujh ke. Solid line "ye raasta hai" ka bharam deti;
                    // dashes batate hain ki ye seedhi disha hai, sadak nahi.
                    PropertyFactory.lineDasharray(arrayOf(2f, 1.5f)),
                    PropertyFactory.lineOpacity(0.9f),
                ),
            )
        }

        // ---- 2. shelters ----
        if (shelters.isNotEmpty()) {
            val feats = shelters.map { Feature.fromGeometry(Point.fromLngLat(it.lng, it.lat)) }
            style.addSource(GeoJsonSource(SRC_SHELTERS, FeatureCollection.fromFeatures(feats)))
            style.addLayer(
                CircleLayer("jr-shelters-layer", SRC_SHELTERS).withProperties(
                    PropertyFactory.circleRadius(7f),
                    PropertyFactory.circleColor(GREEN),
                    PropertyFactory.circleStrokeWidth(2.5f),
                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                ),
            )
        }

        // ---- 3. gaon (sabse upar) ----
        style.addSource(
            GeoJsonSource(SRC_VILLAGE, Point.fromLngLat(village.lng, village.lat)),
        )
        style.addLayer(
            CircleLayer("jr-village-halo", SRC_VILLAGE).withProperties(
                PropertyFactory.circleRadius(15f),
                PropertyFactory.circleColor(BLUE),
                PropertyFactory.circleOpacity(0.16f),
            ),
        )
        style.addLayer(
            CircleLayer("jr-village-layer", SRC_VILLAGE).withProperties(
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleColor(BLUE),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
            ),
        )
    }
}
