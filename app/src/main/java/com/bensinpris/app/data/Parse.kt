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
        return when (p.source) {
            PriceSource.CHAIN -> t - 2 * Format.DAY
            // Website prices carry no report time, so they count as a day old when fetched.
            PriceSource.WEB -> t - Format.DAY
            else -> t
        }
    }

    private fun rank(p: FuelPrice) = when (p.source) {
        PriceSource.ME -> 4; PriceSource.GOOGLE -> 3; PriceSource.COMMUNITY -> 2; PriceSource.WEB -> 1; PriceSource.CHAIN -> 0
    }

    // ---------- Google Places API (New): places:searchNearby with places.fuelOptions ----------

    /** Google fuel types as used in Norway: "Blyfri 95" is regular unleaded or SP95, 98 is premium or SP98. */
    private fun googleFuel(t: String): FuelType? = when (t) {
        "DIESEL" -> FuelType.DIESEL
        "REGULAR_UNLEADED", "SP95", "SP95_E10" -> FuelType.PETROL95
        "PREMIUM", "SP98" -> FuelType.PETROL98
        else -> null
    }

    fun googlePlaces(json: String): List<GooglePlace> {
        val arr = runCatching { JSONObject(json).optJSONArray("places") }.getOrNull() ?: return emptyList()
        val out = ArrayList<GooglePlace>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val loc = o.optJSONObject("location") ?: continue
            val lat = loc.optDouble("latitude", Double.NaN)
            val lng = loc.optDouble("longitude", Double.NaN)
            if (lat.isNaN() || lng.isNaN()) continue
            val prices = HashMap<FuelType, Pair<Double, Long?>>()
            val list = o.optJSONObject("fuelOptions")?.optJSONArray("fuelPrices")
            if (list != null) for (j in 0 until list.length()) {
                val f = list.optJSONObject(j) ?: continue
                val fuel = googleFuel(f.optString("type")) ?: continue
                val money = f.optJSONObject("price") ?: continue
                if (money.optString("currencyCode", "NOK") != "NOK") continue
                val v = money.optString("units", "0").toDoubleOrNull()?.plus(money.optLong("nanos", 0) / 1e9) ?: continue
                if (!plausible(v)) continue
                val t = time(f.optString("updateTime"))
                val old = prices[fuel]
                if (old == null || (t ?: 0) > (old.second ?: 0)) prices[fuel] = v to t
            }
            out += GooglePlace(
                id = o.optString("id"),
                name = o.optJSONObject("displayName")?.optString("text").orEmpty(),
                address = o.optString("shortFormattedAddress"),
                lat = lat, lng = lng, prices = prices,
            )
        }
        return out
    }

    /**
     * Google prices on our stations (nearest within [maxM]). Google stations we don't have
     * are returned as new stations, so nothing Google knows is lost.
     */
    fun matchGoogle(stations: List<Station>, places: List<GooglePlace>, maxM: Double = 150.0): Pair<PriceBook, List<Station>> {
        val book = HashMap<String, Map<FuelType, FuelPrice>>()
        val extra = ArrayList<Station>()
        for (g in places) {
            if (g.prices.isEmpty()) continue
            val st = stations.asSequence()
                .filter { kotlin.math.abs(it.lat - g.lat) < 0.003 && kotlin.math.abs(it.lng - g.lng) < 0.006 }
                .map { it to Rank.distanceM(g.lat, g.lng, it.lat, it.lng) }
                .filter { it.second <= maxM }
                .minByOrNull { it.second }?.first
                ?: Station("g_${g.id}", g.name.ifBlank { "Bensinstasjon" }, brandOf(g.name), g.address, "", g.lat, g.lng).also { extra += it }
            book[st.id] = g.prices.mapValues { (_, v) -> FuelPrice(v.first, v.second, 1, PriceSource.GOOGLE, "Google") }
        }
        return book to extra
    }

    private val KNOWN_BRANDS = listOf("Circle K", "Uno-X", "St1", "YX", "Esso", "Shell", "Driv", "Best", "Tanken", "Automat1", "Automat 1", "Bunker Oil")
    private fun brandOf(name: String) = KNOWN_BRANDS.firstOrNull { name.startsWith(it, ignoreCase = true) }.orEmpty()

    // ---------- station prices from drivstoffapp.no (schema.org GasStation data on each town page) ----------

    fun webStations(html: String): List<WebStation> {
        val out = ArrayList<WebStation>()
        Regex("<script[^>]*application/ld\\+json[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL).findAll(html).forEach { m ->
            val root = runCatching { JSONObject(m.groupValues[1]) }.getOrNull() ?: return@forEach
            val items = root.optJSONArray("itemListElement") ?: return@forEach
            for (i in 0 until items.length()) {
                val st = items.optJSONObject(i)?.optJSONObject("item") ?: continue
                if (st.optString("@type") != "GasStation") continue
                val geo = st.optJSONObject("geo") ?: continue
                val lat = geo.optDouble("latitude", Double.NaN)
                val lng = geo.optDouble("longitude", Double.NaN)
                if (lat.isNaN() || lng.isNaN()) continue
                val offers = st.optJSONObject("hasOfferCatalog")?.optJSONArray("itemListElement") ?: continue
                val prices = HashMap<FuelType, Double>()
                for (j in 0 until offers.length()) {
                    val o = offers.optJSONObject(j) ?: continue
                    val fuel = webFuel(o.optString("name")) ?: continue
                    val v = o.optString("price").replace(',', '.').toDoubleOrNull() ?: continue
                    if (plausible(v)) prices[fuel] = v
                }
                if (prices.isNotEmpty()) out += WebStation(st.optString("name"), lat, lng, prices)
            }
        }
        return out
    }

    private fun webFuel(name: String): FuelType? {
        val n = name.lowercase().trim()
        return when {
            n == "diesel" -> FuelType.DIESEL
            n.contains("95") -> FuelType.PETROL95
            n.contains("98") -> FuelType.PETROL98
            else -> null
        }
    }

    /**
     * Puts website prices on our stations (nearest within [maxM]). Prices that look wrong are dropped:
     * more than [maxOff] away from the chain's typical price (or from the national middle, if the chain
     * isn't listed), or 98 cheaper than 95 at the same station.
     */
    fun matchWeb(
        stations: List<Station>, web: List<WebStation>, chains: List<ChainPrice>, at: Long,
        maxM: Double = 150.0, maxOff: Double = 0.15,
    ): PriceBook {
        val national = FuelType.entries.associateWith { f ->
            chains.mapNotNull { it.prices[f] }.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
        }
        val out = HashMap<String, Map<FuelType, FuelPrice>>()
        for (w in web) {
            val dLat = maxM / 111_000.0
            val st = stations.asSequence()
                .filter { kotlin.math.abs(it.lat - w.lat) < dLat * 2 && kotlin.math.abs(it.lng - w.lng) < dLat * 4 }
                .map { it to Rank.distanceM(w.lat, w.lng, it.lat, it.lng) }
                .filter { it.second <= maxM }
                .minByOrNull { it.second }?.first ?: continue
            val chain = chainFor(st, chains)
            val ok = w.prices.filter { (fuel, v) ->
                val ref = chain?.prices?.get(fuel) ?: national[fuel]
                ref == null || kotlin.math.abs(v - ref) / ref <= maxOff
            }.toMutableMap()
            val p95 = ok[FuelType.PETROL95]
            val p98 = ok[FuelType.PETROL98]
            if (p95 != null && p98 != null && p98 < p95) ok.remove(FuelType.PETROL98)
            if (ok.isEmpty()) continue
            out[st.id] = ok.mapValues { (_, v) -> FuelPrice(v, at, 0, PriceSource.WEB, "drivstoffapp.no") }
        }
        return out
    }

    /** Town names to look up on the price website: the towns of the nearest stations, closest first. */
    fun nearbyTowns(spots: List<Spot>, max: Int = 6): List<String> =
        spots.sortedBy { it.distanceM }.map { it.station.city.trim() }.filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }.take(max)

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
