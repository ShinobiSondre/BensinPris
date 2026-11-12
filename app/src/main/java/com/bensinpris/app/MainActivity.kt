package com.bensinpris.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bensinpris.app.data.FuelRepo
import com.bensinpris.app.data.Fuel
import com.bensinpris.app.data.FuelType
import com.bensinpris.app.data.StationPrice
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { BensinPrisScreen() }
        }
    }
}

private const val FALLBACK_LAT = 59.9133 // Oslo sentrum, only when no location is available
private const val FALLBACK_LNG = 10.7389
private val RADIUS_CHOICES_KM = listOf(5, 10, 25)

private fun norsk(): Boolean = Locale.getDefault().language in listOf("nb", "no", "nn")
private fun tr(en: String, no: String) = if (norsk()) no else en

sealed interface UiState {
    data object Idle : UiState
    data object Loading : UiState
    data class Done(val result: FuelRepo.Result, val usedFallback: Boolean) : UiState
    data class Failed(val message: String) : UiState
}

@SuppressLint("MissingPermission")
@Composable
fun BensinPrisScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var fuel by remember { mutableStateOf(FuelType.PETROL95) }
    var radiusKm by remember { mutableStateOf(10) }
    var state by remember { mutableStateOf<UiState>(UiState.Idle) }

    fun search(lat: Double, lng: Double, fallback: Boolean) {
        scope.launch {
            state = UiState.Loading
            state = runCatching { FuelRepo.nearby(lat, lng, radiusKm * 1000, fuel) }
                .fold(
                    onSuccess = { UiState.Done(it, fallback) },
                    onFailure = { UiState.Failed(it.message ?: "unknown") },
                )
        }
    }

    fun locateAndSearch() {
        val fused = LocationServices.getFusedLocationProviderClient(ctx)
        fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { loc ->
                if (loc != null) search(loc.latitude, loc.longitude, false)
                else search(FALLBACK_LAT, FALLBACK_LNG, true)
            }
            .addOnFailureListener { search(FALLBACK_LAT, FALLBACK_LNG, true) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) locateAndSearch()
        else search(FALLBACK_LAT, FALLBACK_LNG, true)
    }

    fun start() {
        val hasLocation = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (hasLocation) locateAndSearch()
        else permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    LaunchedEffect(Unit) { start() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("BensinPris", style = MaterialTheme.typography.headlineMedium)
            Text(
                tr("Cheapest fuel near you", "Billigst drivstoff i nærheten"),
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer8()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FuelType.entries.forEach { f ->
                    FilterChip(
                        selected = fuel == f,
                        onClick = {
                            fuel = f
                            if (state is UiState.Done) start()
                        },
                        label = { Text(f.label) },
                    )
                }
            }
            Spacer8()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RADIUS_CHOICES_KM.forEach { km ->
                    FilterChip(
                        selected = radiusKm == km,
                        onClick = {
                            radiusKm = km
                            if (state is UiState.Done) start()
                        },
                        label = { Text("$km km") },
                    )
                }
            }
            Spacer8()

            when (val s = state) {
                UiState.Idle, UiState.Loading -> Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Text(tr("Looking for stations…", "Leter etter stasjoner…"))
                }

                is UiState.Failed -> Column {
                    Text(tr("Could not search: ", "Kunne ikke søke: ") + s.message)
                    Button(onClick = { start() }) { Text(tr("Try again", "Prøv igjen")) }
                }

                is UiState.Done -> ResultList(s, ctx)
            }
        }
    }
}

@Composable
private fun Spacer8() = androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))

@Composable
private fun ResultList(done: UiState.Done, ctx: android.content.Context) {
    val r = done.result
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (done.usedFallback) {
            item {
                Text(tr("Location not available, showing Oslo sentrum.", "Fant ikke posisjonen, viser Oslo sentrum."))
            }
        }
        if (!r.pricesAvailable) {
            item {
                Text(tr("No community prices could be loaded. Stations are shown without prices.",
                    "Fikk ikke lastet prisene. Stasjonene vises uten pris."))
            }
        }
        r.stationsError?.let { item { Text(tr("Station list failed: $it", "Stasjonslisten feilet: $it")) } }
        if (r.rows.isEmpty() && r.stationsError == null) {
            item { Text(tr("No fuel stations within this distance.", "Ingen bensinstasjoner innen valgt avstand.")) }
        }
        items(r.rows) { row -> StationCard(row, ctx) }
    }
}

@Composable
private fun StationCard(row: StationPrice, ctx: android.content.Context) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(row.station.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    row.price?.let { String.format(Locale.US, "%.2f kr", it) } ?: tr("no price", "ingen pris"),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(String.format(Locale.US, "%.1f km", row.distanceM / 1000.0))
            TextButton(onClick = {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Fuel.mapsDirectionsUrl(row.station))))
            }) {
                Text(tr("Open in Google Maps", "Åpne i Google Maps"))
            }
        }
    }
}
