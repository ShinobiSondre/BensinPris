package com.bensinpris.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Reads the open data from Drivstoffpriser (https://drivstoffpriser.github.io/Drivstoffpriser-App/data/).
 *
 * stations.json: {"exportedAt", "count", "stations": [{id, name, brand, address, city, latitude, longitude}]}
 * prices.json:   {"exportedAt", "count", "prices": [{stationId, fuelType, price, updatedAt, reportCount}]}
 * Both shapes were checked against the live files.
 */
object Parse {

    fun stations(json: String): List<Station> {
        val arr = listIn(json, "stations") ?: return emptyList()
        val out = ArrayList<Station>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            val lat = o.optDouble("latitude", Double.NaN)
            val lng = o.optDouble("longitude", Double.NaN)
            if (id.isBlank() || lat.isNaN() || lng.isNaN()) continue
            val brand = cleanBrand(o.optString("brand"))
            out += Station(
                id = id,
                name = o.optString("name").trim().ifBlank { brand.ifBlank { "Bensinstasjon" } },
                brand = brand,
                address = o.optString("address").trim(),
                city = o.optString("city").trim(),
                lat = lat,
                lng = lng,
            )
        }
        return out
    }

    fun prices(json: String): PriceBook {
        val arr = listIn(json, "prices") ?: return emptyMap()
        val book = HashMap<String, HashMap<FuelType, FuelPrice>>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val fuel = FuelType.fromKey(o.optString("fuelType")) ?: continue
            val id = o.optString("stationId")
            val price = o.optDouble("price", Double.NaN)
            if (id.isBlank() || !plausible(price)) continue
            val p = FuelPrice(price, time(o.optString("updatedAt")), o.optInt("reportCount", 1), PriceSource.COMMUNITY)
            val forStation = book.getOrPut(id) { HashMap() }
            val old = forStation[fuel]
            if (old == null || newer(p, old)) forStation[fuel] = p
        }
        return book
    }

    /** Joins several price books. For each station and fuel the newest price wins; your own wins a tie. */
    fun merge(vararg books: PriceBook): PriceBook {
        val out = HashMap<String, HashMap<FuelType, FuelPrice>>()
        for (b in books) for ((id, fuels) in b) {
            val forStation = out.getOrPut(id) { HashMap() }
            for ((fuel, p) in fuels) {
                val old = forStation[fuel]
                if (old == null || newer(p, old)) forStation[fuel] = p
            }
        }
        return out
    }

    /** A price a person could plausibly pay for a litre in Norway, in NOK. Filters typos. */
    fun plausible(price: Double) = !price.isNaN() && price in 5.0..60.0

    private fun newer(a: FuelPrice, b: FuelPrice): Boolean {
        val ta = a.updatedAt ?: Long.MIN_VALUE
        val tb = b.updatedAt ?: Long.MIN_VALUE
        return ta > tb || (ta == tb && a.source == PriceSource.ME)
    }

    /** "2026-04-18T12:51:02.310296" (no zone, written by the server in UTC) or a full ISO time with offset. */
    fun time(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
    }

    private fun listIn(json: String, key: String): JSONArray? = runCatching {
        val t = json.trim()
        if (t.startsWith("[")) JSONArray(t) else JSONObject(t).optJSONArray(key)
    }.getOrNull()

    private fun cleanBrand(b: String): String = when (val t = b.trim()) {
        "DRIV" -> "Driv"
        "lokal bensin" -> ""
        else -> t
    }
}
