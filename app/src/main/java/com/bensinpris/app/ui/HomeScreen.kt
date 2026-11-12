@file:OptIn(ExperimentalMaterial3Api::class)

package com.bensinpris.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.CloudOff
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bensinpris.app.BuildConfig
import com.bensinpris.app.data.Format
import com.bensinpris.app.data.FuelType
import com.bensinpris.app.data.PriceSource
import com.bensinpris.app.data.Rank
import com.bensinpris.app.data.SortBy
import com.bensinpris.app.data.Spot
import com.bensinpris.app.data.Summary

@Composable
fun HomeScreen(vm: MainViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val no = remember { isNorwegian() }
    // Recomputed whenever the list changes, so "3 t siden" stays roughly right without a ticking clock.
    val now = remember(s.spots, s.refreshing) { System.currentTimeMillis() }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        vm.onPermissionResult(r.values.any { it })
    }
    val askPermission = {
        permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    LaunchedEffect(Unit) {
        if (vm.hasPermission()) vm.onPermissionResult(true) else askPermission()
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        PullToRefreshBox(
            isRefreshing = s.refreshing,
            onRefresh = { vm.refresh(force = true) },
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "header") { Header(s, no, onRefresh = { vm.refresh(force = true) }, onSettings = { vm.openSettings(true) }) }
                item(key = "controls") { Controls(s, no, vm) }
                if (s.offline) item(key = "offline") { OfflineNote(no) }

                when {
                    s.needsPermission -> item(key = "perm") { PermissionCard(no, onAllow = askPermission, onOslo = vm::useOslo) }
                    s.here == null -> item(key = "locating") { Centered(tr(no, "Finding where you are…", "Finner ut hvor du er …")) }
                    s.stationCount == 0 -> item(key = "nodata") {
                        MessageCard(
                            tr(no, "Couldn't load the station list", "Fikk ikke lastet stasjonslisten"),
                            tr(no, "Check your internet and pull down to try again.", "Sjekk internett og dra ned for å prøve igjen."),
                        )
                    }
                    s.spots.isEmpty() -> item(key = "empty") {
                        MessageCard(
                            tr(no, "No stations within ${s.radiusKm} km", "Ingen stasjoner innen ${s.radiusKm} km"),
                            tr(no, "Try a larger distance above.", "Prøv en større avstand over."),
                        )
                    }
                    else -> {
                        val summary = s.summary
                        if (summary?.cheapest != null) {
                            item(key = "hero") {
                                HeroCard(summary.cheapest, summary, s.fuel, no, now,
                                    onDrive = { openUrl(ctx, Rank.directionsUrl(summary.cheapest.station)) },
                                    onDetails = { vm.select(summary.cheapest.station.id) })
                            }
                        } else {
                            item(key = "noprices") {
                                MessageCard(
                                    tr(no, "No ${s.fuel.en.lowercase()} prices nearby yet",
                                        "Ingen priser på ${s.fuel.no.lowercase()} her ennå"),
                                    tr(no, "Prices come from drivers like you. Tap a station to add the price you see at the pump.",
                                        "Prisene kommer fra bilister som deg. Trykk på en stasjon for å legge inn prisen du ser ved pumpa."),
                                )
                            }
                        }
                        item(key = "listhead") { ListHeader(s, no, onSort = vm::setSort) }
                        items(s.spots, key = { it.station.id }) { spot ->
                            StationRow(
                                spot, no, now,
                                isCheapest = spot.station.id == summary?.cheapest?.station?.id,
                                onClick = { vm.select(spot.station.id) },
                                onDrive = { openUrl(ctx, Rank.directionsUrl(spot.station)) },
                            )
                        }
                    }
                }
                item(key = "footer") { Footer(s.pricesFetchedAt, s.chainsFetchedAt, s.stationCount, no, now) }
            }
        }
    }

    if (s.showSettings) {
        SettingsSheet(
            no = no, keySet = s.googleKeySet, status = s.googleStatus,
            onSave = vm::saveGoogleKey, onDismiss = { vm.openSettings(false) },
        )
    }

    s.selected?.let { spot ->
        StationSheet(
            spot = spot, fuel = s.fuel, no = no, now = now,
            onDismiss = { vm.select(null) },
            onSave = { fuel, price -> vm.saveMyPrice(spot.station.id, fuel, price) },
            onRemove = { fuel -> vm.removeMyPrice(spot.station.id, fuel) },
        )
    }
}

