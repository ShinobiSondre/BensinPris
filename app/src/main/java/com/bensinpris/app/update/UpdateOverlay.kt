package com.bensinpris.app.update

import com.bensinpris.app.util.tr

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** The update card shown over the app while an update is on its way. */
@Composable
fun UpdateOverlay() {
    val u = Updater
    val ctx = LocalContext.current
    val show = u.stage != UpdateStage.Idle
    AnimatedVisibility(show, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
                .clickable(remember { MutableInteractionSource() }, null) { },
            contentAlignment = Alignment.Center
        ) {
            val (title, body) = when (u.stage) {
                UpdateStage.Downloading -> tr("A new BensinPris is here!", "En ny BensinPris er her!") to
                    tr("Downloading build ${u.newBuild}…", "Laster ned versjon ${u.newBuild} …")
                UpdateStage.NeedsPermission -> tr("One quick switch", "Én liten bryter") to
                    tr("Android needs to let BensinPris update itself. Tap Allow, turn the switch on, then come back.",
                        "Android må la BensinPris oppdatere seg selv. Trykk Tillat, slå på bryteren og kom tilbake.")
                UpdateStage.Installing -> tr("Installing…", "Installerer …") to
                    tr("Tap Update. When it's done, tap Open to start the new version.",
                        "Trykk Oppdater. Når den er ferdig, trykk Åpne for å starte den nye versjonen.")
                UpdateStage.Ready -> tr("Ready to update", "Klar til å oppdatere") to
                    tr("Build ${u.newBuild} is downloaded.", "Versjon ${u.newBuild} er lastet ned.")
                UpdateStage.Failed -> tr("No update this time", "Ingen oppdatering nå") to u.error.orEmpty()
                UpdateStage.Idle -> "" to ""
            }
            Surface(
                modifier = Modifier.padding(24.dp).widthIn(max = 380.dp).clip(RoundedCornerShape(22.dp)),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    if (u.newBuild > 0) {
                        Text(tr("Build ${u.currentBuild}  →  ${u.newBuild}", "Versjon ${u.currentBuild}  →  ${u.newBuild}"),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(body, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    if (u.stage == UpdateStage.Downloading) {
                        Spacer(Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { u.progress },
                            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(8.dp)),
                        )
                        Spacer(Modifier.height(6.dp))
                        val mb = u.totalBytes / (1024f * 1024f)
                        val pct = (u.progress * 100).roundToInt().coerceIn(0, 100)
                        Text(
                            if (mb > 0) "$pct %  ·  ${"%.1f".format(mb * u.progress)} / ${"%.1f".format(mb)} MB" else "$pct %",
                            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (u.stage != UpdateStage.Failed) TextButton(onClick = { u.later() }) { Text(tr("Later", "Senere")) }
                        when (u.stage) {
                            UpdateStage.NeedsPermission -> Button(onClick = { u.allowInstalls(ctx) }) { Text(tr("Allow", "Tillat")) }
                            UpdateStage.Ready -> Button(onClick = { u.installNow(ctx) }) { Text(tr("Update now", "Oppdater nå")) }
                            UpdateStage.Failed -> Button(onClick = { u.later() }, colors = ButtonDefaults.buttonColors()) { Text("OK") }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}
