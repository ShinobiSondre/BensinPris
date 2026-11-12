package com.bensinpris.app

import com.bensinpris.app.data.Format
import com.bensinpris.app.data.Freshness
import com.bensinpris.app.data.FuelPrice
import com.bensinpris.app.data.FuelType
import com.bensinpris.app.data.Parse
import com.bensinpris.app.data.PriceSource
import com.bensinpris.app.data.Rank
import com.bensinpris.app.data.SortBy
import com.bensinpris.app.data.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelTest {
    private val oslo = 59.9133 to 10.7389

    private fun st(id: String, lat: Double, lng: Double = 10.7389) = Station(id, "Station $id", "Brand", "Gate 1", "Oslo", lat, lng)
    private fun p(v: Double, at: Long?, src: PriceSource = PriceSource.COMMUNITY) = FuelPrice(v, at, 1, src)

    private val near = st("osm_1", 59.9140)   // ~80 m
    private val mid = st("osm_2", 59.9230)    // ~1.1 km
    private val far = st("ck_3", 59.9500)     // ~4.1 km
    private val stations = listOf(near, mid, far)

    // ---- parsing: the real shapes of stations.json and prices.json ----

    @Test fun parses_stations_file() {
        val json = """
            {"exportedAt":"2026-11-12T23:19:00+00:00","count":2,"stations":[
              {"city":"Hokksund","brand":"Automat1","latitude":59.7845634,"address":"Hasselbakken 2","id":"automat1_10","name":"Automat1 Hokksund","longitude":9.9083118},
              {"city":"X","brand":"DRIV","latitude":59.0,"address":"","id":"osm_9","name":"","longitude":10.0},
              {"city":"Bad","brand":"Esso","address":"No coords","id":"osm_10","name":"Esso"}
            ]}
        """.trimIndent()
        val s = Parse.stations(json)
        assertEquals(2, s.size)
        assertEquals("automat1_10", s[0].id)
        assertEquals("Hasselbakken 2", s[0].address)
        assertEquals("Driv", s[1].brand)
        assertEquals("Driv", s[1].name) // falls back to the brand when the name is empty
    }

    @Test fun parses_prices_file_keeping_newest_and_dropping_junk() {
        val json = """
            {"exportedAt":"2026-11-12T14:49:24Z","count":5,"prices":[
              {"updatedAt":"2026-04-18T12:51:02.310296","fuelType":"diesel","stationId":"osm_1","price":20.99,"reportCount":4},
              {"updatedAt":"2026-04-18T12:51:01.555862","fuelType":"petrol95","stationId":"osm_1","price":17.99,"reportCount":3},
              {"updatedAt":"2026-03-01T10:00:00","fuelType":"petrol95","stationId":"osm_1","price":16.00,"reportCount":1},
              {"updatedAt":"2026-04-19T08:00:00","fuelType":"hydrogen","stationId":"osm_5","price":90.0,"reportCount":1},
              {"updatedAt":"2026-04-19T08:00:00","fuelType":"diesel","stationId":"osm_6","price":2.5,"reportCount":1}
            ]}
        """.trimIndent()
        val b = Parse.prices(json)
        assertEquals(setOf("osm_1"), b.keys)
        assertEquals(17.99, b["osm_1"]!![FuelType.PETROL95]!!.price, 0.0)
        assertEquals(3, b["osm_1"]!![FuelType.PETROL95]!!.reports)
        assertEquals(20.99, b["osm_1"]!![FuelType.DIESEL]!!.price, 0.0)
    }

    @Test fun times_without_zone_are_utc() {
        assertEquals(1776516662310L, Parse.time("2026-04-18T12:51:02.310296"))
        assertEquals(1776516662000L, Parse.time("2026-04-18T14:51:02+02:00"))
        assertNull(Parse.time("not a time"))
    }

    @Test fun my_newer_price_wins_and_older_does_not() {
        val community = mapOf("osm_1" to mapOf(FuelType.PETROL95 to p(19.0, 2_000)))
        val newer = mapOf("osm_1" to mapOf(FuelType.PETROL95 to p(18.5, 3_000, PriceSource.ME)))
        val older = mapOf("osm_1" to mapOf(FuelType.PETROL95 to p(18.5, 1_000, PriceSource.ME)))
        assertEquals(18.5, Parse.merge(community, newer)["osm_1"]!![FuelType.PETROL95]!!.price, 0.0)
        assertEquals(19.0, Parse.merge(community, older)["osm_1"]!![FuelType.PETROL95]!!.price, 0.0)
    }

    // ---- ranking ----

    @Test fun cheapest_first_unpriced_last_within_radius() {
        val book = mapOf(
            "osm_1" to mapOf(FuelType.PETROL95 to p(20.5, 1)),
            "osm_2" to mapOf(FuelType.PETROL95 to p(19.9, 1)),
        )
        val all = Rank.spots(stations, book, FuelType.PETROL95, oslo.first, oslo.second, 10_000.0, SortBy.PRICE)
        assertEquals(listOf("osm_2", "osm_1", "ck_3"), all.map { it.station.id })
        assertNull(all.last().price)

        val within3km = Rank.spots(stations, book, FuelType.PETROL95, oslo.first, oslo.second, 3_000.0, SortBy.PRICE)
        assertEquals(listOf("osm_2", "osm_1"), within3km.map { it.station.id })

        val byDistance = Rank.spots(stations, book, FuelType.PETROL95, oslo.first, oslo.second, 10_000.0, SortBy.DISTANCE)
        assertEquals(listOf("osm_1", "osm_2", "ck_3"), byDistance.map { it.station.id })
    }

    @Test fun other_fuel_does_not_count_as_a_price() {
        val book = mapOf("osm_1" to mapOf(FuelType.DIESEL to p(21.0, 1)))
        val spots = Rank.spots(stations, book, FuelType.PETROL95, oslo.first, oslo.second, 10_000.0, SortBy.PRICE)
        assertNull(spots.first { it.station.id == "osm_1" }.price)
        assertEquals(21.0, spots.first { it.station.id == "osm_1" }.allPrices[FuelType.DIESEL]!!.price, 0.0)
    }

    @Test fun summary_finds_cheapest_median_and_saving() {
        val book = mapOf(
            "osm_1" to mapOf(FuelType.PETROL95 to p(21.0, 1)),
            "osm_2" to mapOf(FuelType.PETROL95 to p(19.0, 1)),
            "ck_3" to mapOf(FuelType.PETROL95 to p(20.0, 1)),
        )
        val sum = Rank.summary(Rank.spots(stations, book, FuelType.PETROL95, oslo.first, oslo.second, 10_000.0, SortBy.DISTANCE))
        assertEquals("osm_2", sum.cheapest!!.station.id)
        assertEquals(20.0, sum.median!!, 1e-9)
        assertEquals(1.0, sum.savingPerLitre, 1e-9)
        assertEquals(3, sum.priced)
    }

    @Test fun no_saving_claimed_from_a_single_price() {
        val book = mapOf("osm_1" to mapOf(FuelType.PETROL95 to p(21.0, 1)))
        val sum = Rank.summary(Rank.spots(stations, book, FuelType.PETROL95, oslo.first, oslo.second, 10_000.0, SortBy.PRICE))
        assertEquals(0.0, sum.savingPerLitre, 0.0)
    }

    @Test fun distance_is_about_right() {
        assertEquals(111.0, Rank.distanceM(59.0, 10.0, 59.001, 10.0), 2.0)
    }

    @Test fun maps_links() {
        val s = Station("x", "Circle K Sinsen", "Circle K", "Sinsenveien 1", "Oslo", 59.914, 10.7389)
        assertEquals("https://www.google.com/maps/dir/?api=1&destination=59.914,10.7389&travelmode=driving", Rank.directionsUrl(s))
        assertTrue(Rank.placeUrl(s).startsWith("https://www.google.com/maps/search/?api=1&query=Circle+K+Sinsen"))
    }

    // ---- formatting ----

    @Test fun norwegian_numbers_and_distances() {
        assertEquals("17,99", Format.price(17.99, true))
        assertEquals("17.99", Format.price(17.99, false))
        assertEquals("80 m", Format.distance(83.0, true))
        assertEquals("1,2 km", Format.distance(1234.0, true))
        assertEquals("23 km", Format.distance(23_400.0, true))
    }

    @Test fun ages_and_freshness() {
        val now = 1_000L * Format.DAY
        assertEquals("nå nettopp", Format.age(now - 10 * 60_000, now, true))
        assertEquals("5 t siden", Format.age(now - 5 * Format.HOUR, now, true))
        assertEquals("i går", Format.age(now - 30 * Format.HOUR, now, true))
        assertEquals("3 uker siden", Format.age(now - 21 * Format.DAY, now, true))
        assertEquals("6 mnd siden", Format.age(now - 185 * Format.DAY, now, true))
        assertEquals("6 months ago", Format.age(now - 185 * Format.DAY, now, false))
        assertEquals(Freshness.FRESH, Format.freshness(now - Format.HOUR, now))
        assertEquals(Freshness.RECENT, Format.freshness(now - 5 * Format.DAY, now))
        assertEquals(Freshness.OLD, Format.freshness(now - 30 * Format.DAY, now))
    }

    // ---- typical chain prices ----

    private val chainHtml = """
        <table class="w-full"><caption class="px-4">Kjedene i dag</caption><thead><tr class="b"><th scope="col">Kjede</th>
        <th scope="col" class="r">Bensin 95</th><th scope="col" class="r">Diesel</th></tr></thead><tbody class="tabular-nums">
        <tr class="b"><th scope="row" class="x"><a class="u" href="/drivstoffpriser/automat-1">Automat 1</a></th><td class="r"><span class="font-bold text-ink">21,07</span></td><td class="r"><span class="font-bold">25,05</span></td></tr>
        <tr class="b"><th scope="row"><a href="/drivstoffpriser/uno-x">Uno-X</a></th><td><span>22,31</span></td><td><span>25,39</span></td></tr>
        <tr class="b"><th scope="row"><a href="/drivstoffpriser/circle-k">Circle K Automat</a></th><td><span>22,62</span></td><td><span>25,77</span></td></tr>
        <tr class="b"><th scope="row"><a href="/drivstoffpriser/circle-k">Circle K</a></th><td><span>22,69</span></td><td><span>25,94</span></td></tr>
        <tr class="b"><th scope="row"><a href="/drivstoffpriser/st1">ST1</a></th><td><span>22,79</span></td><td><span>–</span></td></tr>
        </tbody></table>
    """.trimIndent()

    @Test fun parses_chain_table() {
        val c = Parse.chainPrices(chainHtml)
        assertEquals(listOf("Automat 1", "Uno-X", "Circle K Automat", "Circle K", "ST1"), c.map { it.name })
        assertEquals(21.07, c[0].prices[FuelType.PETROL95]!!, 0.0)
        assertEquals(25.05, c[0].prices[FuelType.DIESEL]!!, 0.0)
        assertNull(c[4].prices[FuelType.DIESEL])
    }

    @Test fun matches_stations_to_chains_and_variants() {
        val c = Parse.chainPrices(chainHtml)
        fun s(brand: String, name: String) = Station("x", name, brand, "", "", 59.0, 10.0)
        assertEquals("Uno-X", Parse.chainFor(s("Uno-X", "Uno-X Rafnes"), c)?.name)
        assertEquals("Circle K Automat", Parse.chainFor(s("Circle K", "Circle K Automat Pors"), c)?.name)
        assertEquals("Circle K", Parse.chainFor(s("Circle K", "Circle K E18 Grelland"), c)?.name)
        assertEquals("Automat 1", Parse.chainFor(s("Automat1", "Automat1 Skien"), c)?.name)
        assertEquals("ST1", Parse.chainFor(s("St1", "St1 Pors"), c)?.name)
        assertNull(Parse.chainFor(s("Esso", "Esso Brua"), c))
        assertNull(Parse.chainFor(s("Uno-X", ""), emptyList()))
    }

    @Test fun recent_station_price_beats_chain_estimate_but_old_one_does_not() {
        val now = 1_000L * Format.DAY
        val chain = mapOf("osm_1" to mapOf(FuelType.PETROL95 to FuelPrice(22.31, now, 0, PriceSource.CHAIN, "Uno-X")))
        val yesterday = mapOf("osm_1" to mapOf(FuelType.PETROL95 to p(21.5, now - Format.DAY, PriceSource.ME)))
        val months = mapOf("osm_1" to mapOf(FuelType.PETROL95 to p(17.99, now - 180 * Format.DAY)))
        assertEquals(21.5, Parse.merge(chain, yesterday)["osm_1"]!![FuelType.PETROL95]!!.price, 0.0)
        val m = Parse.merge(chain, months)["osm_1"]!![FuelType.PETROL95]!!
        assertTrue(m.isEstimate)
        assertEquals(22.31, m.price, 0.0)
    }
}
