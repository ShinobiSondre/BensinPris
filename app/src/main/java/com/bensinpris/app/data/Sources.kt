package com.bensinpris.app.data

import com.bensinpris.app.util.Http
import org.json.JSONArray
import org.json.JSONObject

/**
 * Source 1: fuel stations from OpenStreetMap, via the free Overpass API (no key).
 */
object OsmStations {
    private val SERVERS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
    )

    suspend fun near(lat: Double, lng: Double, radiusM: Int): List<Station> {
        val q = "[out:json][timeout:25];nwr(around:$radiusM,$lat,$lng)[\"amenity\"=\"fuel\"];out center tags;"
        var lastError: Exception? = null
        for (server in SERVERS) {
            try {
                return parse(Http.post(server, "data=" + Http.enc(q), "application/x-www-form-urlencoded"))
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("No Overpass server answered")
    }

    fun parse(json: String): List<Station> {
        val els = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        return (0 until els.length()).mapNotNull { i ->
            val e = els.getJSONObject(i)
            val center = e.optJSONObject("center")
            val lat = if (e.has("lat")) e.getDouble("lat") else center?.optDouble("lat", Double.NaN) ?: Double.NaN
            val lng = if (e.has("lon")) e.getDouble("lon") else center?.optDouble("lon", Double.NaN) ?: Double.NaN
            if (lat.isNaN() || lng.isNaN()) return@mapNotNull null
            val tags = e.optJSONObject("tags") ?: JSONObject()
            val brand = tags.optString("brand").ifBlank { tags.optString("operator") }
            Station(
                id = "${e.optString("type")}/${e.optLong("id")}",
                name = tags.optString("name").ifBlank { brand.ifBlank { "Bensinstasjon" } },
                brand = brand,
                lat = lat,
                lng = lng,
            )
        }
    }
}

/**
 * Source 2: community-reported fuel prices, from the open data that drivstoffpriser publishes
 * (refreshed every 12 hours, free to use).
 *
 * UNVERIFIED: the field names below were not checked against the live file. Compare them with
 * prices.json once and fix the constants if they differ. A failed parse shows "no prices",
 * never a wrong price.
 */
object CommunityPrices {
    const val PRICES_URL = "https://drivstoffpriser.github.io/Drivstoffpriser-App/data/prices.json"

    // Assumed JSON keys (see note above).
    private const val KEY_LIST = "stations"
    private const val KEY_LAT = "lat"
    private const val KEY_LNG = "lng"
    private const val KEY_BRAND = "brand"
    private const val KEY_TIME = "updatedAt"
    private val FUEL_KEYS = mapOf(
        FuelType.PETROL95 to "petrol95",
        FuelType.PETROL98 to "petrol98",
        FuelType.DIESEL to "diesel",
    )

    suspend fun fetch(): List<PriceReport> = parse(Http.get(PRICES_URL, timeoutMs = 30000))

    fun parse(json: String): List<PriceReport> {
        val root = json.trim()
        val arr: JSONArray = if (root.startsWith("[")) JSONArray(root)
            else JSONObject(root).optJSONArray(KEY_LIST) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val lat = o.optDouble(KEY_LAT, Double.NaN)
            val lng = o.optDouble(KEY_LNG, Double.NaN)
            if (lat.isNaN() || lng.isNaN()) return@mapNotNull null
            val prices = FUEL_KEYS.mapNotNull { (fuel, key) ->
                val v = o.optDouble(key, Double.NaN)
                if (v.isNaN() || v <= 0.0) null else fuel to v
            }.toMap()
            if (prices.isEmpty()) return@mapNotNull null
            PriceReport(
                lat = lat,
                lng = lng,
                brand = o.optString(KEY_BRAND),
                prices = prices,
                reportedAt = o.optString(KEY_TIME).takeIf { it.isNotBlank() }?.let { parseTime(it) },
            )
        }
    }

    private fun parseTime(s: String): Long? = runCatching {
        java.time.Instant.parse(s).toEpochMilli()
    }.getOrNull()
}
