package com.bensinpris.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bensinpris.app.data.Format
import com.bensinpris.app.data.FuelPrice
import com.bensinpris.app.data.Freshness
import java.util.Locale

fun isNorwegian(): Boolean = Locale.getDefault().language in listOf("nb", "no", "nn")

/** Tabular figures so prices line up in the list. */
val Tabular = TextStyle(fontFeatureSettings = "tnum")

fun openUrl(ctx: Context, url: String) {
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

// Neutral, distinct colours for the station badges. Picked by brand name, so a chain always gets the same one.
private val BadgeColors = listOf(
    Color(0xFF0F4C5C), Color(0xFF6D4C9F), Color(0xFF9C4A2F), Color(0xFF2F6E3B),
    Color(0xFF3A5A9C), Color(0xFF8A6A12), Color(0xFF7A3B5C), Color(0xFF406B6B),
)

/** Initials of the chain (or station) in a coloured circle. */
@Composable
fun BrandBadge(brand: String, name: String, size: Dp = 40.dp) {
    val key = brand.ifBlank { name }
    val parts = key.split(' ', '-').filter { it.isNotBlank() }
    val initials = when {
        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}"
        parts.size == 1 -> parts[0].take(2)
        else -> "?"
    }.uppercase()
    val color = BadgeColors[(key.lowercase().hashCode() and 0x7fffffff) % BadgeColors.size]
    Box(Modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp)
    }
}

@Composable
fun freshnessColor(f: Freshness): Color = when (f) {
    Freshness.FRESH -> MaterialTheme.colorScheme.tertiary
    Freshness.RECENT -> MaterialTheme.colorScheme.secondary
    Freshness.OLD, Freshness.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** A dot and "3 dager siden", coloured by how fresh the price is. */
@Composable
fun AgeLabel(updatedAt: Long?, now: Long, no: Boolean, color: Color? = null, prefix: String = "") {
    val c = color ?: freshnessColor(Format.freshness(updatedAt, now))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(5.dp))
        Text(prefix + Format.age(updatedAt, now, no), style = MaterialTheme.typography.labelMedium, color = c)
    }
}

/** A small rounded tag. */
@Composable
fun Tag(text: String, bg: Color, fg: Color) {
    Text(
        text,
        color = fg,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** "17,99", or "≈ 22,31" for a chain's typical price. */
fun priceText(p: FuelPrice, no: Boolean): String = (if (p.isEstimate) "≈ " else "") + Format.price(p.price, no)

/** Under a price: how old it is, or that it is the chain's typical price today. */
@Composable
fun PriceNote(p: FuelPrice, now: Long, no: Boolean, color: Color? = null, prefix: String = "") {
    if (p.isEstimate) {
        Text(
            prefix + (if (no) "Typisk ${p.label} i dag" else "Typical ${p.label} today"),
            style = MaterialTheme.typography.labelMedium,
            color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
        )
    } else {
        AgeLabel(p.updatedAt, now, no, color, prefix)
    }
}
