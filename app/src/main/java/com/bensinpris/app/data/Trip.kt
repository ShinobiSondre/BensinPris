package com.bensinpris.app.data

import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.sqrt

// Finding fuel along a drive from A to B. Pure Kotlin, unit-tested.

data class LatLng(val lat: Double, val lng: Double)

/** A driving route: the line on the map, its length and how long it takes. */
data class Route(val points: List<LatLng>, val distanceM: Double, val durationS: Double)

/** A station near the route: how far into the trip it comes and how far off the road it is. */
data class TripSpot(val spot: Spot, val alongM: Double, val offM: Double) {
    /** Extra driving to get there and back to the route, roughly. */
    val detourM: Double get() = offM * 2
}

object Trip {

    /** Reads an OSRM answer (`geometries=geojson`). Null if there is no route. */
    fun osrmRoute(json: String): Route? = runCatching {
        val root = JSONObject(json)
        if (root.optString("code") != "Ok") return null
        val r = root.getJSONArray("routes").getJSONObject(0)
        val coords = r.getJSONObject("geometry").getJSONArray("coordinates")
        val pts = (0 until coords.length()).map { i ->
            val c = coords.getJSONArray(i)
            LatLng(c.getDouble(1), c.getDouble(0)) // GeoJSON is [lng, lat]
        }
        if (pts.size < 2) null else Route(pts, r.optDouble("distance"), r.optDouble("duration"))
    }.getOrNull()

    /**
     * Stations within [corridorM] of the route, with their price for [fuel].
     * Cheapest first; stations without a price last, in driving order.
     */
    fun alongRoute(
        stations: List<Station>, book: PriceBook, fuel: FuelType, route: Route, corridorM: Double,
    ): List<TripSpot> {
        val pts = route.points
        // Length driven up to each point.
        val cum = DoubleArray(pts.size)
        for (i in 1 until pts.size) cum[i] = cum[i - 1] + Rank.distanceM(pts[i - 1].lat, pts[i - 1].lng, pts[i].lat, pts[i].lng)

        // Only look at stations inside the route's box, grown by the corridor.
        val pad = corridorM / 111_000.0
        val minLat = pts.minOf { it.lat } - pad
        val maxLat = pts.maxOf { it.lat } + pad
        val padLng = pad / cos(Math.toRadians((minLat + maxLat) / 2)).coerceAtLeast(0.2)
        val minLng = pts.minOf { it.lng } - padLng
        val maxLng = pts.maxOf { it.lng } + padLng

        val out = ArrayList<TripSpot>()
        for (s in stations) {
            if (s.lat !in minLat..maxLat || s.lng !in minLng..maxLng) continue
            var best = Double.MAX_VALUE
            var bestAlong = 0.0
            for (i in 0 until pts.size - 1) {
                val (d, t) = toSegment(s.lat, s.lng, pts[i], pts[i + 1])
                if (d < best) {
                    best = d
                    bestAlong = cum[i] + t * (cum[i + 1] - cum[i])
                }
            }
            if (best <= corridorM) {
                val all = book[s.id].orEmpty()
                out += TripSpot(Spot(s, best, all[fuel], all), bestAlong, best)
            }
        }
        return out.sortedWith(
            compareBy<TripSpot> { it.spot.price == null }
                .thenBy { it.spot.price?.price ?: 0.0 }
                .thenBy { it.detourM }
                .thenBy { it.alongM }
        )
    }

    /** Distance in metres from a point to segment a–b, and how far along the segment (0..1) the closest point is. */
    fun toSegment(lat: Double, lng: Double, a: LatLng, b: LatLng): Pair<Double, Double> {
        // Flat-earth metres around the point: accurate enough over a few kilometres.
        val kx = 111_320.0 * cos(Math.toRadians(lat))
        val ky = 110_540.0
        val ax = (a.lng - lng) * kx; val ay = (a.lat - lat) * ky
        val bx = (b.lng - lng) * kx; val by = (b.lat - lat) * ky
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
        val px = ax + t * dx; val py = ay + t * dy
        return sqrt(px * px + py * py) to t
    }

    /** Google Maps directions from A to B with the station as a stop on the way. */
    fun directionsVia(from: LatLng, to: LatLng, via: Station): String =
        "https://www.google.com/maps/dir/?api=1&origin=${from.lat},${from.lng}&destination=${to.lat},${to.lng}" +
            "&waypoints=${via.lat},${via.lng}&travelmode=driving"

    fun directions(from: LatLng, to: LatLng): String =
        "https://www.google.com/maps/dir/?api=1&origin=${from.lat},${from.lng}&destination=${to.lat},${to.lng}&travelmode=driving"

    /** "2 t 35 min" / "2 h 35 min". */
    fun duration(seconds: Double, no: Boolean): String {
        val min = (seconds / 60).toInt()
        val h = min / 60
        val m = min % 60
        return if (h > 0) (if (no) "$h t $m min" else "$h h $m min") else "$m min"
    }
}
