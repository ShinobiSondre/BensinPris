package com.bensinpris.app.data

import android.content.Context
import com.bensinpris.app.util.Http
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * A JSON feed kept on the phone. Reads the last download, or the copy bundled into the APK at
 * build time, so the app has stations to show even offline on first start.
 */
class CachedFeed(private val name: String, private val url: String, private val maxAgeMs: Long) {
    private fun file(ctx: Context) = File(ctx.filesDir, name)

    /** When the phone's copy was downloaded (0 if only the bundled copy exists). */
    fun fetchedAt(ctx: Context): Long = file(ctx).takeIf { it.exists() }?.lastModified() ?: 0L

    fun isStale(ctx: Context) = System.currentTimeMillis() - fetchedAt(ctx) > maxAgeMs

    fun read(ctx: Context): String? =
        runCatching { file(ctx).takeIf { it.exists() }?.readText() }.getOrNull()
            ?: runCatching { ctx.assets.open(name).bufferedReader().use { it.readText() } }.getOrNull()

    /** Downloads the feed and keeps it only if [valid] accepts it. */
    suspend fun download(ctx: Context, valid: (String) -> Boolean): String {
        val text = Http.get(url, timeoutMs = 30_000)
        require(valid(text)) { "Unexpected data from $url" }
        val tmp = File(ctx.filesDir, "$name.tmp")
        tmp.writeText(text)
        tmp.renameTo(file(ctx))
        return text
    }

    companion object {
        private const val BASE = "https://drivstoffpriser.github.io/Drivstoffpriser-App/data"
        val STATIONS = CachedFeed("stations.json", "$BASE/stations.json", Format.DAY)
        val PRICES = CachedFeed("prices.json", "$BASE/prices.json", 3 * Format.HOUR)
    }
}

/**
 * Typical price per chain today, read from DrivstoffAppen's public price page (robots.txt allows it).
 * Kept on the phone as parsed JSON and fetched again after 6 hours.
 */
object ChainStore {
    private const val URL = "https://drivstoffappen.no/drivstoffpriser"
    private const val MAX_AGE = 6 * Format.HOUR
    private fun file(ctx: Context) = File(ctx.filesDir, "chains.json")

    data class Snapshot(val chains: List<ChainPrice>, val at: Long)

    fun read(ctx: Context): Snapshot = runCatching {
        val o = JSONObject(file(ctx).readText())
        val arr = o.getJSONArray("chains")
        val list = (0 until arr.length()).map { i ->
            val c = arr.getJSONObject(i)
            val p = c.getJSONObject("prices")
            ChainPrice(c.getString("name"), p.keys().asSequence().mapNotNull { k ->
                FuelType.fromKey(k)?.let { it to p.getDouble(k) }
            }.toMap())
        }
        Snapshot(list, o.getLong("at"))
    }.getOrDefault(Snapshot(emptyList(), 0L))

    fun isStale(ctx: Context) = System.currentTimeMillis() - read(ctx).at > MAX_AGE

    suspend fun download(ctx: Context): Snapshot {
        val chains = Parse.chainPrices(Http.get(URL, timeoutMs = 20_000, accept = "text/html"))
        require(chains.size >= 3) { "Chain table not found" }
        val now = System.currentTimeMillis()
        val arr = JSONArray()
        chains.forEach { c ->
            val p = JSONObject(); c.prices.forEach { (f, v) -> p.put(f.key, v) }
            arr.put(JSONObject().put("name", c.name).put("prices", p))
        }
        file(ctx).writeText(JSONObject().put("at", now).put("chains", arr).toString())
        return Snapshot(chains, now)
    }
}

/**
 * Station prices from drivstoffapp.no town pages (robots.txt allows /search). One small file per town,
 * fetched again after 3 hours, and only for the few towns around you.
 */
object WebStore {
    private const val MAX_AGE = 3 * Format.HOUR
    private fun file(ctx: Context, town: String) =
        File(ctx.filesDir, "web_" + town.lowercase().filter { it.isLetterOrDigit() } + ".json")

    data class Town(val stations: List<WebStation>, val at: Long)

    fun read(ctx: Context, town: String): Town? = runCatching {
        val o = JSONObject(file(ctx, town).readText())
        val arr = o.getJSONArray("s")
        Town((0 until arr.length()).map { i ->
            val w = arr.getJSONObject(i)
            val p = w.getJSONObject("p")
            WebStation(w.getString("n"), w.getDouble("a"), w.getDouble("o"),
                p.keys().asSequence().mapNotNull { k -> FuelType.fromKey(k)?.let { it to p.getDouble(k) } }.toMap())
        }, o.getLong("at"))
    }.getOrNull()

    fun isStale(ctx: Context, town: String) = System.currentTimeMillis() - (read(ctx, town)?.at ?: 0L) > MAX_AGE

    suspend fun download(ctx: Context, town: String): Town {
        val slug = java.net.URLEncoder.encode(town.lowercase(), "UTF-8").replace("+", "%20")
        val list = Parse.webStations(Http.get("https://www.drivstoffapp.no/search/$slug", timeoutMs = 20_000, accept = "text/html"))
        val now = System.currentTimeMillis()
        val arr = JSONArray()
        list.forEach { w ->
            val p = JSONObject(); w.prices.forEach { (f, v) -> p.put(f.key, v) }
            arr.put(JSONObject().put("n", w.name).put("a", w.lat).put("o", w.lng).put("p", p))
        }
        file(ctx, town).writeText(JSONObject().put("at", now).put("s", arr).toString())
        return Town(list, now)
    }
}

