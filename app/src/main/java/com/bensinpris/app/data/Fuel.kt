package com.bensinpris.app.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Pure logic with no Android in it, so the cloud build can unit-test it.

enum class FuelType(val label: String) {
    PETROL95("Bensin 95"),
    PETROL98("Bensin 98"),
    DIESEL("Diesel"),
}

/** A fuel station from OpenStreetMap (location only). */
data class Station(
    val id: String,
    val name: String,
    val brand: String,
    val lat: Double,
    val lng: Double,
)

/** One community price report, with the coordinates of where it was reported. */
data class PriceReport(
    val lat: Double,
    val lng: Double,
    val brand: String,
    val prices: Map<FuelType, Double>,
    val reportedAt: Long?,
)

/** A station with its price for the chosen fuel (null = nobody has reported a price). */
data class StationPrice(
    val station: Station,
    val price: Double?,
    val reportedAt: Long?,
    val distanceM: Double,
)

object Fuel {
    /** Distance between two points, in metres. */
    fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Attach each price report to the nearest station within [maxM] metres.
     * Returns station id -> newest report for that station.
     */
    fun matchReports(stations: List<Station>, reports: List<PriceReport>, maxM: Double = 60.0): Map<String, PriceReport> {
        val out = HashMap<String, PriceReport>()
        for (r in reports) {
            var best: Station? = null
            var bestD = maxM
            for (s in stations) {
                val d = distanceM(r.lat, r.lng, s.lat, s.lng)
                if (d <= bestD) { best = s; bestD = d }
            }
            val s = best ?: continue
            val old = out[s.id]
            if (old == null || (r.reportedAt ?: 0L) > (old.reportedAt ?: 0L)) out[s.id] = r
        }
        return out
    }

    /**
     * Stations within [radiusM] of you, cheapest first for [fuel].
     * Stations with a price come first (cheapest, then closest); unpriced ones follow by distance.
     */
    fun rank(
        stations: List<Station>,
        reports: Map<String, PriceReport>,
        fuel: FuelType,
        fromLat: Double,
        fromLng: Double,
        radiusM: Double,
    ): List<StationPrice> {
        return stations
            .map { s ->
                val d = distanceM(fromLat, fromLng, s.lat, s.lng)
                val rep = reports[s.id]
                StationPrice(s, rep?.prices?.get(fuel), rep?.reportedAt, d)
            }
            .filter { it.distanceM <= radiusM }
            .sortedWith(
                compareBy<StationPrice> { it.price == null }
                    .thenBy { it.price ?: 0.0 }
                    .thenBy { it.distanceM }
            )
    }

    /** Opens driving directions to the station in Google Maps (or the browser if the app is missing). */
    fun mapsDirectionsUrl(s: Station): String =
        "https://www.google.com/maps/dir/?api=1&destination=${s.lat},${s.lng}&travelmode=driving"
}
