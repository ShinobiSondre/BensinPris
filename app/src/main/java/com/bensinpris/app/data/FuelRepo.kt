package com.bensinpris.app.data

/**
 * Combines every source: stations from OpenStreetMap, prices from community reports.
 * Each source is optional, so one failing does not hide the other.
 */
object FuelRepo {
    data class Result(
        val rows: List<StationPrice>,
        val pricesAvailable: Boolean,
        val stationsError: String?,
        val pricesError: String?,
    )

    suspend fun nearby(lat: Double, lng: Double, radiusM: Int, fuel: FuelType): Result {
        var stationsError: String? = null
        var pricesError: String? = null

        val stations = runCatching { OsmStations.near(lat, lng, radiusM) }
            .onFailure { stationsError = it.message ?: "unknown" }
            .getOrDefault(emptyList())

        val reports = runCatching { CommunityPrices.fetch() }
            .onFailure { pricesError = it.message ?: "unknown" }
            .getOrDefault(emptyList())

        val rows = Fuel.rank(stations, Fuel.byStation(reports), fuel, lat, lng, radiusM.toDouble())
        return Result(
            rows = rows,
            pricesAvailable = reports.isNotEmpty(),
            stationsError = stationsError,
            pricesError = pricesError,
        )
    }
}
