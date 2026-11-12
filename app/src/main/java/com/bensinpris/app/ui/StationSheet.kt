@file:OptIn(ExperimentalMaterial3Api::class)

package com.bensinpris.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bensinpris.app.data.Format
import com.bensinpris.app.data.FuelType
import com.bensinpris.app.data.Parse
import com.bensinpris.app.data.PriceSource
import com.bensinpris.app.data.Rank
import com.bensinpris.app.data.Spot

@Composable
fun StationSheet(
    spot: Spot, fuel: FuelType, no: Boolean, now: Long,
    onDismiss: () -> Unit,
    onSave: (FuelType, Double) -> Unit,
    onRemove: (FuelType) -> Unit,
) {
    val ctx = LocalContext.current
    val st = spot.station
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandBadge(st.brand, st.name, 48.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(st.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        listOf(st.address, st.city).filter { it.isNotBlank() }.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    )
                    Text(tr(no, "${Format.distance(spot.distanceM, no)} away", "${Format.distance(spot.distanceM, no)} unna"),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { openUrl(ctx, Rank.directionsUrl(st)) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Navigation, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr(no, "Directions", "Veibeskrivelse"))
                }
                OutlinedButton(onClick = { openUrl(ctx, Rank.placeUrl(st)) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Map, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr(no, "Open in Maps", "Åpne i Maps"))
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(tr(no, "Prices", "Priser"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            FuelType.entries.forEach { f ->
                val p = spot.allPrices[f]
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.label(no), style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (f == fuel) FontWeight.Bold else FontWeight.Normal)
                        if (p != null) {
                            val who = when (p.source) {
                                PriceSource.ME -> tr(no, "Your price · ", "Din pris · ")
                                PriceSource.COMMUNITY -> if (p.reports > 1)
                                    tr(no, "${p.reports} reports · ", "${p.reports} meldinger · ") else ""
                                PriceSource.CHAIN -> ""
                            }
                            PriceNote(p, now, no, prefix = who)
                        }
                    }
                    if (p != null) {
                        Text("${priceText(p, no)} kr", style = MaterialTheme.typography.titleLarge.merge(Tabular),
                            fontWeight = FontWeight.Bold)
                        if (p.source == PriceSource.ME) {
                            IconButton(onClick = { onRemove(f) }) {
                                Icon(Icons.Filled.Close, tr(no, "Remove my price", "Fjern min pris"), tint = cs.onSurfaceVariant)
                            }
                        }
                    } else {
                        Text(tr(no, "no price", "ingen pris"), color = cs.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = cs.outlineVariant)
            }

            Spacer(Modifier.height(20.dp))
            AddPrice(fuel, no, onSave)
        }
    }
}

/** Type in the price on the pump. Saved on this phone and used before older reports. */
@Composable
private fun AddPrice(initialFuel: FuelType, no: Boolean, onSave: (FuelType, Double) -> Unit) {
    var fuel by remember { mutableStateOf(initialFuel) }
    var text by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val value = text.replace(',', '.').toDoubleOrNull()
    val valid = value != null && Parse.plausible(value)

    Column(
        Modifier.fillMaxWidth()
            .padding(0.dp)
    ) {
        Text(tr(no, "See a different price?", "Ser du en annen pris?"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            tr(no, "Type what the pump says. It's kept on this phone and shown until a newer price comes in.",
                "Skriv inn det som står på pumpa. Den lagres på telefonen og vises til en nyere pris kommer inn."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FuelType.entries.forEach { f ->
                FilterChip(selected = fuel == f, onClick = { fuel = f; saved = false }, label = { Text(f.label(no)) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { t -> text = t.filter { it.isDigit() || it == ',' || it == '.' }.take(6); saved = false },
                label = { Text(tr(no, "Price per litre", "Pris per liter")) },
                suffix = { Text("kr") },
                singleLine = true,
                isError = text.isNotEmpty() && !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = { onSave(fuel, value!!); text = ""; saved = true; focus.clearFocus() },
                enabled = valid,
            ) { Text(tr(no, "Save", "Lagre")) }
        }
        if (saved) {
            Spacer(Modifier.height(6.dp))
            Text(tr(no, "Saved. Thanks!", "Lagret. Takk!"), color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
