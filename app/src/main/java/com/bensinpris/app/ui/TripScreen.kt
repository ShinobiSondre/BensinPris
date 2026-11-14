@file:OptIn(ExperimentalMaterial3Api::class)

package com.bensinpris.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bensinpris.app.data.Format
import com.bensinpris.app.data.FuelType
import com.bensinpris.app.data.Trip
import com.bensinpris.app.data.TripSpot

private val CORRIDORS_KM = listOf(1, 2, 5)

/** Plan a drive from A to B and find the cheapest fuel along the way. */
@Composable
fun TripScreen(s: UiState, vm: MainViewModel, no: Boolean, now: Long, pad: PaddingValues) {
    val t = s.trip
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    val go = { focus.clearFocus(); vm.planTrip() }

    Column(Modifier.fillMaxSize().padding(pad)) {
        // ---- where from, where to ----
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            Text(tr(no, "Trip", "Tur"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = t.fromText, onValueChange = { vm.setTripText(from = it) },
                        placeholder = { Text(tr(no, "My location", "Min posisjon")) },
                        leadingIcon = { Icon(Icons.Filled.MyLocation, null, tint = MaterialTheme.colorScheme.primary) },
                        singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        value = t.toText, onValueChange = { vm.setTripText(to = it) },
                        placeholder = { Text(tr(no, "Where to? (e.g. Bergen)", "Hvor skal du? (f.eks. Bergen)")) },
                        leadingIcon = { Icon(Icons.Filled.Flag, null, tint = MaterialTheme.colorScheme.secondary) },
                        singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { go() }),
                    )
                }
                IconButton(onClick = vm::swapTrip) { Icon(Icons.Filled.SwapVert, tr(no, "Swap", "Bytt")) }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = go, enabled = t.toText.isNotBlank() && !t.loading) {
                    Icon(Icons.Filled.Search, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr(no, "Find fuel", "Finn drivstoff"))
                }
                Spacer(Modifier.weight(1f))
                CORRIDORS_KM.forEach { km ->
                    FilterChip(selected = t.corridorKm == km, onClick = { vm.setCorridor(km) }, label = { Text("$km km") })
                }
            }
            Text(
                tr(no, "How far off the road a station may be.", "Hvor langt fra veien en stasjon kan ligge."),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End),
            )
            Spacer(Modifier.height(6.dp))
            FuelPicker(s.fuel, no, vm::setFuel)
        }

        // ---- result ----
        when {
            t.loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(10.dp))
                    Text(tr(no, "Finding the route…", "Finner ruten …"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            t.error != null -> Text(t.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            t.route == null -> Text(
                tr(no, "Enter where you're going. The app finds the route and the cheapest fuel along it, and you can drive there via Google Maps.",
                    "Skriv hvor du skal. Appen finner ruten og det billigste drivstoffet langs den, og du kan kjøre dit via Google Maps."),
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp),
            )
            else -> {
                val route = t.route ?: return@Column
                val cheapest = t.spots.firstOrNull { it.spot.price != null }
                TripMap(
                    route = route, spots = t.spots, cheapestId = cheapest?.spot?.station?.id, no = no,
                    onSelect = vm::select,
                    modifier = Modifier.padding(top = 10.dp).fillMaxWidth().height(250.dp),
                )
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    item(key = "sum") {
                        Text(
                            "${t.fromName} → ${t.toName} · ${Format.distance(route.distanceM, no)} · ${Trip.duration(route.durationS, no)}",
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        )
                        Text(
                            tr(no, "${t.spots.size} stations within ${t.corridorKm} km of the road · ${t.spots.count { it.spot.price != null }} with price",
                                "${t.spots.size} stasjoner innen ${t.corridorKm} km fra veien · ${t.spots.count { it.spot.price != null }} med pris"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (cheapest != null && t.from != null && t.to != null) item(key = "best") {
                        TripHero(cheapest, t.spots, s.fuel, no, now,
                            onDrive = { openUrl(ctx, Trip.directionsVia(t.from, t.to, cheapest.spot.station)) },
                            onDetails = { vm.select(cheapest.spot.station.id) })
                    }
                    if (t.spots.isEmpty()) item(key = "none") {
                        Text(tr(no, "No stations that close to the road. Try a wider distance.", "Ingen stasjoner så nær veien. Prøv en større avstand."),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(t.spots, key = { it.spot.station.id }) { ts ->
                        TripRow(ts, route.distanceM, no, now, isCheapest = ts === cheapest,
                            onClick = { vm.select(ts.spot.station.id) },
                            onDrive = { if (t.from != null && t.to != null) openUrl(ctx, Trip.directionsVia(t.from, t.to, ts.spot.station)) })
                    }
                    item(key = "attrib") {
                        Text(tr(no, "Route: OSRM · Map: © OpenStreetMap contributors", "Rute: OSRM · Kart: © OpenStreetMap-bidragsytere"),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun FuelPicker(fuel: FuelType, no: Boolean, onPick: (FuelType) -> Unit) {
    val fuels = FuelType.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        fuels.forEachIndexed { i, f ->
            SegmentedButton(selected = fuel == f, onClick = { onPick(f) },
                shape = SegmentedButtonDefaults.itemShape(i, fuels.size), icon = {}) { Text(f.label(no), maxLines = 1) }
        }
    }
}

/** "km 45 of 180 · +1.2 km detour" */
private fun where(ts: TripSpot, totalM: Double, no: Boolean): String {
    val along = "${(ts.alongM / 1000).toInt()}"
    val total = "${(totalM / 1000).toInt()}"
    return tr(no, "km $along of $total · ${detour(ts, no)}", "km $along av $total · ${detour(ts, no)}")
}

private fun detour(ts: TripSpot, no: Boolean): String =
    if (ts.detourM < 100) tr(no, "on the road", "ved veien")
    else tr(no, "+${Format.distance(ts.detourM, no)} detour", "+${Format.distance(ts.detourM, no)} omvei")

@Composable
private fun TripHero(best: TripSpot, all: List<TripSpot>, fuel: FuelType, no: Boolean, now: Long, onDrive: () -> Unit, onDetails: () -> Unit) {
    val p = best.spot.price ?: return
    val prices = all.mapNotNull { it.spot.price?.price }.sorted()
    val top = prices.lastOrNull()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Petrol, PetrolDeep)))
            .clickable(onClick = onDetails).padding(18.dp)
    ) {
        Text(tr(no, "CHEAPEST ON THE WAY · ${fuel.en.uppercase()}", "BILLIGST PÅ TUREN · ${fuel.no.uppercase()}"),
            color = Amber, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandBadge(best.spot.station.brand, best.spot.station.name, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(best.spot.station.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(tr(no, "After ${(best.alongM / 1000).toInt()} km · ${detour(best, no)}", "Etter ${(best.alongM / 1000).toInt()} km · ${detour(best, no)}"),
                    color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
            }
            Text(priceText(p, no), color = Amber, fontSize = 34.sp, fontWeight = FontWeight.Black, style = Tabular)
        }
        PriceNote(p, now, no, color = Color.White.copy(alpha = 0.75f))
        if (top != null && top - p.price > 0.004 && prices.size >= 2) {
            Spacer(Modifier.height(6.dp))
            Text(
                tr(no, "Up to ${Format.price(top - p.price, no)} kr/l cheaper than other stations on the way · about ${Format.price((top - p.price) * 50, no)} kr on 50 litres",
                    "Opptil ${Format.price(top - p.price, no)} kr/l billigere enn andre stasjoner på veien · ca. ${Format.price((top - p.price) * 50, no)} kr på 50 liter"),
                color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onDrive, colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = PetrolDeep), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Navigation, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(tr(no, "Drive via this station", "Kjør via denne stasjonen"), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TripRow(ts: TripSpot, totalM: Double, no: Boolean, now: Long, isCheapest: Boolean, onClick: () -> Unit, onDrive: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val st = ts.spot.station
    Card(
        onClick = onClick, shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        modifier = Modifier.fillMaxWidth().then(if (isCheapest) Modifier.border(1.5.dp, cs.tertiary, RoundedCornerShape(16.dp)) else Modifier),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandBadge(st.brand, st.name, 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(st.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(where(ts, totalM, no), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
                ts.spot.price?.let { PriceNote(it, now, no) }
            }
            Text(ts.spot.price?.let { priceText(it, no) } ?: "–", style = MaterialTheme.typography.titleLarge.merge(Tabular),
                fontWeight = FontWeight.Bold, color = if (ts.spot.price == null) cs.onSurfaceVariant else cs.onSurface)
            IconButton(onClick = onDrive) { Icon(Icons.Filled.Navigation, tr(no, "Drive via", "Kjør via"), tint = cs.primary) }
        }
    }
}