/**
 * Fuel prices from Google Maps through the official Places API (New), with your own API key.
 * One Nearby Search per area: reused for 30 minutes unless you move more than 1 km.
 */
object GoogleStore {
    private const val URL = "https://places.googleapis.com/v1/places:searchNearby"
    private const val MAX_AGE = 30 * 60 * 1000L
    private fun file(ctx: Context) = File(ctx.filesDir, "google.json")

    data class Result(
        val places: List<GooglePlace>, val at: Long, val lat: Double, val lng: Double, val radius: Int,
        /** What Google actually sent about fuel, for the test line in settings. */
        val withFuelOptions: Int = 0, val fuelTypes: List<String> = emptyList(), val currencies: List<String> = emptyList(),
    )

    private fun diagnose(raw: String): Triple<Int, List<String>, List<String>> {
        val withFuel = Regex("\"fuelOptions\"").findAll(raw).count()
        val types = Regex("\"type\"\\s*:\\s*\"([A-Z0-9_]+)\"").findAll(raw).map { it.groupValues[1] }.distinct().toList()
        val cur = Regex("\"currencyCode\"\\s*:\\s*\"([A-Z]+)\"").findAll(raw).map { it.groupValues[1] }.distinct().toList()
        return Triple(withFuel, types, cur)
    }

    fun read(ctx: Context): Result? = runCatching {
        val o = JSONObject(file(ctx).readText())
        Result(Parse.googlePlaces(o.getString("raw")), o.getLong("at"), o.getDouble("lat"), o.getDouble("lng"), o.getInt("radius"))
    }.getOrNull()

    fun fresh(r: Result?, lat: Double, lng: Double, radius: Int): Boolean =
        r != null && System.currentTimeMillis() - r.at < MAX_AGE && r.radius == radius &&
            Rank.distanceM(lat, lng, r.lat, r.lng) < 1000

    suspend fun fetch(ctx: Context, key: String, lat: Double, lng: Double, radius: Int): Result {
        val body = JSONObject()
            .put("includedTypes", JSONArray().put("gas_station"))
            .put("maxResultCount", 20)
            .put("rankPreference", "DISTANCE")
            .put("languageCode", "no")
            .put("locationRestriction", JSONObject().put("circle", JSONObject()
                .put("center", JSONObject().put("latitude", lat).put("longitude", lng))
                .put("radius", radius.coerceIn(1000, 50_000).toDouble())))
        val raw = Http.post(URL, body.toString(), "application/json", headers = mapOf(
            "X-Goog-Api-Key" to key,
            "X-Goog-FieldMask" to "places.id,places.displayName,places.shortFormattedAddress,places.location,places.fuelOptions",
        ))
        val now = System.currentTimeMillis()
        file(ctx).writeText(JSONObject().put("raw", raw).put("at", now).put("lat", lat).put("lng", lng).put("radius", radius).toString())
        val (n, types, cur) = diagnose(raw)
        return Result(Parse.googlePlaces(raw), now, lat, lng, radius, n, types, cur)
    }

    fun clear(ctx: Context) { file(ctx).delete() }
}

/** Prices you typed in yourself, kept only on this phone. */
object MyPrices {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("my_prices", Context.MODE_PRIVATE)

    fun load(ctx: Context): PriceBook {
        val arr = runCatching { JSONArray(prefs(ctx).getString("all", "[]")) }.getOrDefault(JSONArray())
        val book = HashMap<String, HashMap<FuelType, FuelPrice>>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val fuel = FuelType.fromKey(o.optString("fuel")) ?: continue
            book.getOrPut(o.optString("station")) { HashMap() }[fuel] =
                FuelPrice(o.optDouble("price"), o.optLong("at"), 1, PriceSource.ME)
        }
        return book
    }

    fun put(ctx: Context, stationId: String, fuel: FuelType, price: Double) =
        save(ctx, entries(ctx).filterNot { it.first == stationId && it.second == fuel } +
            Entry(stationId, fuel, price, System.currentTimeMillis()))

    fun remove(ctx: Context, stationId: String, fuel: FuelType) =
        save(ctx, entries(ctx).filterNot { it.first == stationId && it.second == fuel })

    private data class Entry(val first: String, val second: FuelType, val price: Double, val at: Long)

    private fun entries(ctx: Context): List<Entry> = load(ctx).flatMap { (id, fuels) ->
        fuels.map { (f, p) -> Entry(id, f, p.price, p.updatedAt ?: 0L) }
    }

    private fun save(ctx: Context, list: List<Entry>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("station", it.first).put("fuel", it.second.key).put("price", it.price).put("at", it.at))
        }
        prefs(ctx).edit().putString("all", arr.toString()).apply()
    }
}

/** Choices remembered between launches. */
object Prefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun fuel(ctx: Context) = FuelType.fromKey(p(ctx).getString("fuel", null)) ?: FuelType.PETROL95
    fun setFuel(ctx: Context, f: FuelType) = p(ctx).edit().putString("fuel", f.key).apply()

    fun radiusKm(ctx: Context) = p(ctx).getInt("radius", 10)
    fun setRadiusKm(ctx: Context, km: Int) = p(ctx).edit().putInt("radius", km).apply()

    fun googleKey(ctx: Context) = p(ctx).getString("google_key", "").orEmpty()
    fun setGoogleKey(ctx: Context, k: String) = p(ctx).edit().putString("google_key", k.trim()).apply()

    fun sort(ctx: Context) = runCatching { SortBy.valueOf(p(ctx).getString("sort", "PRICE")!!) }.getOrDefault(SortBy.PRICE)
    fun setSort(ctx: Context, s: SortBy) = p(ctx).edit().putString("sort", s.name).apply()
}
