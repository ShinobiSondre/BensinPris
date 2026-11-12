package com.bensinpris.app.data

// Plain data types. No Android in this file, so it can be unit-tested.

enum class FuelType(val key: String, val no: String, val en: String) {
    PETROL95("petrol95", "Bensin 95", "Petrol 95"),
    DIESEL("diesel", "Diesel", "Diesel"),
    PETROL98("petrol98", "Bensin 98", "Petrol 98");

    fun label(norwegian: Boolean) = if (norwegian) no else en

    companion object {
        fun fromKey(k: String?): FuelType? = entries.firstOrNull { it.key == k }
    }
}

/** A fuel station. [id] is the id used by the price feed, e.g. "osm_1118788394" or "ck_circle-k-e18-grelland". */
data class Station(
    val id: String,
    val name: String,
    val brand: String,
    val address: String,
    val city: String,
    val lat: Double,
    val lng: Double,
)

enum class PriceSource { COMMUNITY, ME }

/** One known price for one fuel at one station. */
data class FuelPrice(
    val price: Double,
    /** Epoch millis, or null if the source gave no time. */
    val updatedAt: Long?,
    val reports: Int,
    val source: PriceSource,
)

/** Station id -> fuel -> newest known price. */
typealias PriceBook = Map<String, Map<FuelType, FuelPrice>>

/** A station as seen from where you are, with its price for the chosen fuel. */
data class Spot(
    val station: Station,
    val distanceM: Double,
    /** Price for the chosen fuel, or null if none is known. */
    val price: FuelPrice?,
    val allPrices: Map<FuelType, FuelPrice>,
)

enum class SortBy { PRICE, DISTANCE }

/** What the top of the screen says about the area. */
data class Summary(
    val cheapest: Spot?,
    /** Middle price among the stations with a price, or null if none. */
    val median: Double?,
    val priced: Int,
    val total: Int,
) {
    /** How much cheaper the cheapest is than the middle price, per litre (0 if not meaningful). */
    val savingPerLitre: Double
        get() {
            val c = cheapest?.price?.price ?: return 0.0
            val m = median ?: return 0.0
            return if (priced >= 2 && m - c > 0.004) m - c else 0.0
        }
}
