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

/** A fuel station from OpenStreetMap. [id] is the OSM id without its type prefix, e.g. "1118788394". */
data class Station(
    val id: String,
    val name: String,
    val brand: String,
    val lat: Double,
    val lng: Double,
)

/** Community prices for one station, all fuel types merged. */
data class PriceReport(
    val stationId: String,
    val prices: Map<FuelType, Double>,
    /** Newest update time across the fuel types, in epoch millis. */
    val updatedAt: Long?,
)

/** A station with its price for the chosen fuel (null = no price reported for that fuel). */
data class StationPrice(
    val station: Station,
    val price: Double?,
    val updatedAt: Long?,
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

    /** Station id -> its price report. Matching is by OSM id, so no distance check is needed. */
    fun byStation(reports: List<PriceReport>): Map<String, PriceReport> =
        reports.associateBy { it.stationId }

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
                StationPrice(s, rep?.prices?.get(fuel), rep?.updatedAt, d)
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
