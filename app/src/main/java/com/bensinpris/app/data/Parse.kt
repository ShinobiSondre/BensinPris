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

    /**
     * Which price to show. Newest wins, except that a chain's typical price counts as two days older
     * than it is: a real price from this station within the last two days beats the chain estimate.
     */
    private fun newer(a: FuelPrice, b: FuelPrice): Boolean {
        val ta = effective(a)
        val tb = effective(b)
        return ta > tb || (ta == tb && rank(a) > rank(b))
    }

    private fun effective(p: FuelPrice): Long {
        val t = p.updatedAt ?: return Long.MIN_VALUE
        return if (p.isEstimate) t - 2 * Format.DAY else t
    }

    private fun rank(p: FuelPrice) = when (p.source) { PriceSource.ME -> 2; PriceSource.COMMUNITY -> 1; PriceSource.CHAIN -> 0 }

    // ---------- typical chain prices (drivstoffappen.no/drivstoffpriser, table "Kjedene i dag") ----------

    /** Reads the chain table: one row per chain with Bensin 95 and Diesel. Unknown columns are ignored. */
    fun chainPrices(html: String): List<ChainPrice> {
        val tables = Regex("<table[^>]*>(.*?)</table>", RegexOption.DOT_MATCHES_ALL).findAll(html).map { it.groupValues[1] }
        val table = tables.firstOrNull { it.contains("Kjedene i dag") } ?: tables.firstOrNull { it.contains(">Kjede<") } ?: return emptyList()
        val rows = Regex("<tr[^>]*>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL).findAll(table).map { row ->
            Regex("<t[hd][^>]*>(.*?)</t[hd]>", RegexOption.DOT_MATCHES_ALL).findAll(row.groupValues[1])
                .map { cleanCell(it.groupValues[1]) }.toList()
        }.toList()
        val header = rows.firstOrNull { r -> r.any { it.equals("Kjede", ignoreCase = true) } } ?: return emptyList()
        val columns = header.mapIndexedNotNull { i, h ->
            when {
                h.contains("95") -> i to FuelType.PETROL95
                h.contains("98") -> i to FuelType.PETROL98
                h.contains("diesel", ignoreCase = true) -> i to FuelType.DIESEL
                else -> null
            }
        }
        return rows.filter { it !== header && it.isNotEmpty() && it[0].isNotBlank() }.mapNotNull { r ->
            val prices = columns.mapNotNull { (i, fuel) ->
                r.getOrNull(i)?.replace(',', '.')?.toDoubleOrNull()?.takeIf(::plausible)?.let { fuel to it }
            }.toMap()
            if (prices.isEmpty()) null else ChainPrice(r[0], prices)
        }
    }

    private fun cleanCell(s: String) = s.replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace("&nbsp;", " ").trim()

    private val VARIANTS = listOf("automat", "express", "7-eleven", "truck")

    /** "Circle K Automat" -> ("circlek", "automat"); "Uno-X" -> ("unox", null); "Automat 1" -> ("automat1", null). */
    fun chainKey(name: String): Pair<String, String?> {
        val low = name.lowercase().trim()
        val v = VARIANTS.firstOrNull { low.endsWith(" $it") }
        val base = if (v != null) low.removeSuffix(" $v") else low
        return squash(base) to v
    }

    private fun squash(s: String) = s.filter { it.isLetterOrDigit() }

    /** The chain price that fits this station: its variant (Automat, Express …) if listed, else the plain chain. */
    fun chainFor(st: Station, chains: List<ChainPrice>): ChainPrice? {
        if (st.brand.isBlank()) return null
        val (base, brandVariant) = chainKey(st.brand)
        val same = chains.filter { chainKey(it.name).first == base }
        if (same.isEmpty()) return null
        val name = st.name.lowercase()
        val variant = brandVariant ?: VARIANTS.firstOrNull { name.contains(it) }
        variant?.let { v -> same.firstOrNull { chainKey(it.name).second == v } }?.let { return it }
        // Truck stops price diesel differently, so don't guess from the car stations.
        if (variant == "truck") return null
        return same.firstOrNull { chainKey(it.name).second == null }
    }

    /** A chain estimate for every station whose chain is listed. */
    fun estimates(stations: List<Station>, chains: List<ChainPrice>, at: Long): PriceBook {
        if (chains.isEmpty()) return emptyMap()
        val out = HashMap<String, Map<FuelType, FuelPrice>>()
        for (st in stations) {
            val c = chainFor(st, chains) ?: continue
            out[st.id] = c.prices.mapValues { (_, v) -> FuelPrice(v, at, 0, PriceSource.CHAIN, c.name) }
        }
        return out
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
