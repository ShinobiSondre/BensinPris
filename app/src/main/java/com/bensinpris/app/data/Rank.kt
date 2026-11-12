package com.bensinpris.app.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Finding and ordering stations around you. No Android in here. */
object Rank {

    fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Stations within [radiusM] of the given point, in the requested order. */
    fun spots(
        stations: List<Station>,
        book: PriceBook,
        fuel: FuelType,
        lat: Double,
        lng: Double,
        radiusM: Double,
        sort: SortBy,
    ): List<Spot> {
        // Cheap box test first, then the exact distance.
        val dLat = radiusM / 111_000.0
        val dLng = radiusM / (111_000.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.1))
        val near = stations.asSequence()
            .filter { kotlin.math.abs(it.lat - lat) <= dLat && kotlin.math.abs(it.lng - lng) <= dLng }
            .map { s ->
                val all = book[s.id].orEmpty()
                Spot(s, distanceM(lat, lng, s.lat, s.lng), all[fuel], all)
            }
            .filter { it.distanceM <= radiusM }
            .toList()
        return sort(near, sort)
    }

    fun sort(spots: List<Spot>, sort: SortBy): List<Spot> = when (sort) {
        SortBy.PRICE -> spots.sortedWith(
            compareBy<Spot> { it.price == null }
                .thenBy { it.price?.price ?: 0.0 }
                .thenBy { it.distanceM }
        )
        SortBy.DISTANCE -> spots.sortedBy { it.distanceM }
    }

    fun summary(spots: List<Spot>): Summary {
        val priced = spots.filter { it.price != null }
        val cheapest = priced.minWithOrNull(compareBy<Spot> { it.price!!.price }.thenBy { it.distanceM })
        val sorted = priced.map { it.price!!.price }.sorted()
        val median = when {
            sorted.isEmpty() -> null
            sorted.size % 2 == 1 -> sorted[sorted.size / 2]
            else -> (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        }
        return Summary(cheapest, median, priced.size, spots.size)
    }

    /** Driving directions in Google Maps (the app if installed, otherwise the browser). */
    fun directionsUrl(s: Station): String =
        "https://www.google.com/maps/dir/?api=1&destination=${s.lat},${s.lng}&travelmode=driving"

    /** The station's place in Google Maps, to see opening hours, photos and reviews. */
    fun placeUrl(s: Station): String {
        val q = java.net.URLEncoder.encode("${s.name} ${s.address} ${s.city}".trim(), "UTF-8")
        return "https://www.google.com/maps/search/?api=1&query=$q"
    }
}
