package com.bensinpris.app.data

import com.bensinpris.app.util.Http
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId

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
                id = e.optLong("id").toString(),
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
 * Shape confirmed from a sample export: {"exportedAt", "count", "prices": [ {updatedAt, fuelType,
 * stationId ("osm_<OSM id>"), price, reportCount} ]}. One row per station and fuel type.
 * "petrol98" is assumed for Bensin 98 (not seen in the sample yet).
 */
object CommunityPrices {
    const val PRICES_URL = "https://drivstoffpriser.github.io/Drivstoffpriser-App/data/prices.json"

    private val OSLO = ZoneId.of("Europe/Oslo")
    private val FUEL_KEYS = mapOf(
        "petrol95" to FuelType.PETROL95,
        "petrol98" to FuelType.PETROL98,
        "diesel" to FuelType.DIESEL,
    )

    suspend fun fetch(): List<PriceReport> = parse(Http.get(PRICES_URL, timeoutMs = 30000))

    fun parse(json: String): List<PriceReport> {
        val root = json.trim()
        val rows: JSONArray = if (root.startsWith("[")) JSONArray(root)
            else JSONObject(root).optJSONArray("prices") ?: return emptyList()

        // Group the per-fuel rows by station.
        val byStation = LinkedHashMap<String, MutableMap<FuelType, Double>>()
        val newest = HashMap<String, Long>()
        for (i in 0 until rows.length()) {
            val o = rows.optJSONObject(i) ?: continue
            val fuel = FUEL_KEYS[o.optString("fuelType")] ?: continue
            val price = o.optDouble("price", Double.NaN)
            if (price.isNaN() || price <= 0.0) continue
            val stationId = o.optString("stationId").removePrefix("osm_")
            if (stationId.isBlank()) continue

            byStation.getOrPut(stationId) { mutableMapOf() }[fuel] = price
            parseTime(o.optString("updatedAt"))?.let { t ->
                newest[stationId] = maxOf(newest[stationId] ?: 0L, t)
            }
        }
        return byStation.map { (id, prices) ->
            PriceReport(id, prices.toMap(), newest[id])
        }
    }

    /** The export has no time zone, so it is read as Norwegian local time. */
    private fun parseTime(s: String): Long? = runCatching {
        LocalDateTime.parse(s).atZone(OSLO).toInstant().toEpochMilli()
    }.getOrNull()
}
