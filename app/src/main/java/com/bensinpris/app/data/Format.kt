package com.bensinpris.app.data

import java.util.Locale
import kotlin.math.roundToInt

enum class Freshness { FRESH, RECENT, OLD, UNKNOWN }

/** Text for numbers and times. Pure, so it is unit-tested. */
object Format {
    private val NB = Locale.forLanguageTag("nb-NO")

    fun price(p: Double, no: Boolean): String = String.format(if (no) NB else Locale.UK, "%.2f", p)

    fun distance(m: Double, no: Boolean): String {
        val loc = if (no) NB else Locale.UK
        return when {
            m < 1000 -> "${((m / 10).roundToInt() * 10).coerceAtLeast(10)} m"
            m < 10_000 -> String.format(loc, "%.1f km", m / 1000)
            else -> "${(m / 1000).roundToInt()} km"
        }
    }

    fun freshness(updatedAt: Long?, now: Long): Freshness {
        if (updatedAt == null) return Freshness.UNKNOWN
        val days = (now - updatedAt) / DAY
        return when {
            days < 2 -> Freshness.FRESH
            days < 14 -> Freshness.RECENT
            else -> Freshness.OLD
        }
    }

    fun age(updatedAt: Long?, now: Long, no: Boolean): String {
        if (updatedAt == null) return if (no) "ukjent tid" else "unknown time"
        val ms = (now - updatedAt).coerceAtLeast(0)
        val hours = ms / HOUR
        val days = ms / DAY
        return when {
            hours < 1 -> if (no) "nå nettopp" else "just now"
            hours < 24 -> if (no) "$hours t siden" else "${hours}h ago"
            days < 2 -> if (no) "i går" else "yesterday"
            days < 14 -> if (no) "$days dager siden" else "$days days ago"
            days < 60 -> (days / 7).let { if (no) "$it uker siden" else "$it weeks ago" }
            else -> (days / 30).let { if (no) "$it mnd siden" else "$it months ago" }
        }
    }

    const val HOUR = 3_600_000L
    const val DAY = 24 * HOUR
}
