package com.bensinpris.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bensinpris.app.data.AlertBasis
import com.bensinpris.app.data.CheapAlert
import com.bensinpris.app.data.Format
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

private val SplashTop = Color(0xFF0E5A3A)
private val SplashBottom = Color(0xFF062A33)
private val Mint = Color(0xFF7BE0AE)

/**
 * Full-screen "fill up now" when fuel near you is clearly cheaper than usual.
 * Same feel as FaithScroll's opening screen: a deep gradient, things drifting slowly,
 * a glowing symbol and text that rises in.
 */
@Composable
fun CheapSplash(alert: CheapAlert, no: Boolean, onDrive: () -> Unit, onDismiss: () -> Unit) {
    val rise = remember { Animatable(0f) }
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) {
        launch { rise.animateTo(1f, tween(900)) }
        pop.animateTo(1f, tween(700))
    }
    val t = rememberInfiniteTransition(label = "cheap")
    val time by t.animateFloat(0f, 1f, infiniteRepeatable(tween(10_000, easing = LinearEasing)), label = "t")
    val glow by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "g")

    val station = alert.spot.station
    val price = alert.price
    val fuel = alert.fuel.label(no)

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(SplashTop, SplashBottom)))
            .clickable(remember { MutableInteractionSource() }, null) { },
        contentAlignment = Alignment.Center,
    ) {
        // Price arrows and bubbles drifting down: prices are falling.
        Canvas(Modifier.fillMaxSize()) { drawFalling(time) }

        Column(
            Modifier.systemBarsPadding().padding(28.dp).widthIn(max = 460.dp)
                .graphicsLayer { translationY = (1f - rise.value) * 50f; alpha = rise.value },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(132.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Brush.radialGradient(listOf(Amber.copy(alpha = 0.5f * glow), Color.Transparent), center, size.minDimension / 2))
                    drawCircle(Color.White.copy(alpha = 0.12f), size.minDimension * 0.32f)
                }
                Icon(Icons.Filled.LocalGasStation, null, tint = Amber, modifier = Modifier.size(56.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                (if (alert.example) tr(no, "EXAMPLE · ", "EKSEMPEL · ") else "") +
                    tr(no, "CHEAP ${fuel.uppercase()} NOW", "BILLIG ${fuel.uppercase()} NÅ"),
                color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(tr(no, "Fill up now!", "Fyll tanken nå!"), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                if (price.isEstimate) Text("≈ ", color = Amber.copy(alpha = 0.8f), fontSize = 30.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 10.dp))
                Text(Format.price(price.price, no), color = Amber, fontSize = 64.sp, fontWeight = FontWeight.Black, style = Tabular, lineHeight = 64.sp)
                Text(" kr/l", color = Amber.copy(alpha = 0.8f), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 11.dp))
            }

            Spacer(Modifier.height(10.dp))
            // The drop, in a chip.
            Text(
                "↓ " + Format.price(alert.drop, no) + " kr/l " + when (alert.basis) {
                    AlertBasis.LAST_WEEK -> tr(no, "below last week's average", "under snittet forrige uke")
                    AlertBasis.NATIONAL -> tr(no, "below the usual price in Norway today", "under vanlig pris i Norge i dag")
                },
                color = SplashBottom, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Mint).padding(horizontal = 14.dp, vertical = 7.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                tr(no, "About ${Format.price(alert.drop * 50, no)} kr saved on 50 litres", "Ca. ${Format.price(alert.drop * 50, no)} kr spart på 50 liter"),
                color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(22.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.08f)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandBadge(station.brand, station.name, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(station.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        listOf(station.address, Format.distance(alert.spot.distanceM, no)).filter { it.isNotBlank() }.joinToString(" · "),
                        color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp,
                    )
                    PriceNote(price, System.currentTimeMillis(), no, color = Color.White.copy(alpha = 0.6f))
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onDrive,
                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = PetrolDeep),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Icon(Icons.Filled.Navigation, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr(no, "Drive there", "Kjør dit"), fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text(tr(no, "Not now", "Ikke nå"), color = Color.White.copy(alpha = 0.8f)) }
            }
        }
    }
}

/** Small downward arrows and bubbles drifting down the screen. */
private fun DrawScope.drawFalling(t: Float) {
    for (i in 0 until 28) {
        val seed = (i * 7919 % 1000) / 1000f
        val seed2 = (i * 104729 % 1000) / 1000f
        val p = (t * (0.5f + seed2 * 0.7f) + seed) % 1f
        val x = seed * size.width + 18f * sin((p * 2 * PI + i).toFloat())
        val y = p * size.height
        val fade = sin(p * PI.toFloat()) // in at the top, out at the bottom
        if (i % 3 == 0) {
            val s = 10f + seed2 * 12f
            val arrow = Path().apply {
                moveTo(x - s * 0.55f, y - s * 0.3f); lineTo(x, y + s * 0.5f); lineTo(x + s * 0.55f, y - s * 0.3f); close()
            }
            drawPath(arrow, Mint.copy(alpha = 0.35f * fade))
        } else {
            drawCircle((if (i % 2 == 0) Amber else Color.White).copy(alpha = 0.22f * fade), 2.5f + seed2 * 4.5f, Offset(x, y))
        }
    }
}
