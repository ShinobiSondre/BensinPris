package com.bensinpris.app

import com.bensinpris.app.data.CommunityPrices
import com.bensinpris.app.data.Fuel
import com.bensinpris.app.data.FuelType
import com.bensinpris.app.data.OsmStations
import com.bensinpris.app.data.PriceReport
import com.bensinpris.app.data.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelTest {
    private val here = 59.9133 to 10.7389

    private val near = Station("node/1", "Circle K Grünerløkka", "Circle K", 59.9140, 10.7389)
    private val mid = Station("node/2", "YX Sinsen", "YX", 59.9230, 10.7389)
    private val far = Station("node/3", "Uno-X Ullevål", "Uno-X", 59.9500, 10.7389)
    private val stations = listOf(near, mid, far)

    @Test
    fun distance_is_about_right() {
        // 0.001 degrees of latitude is roughly 111 m
        val d = Fuel.distanceM(59.0, 10.0, 59.001, 10.0)
        assertEquals(111.0, d, 2.0)
    }

    @Test
    fun report_matches_nearest_station_within_60m() {
        val reports = listOf(
            PriceReport(59.91401, 10.7389, "Circle K", mapOf(FuelType.PETROL95 to 19.5), 1_000L),
            PriceReport(60.5, 11.0, "Nowhere", mapOf(FuelType.PETROL95 to 15.0), 1_000L),
        )
        val matched = Fuel.matchReports(stations, reports)
        assertEquals(setOf("node/1"), matched.keys)
    }

    @Test
    fun newest_report_wins_for_a_station() {
        val reports = listOf(
            PriceReport(59.9140, 10.7389, "", mapOf(FuelType.PETROL95 to 20.0), 1_000L),
            PriceReport(59.9140, 10.7389, "", mapOf(FuelType.PETROL95 to 19.0), 2_000L),
        )
        assertEquals(19.0, Fuel.matchReports(stations, reports)["node/1"]!!.prices[FuelType.PETROL95]!!, 0.0)
    }

    @Test
    fun cheapest_first_then_unpriced_by_distance_and_radius_is_respected() {
        val reports = mapOf(
            "node/1" to PriceReport(0.0, 0.0, "", mapOf(FuelType.PETROL95 to 20.5), null),
            "node/2" to PriceReport(0.0, 0.0, "", mapOf(FuelType.PETROL95 to 19.9), null),
        )
        val rows = Fuel.rank(stations, reports, FuelType.PETROL95, here.first, here.second, 10_000.0)
        assertEquals(listOf("node/2", "node/1", "node/3"), rows.map { it.station.id })
        assertNull(rows.last().price)

        val within3km = Fuel.rank(stations, reports, FuelType.PETROL95, here.first, here.second, 3_000.0)
        assertEquals(listOf("node/2", "node/1"), within3km.map { it.station.id })
    }

    @Test
    fun maps_link_points_at_station() {
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=59.914,10.7389&travelmode=driving",
            Fuel.mapsDirectionsUrl(Station("x", "x", "x", 59.914, 10.7389)),
        )
    }

    @Test
    fun parses_overpass_nodes_and_ways() {
        val json = """
            {"elements":[
              {"type":"node","id":1,"lat":59.9,"lon":10.7,"tags":{"amenity":"fuel","brand":"YX","name":"YX Sentrum"}},
              {"type":"way","id":2,"center":{"lat":59.8,"lon":10.6},"tags":{"amenity":"fuel","operator":"Esso"}}
            ]}
        """.trimIndent()
        val s = OsmStations.parse(json)
        assertEquals(2, s.size)
        assertEquals("YX Sentrum", s[0].name)
        assertEquals("Esso", s[1].name) // falls back to operator when there is no name
        assertEquals(59.8, s[1].lat, 0.0)
    }

    @Test
    fun parses_price_feed_and_drops_entries_without_prices() {
        val json = """
            {"stations":[
              {"lat":59.9,"lng":10.7,"brand":"YX","petrol95":19.9,"diesel":20.1,"updatedAt":"2026-11-12T10:00:00Z"},
              {"lat":59.8,"lng":10.6,"brand":"Esso"}
            ]}
        """.trimIndent()
        val r = CommunityPrices.parse(json)
        assertEquals(1, r.size)
        assertEquals(19.9, r[0].prices[FuelType.PETROL95]!!, 0.0)
        assertNull(r[0].prices[FuelType.PETROL98])
    }
}