fun tr(no: Boolean, en: String, nb: String) = if (no) nb else en

// ---------- header & controls ----------

@Composable
private fun Header(s: UiState, no: Boolean, onRefresh: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("BensinPris", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (s.here?.isFallback == false) Icons.Filled.MyLocation else Icons.Filled.LocationOn, null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(5.dp))
                val where = when {
                    s.here == null -> tr(no, "Locating…", "Finner posisjon …")
                    s.here.isFallback -> tr(no, "Oslo sentrum (no location)", "Oslo sentrum (uten posisjon)")
                    s.place != null -> tr(no, "Near ${s.place}", "Nær ${s.place}")
                    else -> tr(no, "Near you", "Nær deg")
                }
                Text(where, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (s.refreshing || s.locating) {
            CircularProgressIndicator(Modifier.padding(12.dp).size(22.dp), strokeWidth = 2.5.dp)
        } else {
            IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, tr(no, "Refresh", "Oppdater")) }
        }
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, tr(no, "Settings", "Innstillinger")) }
    }
}

@Composable
private fun Controls(s: UiState, no: Boolean, vm: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val fuels = FuelType.entries
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            fuels.forEachIndexed { i, f ->
                SegmentedButton(
                    selected = s.fuel == f,
                    onClick = { vm.setFuel(f) },
                    shape = SegmentedButtonDefaults.itemShape(i, fuels.size),
                    icon = {},
                ) { Text(f.label(no), maxLines = 1) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MainViewModel.RADII_KM.forEach { km ->
                FilterChip(selected = s.radiusKm == km, onClick = { vm.setRadius(km) }, label = { Text("$km km") })
            }
        }
    }
}

// ---------- the cheapest station ----------

@Composable
private fun HeroCard(
    spot: Spot, summary: Summary, fuel: FuelType, no: Boolean, now: Long,
    onDrive: () -> Unit, onDetails: () -> Unit,
) {
    val price = spot.price ?: return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Petrol, PetrolDeep)))
            .clickable(onClick = onDetails)
            .padding(20.dp)
    ) {
        Text(
            tr(no, "CHEAPEST NEAR YOU · ${fuel.en.uppercase()}", "BILLIGST NÆR DEG · ${fuel.no.uppercase()}"),
            color = Amber, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandBadge(spot.station.brand, spot.station.name, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(spot.station.name, color = Color.White, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(spot.station.address, Format.distance(spot.distanceM, no)).filter { it.isNotBlank() }.joinToString(" · "),
                    color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            if (price.isEstimate) {
                Text("≈", color = Amber.copy(alpha = 0.8f), fontSize = 34.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp, end = 6.dp))
            }
            Text(Format.price(price.price, no), color = Amber, fontSize = 56.sp, fontWeight = FontWeight.Black,
                style = Tabular, lineHeight = 56.sp)
            Text(" kr/l", color = Amber.copy(alpha = 0.8f), fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 9.dp))
        }
        PriceNote(
            price, now, no, color = Color.White.copy(alpha = 0.8f),
            prefix = when (price.source) {
                PriceSource.ME -> tr(no, "Your price · ", "Din pris · ")
                PriceSource.COMMUNITY -> tr(no, "Reported ", "Meldt ")
                PriceSource.CHAIN, PriceSource.WEB, PriceSource.GOOGLE -> ""
            },
        )
        if (price.isEstimate) {
            Text(tr(no, "Not measured at this station. Check the pump.", "Ikke målt på stasjonen. Sjekk pumpa."),
                color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
        }
        val save = summary.savingPerLitre
        if (save > 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                tr(no,
                    "${Format.price(save, no)} kr/l below the middle price of ${summary.priced} stations here · about ${Format.price(save * 50, no)} kr on 50 litres",
                    "${Format.price(save, no)} kr/l under midtprisen for ${summary.priced} stasjoner her · ca. ${Format.price(save * 50, no)} kr spart på 50 liter"),
                color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onDrive,
                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = PetrolDeep),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Navigation, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr(no, "Drive here", "Kjør hit"), fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onDetails,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
            ) { Text(tr(no, "Details", "Detaljer")) }
        }
    }
}

