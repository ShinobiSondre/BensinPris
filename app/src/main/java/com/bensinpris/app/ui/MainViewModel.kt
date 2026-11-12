package com.bensinpris.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bensinpris.app.data.CachedFeed
import com.bensinpris.app.data.ChainStore
import com.bensinpris.app.data.WebStore
import com.bensinpris.app.data.FuelType
import com.bensinpris.app.data.MyPrices
import com.bensinpris.app.data.Parse
import com.bensinpris.app.data.PriceBook
import com.bensinpris.app.data.Prefs
import com.bensinpris.app.data.Rank
import com.bensinpris.app.data.SortBy
import com.bensinpris.app.data.Spot
import com.bensinpris.app.data.Station
import com.bensinpris.app.data.Summary
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

data class Here(val lat: Double, val lng: Double, val isFallback: Boolean)

data class UiState(
    val fuel: FuelType,
    val radiusKm: Int,
    val sort: SortBy,
    val needsPermission: Boolean = false,
    val locating: Boolean = true,
    val here: Here? = null,
    val place: String? = null,
    val stationCount: Int = 0,
    val spots: List<Spot> = emptyList(),
    val summary: Summary? = null,
    val refreshing: Boolean = false,
    /** True when the last download failed and older data is shown. */
    val offline: Boolean = false,
    val pricesFetchedAt: Long = 0L,
    val chainsFetchedAt: Long = 0L,
    val selectedId: String? = null,
) {
    val selected: Spot? get() = selectedId?.let { id -> spots.firstOrNull { it.station.id == id } }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Application get() = getApplication()

    private val _state = MutableStateFlow(UiState(Prefs.fuel(app), Prefs.radiusKm(app), Prefs.sort(app)))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var stations: List<Station> = emptyList()
    private var community: PriceBook = emptyMap()
    private var mine: PriceBook = emptyMap()
    private var chains: ChainStore.Snapshot = ChainStore.Snapshot(emptyList(), 0L)
    /** Website prices per town, for the towns around you. */
    private val web = HashMap<String, WebStore.Town>()
    private var webJob: kotlinx.coroutines.Job? = null
    private var book: PriceBook = emptyMap()

    init {
        viewModelScope.launch {
            loadFromPhone()
            refresh(force = false)
        }
    }

    // ---- data ----

    private suspend fun loadFromPhone() {
        withContext(Dispatchers.IO) {
            stations = CachedFeed.STATIONS.read(ctx)?.let(Parse::stations).orEmpty()
            community = CachedFeed.PRICES.read(ctx)?.let(Parse::prices).orEmpty()
            mine = MyPrices.load(ctx)
            chains = ChainStore.read(ctx)
        }
        rebuild()
    }

    /** Downloads fresh stations and prices when they are old (or always, if [force]). */
    fun refresh(force: Boolean) {
        if (_state.value.refreshing) return
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            var failed = false
            withContext(Dispatchers.IO) {
                if (force || CachedFeed.STATIONS.isStale(ctx) || stations.isEmpty()) {
                    runCatching { CachedFeed.STATIONS.download(ctx) { Parse.stations(it).size > 100 } }
                        .onSuccess { stations = Parse.stations(it) }
                        .onFailure { failed = true }
                }
                if (force || ChainStore.isStale(ctx)) {
                    runCatching { ChainStore.download(ctx) }
                        .onSuccess { chains = it }
                        .onFailure { failed = true }
                }
                if (force || CachedFeed.PRICES.isStale(ctx)) {
                    runCatching { CachedFeed.PRICES.download(ctx) { t -> t.contains("\"prices\"") } }
                        .onSuccess { community = Parse.prices(it) }
                        .onFailure { failed = true }
                }
            }
            if (force) locate()
            rebuild()
            _state.update { it.copy(refreshing = false, offline = failed) }
            loadTowns(force)
        }
    }

    private fun rebuild() {
        val webBook = Parse.merge(*web.values.map { t -> Parse.matchWeb(stations, t.stations, chains.chains, t.at) }.toTypedArray())
        book = Parse.merge(Parse.estimates(stations, chains.chains, chains.at), webBook, community, mine)
        _state.update {
            it.copy(stationCount = stations.size, pricesFetchedAt = CachedFeed.PRICES.fetchedAt(ctx), chainsFetchedAt = chains.at)
        }
        recompute()
    }

    private fun recompute() {
        val s = _state.value
        val here = s.here ?: return
        val spots = Rank.spots(stations, book, s.fuel, here.lat, here.lng, s.radiusKm * 1000.0, s.sort)
        _state.update { it.copy(spots = spots, summary = Rank.summary(spots)) }
    }

    /** Station prices from the price website for the towns around you (cached 3 hours per town). */
    private fun loadTowns(force: Boolean) {
        val towns = Parse.nearbyTowns(_state.value.spots)
        if (towns.isEmpty()) return
        webJob?.cancel()
        webJob = viewModelScope.launch {
            var changed = false
            for (town in towns) {
                val key = town.lowercase()
                val cached = withContext(Dispatchers.IO) { WebStore.read(ctx, town) }
                if (cached != null && web[key] == null) { web[key] = cached; changed = true }
                if (force || withContext(Dispatchers.IO) { WebStore.isStale(ctx, town) }) {
                    withContext(Dispatchers.IO) { runCatching { WebStore.download(ctx, town) }.getOrNull() }
                        ?.let { web[key] = it; changed = true }
                }
            }
            if (changed) rebuild()
        }
    }

    // ---- location ----

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            _state.update { it.copy(needsPermission = false) }
            viewModelScope.launch { locate() }
        } else if (_state.value.here == null) {
            _state.update { it.copy(needsPermission = true, locating = false) }
        }
    }

    /** Used when you'd rather not share your location. */
    fun useOslo() {
        _state.update { it.copy(needsPermission = false, locating = false, here = Here(OSLO_LAT, OSLO_LNG, true), place = "Oslo sentrum") }
        recompute()
        loadTowns(false)
    }

    @SuppressLint("MissingPermission")
    private suspend fun locate() {
        if (!hasPermission()) return
        _state.update { it.copy(locating = true) }
        val client = LocationServices.getFusedLocationProviderClient(ctx)
        // A recent fix first, so the list shows up at once; then a fresh one.
        runCatching { client.lastLocation.await() }.getOrNull()?.let { setHere(it.latitude, it.longitude) }
        val fresh = withTimeoutOrNull(15_000) {
            runCatching {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token).await()
            }.getOrNull()
        }
        if (fresh != null) setHere(fresh.latitude, fresh.longitude)
        if (_state.value.here == null) useOslo()
        _state.update { it.copy(locating = false) }
    }

    private suspend fun setHere(lat: Double, lng: Double) {
        _state.update { it.copy(here = Here(lat, lng, false)) }
        recompute()
        loadTowns(force = false)
        val place = withContext(Dispatchers.IO) { placeName(lat, lng) }
        if (place != null) _state.update { it.copy(place = place) }
    }

    @Suppress("DEPRECATION")
    private fun placeName(lat: Double, lng: Double): String? = runCatching {
        if (!Geocoder.isPresent()) return null
        val a = Geocoder(ctx, Locale.forLanguageTag("nb-NO")).getFromLocation(lat, lng, 1)?.firstOrNull() ?: return null
        listOfNotNull(a.subLocality, a.locality ?: a.subAdminArea).distinct().joinToString(", ").ifBlank { null }
    }.getOrNull()

    // ---- choices ----

    fun setFuel(f: FuelType) { Prefs.setFuel(ctx, f); _state.update { it.copy(fuel = f) }; recompute() }
    fun setRadius(km: Int) { Prefs.setRadiusKm(ctx, km); _state.update { it.copy(radiusKm = km) }; recompute(); loadTowns(false) }
    fun setSort(s: SortBy) { Prefs.setSort(ctx, s); _state.update { it.copy(sort = s) }; recompute() }

    fun select(id: String?) = _state.update { it.copy(selectedId = id) }

    fun saveMyPrice(stationId: String, fuel: FuelType, price: Double) {
        MyPrices.put(ctx, stationId, fuel, price)
        mine = MyPrices.load(ctx)
        rebuild()
    }

    fun removeMyPrice(stationId: String, fuel: FuelType) {
        MyPrices.remove(ctx, stationId, fuel)
        mine = MyPrices.load(ctx)
        rebuild()
    }

    companion object {
        const val OSLO_LAT = 59.9133
        const val OSLO_LNG = 10.7389
        val RADII_KM = listOf(5, 10, 25, 50)
    }
}
