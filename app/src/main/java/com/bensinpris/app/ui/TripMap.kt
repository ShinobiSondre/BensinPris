package com.bensinpris.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bensinpris.app.data.Route
import com.bensinpris.app.data.TripSpot
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.android.geometry.LatLng as MapLatLng

// Free vector map tiles from OpenFreeMap (no key). Liberty is their maintained light style.
private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"

/**
 * Vector map with the route and a price pin for each station along it. Where pins would overlap,
 * the cheaper one is kept and the others appear as you zoom in. The cheapest is always shown, in green.
 */
@Composable
fun TripMap(
    route: Route, spots: List<TripSpot>, cheapestId: String?, no: Boolean,
    onSelect: (String) -> Unit, modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val select by rememberUpdatedState(onSelect)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(ctx).also { it.onCreate(null) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val fitted = remember { mutableListOf<Route?>(null) }
    val lineColor = (if (dark) FreshDark else Petrol).toArgb()

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            val now = lifecycle.currentState
            if (now.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (now.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    // The map itself, once: gestures and taps on pins.
    LaunchedEffect(Unit) {
        mapView.getMapAsync { m ->
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.uiSettings.isCompassEnabled = false
            m.addOnMapClickListener { p ->
                val pt = m.projection.toScreenLocation(p)
                val hit = m.queryRenderedFeatures(pt, "st-cheap", "st-price", "st-none").firstOrNull()
                hit?.getStringProperty("id")?.let { select(it); true } ?: false
            }
            map = m
        }
    }

    // Light or dark map; layers are added again whenever the style is (re)loaded.
    LaunchedEffect(map, dark) {
        val m = map ?: return@LaunchedEffect
        style = null
        fitted[0] = null
        m.setStyle(Style.Builder().fromUri(if (dark) STYLE_DARK else STYLE_LIGHT)) { st ->
            addLayers(st, dark, lineColor)
            st.addImage("end_a", endPin(ctx, "A"))
            st.addImage("end_b", endPin(ctx, "B"))
            style = st
        }
    }

    // Data: route, pins, camera.
    LaunchedEffect(style, route, spots, cheapestId) {
        val st = style ?: return@LaunchedEffect
        val m = map ?: return@LaunchedEffect

        val line = route.points.map { Point.fromLngLat(it.lng, it.lat) }
        st.getSourceAs<GeoJsonSource>("route")?.setGeoJson(LineString.fromLngLats(line))
        st.getSourceAs<GeoJsonSource>("ends")?.setGeoJson(FeatureCollection.fromFeatures(listOf(
            Feature.fromGeometry(line.first()).apply { addStringProperty("icon", "end_a") },
            Feature.fromGeometry(line.last()).apply { addStringProperty("icon", "end_b") },
        )))

        val priced = spots.filter { it.spot.price != null }
        val cheap = ArrayList<Feature>()
        val rest = ArrayList<Feature>()
        priced.forEachIndexed { i, t ->
            val s = t.spot.station
            val isCheapest = s.id == cheapestId
            val icon = "pin_${s.id}"
            st.addImage(icon, pricePin(ctx, initials(s.brand, s.name), badgeColor(s.brand, s.name).toArgb(),
                priceText(t.spot.price!!, no), isCheapest, dark))
            val f = Feature.fromGeometry(Point.fromLngLat(s.lng, s.lat)).apply {
                addStringProperty("id", s.id)
                addStringProperty("icon", icon)
                addNumberProperty("rank", i) // cheapest first wins when pins overlap
            }
            if (isCheapest) cheap += f else rest += f
        }
        val none = spots.filter { it.spot.price == null }.map { t ->
            Feature.fromGeometry(Point.fromLngLat(t.spot.station.lng, t.spot.station.lat)).apply {
                addStringProperty("id", t.spot.station.id)
            }
        }
        st.getSourceAs<GeoJsonSource>("cheap")?.setGeoJson(FeatureCollection.fromFeatures(cheap))
        st.getSourceAs<GeoJsonSource>("priced")?.setGeoJson(FeatureCollection.fromFeatures(rest))
        st.getSourceAs<GeoJsonSource>("none")?.setGeoJson(FeatureCollection.fromFeatures(none))

        if (fitted[0] !== route) {
            fitted[0] = route
            val bounds = LatLngBounds.Builder().includes(route.points.map { MapLatLng(it.lat, it.lng) }).build()
            val d = ctx.resources.displayMetrics.density
            mapView.post {
                runCatching {
                    m.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, (36 * d).toInt(), (56 * d).toInt(), (36 * d).toInt(), (28 * d).toInt()))
                }
            }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun addLayers(st: Style, dark: Boolean, lineColor: Int) {
    st.addSource(GeoJsonSource("route"))
    st.addSource(GeoJsonSource("ends"))
    st.addSource(GeoJsonSource("none"))
    st.addSource(GeoJsonSource("priced"))
    st.addSource(GeoJsonSource("cheap"))

    st.addLayer(LineLayer("route-case", "route").withProperties(
        PropertyFactory.lineColor(if (dark) 0xFF0D1214.toInt() else 0xFFFFFFFF.toInt()),
        PropertyFactory.lineWidth(9f),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
    ))
    st.addLayer(LineLayer("route-line", "route").withProperties(
        PropertyFactory.lineColor(lineColor),
        PropertyFactory.lineWidth(5f),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
    ))
    // Stations without a known price: small dots.
    st.addLayer(CircleLayer("st-none", "none").withProperties(
        PropertyFactory.circleRadius(4.5f),
        PropertyFactory.circleColor(if (dark) 0xFF6B7476.toInt() else 0xFF9AA0A2.toInt()),
        PropertyFactory.circleStrokeColor(if (dark) 0xFF0D1214.toInt() else 0xFFFFFFFF.toInt()),
        PropertyFactory.circleStrokeWidth(1.5f),
    ))
    st.addLayer(SymbolLayer("st-price", "priced").withProperties(
        PropertyFactory.iconImage(Expression.get("icon")),
        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
        PropertyFactory.iconAllowOverlap(false),
        PropertyFactory.symbolSortKey(Expression.get("rank")),
    ))
    st.addLayer(SymbolLayer("st-cheap", "cheap").withProperties(
        PropertyFactory.iconImage(Expression.get("icon")),
        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
        PropertyFactory.iconAllowOverlap(true),
    ))
    st.addLayer(SymbolLayer("ends", "ends").withProperties(
        PropertyFactory.iconImage(Expression.get("icon")),
        PropertyFactory.iconAllowOverlap(true),
        PropertyFactory.iconIgnorePlacement(true),
    ))
}

/** A price pin: brand badge, price, and a small point at the bottom. */
private fun pricePin(ctx: Context, initials: String, badge: Int, price: String, cheapest: Boolean, dark: Boolean): Bitmap {
    val d = ctx.resources.displayMetrics.density
    val shadow = 3 * d
    val h = (if (cheapest) 32 else 28) * d
    val tail = 6 * d
    val badgeR = h / 2 - 4 * d
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = (if (cheapest) 14.5f else 13f) * d
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = when {
            cheapest -> 0xFFFFFFFF.toInt()
            dark -> 0xFFE2E3E3.toInt()
            else -> 0xFF1A1C1C.toInt()
        }
    }
    val w = 4 * d + badgeR * 2 + 6 * d + text.measureText(price) + 10 * d
    val bmp = Bitmap.createBitmap((w + shadow * 2).toInt(), (h + tail + shadow * 2).toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.translate(shadow, shadow * 0.6f)
    val bg = when {
        cheapest -> 0xFF1E8E5A.toInt()
        dark -> 0xFF1E2527.toInt()
        else -> 0xFFFFFFFF.toInt()
    }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bg
        setShadowLayer(shadow, 0f, d, 0x55000000)
    }
    val shape = Path().apply {
        addRoundRect(RectF(0f, 0f, w, h), h / 2, h / 2, Path.Direction.CW)
        moveTo(w / 2 - tail, h - 1); lineTo(w / 2, h + tail); lineTo(w / 2 + tail, h - 1); close()
    }
    c.drawPath(shape, fill)
    // Brand badge.
    val cx = 4 * d + badgeR
    c.drawCircle(cx, h / 2, badgeR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (cheapest) 0x33FFFFFF else badge })
    val ini = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = badgeR * 0.85f; typeface = Typeface.DEFAULT_BOLD; color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER
    }
    c.drawText(initials, cx, h / 2 - (ini.descent() + ini.ascent()) / 2, ini)
    c.drawText(price, cx + badgeR + 6 * d, h / 2 - (text.descent() + text.ascent()) / 2, text)
    return bmp
}

/** Start (A) and end (B) of the trip. */
private fun endPin(ctx: Context, label: String): Bitmap {
    val d = ctx.resources.displayMetrics.density
    val size = (28 * d).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawCircle(size / 2f, size / 2f, size / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
    c.drawCircle(size / 2f, size / 2f, size / 2f - 2.5f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF072830.toInt() })
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13 * d; typeface = Typeface.DEFAULT_BOLD; color = 0xFFF5B841.toInt(); textAlign = Paint.Align.CENTER
    }
    c.drawText(label, size / 2f, size / 2f - (p.descent() + p.ascent()) / 2, p)
    return bmp
}