// ---------- list ----------

@Composable
private fun ListHeader(s: UiState, no: Boolean, onSort: (SortBy) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        val priced = s.summary?.priced ?: 0
        Text(
            tr(no, "${s.spots.size} stations · $priced with price", "${s.spots.size} stasjoner · $priced med pris"),
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onSort(if (s.sort == SortBy.PRICE) SortBy.DISTANCE else SortBy.PRICE) }) {
            Icon(Icons.Filled.SwapVert, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (s.sort == SortBy.PRICE) tr(no, "Cheapest first", "Billigst først") else tr(no, "Closest first", "Nærmest først"))
        }
    }
}

@Composable
private fun StationRow(spot: Spot, no: Boolean, now: Long, isCheapest: Boolean, onClick: () -> Unit, onDrive: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        modifier = Modifier.fillMaxWidth()
            .then(if (isCheapest) Modifier.border(1.5.dp, cs.tertiary, RoundedCornerShape(18.dp)) else Modifier),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandBadge(spot.station.brand, spot.station.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(spot.station.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(spot.station.address, spot.station.city).filter { it.isNotBlank() }.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                spot.price?.let { p -> PriceNote(p, now, no) }
            }
            Spacer(Modifier.width(8.dp))
            // Price on top, distance from you right under it.
            Column(horizontalAlignment = Alignment.End) {
                val p = spot.price
                if (p != null) {
                    Text(priceText(p, no), style = MaterialTheme.typography.titleLarge.merge(Tabular), fontWeight = FontWeight.Bold,
                        color = if (p.isEstimate) cs.onSurface.copy(alpha = 0.75f) else cs.onSurface)
                } else {
                    Text("–", style = MaterialTheme.typography.titleLarge, color = cs.onSurfaceVariant)
                }
                Text(Format.distance(spot.distanceM, no), style = MaterialTheme.typography.labelLarge.merge(Tabular),
                    color = cs.primary, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = onDrive) {
                Icon(Icons.Filled.Navigation, tr(no, "Directions", "Veibeskrivelse"), tint = cs.primary)
            }
        }
    }
}

// ---------- states ----------

@Composable
private fun PermissionCard(no: Boolean, onAllow: () -> Unit, onOslo: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(20.dp)) {
            Icon(Icons.Filled.LocalGasStation, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(10.dp))
            Text(tr(no, "Where are you?", "Hvor er du?"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(tr(no, "BensinPris uses your location only on this phone, to find the stations around you.",
                "BensinPris bruker posisjonen din bare på telefonen, for å finne stasjonene rundt deg."))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onAllow) { Text(tr(no, "Use my location", "Bruk posisjonen min")) }
                TextButton(onClick = onOslo) { Text(tr(no, "Show Oslo", "Vis Oslo")) }
            }
        }
    }
}

@Composable
private fun MessageCard(title: String, body: String) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OfflineNote(no: Boolean) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CloudOff, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Spacer(Modifier.width(8.dp))
        Text(tr(no, "Couldn't update. Showing the last saved data.", "Fikk ikke oppdatert. Viser sist lagrede data."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun Centered(text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Footer(pricesAt: Long, chainsAt: Long, stationCount: Int, no: Boolean, now: Long) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val updated = if (pricesAt > 0) Format.age(pricesAt, now, no) else tr(no, "from the app", "fra appen")
        val chains = if (chainsAt > 0) Format.age(chainsAt, now, no) else tr(no, "not yet", "ikke ennå")
        Text(
            tr(no,
                "$stationCount stations. ≈ prices are the chain's typical price today, not this station's. Reported prices may be out of date; check the pump. Driver prices updated $updated, chain prices $chains.",
                "$stationCount stasjoner. ≈-priser er kjedens typiske pris i dag, ikke stasjonens egen. Meldte priser kan være utdatert; sjekk pumpa. Bilistpriser hentet $updated, kjedepriser $chains."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            tr(no, "Data: Drivstoffpriser (ODbL) · © OpenStreetMap contributors · chain prices: DrivstoffAppen · station prices: drivstoffapp.no, Google",
                "Data: Drivstoffpriser (ODbL) · © OpenStreetMap-bidragsytere · kjedepriser: DrivstoffAppen · stasjonspriser: drivstoffapp.no, Google") +
                " · v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
