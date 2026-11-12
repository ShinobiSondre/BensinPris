@file:OptIn(ExperimentalMaterial3Api::class)

package com.bensinpris.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** Settings: your own Google Maps key for station prices from Google. */
@Composable
fun SettingsSheet(
    no: Boolean, keySet: Boolean, status: String?,
    onSave: (String) -> Unit, onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    var key by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text(tr(no, "Prices from Google", "Priser fra Google"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                tr(no,
                    "Paste your Google Maps API key (Places API (New)) to get the prices Google Maps shows, with the time they were updated. The key is kept only on this phone. The app asks Google at most every 30 minutes, or when you move more than 1 km.",
                    "Lim inn din Google Maps API-nøkkel (Places API (New)) for å få prisene Google Maps viser, med tidspunkt. Nøkkelen lagres bare på telefonen. Appen spør Google høyst hvert 30. minutt, eller når du flytter deg mer enn 1 km."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = key,
                onValueChange = { key = it.trim() },
                label = { Text(if (keySet) tr(no, "Key saved. Paste a new one to replace it", "Nøkkel lagret. Lim inn ny for å bytte") else "API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row {
                Button(onClick = { onSave(key); key = "" }, enabled = key.length >= 20) { Text(tr(no, "Save and test", "Lagre og test")) }
                Spacer(Modifier.width(8.dp))
                if (keySet) TextButton(onClick = { onSave("") }) { Text(tr(no, "Remove key", "Fjern nøkkel")) }
            }
            if (status != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    status, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    color = if (status.startsWith("Feil") || status.startsWith("Error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                )
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { openUrl(ctx, "https://console.cloud.google.com/apis/library/places.googleapis.com") }) {
                Text(tr(no, "Get a key in Google Cloud", "Skaff nøkkel i Google Cloud"))
            }
        }
    }
}
