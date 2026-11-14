package com.bensinpris.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.bensinpris.app.data.Route
import com.bensinpris.app.data.TripSpot
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay

/**
 * OpenStreetMap with the route drawn on it and a price tag on each station along the way.
 * The cheapest one is green; tapping a tag opens the station.
 */
@Composable
fun TripMap(
    route: Route, spots: List<TripSpot>, cheapestId: String?, no: Boolean,
    onSelect: (String) -> Unit, modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val routeColor = Petrol.toArgb()
    val ctx = LocalContext.current
    // Zoom to the route only when it is new, not every time prices change.
    val fitted = remember { mutableListOf<Route?>(null) }
    val map = remember {
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 4.0
            if (dark) overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
        }
    }
    AndroidView(factory = { map }, modifier = modifier, update = { m ->
        m.overlays.clear()
        m.overlays += Polyline(m).apply {
            setPoints(route.points.map { GeoPoint(it.lat, it.lng) })
            outlinePaint.color = if (dark) 0xFF86CFDF.toInt() else routeColor
            outlinePaint.strokeWidth = 10f
            outlinePaint.strokeCap = Paint.Cap.ROUND
        }
        route.points.first().let { p -> m.overlays += dot(m, GeoPoint(p.lat, p.lng), "A") }
        route.points.last().let { p -> m.overlays += dot(m, GeoPoint(p.lat, p.lng), "B") }

        // Priced stations, the most expensive first so the cheap ones end up on top. At most 40 tags.
        val priced = spots.filter { it.spot.price != null }.take(40).reversed()
        priced.forEach { t ->
            val p = t.spot.price!!
            val cheapest = t.spot.station.id == cheapestId
            m.overlays += Marker(m).apply {
                position = GeoPoint(t.spot.station.lat, t.spot.station.lng)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = pill(ctx, priceText(p, no), if (cheapest) 0xFF1E8E5A.toInt() else 0xFF0F4C5C.toInt())
                title = t.spot.station.name
                setOnMarkerClickListener { _, _ -> onSelect(t.spot.station.id); true }
            }
        }
        m.overlays += CopyrightOverlay(ctx)

        if (fitted[0] !== route) {
            fitted[0] = route
            val box = BoundingBox.fromGeoPointsSafe(route.points.map { GeoPoint(it.lat, it.lng) })
            m.post { m.zoomToBoundingBox(box.increaseByScale(1.15f), false, 40) }
        }
        m.invalidate()
    })
}

private fun dot(m: MapView, at: GeoPoint, label: String) = Marker(m).apply {
    position = at
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    icon = circle(m.context, label)
    setInfoWindow(null)
}

/** A rounded price tag with a little point at the bottom. */
private fun pill(ctx: Context, text: String, bg: Int): BitmapDrawable {
    val d = ctx.resources.displayMetrics.density
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12.5f * d; typeface = Typeface.DEFAULT_BOLD; color = 0xFFFFFFFF.toInt() }
    val w = paint.measureText(text) + 14 * d
    val h = 22 * d
    val tail = 6 * d
    val bmp = Bitmap.createBitmap(w.toInt() + 1, (h + tail).toInt() + 1, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
    c.drawRoundRect(RectF(0f, 0f, w, h), h / 2, h / 2, fill)
    c.drawPath(Path().apply { moveTo(w / 2 - tail, h - 1); lineTo(w / 2, h + tail); lineTo(w / 2 + tail, h - 1); close() }, fill)
    c.drawText(text, 7 * d, h / 2 - (paint.descent() + paint.ascent()) / 2, paint)
    return BitmapDrawable(ctx.resources, bmp)
}

/** Start and end of the trip. */
private fun circle(ctx: Context, label: String): BitmapDrawable {
    val d = ctx.resources.displayMetrics.density
    val size = (26 * d).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawCircle(size / 2f, size / 2f, size / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
    c.drawCircle(size / 2f, size / 2f, size / 2f - 2.5f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF072830.toInt() })
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13 * d; typeface = Typeface.DEFAULT_BOLD; color = 0xFFF5B841.toInt(); textAlign = Paint.Align.CENTER }
    c.drawText(label, size / 2f, size / 2f - (p.descent() + p.ascent()) / 2, p)
    return BitmapDrawable(ctx.resources, bmp)
}
