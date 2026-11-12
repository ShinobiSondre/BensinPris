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

    private val near = Station("1", "Circle K Grünerløkka", "Circle K", 59.9140, 10.7389)
    private val mid = Station("2", "YX Sinsen", "YX", 59.9230, 10.7389)
    private val far = Station("3", "Uno-X Ullevål", "Uno-X", 59.9500, 10.7389)
    private val stations = listOf(near, mid, far)

    @Test
    fun distance_is_about_right() {
        // 0.001 degrees of latitude is roughly 111 m
        assertEquals(111.0, Fuel.distanceM(59.0, 10.0, 59.001, 10.0), 2.0)
    }

    @Test
    fun cheapest_first_then_unpriced_and_radius_is_respected() {
        val reports = Fuel.byStation(listOf(
            PriceReport("1", mapOf(FuelType.PETROL95 to 20.5), null),
            PriceReport("2", mapOf(FuelType.PETROL95 to 19.9), null),
        ))
        val rows = Fuel.rank(stations, reports, FuelType.PETROL95, here.first, here.second, 10_000.0)
        assertEquals(listOf("2", "1", "3"), rows.map { it.station.id })
        assertNull(rows.last().price)

        val within3km = Fuel.rank(stations, reports, FuelType.PETROL95, here.first, here.second, 3_000.0)
        assertEquals(listOf("2", "1"), within3km.map { it.station.id })
    }

    @Test
    fun a_fuel_without_a_price_is_unpriced_for_that_station() {
        val reports = Fuel.byStation(listOf(PriceReport("1", mapOf(FuelType.DIESEL to 21.0), null)))
        val rows = Fuel.rank(stations, reports, FuelType.PETROL95, here.first, here.second, 10_000.0)
        assertNull(rows.first { it.station.id == "1" }.price)
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
              {"type":"node","id":1118788394,"lat":59.9,"lon":10.7,"tags":{"amenity":"fuel","brand":"YX","name":"YX Sentrum"}},
              {"type":"way","id":2,"center":{"lat":59.8,"lon":10.6},"tags":{"amenity":"fuel","operator":"Esso"}}
            ]}
        """.trimIndent()
        val s = OsmStations.parse(json)
        assertEquals("1118788394", s[0].id)
        assertEquals("YX Sentrum", s[0].name)
        assertEquals("Esso", s[1].name)
        assertEquals(59.8, s[1].lat, 0.0)
    }

    @Test
    fun parses_sample_export_merging_fuel_rows_per_station() {
        val json = """
            {"exportedAt":"2026-11-12T14:49:24Z","count":3,"prices":[
              {"updatedAt":"2026-04-18T12:51:02.310296","fuelType":"diesel","stationId":"osm_1118788394","price":20.99,"reportCount":4},
              {"updatedAt":"2026-04-18T12:51:01.555862","fuelType":"petrol95","stationId":"osm_1118788394","price":17.99,"reportCount":3},
              {"updatedAt":"2026-04-19T08:00:00","fuelType":"fuel_unknown","stationId":"osm_5","price":9.0,"reportCount":1}
            ]}
        """.trimIndent()
        val r = CommunityPrices.parse(json)
        assertEquals(1, r.size)
        assertEquals("1118788394", r[0].stationId)
        assertEquals(17.99, r[0].prices[FuelType.PETROL95]!!, 0.0)
        assertEquals(20.99, r[0].prices[FuelType.DIESEL]!!, 0.0)
        assertNull(r[0].prices[FuelType.PETROL98])
    }
}
